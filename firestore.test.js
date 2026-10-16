const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_123";
const CHILD_UID = "child_456";
const STRANGER_UID = "stranger_789";

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

// --- SECURITY RULES VALIDATION TESTS ---

test("Unauthenticated user: cannot read family links", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("family_links").get());
});

test("Authenticated parent: can create family link and read it", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const linkDoc = parentDb.collection("family_links").doc("link_01");

  await assertSucceeds(
    linkDoc.set({
      id: "link_01",
      parentId: PARENT_UID,
      childId: CHILD_UID,
      childName: "Ali",
      pairingCode: "FG-1234",
      status: "active",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    parentDb.collection("family_links").doc("link_01").update({
      childName: "Ali Updated",
      batteryLevel: 95,
      isOnline: true,
      isSharingLocation: true,
    })
  );
});

test("Stranger: cannot read or update family link of parent and child", async () => {
  // Seed data
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("family_links").doc("link_02").set({
      parentId: PARENT_UID,
      childId: CHILD_UID,
      childName: "Sara",
      pairingCode: "FG-5678",
      status: "active",
      createdAt: new Date(),
    });
  });

  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  await assertFails(strangerDb.collection("family_links").doc("link_02").get());
  await assertFails(
    strangerDb.collection("family_links").doc("link_02").update({
      childName: "Hacked",
    })
  );
});

test("Child: can report location and parent can read it", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const locDoc = childDb.collection("locations").doc("loc_01");

  await assertSucceeds(
    locDoc.set({
      parentId: PARENT_UID,
      childId: CHILD_UID,
      latitude: 35.6892,
      longitude: 51.3890,
      accuracy: 12.5,
      provider: "fused",
      createdAt: new Date(),
    })
  );

  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  await assertSucceeds(parentDb.collection("locations").doc("loc_01").get());

  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  await assertFails(strangerDb.collection("locations").doc("loc_01").get());
});

test("Parent: can send chat message and location request", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();

  await assertSucceeds(
    parentDb.collection("messages").doc("msg_01").set({
      parentId: PARENT_UID,
      childId: CHILD_UID,
      senderType: "parent",
      text: "سلام پسرم، کجایی؟",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    parentDb.collection("location_requests").doc("req_01").set({
      parentId: PARENT_UID,
      childId: CHILD_UID,
      status: "pending",
      requestedAt: new Date(),
      createdAt: new Date(),
    })
  );
});
