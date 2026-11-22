const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_hasim_uid";
const ADMIN_EMAIL = "hasimsk12345678sk@gmail.com";
const USER_ALICE = "alice_uid_123";
const USER_BOB = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validApplicationPayload(appId, uploadedBy) {
  return {
    appId,
    appName: "STORE PRO Player",
    developerName: "VideoLAN Studio",
    version: "3.5.4",
    description: "Universal Android media player.",
    iconUrl: "https://example.com/icon.png",
    apkUrl: "https://example.com/app.apk",
    screenshots: ["http://example.com/shot1.webp", "https://example.com/shot2.png"],
    whatsNew: "Performance boosts",
    privacyPolicyUrl: "https://example.com/privacy",
    packageId: "org.videolan.vlc",
    fileSizeBytes: 35651584,
    downloads: 10,
    averageRating: 4.8,
    ratingCount: 5,
    status: "published",
    verificationStatus: "verified",
    uploadedBy,
    createdAt: Date.now() - 5000,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated guests can browse applications & reviews and increment downloads by +1 only", async () => {
  const appId = "app_public_1";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("applications").doc(appId).set(validApplicationPayload(appId, ADMIN_UID));
    await db.collection("reviews").doc("rev_alice_1").set({
      reviewId: "rev_alice_1",
      appId,
      userId: USER_ALICE,
      userName: "Alice",
      userAvatar: null,
      rating: 5,
      comment: "Great app!",
      timestamp: Date.now() - 2000,
    });
  });

  const guestDb = testEnv.unauthenticatedContext().firestore();

  // Guest can read applications and reviews
  await assertSucceeds(guestDb.collection("applications").get());
  await assertSucceeds(guestDb.collection("reviews").where("appId", "==", appId).get());

  // Guest can increment download counter by +1
  await assertSucceeds(
    guestDb.collection("applications").doc(appId).update({
      downloads: 11,
    })
  );

  // Regular signed-in user increments the same downloads counter by +1
  const userDb = testEnv
    .authenticatedContext(USER_ALICE, { email: "alice@example.com", email_verified: true })
    .firestore();
  await assertSucceeds(
    userDb.collection("applications").doc(appId).update({
      downloads: 12,
    })
  );

  // Neither guest nor user can inflate downloads by +2 or overwrite apkUrl
  await assertFails(
    guestDb.collection("applications").doc(appId).update({
      downloads: 14,
    })
  );
  await assertFails(
    userDb.collection("applications").doc(appId).update({
      downloads: 14,
    })
  );
  await assertFails(
    guestDb.collection("applications").doc(appId).update({
      apkUrl: "https://malicious.example.com/fake.apk",
    })
  );

  // Guest CANNOT create or delete applications or read private user profiles
  await assertFails(
    guestDb.collection("applications").doc("app_guest_hack").set(validApplicationPayload("app_guest_hack", "guest"))
  );
  await assertFails(guestDb.collection("applications").doc(appId).delete());
  await assertFails(guestDb.collection("users").doc(USER_ALICE).get());
});

test("Regular signed-in user and unverified-email spoofed admin CANNOT upload, edit catalog, or delete apps", async () => {
  const appId = "app_admin_1";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("applications").doc(appId).set(validApplicationPayload(appId, ADMIN_UID));
  });

  const regularUserDb = testEnv
    .authenticatedContext(USER_ALICE, { email: "alice@example.com", email_verified: true })
    .firestore();

  await assertFails(
    regularUserDb.collection("applications").doc("app_user_upload").set(validApplicationPayload("app_user_upload", USER_ALICE))
  );
  await assertFails(
    regularUserDb.collection("applications").doc(appId).update({ appName: "Hijacked Name" })
  );
  await assertFails(regularUserDb.collection("applications").doc(appId).delete());

  // Email spoofing check: matching admin email with email_verified=false must be rejected
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();

  await assertFails(
    spoofedAdminDb.collection("applications").doc("app_spoof").set(validApplicationPayload("app_spoof", "spoof_uid"))
  );
});

