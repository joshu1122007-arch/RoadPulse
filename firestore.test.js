const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_citizen_1";
const BOB_UID = "bob_citizen_2";
const ADMIN_UID = "admin_user_1";
const ADMIN_EMAIL = "joshu1122007@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("incidents").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("incidents").doc("inc_1").set({
      reporterId: ALICE_UID,
      title: "Unauthorized report",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile: owner can create public profile, cannot self-assign admin or read others PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Rao",
      email: "alice@example.com",
      role: "public",
      preferredRegionId: "bengaluru_metro",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Self-assigned admin role must fail
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      name: "Bob Escalator",
      role: "admin",
      preferredRegionId: "bengaluru_metro",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user PII read must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Role escalation on update must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Allowed profile field update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      name: "Alice K. Rao",
      phone: "+91-9900000000",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("User preferences & notifications are strictly owner-isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("settings").set({
      userId: ALICE_UID,
      alertRadiusKm: 10.0,
      enabledCategories: ["pothole", "waterlogging"],
      notificationFrequency: "REALTIME",
      watchedRegions: ["bengaluru_metro"],
      updatedAt: now,
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("preferences").doc("settings").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      incidentId: "inc_1",
      title: "Waterlogging nearby",
      message: "Verified waterlogging 1.2 km away",
      categoryId: "waterlogging",
      approximateLocation: "Silk Board Junction",
      incidentStatus: "VERIFIED",
      notificationType: "NEARBY_HAZARD",
      read: false,
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      read: true,
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").get()
  );
});

