const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const RIDER_A_UID = "rider_alice_123";
const RIDER_B_UID = "rider_bob_456";
const ADMIN_CLAIM_UID = "admin_claim_001";
const ADMIN_DOC_UID = "admin_doc_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function riderProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    fullName: "Ravi Kumar",
    mobileNumber: "+919876543210",
    email: "ravi@example.com",
    vehicleType: "Bike",
    zone: "Madhapur",
    accountStatus: "active",
    savedAddressCount: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function addressPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    customerName: "Sita Devi",
    mobileNumber: "+919123456789",
    fullAddress: "Plot 42, Kavuri Hills, Phase 2",
    area: "Madhapur",
    landmark: "Near Metro Pillar 1732",
    deliveryNotes: "Call before arriving",
    latitude: 17.4401,
    longitude: 78.3955,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("delivery_boys").doc(RIDER_A_UID).get());
  await assertFails(unauthDb.collection("delivery_boys").get());
  await assertFails(
    unauthDb.collection("delivery_boys").doc(RIDER_A_UID).set(riderProfilePayload(RIDER_A_UID))
  );
  await assertFails(
    unauthDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").get()
  );
  await assertFails(unauthDb.collection("app_config").doc("admob").get());
});

test("Delivery boy registration enforces ownership, active status, zero count, and blocks shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(RIDER_A_UID).firestore();

  // Valid create succeeds
  await assertSucceeds(
    aliceDb.collection("delivery_boys").doc(RIDER_A_UID).set(riderProfilePayload(RIDER_A_UID))
  );

  // Cannot create profile for another UID
  await assertFails(
    aliceDb.collection("delivery_boys").doc(RIDER_B_UID).set(riderProfilePayload(RIDER_B_UID))
  );

  // Cannot inject shadow field (e.g., isAdmin)
  await assertFails(
    aliceDb
      .collection("delivery_boys")
      .doc("shadow_user")
      .set(riderProfilePayload("shadow_user", { isAdmin: true }))
  );

  // Cannot list all delivery boys as non-admin
  await assertFails(aliceDb.collection("delivery_boys").get());
});

test("Delivery boy cannot self-unsuspend or tamper with immutable fields", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .set(riderProfilePayload(RIDER_A_UID, { accountStatus: "suspended" }));
  });

  const aliceDb = testEnv.authenticatedContext(RIDER_A_UID).firestore();
  await assertFails(
    aliceDb.collection("delivery_boys").doc(RIDER_A_UID).update({
      accountStatus: "active",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Suspended delivery boy cannot add new addresses
  await assertFails(
    aliceDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_1")
      .set(addressPayload(RIDER_A_UID))
  );
});

test("Strict per-rider privacy: Rider A can CRUD own addresses, Rider B is completely blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(RIDER_A_UID).firestore();
  const bobDb = testEnv.authenticatedContext(RIDER_B_UID).firestore();

  // Register both riders
  await assertSucceeds(
    aliceDb.collection("delivery_boys").doc(RIDER_A_UID).set(riderProfilePayload(RIDER_A_UID))
  );
  await assertSucceeds(
    bobDb.collection("delivery_boys").doc(RIDER_B_UID).set(riderProfilePayload(RIDER_B_UID))
  );

  // Rider A creates an address (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_1")
      .set(addressPayload(RIDER_A_UID))
  );

  // Reject if `id` field is injected
  await assertFails(
    aliceDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_2")
      .set(addressPayload(RIDER_A_UID, { id: "addr_2" }))
  );

  // Rider A lists and updates own address
  await assertSucceeds(
    aliceDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").get()
  );
  await assertSucceeds(
    aliceDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_1")
      .update({
        customerName: "Sita Devi Updated",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Rider B cannot read, list, create, update, or delete Rider A's addresses
  await assertFails(
    bobDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").doc("addr_1").get()
  );
  await assertFails(
    bobDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").get()
  );
  await assertFails(
    bobDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_3")
      .set(addressPayload(RIDER_B_UID))
  );
  await assertFails(
    bobDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_1")
      .update({ customerName: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").doc("addr_1").delete()
  );
});

test("Admin permissions require verified Firebase role (custom claim or /admins/{uid}), private addresses remain isolated", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminSeedDb = ctx.firestore();
    await adminSeedDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .set(riderProfilePayload(RIDER_A_UID));
    await adminSeedDb
      .collection("delivery_boys")
      .doc(RIDER_A_UID)
      .collection("addresses")
      .doc("addr_1")
      .set(addressPayload(RIDER_A_UID));
    await adminSeedDb
      .collection("admins")
      .doc(ADMIN_DOC_UID)
      .set({
        email: "ops-admin@example.com",
        displayName: "Ops Admin",
        grantedBy: ADMIN_CLAIM_UID,
        createdAt: new Date(Date.now() - 2000),
      });
  });

  // Non-admin user (even with arbitrary email or admin:false) MUST fail
  const nonAdminDb = testEnv
    .authenticatedContext("spoofer_uid", {
      email: "someone@example.com",
      email_verified: true,
      admin: false,
    })
    .firestore();
  await assertFails(nonAdminDb.collection("delivery_boys").get());
  await assertFails(
    nonAdminDb.collection("delivery_boys").doc(RIDER_A_UID).update({
      accountStatus: "suspended",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    nonAdminDb.collection("admins").doc("spoofer_uid").set({
      email: "someone@example.com",
      displayName: "Spoofer",
      grantedBy: "spoofer_uid",
      createdAt: new Date(Date.now() - 500),
    })
  );

  // Admin via Firebase Auth custom claim (admin: true) succeeds
  const claimAdminDb = testEnv
    .authenticatedContext(ADMIN_CLAIM_UID, {
      email: "claim-admin@example.com",
      email_verified: true,
      admin: true,
    })
    .firestore();
  await assertSucceeds(claimAdminDb.collection("delivery_boys").get());

  // Admin via /admins/{uid} document succeeds
  const docAdminDb = testEnv
    .authenticatedContext(ADMIN_DOC_UID, {
      email: "ops-admin@example.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(docAdminDb.collection("delivery_boys").get());
  await assertSucceeds(
    docAdminDb.collection("delivery_boys").doc(RIDER_A_UID).update({
      accountStatus: "suspended",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can configure AdMob settings
  await assertSucceeds(
    docAdminDb.collection("app_config").doc("admob").set({
      useTestAds: true,
      bannerAdUnitId: "ca-app-pub-3940256099942544/6300978111",
      rewardedAdUnitId: "ca-app-pub-3940256099942544/5224354917",
      updatedBy: ADMIN_DOC_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Signed-in delivery boy can read admob config, cannot write it
  const aliceDb = testEnv.authenticatedContext(RIDER_A_UID).firestore();
  await assertSucceeds(aliceDb.collection("app_config").doc("admob").get());
  await assertFails(
    aliceDb.collection("app_config").doc("admob").update({
      useTestAds: false,
      updatedBy: RIDER_A_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Privacy invariant: even verified Admin cannot read Rider A's private customer addresses
  await assertFails(
    claimAdminDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").get()
  );
  await assertFails(
    docAdminDb.collection("delivery_boys").doc(RIDER_A_UID).collection("addresses").get()
  );
});
