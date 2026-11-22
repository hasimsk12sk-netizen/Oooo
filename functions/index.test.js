const test = require("node:test");
const assert = require("node:assert/strict");
const {
  onApplicationPublished,
  _internal: { DATABASE_ID, NOTIFICATION_CHANNEL_ID, isValidHttpUrl, handleApplicationPublishedEvent },
} = require("./index.js");

function createFakeDb({ initialTokens = [], initialDispatches = {} } = {}) {
  const dispatches = { ...initialDispatches };
  const deletedTokenIds = [];

  return {
    dispatches,
    deletedTokenIds,
    collection(name) {
      if (name === "notification_dispatches") {
        return {
          doc(id) {
            return {
              id,
              async update(patch) {
                dispatches[id] = { ...(dispatches[id] || {}), ...patch };
              },
            };
          },
        };
      }
      if (name === "fcm_tokens") {
        return {
          where(field, op, value) {
            assert.equal(field, "notificationsAllowed");
            assert.equal(op, "==");
            assert.equal(value, true);
            return {
              async get() {
                const eligible = initialTokens.filter((t) => t.notificationsAllowed === true);
                return {
                  forEach(cb) {
                    eligible.forEach((item) => {
                      cb({
                        ref: { id: item.id },
                        data: () => item,
                      });
                    });
                  },
                };
              },
            };
          },
        };
      }
      throw new Error(`Unexpected collection ${name}`);
    },
    async runTransaction(fn) {
      const tx = {
        async get(ref) {
          const val = dispatches[ref.id];
          return {
            exists: Boolean(val),
            data: () => val,
          };
        },
        set(ref, payload) {
          dispatches[ref.id] = payload;
        },
      };
      return fn(tx);
    },
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push(ref.id);
        },
        async commit() {
          deletedTokenIds.push(...ops);
        },
      };
    },
  };
}

test("Cloud Function exports onApplicationPublished and valid HTTP URL helper", () => {
  assert.ok(onApplicationPublished);
  assert.equal(DATABASE_ID, "ai-studio-android-storepro-deb69b25-7996-4b04-92a9-80ae1a6a1384");
  assert.equal(isValidHttpUrl("https://example.com/icon.png"), true);
  assert.equal(isValidHttpUrl("http://example.com/icon.png"), true);
  assert.equal(isValidHttpUrl("ftp://example.com/icon.png"), false);
});

test("Publishes FCM notification to eligible devices with app details, deep link, dedup, and stale token pruning", async () => {
  const db = createFakeDb({
    initialTokens: [
      { id: "dev_valid", token: "valid_fcm_token_0001", notificationsAllowed: true },
      { id: "dev_stale", token: "stale_fcm_token_0002", notificationsAllowed: true },
      { id: "dev_muted", token: "muted_fcm_token_0003", notificationsAllowed: false },
    ],
  });

  const sentPayloads = [];
  const messaging = {
    async sendEachForMulticast(msg) {
      sentPayloads.push(msg);
      return {
        successCount: 1,
        failureCount: 1,
        responses: [
          { success: true },
          { success: false, error: { code: "messaging/registration-token-not-registered" } },
        ],
      };
    },
  };

  const event = {
    params: { appId: "app_nova_1" },
    data: {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          appName: "Nova Player Pro",
          developerName: "Nova Studio",
          iconUrl: "https://example.com/nova.png",
          status: "published",
        }),
      },
    },
  };

  const result = await handleApplicationPublishedEvent(event, { db, messaging });
  assert.deepEqual(result, { deliveredCount: 1, failureCount: 1, prunedTokens: 1 });

  assert.equal(sentPayloads.length, 1);
  assert.deepEqual(sentPayloads[0].tokens, ["valid_fcm_token_0001", "stale_fcm_token_0002"]);
  assert.equal(sentPayloads[0].data.appId, "app_nova_1");
  assert.equal(sentPayloads[0].data.appName, "Nova Player Pro");
  assert.equal(sentPayloads[0].data.click_action, "OPEN_APP_DETAILS");
  assert.equal(sentPayloads[0].android.notification.channelId, NOTIFICATION_CHANNEL_ID);
  assert.match(sentPayloads[0].notification.body, /Nova Player Pro/);
  assert.deepEqual(db.deletedTokenIds, ["dev_stale"]);
  assert.equal(db.dispatches["app_nova_1"].dispatched, true);

  // Duplicate retry / re-delivery must be deduplicated cleanly
  const retryResult = await handleApplicationPublishedEvent(event, { db, messaging });
  assert.equal(retryResult, null);
  assert.equal(sentPayloads.length, 1);
});

test("Ignores draft applications and updates to already-published applications", async () => {
  const db = createFakeDb();
  let sendCount = 0;
  const messaging = {
    async sendEachForMulticast() {
      sendCount++;
      return { successCount: 0, failureCount: 0, responses: [] };
    },
  };

  // Draft should not trigger notification
  const draftRes = await handleApplicationPublishedEvent(
    {
      params: { appId: "app_draft" },
      data: {
        before: { exists: false },
        after: { exists: true, data: () => ({ appName: "Draft App", status: "draft" }) },
      },
    },
    { db, messaging }
  );
  assert.equal(draftRes, null);

  // Update to already published app should not re-notify
  const updateRes = await handleApplicationPublishedEvent(
    {
      params: { appId: "app_live" },
      data: {
        before: { exists: true, data: () => ({ appName: "Live App", status: "published" }) },
        after: { exists: true, data: () => ({ appName: "Live App v2", status: "published" }) },
      },
    },
    { db, messaging }
  );
  assert.equal(updateRes, null);
  assert.equal(sendCount, 0);
});
