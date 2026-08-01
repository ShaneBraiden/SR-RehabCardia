#!/usr/bin/env node
/**
 * Sets the release gate in `config/appVersion`.
 *
 * The Android client reads this document at launch and on every resume:
 *
 *   installed <  minVersionCode     -> blocking screen, no dismiss
 *   installed <  latestVersionCode  -> "Update available" with Maybe later
 *   otherwise                       -> nothing shown
 *
 * So a normal release bumps only `--latest`. Raising `--min` to match is what
 * makes an update mandatory, and it can be done at any time after the release
 * has shipped — that is the point of keeping the gate server-side.
 *
 * Usage:
 *   cd functions
 *
 *   # Read the current gate
 *   GOOGLE_APPLICATION_CREDENTIALS=../backend/service-account-key.json \
 *     node scripts/set-app-version.js --show
 *
 *   # Announce build 5 as available, keep 4 usable
 *   GOOGLE_APPLICATION_CREDENTIALS=../backend/service-account-key.json \
 *     node scripts/set-app-version.js --latest 5
 *
 *   # Force everyone onto build 5
 *   GOOGLE_APPLICATION_CREDENTIALS=../backend/service-account-key.json \
 *     node scripts/set-app-version.js --min 5 --latest 5 \
 *     --message "This update fixes patient login. Please install it."
 *
 * Idempotent. Writing the same numbers twice changes nothing.
 */

const admin = require("firebase-admin");

admin.initializeApp();

const DOC_PATH = "config/appVersion";

/**
 * Reads a `--flag value` pair from argv.
 * @param {string} name Flag name without dashes.
 * @return {?string} The value, or null when the flag is absent.
 */
function arg(name) {
  const i = process.argv.indexOf(`--${name}`);
  if (i === -1 || i === process.argv.length - 1) return null;
  return process.argv[i + 1];
}

/**
 * Parses a version code argument.
 * @param {?string} raw The raw argument value.
 * @param {string} name The flag name, for the error message.
 * @return {?number} The parsed integer, or null when absent.
 */
function versionArg(raw, name) {
  if (raw === null) return null;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < 0) {
    throw new Error(`--${name} must be a non-negative integer, got "${raw}"`);
  }
  return n;
}

/** Entry point. */
async function main() {
  const db = admin.firestore();
  const ref = db.doc(DOC_PATH);
  const snap = await ref.get();
  const current = snap.exists ? snap.data() : {};

  if (process.argv.includes("--show")) {
    console.log(snap.exists ?
      JSON.stringify(current, null, 2) :
      `${DOC_PATH} does not exist yet — no gate is active.`);
    return;
  }

  const min = versionArg(arg("min"), "min");
  const latest = versionArg(arg("latest"), "latest");
  const message = arg("message");

  if (min === null && latest === null && message === null) {
    throw new Error(
        "Nothing to do. Pass --show, or at least one of --min / --latest / " +
      "--message.",
    );
  }

  const next = {
    minVersionCode: min !== null ? min : (current.minVersionCode || 0),
    latestVersionCode: latest !== null ?
      latest :
      (current.latestVersionCode || 0),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  if (message !== null) next.updateMessage = message;
  if (current.playStoreUrl) next.playStoreUrl = current.playStoreUrl;

  // The rules enforce this too, but failing here costs one round trip instead
  // of a confusing permission error.
  if (next.latestVersionCode < next.minVersionCode) {
    throw new Error(
        `latestVersionCode (${next.latestVersionCode}) cannot be below ` +
      `minVersionCode (${next.minVersionCode}) — every install would be ` +
      "blocked with no version to upgrade to.",
    );
  }

  await ref.set(next, {merge: true});

  console.log(`Updated ${DOC_PATH}:`);
  console.log(`  minVersionCode    ${next.minVersionCode}  (below this: ` +
    "blocked)");
  console.log(`  latestVersionCode ${next.latestVersionCode}  (below this: ` +
    "prompted)");
  if (next.updateMessage) {
    console.log(`  updateMessage     ${next.updateMessage}`);
  }
}

main().then(() => process.exit(0)).catch((err) => {
  console.error(err.message);
  process.exit(1);
});
