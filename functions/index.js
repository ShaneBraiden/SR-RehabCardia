/**
 * Firebase Cloud Functions — SR-Cardiocare
 *
 * Two responsibilities:
 *
 * 1. `syncUserClaims` — mirror each `users/{uid}` document's authorization
 *    fields (`role`, `isBlocked`, `assignedDoctorId`) into that user's Firebase
 *    Auth custom claims. The security rules read authorization from the ID
 *    token, never from Firestore, so a client that can write a user document
 *    still cannot grant itself privileges.
 *
 * 2. `fanOutNotification` — fan out every `notifications/{id}` document write
 *    as an FCM data-only message to each of the recipient user's registered
 *    device tokens (`users/{uid}.fcmTokens`).
 *
 *    The Android client writes notification docs via `core.push.Notifier`,
 *    which carries `title`, `body`, `type`, `route` and `params`. We forward
 *    all of those as the `data` payload so the client can build its own
 *    PendingIntent (tap-to-route) and post its own NotificationCompat. We
 *    never send a `notification:` block — that would cause the system
 *    launcher to handle taps in background state and bypass our routing.
 */

const {setGlobalOptions} = require("firebase-functions");
const {
  onDocumentCreated,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

setGlobalOptions({maxInstances: 10});

// ───────────────────────────────────────────────────────────────────────────
// Custom claims sync
// ───────────────────────────────────────────────────────────────────────────

const VALID_ROLES = ["patient", "doctor", "admin"];

/**
 * Mirrors the authorization-relevant fields of a user document into that
 * user's Firebase Auth custom claims.
 *
 * Why this exists: roles used to live only in Firestore and the rules read
 * them with get(). That made the `users` collection the de-facto permission
 * store — anyone who could write a role field controlled authorization. Claims
 * are signed into the ID token by the Auth backend and are not client-writable,
 * so the escalation path closes by construction.
 *
 * Propagation caveat: a claim change only reaches the client on its next token
 * refresh (Firebase refreshes hourly, or immediately via getIdToken(true)).
 * When privileges *shrink* — a block, or a role change — we additionally revoke
 * the user's refresh tokens so their session cannot outlive the demotion.
 */
exports.syncUserClaims = onDocumentWritten("users/{uid}", async (event) => {
  const uid = event.params.uid;
  const before = (event.data && event.data.before.data()) || {};
  const after = event.data && event.data.after.data();

  // Document deleted — strip claims and kill any live session.
  if (!after) {
    try {
      await admin.auth().setCustomUserClaims(uid, null);
      await admin.auth().revokeRefreshTokens(uid);
      logger.info("syncUserClaims: cleared claims for deleted user", {uid});
    } catch (err) {
      // A deleted Firestore doc for an already-deleted Auth user is normal.
      logger.info("syncUserClaims: nothing to clear", {uid, code: err.code});
    }
    return;
  }

  const role = VALID_ROLES.includes(after.role) ? after.role : null;
  const isBlocked = after.isBlocked === true;
  const assignedDoctorId = typeof after.assignedDoctorId === "string" &&
    after.assignedDoctorId.length > 0 ? after.assignedDoctorId : null;

  if (after.role && !role) {
    logger.warn("syncUserClaims: unrecognised role, claim left null",
        {uid, role: String(after.role).slice(0, 40)});
  }

  // Skip the write when nothing authorization-relevant changed. Without this
  // guard every profile edit would burn an Auth write and churn tokens.
  const beforeBlocked = before.isBlocked === true;
  const beforeAssigned = typeof before.assignedDoctorId === "string" &&
    before.assignedDoctorId.length > 0 ? before.assignedDoctorId : null;
  if (before.role === after.role &&
      beforeBlocked === isBlocked &&
      beforeAssigned === assignedDoctorId) {
    return;
  }

  try {
    await admin.auth().setCustomUserClaims(uid, {
      role,
      isBlocked,
      assignedDoctorId,
    });
  } catch (err) {
    // The Auth account may not exist yet if a user doc was seeded manually.
    logger.error("syncUserClaims: setCustomUserClaims failed",
        {uid, code: err.code, message: err.message});
    return;
  }

  // Force the change to bite now, rather than up to an hour from now, whenever
  // the user is losing access rather than gaining it.
  const demoted = (isBlocked && !beforeBlocked) ||
    (before.role !== undefined && before.role !== after.role);
  if (demoted) {
    try {
      await admin.auth().revokeRefreshTokens(uid);
      logger.info("syncUserClaims: revoked sessions after privilege change",
          {uid, role, isBlocked});
    } catch (err) {
      logger.error("syncUserClaims: revokeRefreshTokens failed",
          {uid, code: err.code});
    }
  }

  logger.info("syncUserClaims: claims updated",
      {uid, role, isBlocked, hasDoctor: assignedDoctorId !== null});

  // Doctor-scoped rules authorise clinical reads against a denormalised
  // `doctorId` on each document. When a patient is reassigned, that stamp has
  // to follow them or their history becomes invisible to the incoming
  // clinician. Fails closed either way, but it would be a silent data bug.
  if (role === "patient" && beforeAssigned !== assignedDoctorId) {
    await restampPatientRecords(uid, assignedDoctorId);
  }
});

/** Collections carrying a denormalised doctorId keyed by patientId. */
const PATIENT_SCOPED_COLLECTIONS = [
  "sessionLogs",
  "postWorkoutFeedback",
  "workouts",
];

/**
 * Rewrites the denormalised `doctorId` on a patient's clinical documents after
 * a reassignment.
 * @param {string} patientId The patient whose records should be re-stamped.
 * @param {?string} doctorId The new assigned doctor, or null if unassigned.
 * @return {Promise<void>} Resolves when every collection has been processed.
 */
async function restampPatientRecords(patientId, doctorId) {
  const db = admin.firestore();
  const value = doctorId === null ? "" : doctorId;

  for (const collection of PATIENT_SCOPED_COLLECTIONS) {
    try {
      const snap = await db.collection(collection)
          .where("patientId", "==", patientId)
          .get();
      if (snap.empty) continue;

      // Firestore caps a batch at 500 writes.
      const docs = snap.docs;
      for (let i = 0; i < docs.length; i += 450) {
        const batch = db.batch();
        for (const doc of docs.slice(i, i + 450)) {
          batch.update(doc.ref, {doctorId: value});
        }
        await batch.commit();
      }
      logger.info("restampPatientRecords: updated",
          {collection, patientId, count: docs.length});
    } catch (err) {
      logger.error("restampPatientRecords: failed",
          {collection, patientId, message: err.message});
    }
  }
}

// ───────────────────────────────────────────────────────────────────────────
// Push notification fan-out
// ───────────────────────────────────────────────────────────────────────────

exports.fanOutNotification = onDocumentCreated(
    "notifications/{id}",
    async (event) => {
      const snapshot = event.data;
      if (!snapshot) {
        logger.warn("fanOutNotification: no snapshot");
        return;
      }
      const doc = snapshot.data() || {};
      const userId = doc.userId;
      if (!userId) {
        logger.warn("fanOutNotification: missing userId",
            {id: event.params.id});
        return;
      }

      // Look up the recipient's registered device tokens.
      const userRef = admin.firestore().collection("users").doc(userId);
      const userSnap = await userRef.get();
      let tokens = [];
      if (userSnap.exists) {
        const raw = userSnap.get("fcmTokens");
        if (Array.isArray(raw)) {
          // sendEachForMulticast hard-caps at 500 tokens; slicing also stops a
          // bloated array from turning one write into an unbounded fan-out.
          tokens = raw.filter(
              (t) => typeof t === "string" && t.length > 0 && t.length <= 4096,
          ).slice(0, 500);
        }
      }
      if (tokens.length === 0) {
        logger.info("fanOutNotification: no tokens for user", {userId});
        return;
      }

      // Defence in depth: this payload is client-authored and lands directly
      // in a system notification, so clamp it here as well as in the rules.
      // `route` is what drives the in-app PendingIntent, so it is restricted
      // to a conservative character set — never a URL an attacker supplies.
      const clamp = (v, max) => String(v || "").slice(0, max);
      const route = clamp(doc.route, 128);
      const safeRoute = /^[A-Za-z0-9_\-/{}.]*$/.test(route) ? route : "";
      if (route !== safeRoute) {
        logger.warn("fanOutNotification: rejected unsafe route",
            {id: event.params.id});
      }

      // Serialise params as JSON so the client can round-trip it through the
      // PendingIntent extras (FCM data values must all be strings). Cap the
      // serialised size — FCM rejects payloads over 4 KB outright.
      let paramsJson = JSON.stringify(doc.params || {});
      if (typeof doc.params !== "object" || Array.isArray(doc.params) ||
          paramsJson.length > 2048) {
        paramsJson = "{}";
      }

      const data = {
        title: clamp(doc.title, 200),
        body: clamp(doc.body, 2000),
        type: clamp(doc.type, 64),
        route: safeRoute,
        params: paramsJson,
        channelId: channelFor(doc.type),
        notificationId: String(event.params.id),
      };

      const response = await admin.messaging().sendEachForMulticast({
        tokens,
        data,
        android: {
          priority: "high",
        },
      });

      // Prune tokens that FCM rejected as unregistered/invalid so the user's
      // array doesn't grow stale.
      const invalid = [];
      response.responses.forEach((r, i) => {
        if (r.success) return;
        const code = r.error && r.error.code;
        if (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token" ||
            code === "messaging/invalid-argument") {
          invalid.push(tokens[i]);
        }
      });
      if (invalid.length > 0) {
        await admin.firestore().collection("users").doc(userId).update({
          fcmTokens: admin.firestore.FieldValue.arrayRemove(...invalid),
        });
        logger.info("fanOutNotification: pruned invalid tokens",
            {userId, count: invalid.length});
      }

      logger.info("fanOutNotification: sent", {
        userId,
        delivered: response.successCount,
        failed: response.failureCount,
      });
    },
);

/**
 * Returns the FCM channel ID for a given notification type.
 * @param {string} type - The notification type.
 * @return {string} The channel ID.
 */
function channelFor(type) {
  switch ((type || "").toLowerCase()) {
    case "message":
      return "srcc_chat";
    case "appointment":
    case "appointment_update":
    case "appointment_request":
      return "srcc_appointments";
    default:
      return "srcc_general";
  }
}
