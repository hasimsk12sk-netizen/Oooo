const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { getApps, initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

if (getApps().length === 0) {
  initializeApp();
}

const DATABASE_ID = "ai-studio-android-storepro-deb69b25-7996-4b04-92a9-80ae1a6a1384";
const NOTIFICATION_CHANNEL_ID = "storepro_new_apps_channel";

function isValidHttpUrl(url) {
  return (
    typeof url === "string" &&
    (url.startsWith("https://") || url.startsWith("http://")) &&
    url.length >= 10 &&
    url.length <= 2000
  );
}

/**
 * Core handler for application publication events.
 * Extracted cleanly with optional dependency injection for deterministic unit testing.
 */
async function handleApplicationPublishedEvent(event, deps = null) {
  const appId = event?.params?.appId;
  if (!appId) {
    return null;
  }

  const afterSnap = event.data ? event.data.after : null;
  if (!afterSnap || !afterSnap.exists) {
    return null;
  }

  const afterData = afterSnap.data() || {};
  const beforeData =
    event.data && event.data.before && event.data.before.exists
      ? event.data.before.data()
      : null;

  if (afterData.status !== "published") {
    return null;
  }

  // Only notify when newly created as published OR transitioned from non-published to published
  const wasAlreadyPublished = Boolean(beforeData && beforeData.status === "published");
  if (wasAlreadyPublished) {
    return null;
  }

  const db = deps?.db || getFirestore(DATABASE_ID);
  const messaging = deps?.messaging || getMessaging();
  const dispatchRef = db.collection("notification_dispatches").doc(appId);

  // Transactional idempotency guard prevents duplicate notifications on function retries
  const shouldDispatch = await db.runTransaction(async (tx) => {
    const existingDispatch = await tx.get(dispatchRef);
    if (existingDispatch.exists && existingDispatch.data()?.dispatched === true) {
      return false;
    }
    tx.set(dispatchRef, {
      appId,
      dispatched: true,
      dispatchedAt: FieldValue.serverTimestamp(),
    });
    return true;
  });

  if (!shouldDispatch) {
    return null;
  }

  const appName = (afterData.appName || "New Application").toString().trim().slice(0, 100);
  const developerName = (afterData.developerName || "").toString().trim().slice(0, 80);
  const iconUrl = isValidHttpUrl(afterData.iconUrl) ? afterData.iconUrl : "";
  const title = "New App Available on STORE PRO!";
  const body = developerName
    ? `${appName} by ${developerName} is now available on STORE PRO.`
    : `${appName} has just been uploaded. Explore it now on STORE PRO.`;
  const eventId = `pub_${appId}_${Date.now()}`;

  // Fetch all eligible registered FCM tokens where notifications are permitted
  const tokenSnap = await db
    .collection("fcm_tokens")
    .where("notificationsAllowed", "==", true)
    .get();

  const tokenEntries = [];
  tokenSnap.forEach((doc) => {
    const data = doc.data();
    if (typeof data.token === "string" && data.token.length >= 10) {
      tokenEntries.push({ ref: doc.ref, token: data.token });
    }
  });

  if (tokenEntries.length === 0) {
    await dispatchRef.update({ deliveredCount: 0, failureCount: 0, prunedTokens: 0 });
    return { deliveredCount: 0, failureCount: 0, prunedTokens: 0 };
  }

  let deliveredCount = 0;
  let failureCount = 0;
  const staleTokenRefs = [];

  // FCM multicast limit is 500 tokens per batch
  for (let i = 0; i < tokenEntries.length; i += 500) {
    const batch = tokenEntries.slice(i, i + 500);
    const multicastMessage = {
      tokens: batch.map((entry) => entry.token),
      notification: {
        title,
        body,
        ...(iconUrl ? { imageUrl: iconUrl } : {}),
      },
      data: {
        appId: String(appId),
        appName,
        developerName,
        iconUrl,
        eventId,
        title,
        body,
        click_action: "OPEN_APP_DETAILS",
      },
      android: {
        priority: "high",
        notification: {
          channelId: NOTIFICATION_CHANNEL_ID,
          clickAction: "OPEN_APP_DETAILS",
          ...(iconUrl ? { imageUrl: iconUrl } : {}),
        },
      },
    };

    const response = await messaging.sendEachForMulticast(multicastMessage);
    deliveredCount += response.successCount;
    failureCount += response.failureCount;

    response.responses.forEach((res, idx) => {
      if (!res.success && res.error) {
        const code = res.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token"
        ) {
          staleTokenRefs.push(batch[idx].ref);
        }
      }
    });
  }

  // Prune stale tokens in chunks of 500 to stay within Firestore batch write limits
  for (let i = 0; i < staleTokenRefs.length; i += 500) {
    const writeBatch = db.batch();
    staleTokenRefs.slice(i, i + 500).forEach((ref) => writeBatch.delete(ref));
    await writeBatch.commit();
  }

  await dispatchRef.update({
    deliveredCount,
    failureCount,
    prunedTokens: staleTokenRefs.length,
  });

  return { deliveredCount, failureCount, prunedTokens: staleTokenRefs.length };
}

/**
 * Automatically sends an FCM push notification whenever an administrator publishes
 * an application (created as status == "published" or transitioned to status == "published").
 */
exports.onApplicationPublished = onDocumentWritten(
  {
    document: "applications/{appId}",
    database: DATABASE_ID,
    region: "asia-east1",
  },
  async (event) => handleApplicationPublishedEvent(event)
);

exports._internal = {
  DATABASE_ID,
  NOTIFICATION_CHANNEL_ID,
  isValidHttpUrl,
  handleApplicationPublishedEvent,
};