test("Incidents: citizen report must start PENDING_VERIFICATION, no @DocumentId id field, aligned queries succeed and unfiltered fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 3000);

  const validIncident = {
    reporterId: ALICE_UID,
    reporterRoleLabel: "Citizen Reporter",
    categoryId: "pothole",
    title: "Deep pothole near Silk Board flyover",
    description: "Large water-filled pothole on the left lane heading towards HSR.",
    latitude: 12.9177,
    longitude: 77.6238,
    locationLabel: "Silk Board Flyover, Bengaluru",
    regionId: "bengaluru_metro",
    severity: "MODERATE",
    status: "PENDING_VERIFICATION",
    visibility: "PUBLIC",
    riskLevel: "MODERATE",
    riskSummary: "Rule-based indicative assessment: moderate road hazard.",
    source: "COMMUNITY_REPORT",
    photoPresent: false,
    aiStatus: "SKIPPED",
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(aliceDb.collection("incidents").doc("inc_1").set(validIncident));

  // Reject ghost "id" field
  await assertFails(
    aliceDb.collection("incidents").doc("inc_ghost").set({
      ...validIncident,
      id: "inc_ghost",
    })
  );

  // Reject citizen self-verifying on create
  await assertFails(
    aliceDb.collection("incidents").doc("inc_bypass").set({
      ...validIncident,
      status: "VERIFIED",
    })
  );

  // Initial citizen audit entry in top-level incident_updates succeeds
  await assertSucceeds(
    aliceDb.collection("incident_updates").doc("upd_1").set({
      incidentId: "inc_1",
      reporterId: ALICE_UID,
      visibility: "PUBLIC",
      previousStatus: "NONE",
      newStatus: "PENDING_VERIFICATION",
      updatedBy: ALICE_UID,
      updatedByRole: "CITIZEN",
      notes: "Submitted by citizen reporter",
      createdAt: now,
    })
  );

  // Aligned public query succeeds
  await assertSucceeds(
    bobDb.collection("incidents").where("visibility", "==", "PUBLIC").get()
  );

  // Aligned reporter query succeeds
  await assertSucceeds(
    aliceDb.collection("incidents").where("reporterId", "==", ALICE_UID).get()
  );

  // Unfiltered query by non-admin fails
  await assertFails(bobDb.collection("incidents").get());

  // Non-owner citizen cannot verify or modify Alice's incident
  await assertFails(
    bobDb.collection("incidents").doc("inc_1").update({
      status: "VERIFIED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Even owner cannot verify their own incident
  await assertFails(
    aliceDb.collection("incidents").doc("inc_1").update({
      status: "VERIFIED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Admin RBAC: verified admin can list all, verify/reject incidents, and append immutable audit logs; spoofed unverified admin email fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const now = new Date(Date.now() - 3000);
  await assertSucceeds(
    aliceDb.collection("incidents").doc("inc_admin_test").set({
      reporterId: ALICE_UID,
      reporterRoleLabel: "Citizen Reporter",
      categoryId: "waterlogging",
      title: "Waterlogging on Outer Ring Road",
      description: "Knee-deep water near Bellandur gate slowing traffic.",
      latitude: 12.9279,
      longitude: 77.6762,
      locationLabel: "Bellandur Gate, ORR, Bengaluru",
      regionId: "bengaluru_metro",
      severity: "HIGH",
      status: "PENDING_VERIFICATION",
      visibility: "PUBLIC",
      riskLevel: "HIGH",
      riskSummary: "Rule-based indicative assessment: high flooding risk.",
      source: "COMMUNITY_REPORT",
      photoPresent: false,
      aiStatus: "UNAVAILABLE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Email spoofing with email_verified=false MUST fail
  await assertFails(
    spoofedAdminDb.collection("incidents").doc("inc_admin_test").update({
      status: "VERIFIED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Real verified admin can list all and update status
  await assertSucceeds(realAdminDb.collection("incidents").get());
  await assertSucceeds(
    realAdminDb.collection("incidents").doc("inc_admin_test").update({
      status: "VERIFIED",
      verificationNotes: "Confirmed via traffic control camera",
      verifiedBy: ADMIN_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    realAdminDb.collection("incident_updates").doc("upd_admin").set({
      incidentId: "inc_admin_test",
      reporterId: ALICE_UID,
      visibility: "PUBLIC",
      previousStatus: "PENDING_VERIFICATION",
      newStatus: "VERIFIED",
      updatedBy: ADMIN_UID,
      updatedByRole: "ADMIN",
      notes: "Confirmed via traffic control camera",
      createdAt: new Date(Date.now() - 500),
    })
  );

  // Non-admin citizen cannot update or delete incident_updates
  await assertFails(
    aliceDb.collection("incident_updates").doc("upd_admin").update({
      notes: "Citizen tampering",
    })
  );
  await assertFails(
    aliceDb.collection("incident_updates").doc("upd_admin").delete()
  );

  // Admin can update notes ONLY; tampering with status or role fails even for admin
  await assertSucceeds(
    realAdminDb.collection("incident_updates").doc("upd_admin").update({
      notes: "Confirmed via traffic control camera #14",
    })
  );
  await assertFails(
    realAdminDb.collection("incident_updates").doc("upd_admin").update({
      newStatus: "REJECTED",
    })
  );
});

test("Citizen pending incident update and withdraw/delete enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 3000);

  const pendingIncident = {
    reporterId: ALICE_UID,
    reporterRoleLabel: "Citizen Reporter",
    categoryId: "pothole",
    title: "Pending road crack",
    description: "Deep crack along lane near Hebbal flyover.",
    latitude: 13.0358,
    longitude: 77.597,
    locationLabel: "Hebbal Flyover",
    regionId: "bengaluru_metro",
    severity: "LOW",
    status: "PENDING_VERIFICATION",
    visibility: "PUBLIC",
    riskLevel: "LOW",
    riskSummary: "Indicative low risk",
    source: "COMMUNITY_REPORT",
    photoPresent: false,
    aiStatus: "SKIPPED",
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(aliceDb.collection("incidents").doc("inc_edit_1").set(pendingIncident));

  // Owner can edit allowed fields while PENDING_VERIFICATION
  await assertSucceeds(
    aliceDb.collection("incidents").doc("inc_edit_1").update({
      categoryId: "road_collapse",
      title: "Updated sinkhole near Hebbal",
      description: "Expanding collapse in left lane requiring barriers.",
      severity: "HIGH",
      riskLevel: "HIGH",
      riskSummary: "Indicative high risk",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Non-owner cannot edit or delete pending incident
  await assertFails(
    bobDb.collection("incidents").doc("inc_edit_1").update({
      title: "Bob takeover",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(bobDb.collection("incidents").doc("inc_edit_1").delete());

  // Owner CAN withdraw/delete while PENDING_VERIFICATION
  await assertSucceeds(aliceDb.collection("incidents").doc("inc_edit_1").delete());

  // Once verified by admin, owner can no longer edit or delete
  await assertSucceeds(aliceDb.collection("incidents").doc("inc_verified_1").set(pendingIncident));
  await assertSucceeds(
    realAdminDb.collection("incidents").doc("inc_verified_1").update({
      status: "VERIFIED",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDb.collection("incidents").doc("inc_verified_1").update({
      title: "Late edit",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(aliceDb.collection("incidents").doc("inc_verified_1").delete());

  // Admin can delete verified incident
  await assertSucceeds(realAdminDb.collection("incidents").doc("inc_verified_1").delete());
});

test("Evidence top-level collection CRUD: owner create/update/delete, aligned queries, validation & RBAC", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("incidents").doc("inc_ev_1").set({
      reporterId: ALICE_UID,
      reporterRoleLabel: "Citizen Reporter",
      categoryId: "waterlogging",
      title: "Flooded underpass",
      description: "Water level rising near KR Puram underpass.",
      latitude: 13.0012,
      longitude: 77.6951,
      locationLabel: "KR Puram",
      regionId: "bengaluru_metro",
      severity: "HIGH",
      status: "PENDING_VERIFICATION",
      visibility: "PUBLIC",
      riskLevel: "HIGH",
      riskSummary: "Indicative high risk",
      source: "COMMUNITY_REPORT",
      photoPresent: true,
      aiStatus: "SKIPPED",
      createdAt: now,
      updatedAt: now,
    })
  );

  const validEvidence = {
    incidentId: "inc_ev_1",
    reporterId: ALICE_UID,
    visibility: "PUBLIC",
    fileType: "image/jpeg",
    caption: "Front view of flooded lane",
    thumbnailBase64: "ZmFrZV9iYXNlNjRfdGh1bWJuYWls",
    uploadedAt: now,
  };

  // Owner creates valid evidence in top-level 'evidence' collection
  await assertSucceeds(
    aliceDb.collection("evidence").doc("ev_1").set(validEvidence)
  );

  // Non-owner cannot attach evidence to Alice's incident
  await assertFails(
    bobDb.collection("evidence").doc("ev_bob").set({
      ...validEvidence,
      reporterId: BOB_UID,
    })
  );

  // Unsupported MIME fileType rejected
  await assertFails(
    aliceDb.collection("evidence").doc("ev_bad_mime").set({
      ...validEvidence,
      fileType: "application/x-msdownload",
    })
  );

  // Future uploadedAt timestamp rejected
  await assertFails(
    aliceDb.collection("evidence").doc("ev_future").set({
      ...validEvidence,
      uploadedAt: new Date(Date.now() + 600000),
    })
  );

  // Aligned PUBLIC list succeeds; unfiltered non-admin list fails
  await assertSucceeds(
    bobDb.collection("evidence")
      .where("incidentId", "==", "inc_ev_1")
      .where("visibility", "==", "PUBLIC")
      .get()
  );
  await assertFails(
    bobDb.collection("evidence").where("incidentId", "==", "inc_ev_1").get()
  );
  await assertSucceeds(
    realAdminDb.collection("evidence").where("incidentId", "==", "inc_ev_1").get()
  );

  // Owner can update caption only
  await assertSucceeds(
    aliceDb.collection("evidence").doc("ev_1").update({
      caption: "Updated angle from footpath",
    })
  );

  // Owner cannot tamper with reporterId or uploadedAt
  await assertFails(
    aliceDb.collection("evidence").doc("ev_1").update({
      reporterId: BOB_UID,
    })
  );

  // Non-owner cannot update or delete evidence
  await assertFails(
    bobDb.collection("evidence").doc("ev_1").update({
      caption: "Vandalized",
    })
  );
  await assertFails(
    bobDb.collection("evidence").doc("ev_1").delete()
  );

  // Owner can delete evidence while incident is PENDING_VERIFICATION
  await assertSucceeds(
    aliceDb.collection("evidence").doc("ev_1").delete()
  );
});