test("Verified Administrator CAN publish, update catalog fields, and delete applications", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const appId = "app_verified_1";
  const payload = validApplicationPayload(appId, ADMIN_UID);

  // Publish
  await assertSucceeds(adminDb.collection("applications").doc(appId).set(payload));

  // Partial update mirroring StoreProRepository.updateApplication
  await assertSucceeds(
    adminDb.collection("applications").doc(appId).update({
      appName: "STORE PRO Player HD",
      version: "3.6.0",
      iconUrl: "http://cdn.example.com/new-icon.gif",
      screenshots: ["https://cdn.example.com/s1.webp"],
      whatsNew: "Added universal HTTP image support",
    })
  );

  // Reject shadow/ghost field injection even for admin
  await assertFails(
    adminDb.collection("applications").doc(appId).update({
      ghostPrivilegeField: true,
    })
  );

  // Reject mutating immutable uploadedBy
  await assertFails(
    adminDb.collection("applications").doc(appId).update({
      uploadedBy: USER_BOB,
    })
  );

  // Delete
  await assertSucceeds(adminDb.collection("applications").doc(appId).delete());
});

test("Authenticated users can manage only their own profiles, saved apps, downloads, and reviews", async () => {
  const aliceDb = testEnv
    .authenticatedContext(USER_ALICE, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(USER_BOB, { email: "bob@example.com", email_verified: true })
    .firestore();

  // Owner profile write/read succeeds; cross-user read/write fails
  await assertSucceeds(
    aliceDb.collection("users").doc(USER_ALICE).set({
      userId: USER_ALICE,
      email: "alice@example.com",
      displayName: "Alice",
      username: "alice123",
      avatarUrl: null,
    })
  );
  await assertFails(bobDb.collection("users").doc(USER_ALICE).get());
  await assertFails(
    bobDb.collection("users").doc(USER_ALICE).update({ displayName: "Hacked" })
  );

  // Saved apps & downloads subcollections
  await assertSucceeds(
    aliceDb.collection("users").doc(USER_ALICE).collection("saved_apps").doc("seed_vlc").set({
      appId: "seed_vlc",
      appName: "VLC",
      developerName: "VideoLAN",
      iconUrl: "https://example.com/vlc.png",
      version: "3.5.4",
      savedAt: Date.now(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(USER_ALICE).collection("saved_apps").get()
  );

  // Reviews: Alice creates review, updates averageRating/ratingCount; Bob cannot edit/delete Alice's review
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("applications").doc("seed_vlc").set(validApplicationPayload("seed_vlc", ADMIN_UID));
  });

  const revId = `rev_${USER_ALICE}_seed_vlc`;
  await assertSucceeds(
    aliceDb.collection("reviews").doc(revId).set({
      reviewId: revId,
      appId: "seed_vlc",
      userId: USER_ALICE,
      userName: "Alice",
      userAvatar: null,
      rating: 5,
      comment: "Awesome!",
      timestamp: Date.now(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("applications").doc("seed_vlc").update({
      averageRating: 5.0,
      ratingCount: 1,
    })
  );
  await assertFails(bobDb.collection("reviews").doc(revId).delete());
});

test("Legacy application documents with Timestamp createdAt and omitted optional fields support guest +1 downloads and admin updates", async () => {
  const legacyId = "legacy_ts_app";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("applications").doc(legacyId).set({
      appId: legacyId,
      appName: "Legacy App",
      developerName: "Legacy Dev",
      version: "1.0.0",
      description: "Seeded with Firestore Timestamp",
      iconUrl: "https://example.com/legacy.png",
      apkUrl: "https://example.com/legacy.apk",
      downloads: 42,
      uploadedBy: ADMIN_UID,
      createdAt: new Date(Date.now() - 10000),
    });
  });

  const guestDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(guestDb.collection("applications").get());
  await assertSucceeds(
    guestDb.collection("applications").doc(legacyId).update({
      downloads: 43,
    })
  );

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    adminDb.collection("applications").doc(legacyId).update({
      version: "1.0.1",
      whatsNew: "Migrated release",
    })
  );
});

test("Live Updates enforce admin-only writes and isolate unpublished drafts from ordinary users and guests", async () => {
  const guestDb = testEnv.unauthenticatedContext().firestore();
  const userDb = testEnv
    .authenticatedContext(USER_ALICE, { email: "alice@example.com", email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const now = new Date(Date.now() - 1000);

  const publishedRelease = {
    releaseId: "release_v3",
    versionName: "1.2",
    versionCode: 3,
    packageName: "com.aistudio.storepro.vqxknp",
    apkDownloadUrl: "https://firebasestorage.googleapis.com/v0/b/bucket/o/storepro_v3.apk",
    apkSize: 25869902,
    releaseNotes: "New Live Update system release.",
    releaseDate: "2026-11-21",
    published: true,
    releaseStatus: "PUBLISHED",
    storagePath: "releases/live_v3/storepro_v3.apk",
    apkSha256: "abcdef123456",
    signingSha256: "123456abcdef",
    publishedBy: ADMIN_UID,
    updatedAt: now,
  };

  const draftRelease = {
    releaseId: "release_v4",
    versionName: "1.3",
    versionCode: 4,
    packageName: "com.aistudio.storepro.vqxknp",
    apkDownloadUrl: "",
    apkSize: 0,
    releaseNotes: "Upcoming draft build",
    releaseDate: "2026-11-22",
    published: false,
    releaseStatus: "DRAFT",
    storagePath: "",
    apkSha256: "",
    signingSha256: "",
    publishedBy: ADMIN_UID,
    updatedAt: now,
  };

  // Guests and regular users CANNOT create, update, or delete live_updates
  await assertFails(guestDb.collection("live_updates").doc("release_v3").set(publishedRelease));
  await assertFails(userDb.collection("live_updates").doc("release_v3").set(publishedRelease));

  // Admin can publish release and save a draft
  await assertSucceeds(adminDb.collection("live_updates").doc("release_v3").set(publishedRelease));
  await assertSucceeds(adminDb.collection("live_updates").doc("release_v4").set(draftRelease));

  // Invalid state combinations (published=true without URL or published/releaseStatus mismatch) fail even for admin
  await assertFails(
    adminDb.collection("live_updates").doc("release_bad").set({
      ...draftRelease,
      releaseId: "release_bad",
      published: true,
      releaseStatus: "DRAFT",
    })
  );
  await assertFails(
    adminDb.collection("live_updates").doc("release_v3").update({
      versionCode: 0,
    })
  );

  // Ordinary users and guests CAN query published == true and get published doc
  await assertSucceeds(guestDb.collection("live_updates").where("published", "==", true).get());
  await assertSucceeds(userDb.collection("live_updates").where("published", "==", true).get());
  await assertSucceeds(guestDb.collection("live_updates").doc("release_v3").get());

  // Ordinary users and guests CANNOT list all docs (which could expose drafts) or get DRAFT doc
  await assertFails(guestDb.collection("live_updates").get());
  await assertFails(guestDb.collection("live_updates").doc("release_v4").get());
  await assertFails(userDb.collection("live_updates").doc("release_v4").get());

  // Admin can list all docs (Published + Draft + Archived) and delete drafts
  const adminAll = await assertSucceeds(adminDb.collection("live_updates").get());
  if (adminAll.docs.length !== 2) {
    throw new Error("Expected admin to see both published and draft releases");
  }
  await assertFails(userDb.collection("live_updates").doc("release_v4").delete());
  await assertSucceeds(adminDb.collection("live_updates").doc("release_v4").delete());
});

test("Device management rules enforce self-registration/heartbeat without status tampering and admin-only listing/blocking/deletion", async () => {
  const guestDb = testEnv.unauthenticatedContext().firestore();
  const userDb = testEnv
    .authenticatedContext(USER_ALICE, { email: "alice@example.com", email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const installId = "device_uuid_12345678";
  const now = new Date(Date.now() - 2000);

  // Installation cannot self-register directly as BLOCKED
  await assertFails(
    guestDb.collection("registered_devices").doc(installId).set({
      installationId: installId,
      deviceName: "Pixel 8",
      deviceModel: "Google Pixel 8",
      status: "BLOCKED",
      appVersionCode: 2,
      appVersionName: "1.1",
      registeredAt: now,
      lastActiveAt: now,
    })
  );

  // Emulator / Robolectric payloads must be denied by security rules
  await assertFails(
    guestDb.collection("registered_devices").doc("robo_device_01").set({
      installationId: "robo_device_01",
      deviceName: "Robolectric",
      deviceModel: "robolectric",
      manufacturer: "robolectric",
      status: "ACTIVE",
      appVersionCode: 2,
      appVersionName: "1.1",
      registeredAt: now,
      lastActiveAt: now,
    })
  );
  await assertFails(
    guestDb.collection("registered_devices").doc("emu_device_01").set({
      installationId: "emu_device_01",
      deviceName: "sdk_gphone64_arm64",
      deviceModel: "sdk_gphone64_arm64",
      manufacturer: "Google",
      status: "ACTIVE",
      appVersionCode: 2,
      appVersionName: "1.1",
      registeredAt: now,
      lastActiveAt: now,
    })
  );

  // Installation can register itself as ACTIVE in GUEST mode with full real hardware metadata
  await assertSucceeds(
    guestDb.collection("registered_devices").doc(installId).set({
      installationId: installId,
      deviceName: "Pixel 8",
      deviceModel: "Pixel 8",
      manufacturer: "Google",
      brand: "google",
      androidVersion: "15",
      sdkVersion: 35,
      deviceType: "Phone",
      accountStatus: "GUEST",
      associatedUserId: null,
      status: "ACTIVE",
      appVersionCode: 2,
      appVersionName: "1.1",
      registeredAt: now,
      firstUsedAt: now,
      lastActiveAt: now,
    })
  );

  // Guest CANNOT mutate immutable firstUsedAt or spoof LOGGED_IN / another user's UID
  await assertFails(
    guestDb.collection("registered_devices").doc(installId).update({
      firstUsedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    guestDb.collection("registered_devices").doc(installId).update({
      accountStatus: "LOGGED_IN",
      associatedUserId: USER_ALICE,
    })
  );
  await assertFails(
    userDb.collection("registered_devices").doc(installId).update({
      accountStatus: "LOGGED_IN",
      associatedUserId: USER_BOB,
    })
  );

  // Active installation can read its own status and send heartbeat updating lastActiveAt & device metadata
  await assertSucceeds(guestDb.collection("registered_devices").doc(installId).get());
  await assertSucceeds(
    guestDb.collection("registered_devices").doc(installId).update({
      brand: "Google",
      androidVersion: "15",
      sdkVersion: 35,
      deviceType: "Phone",
      lastActiveAt: new Date(Date.now() - 1000),
      appVersionCode: 2,
    })
  );

  // When user signs in with Google, the SAME installation document transitions to LOGGED_IN without creating a duplicate
  await assertSucceeds(
    userDb.collection("registered_devices").doc(installId).update({
      accountStatus: "LOGGED_IN",
      associatedUserId: USER_ALICE,
      lastActiveAt: new Date(Date.now() - 800),
    })
  );

  // Regular users & guests CANNOT self-register or escalate to isAdminDevice = true
  await assertFails(
    userDb.collection("registered_devices").doc("fake_admin_dev_1").set({
      installationId: "fake_admin_dev_1",
      deviceName: "Spoof Phone",
      deviceModel: "SM-S921B",
      isAdminDevice: true,
      status: "ACTIVE",
      appVersionCode: 2,
      appVersionName: "1.1",
      registeredAt: now,
      lastActiveAt: now,
    })
  );
  await assertFails(
    userDb.collection("registered_devices").doc(installId).update({
      isAdminDevice: true,
    })
  );

  // Regular users & guests CANNOT list all registered devices or delete them
  await assertFails(guestDb.collection("registered_devices").get());
  await assertFails(userDb.collection("registered_devices").get());
  await assertFails(userDb.collection("registered_devices").doc(installId).delete());
  await assertFails(
    userDb.collection("registered_devices").doc(installId).update({ status: "BLOCKED" })
  );

  // Verified Admin CAN mark their device as isAdminDevice = true
  await assertSucceeds(
    adminDb.collection("registered_devices").doc(installId).update({ isAdminDevice: true })
  );

  // Admin can list all devices and block even a LOGGED_IN user's device
  await assertSucceeds(adminDb.collection("registered_devices").get());
  await assertSucceeds(
    adminDb.collection("registered_devices").doc(installId).update({ status: "BLOCKED" })
  );

  // Blocked device CANNOT unblock itself OR send heartbeat updates once blocked
  await assertFails(
    guestDb.collection("registered_devices").doc(installId).update({ status: "ACTIVE" })
  );
  await assertFails(
    userDb.collection("registered_devices").doc(installId).update({
      lastActiveAt: new Date(Date.now() - 500),
    })
  );

  // Admin can unblock and later delete device
  await assertSucceeds(
    adminDb.collection("registered_devices").doc(installId).update({ status: "ACTIVE" })
  );
  await assertSucceeds(adminDb.collection("registered_devices").doc(installId).delete());
});

test("Legacy registered_devices records without manufacturer or accountStatus remain compatible and updatable", async () => {
  const guestDb = testEnv.unauthenticatedContext().firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const legacyId = "sp_legacy_device_01";
  const now = new Date(Date.now() - 10000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("registered_devices").doc(legacyId).set({
      installationId: legacyId,
      deviceName: "Legacy Galaxy",
      deviceModel: "SM-G991B",
      status: "ACTIVE",
      appVersionCode: 1,
      appVersionName: "1.0",
      registeredAt: now,
      lastActiveAt: now,
    });
  });

  await assertSucceeds(
    guestDb.collection("registered_devices").doc(legacyId).update({
      manufacturer: "Samsung",
      accountStatus: "GUEST",
      associatedUserId: null,
      appVersionCode: 2,
      appVersionName: "1.1",
      lastActiveAt: new Date(Date.now() - 1000),
    })
  );
  const allSnap = await assertSucceeds(adminDb.collection("registered_devices").get());
  if (allSnap.docs.length !== 1) {
    throw new Error("Expected legacy registered device to remain queryable by admin");
  }
});

test("Admin-only app_update_config, app_releases, admin_roles, fcm_tokens, and server-only notification_dispatches enforce strict authorization", async () => {
  const guestDb = testEnv.unauthenticatedContext().firestore();
  const userDb = testEnv
    .authenticatedContext(USER_ALICE, { email: "alice@example.com", email_verified: true })
    .firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const now = new Date(Date.now() - 1000);

  // Regular users cannot create an admin_roles document to escalate privilege
  await assertFails(
    userDb.collection("admin_roles").doc(USER_ALICE).set({
      uid: USER_ALICE,
      email: "alice@example.com",
      role: "admin",
      active: true,
      updatedAt: now,
    })
  );

  // Bootstrapped admin creates admin role document
  await assertSucceeds(
    adminDb.collection("admin_roles").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: ADMIN_EMAIL,
      role: "admin",
      active: true,
      updatedAt: now,
    })
  );

  // fcm_tokens: installation writes own token; guests/regular users cannot list/read tokens
  const installId = "device_uuid_fcm_01";
  await assertSucceeds(
    guestDb.collection("fcm_tokens").doc(installId).set({
      installationId: installId,
      token: "fcm_token_example_abcdef1234567890",
      notificationsAllowed: true,
      appVersionCode: 2,
      updatedAt: now,
    })
  );
  await assertFails(guestDb.collection("fcm_tokens").doc(installId).get());
  await assertFails(userDb.collection("fcm_tokens").get());
  await assertSucceeds(adminDb.collection("fcm_tokens").get());

  // notification_dispatches: clients cannot forge dispatches
  await assertFails(
    adminDb.collection("notification_dispatches").doc("app_1").set({
      appId: "app_1",
      dispatched: true,
    })
  );
});

