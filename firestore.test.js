const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nav_123";
const BOB_UID = "bob_nav_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSettingsPayload(uid, timestamp = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    voiceId: "orbit",
    styleId: "natural",
    aiModeId: "copilot",
    particleModeId: "smart_context",
    pitchSemitones: 0.0,
    expressiveness: 0.65,
    speakingRate: 0.95,
    synthesisBackendId: "auto_hybrid",
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validPhrasePayload(uid, timestamp = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "ทางเข้าสุขุมวิท",
    rawText: "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท",
    rewrittenText: "อีกประมาณ 800 เมตร เตรียมตัวเลี้ยวขวาเข้าถนนสุขุมวิทนะคะ",
    voiceId: "orbit",
    localeTag: "th-TH",
    createdAt: timestamp,
    updatedAt: timestamp,
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

test("Unauthenticated users cannot read or write settings or customPhrases", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("tts_profile").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("tts_profile").set(validSettingsPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("customPhrases").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("p1").set(validPhrasePayload(ALICE_UID)));
});

test("Authenticated owner: valid Kotlin settings create & partial update succeed; shadow fields and immutable mutations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const settingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("tts_profile");

  await assertSucceeds(settingsRef.set(validSettingsPayload(ALICE_UID, created)));
  await assertSucceeds(settingsRef.get());

  // Partial update mirroring Kotlin CloudSyncRepository.saveTtsSettings
  await assertSucceeds(
    settingsRef.update({
      voiceId: "orion",
      styleId: "warm",
      speakingRate: 1.05,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    settingsRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject out-of-range speakingRate (value poisoning)
  await assertFails(
    settingsRef.update({
      speakingRate: 3.5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow/ghost field on create (including @DocumentId 'id')
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("tts_profile_2").set({
      ...validSettingsPayload(ALICE_UID, created),
      id: "tts_profile_2",
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("tts_profile_3").set(
      validSettingsPayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );
});

test("Authenticated owner: valid customPhrases CRUD and list query succeed; cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list query succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("customPhrases").get());

  const phraseRef = aliceDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("phrase_1");
  await assertSucceeds(phraseRef.set(validPhrasePayload(ALICE_UID, created)));

  // Optional rewrittenText omitted (matches Kotlin .filterValues { it != null })
  const minimalPhrase = validPhrasePayload(ALICE_UID, created);
  delete minimalPhrase.rewrittenText;
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("phrase_2").set(minimalPhrase));

  // Ordered query matching Kotlin repository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customPhrases").orderBy("createdAt", "desc").get()
  );

  // Partial update
  await assertSucceeds(
    phraseRef.update({
      title: "สุขุมวิท (อัปเดต)",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation: Bob cannot read, list, update, or delete Alice's data
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customPhrases").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("phrase_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("phrase_1").update({ title: "Hacked", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("phrase_1").delete());

  // Identity spoofing: Alice cannot write Bob's UID in userId field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("customPhrases").doc("spoof").set(validPhrasePayload(BOB_UID, created))
  );

  // Owner delete succeeds
  await assertSucceeds(phraseRef.delete());
});
