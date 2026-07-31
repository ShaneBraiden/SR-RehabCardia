#!/usr/bin/env node
/**
 * One-off backfill: copy every existing users/{uid} document's authorization
 * fields into that user's Firebase Auth custom claims.
 *
 * `syncUserClaims` only fires on future writes, so without this every account
 * that existed before the claims migration would have an empty `role` claim and
 * would be denied by the new security rules. Run this ONCE, after deploying the
 * function and BEFORE deploying the rules.
 *
 * Usage:
 *   cd functions
 *   GOOGLE_APPLICATION_CREDENTIALS=../backend/service-account-key.json \
 *     node scripts/backfill-claims.js --dry-run
 *   GOOGLE_APPLICATION_CREDENTIALS=../backend/service-account-key.json \
 *     node scripts/backfill-claims.js
 *
 * Idempotent — safe to re-run. Users whose claims already match are skipped.
 */

const admin = require("firebase-admin");

const DRY_RUN = process.argv.includes("--dry-run");
const VALID_ROLES = ["patient", "doctor", "admin"];

admin.initializeApp();

/**
 * Normalises a user document into the claim shape written by syncUserClaims.
 * @param {object} data Firestore user document data.
 * @return {object} The claims object.
 */
function claimsFor(data) {
  return {
    role: VALID_ROLES.includes(data.role) ? data.role : null,
    isBlocked: data.isBlocked === true,
    assignedDoctorId: typeof data.assignedDoctorId === "string" &&
      data.assignedDoctorId.length > 0 ? data.assignedDoctorId : null,
  };
}

/**
 * Compares existing claims against the desired shape.
 * @param {object} existing Current custom claims on the Auth user.
 * @param {object} desired Claims derived from the Firestore document.
 * @return {boolean} True when a write is required.
 */
function needsUpdate(existing, desired) {
  const e = existing || {};
  return e.role !== desired.role ||
    e.isBlocked !== desired.isBlocked ||
    e.assignedDoctorId !== desired.assignedDoctorId;
}

/** Runs the backfill. */
async function main() {
  console.log(DRY_RUN ?
    "DRY RUN — no claims will be written\n" :
    "Writing custom claims...\n");

  const snap = await admin.firestore().collection("users").get();
  console.log(`Found ${snap.size} user documents\n`);

  let updated = 0;
  let skipped = 0;
  let missing = 0;
  let noRole = 0;

  for (const doc of snap.docs) {
    const uid = doc.id;
    const desired = claimsFor(doc.data());

    if (desired.role === null) {
      console.warn(`  ! ${uid} — no valid role in Firestore ` +
        `(found: ${JSON.stringify(doc.data().role)}). ` +
        `This account will be denied by the rules until fixed.`);
      noRole++;
    }

    let user;
    try {
      user = await admin.auth().getUser(uid);
    } catch (err) {
      if (err.code === "auth/user-not-found") {
        console.warn(
            `  ! ${uid} — Firestore doc with no Auth account, skipped`);
        missing++;
        continue;
      }
      throw err;
    }

    if (!needsUpdate(user.customClaims, desired)) {
      skipped++;
      continue;
    }

    if (!DRY_RUN) {
      await admin.auth().setCustomUserClaims(uid, desired);
    }
    console.log(`  ${DRY_RUN ? "would set" : "set"} ${uid} -> ` +
      `role=${desired.role} blocked=${desired.isBlocked}`);
    updated++;
  }

  console.log(`\nDone. updated=${updated} alreadyCorrect=${skipped} ` +
    `noAuthAccount=${missing} invalidRole=${noRole}`);

  if (!DRY_RUN && updated > 0) {
    console.log(
        "\nNote: existing signed-in users pick up their new claims on " +
      "the next token refresh (within the hour), or immediately if the app " +
      "calls getIdToken(true).");
  }
  if (noRole > 0) {
    console.log(`\nWARNING: ${noRole} account(s) have no valid role and will ` +
      `be locked out by the new rules. Fix those documents before deploying ` +
      `the rules.`);
  }
}

main().then(() => process.exit(0)).catch((err) => {
  console.error("Backfill failed:", err);
  process.exit(1);
});
