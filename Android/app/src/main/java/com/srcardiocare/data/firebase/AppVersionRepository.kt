// AppVersionRepository.kt — Reads the release gate from `config/appVersion`.
package com.srcardiocare.data.firebase

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.tasks.await

/**
 * Decides whether the installed build is still allowed to run.
 *
 * The requirement lives in Firestore rather than in the APK so a release can be
 * gated *after* it has shipped — the case that actually matters, since a build
 * you need to pull is by definition one you did not know was bad when you built
 * it.
 *
 * `config/appVersion`:
 * ```
 * {
 *   "minVersionCode":    4,   // below this, the app refuses to run
 *   "latestVersionCode": 5,   // below this, the app suggests updating
 *   "updateMessage":     "…", // optional, shown instead of the default copy
 *   "playStoreUrl":      "…"  // optional, defaults to this app's Play listing
 * }
 * ```
 *
 * Bumping only `latestVersionCode` makes a release optional; raising
 * `minVersionCode` to match makes it mandatory. One document, both tiers.
 */
object AppVersionRepository {

    private const val TAG = "SRCardiocare"
    private const val CONFIG_COLLECTION = "config"
    private const val CONFIG_DOCUMENT = "appVersion"

    /** What the app should do about the installed version. */
    sealed interface UpdateStatus {
        /** Build is current enough — nothing to show. */
        data object UpToDate : UpdateStatus

        /** A newer build exists. Dismissible. */
        data class Optional(
            val message: String,
            val storeUrl: String,
        ) : UpdateStatus

        /** Build is below the floor. Blocking, no dismiss. */
        data class Required(
            val message: String,
            val storeUrl: String,
        ) : UpdateStatus
    }

    private const val DEFAULT_OPTIONAL_MESSAGE =
        "A newer version of SR Cardiocare is available with improvements and " +
            "fixes. Updating takes a moment."

    private const val DEFAULT_REQUIRED_MESSAGE =
        "This version of SR Cardiocare is no longer supported. Please update " +
            "to continue using the app."

    /** The installed build's versionCode, read from the package itself. */
    fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        // Cannot happen for our own package, but a thrown exception here would
        // block launch, so fail open.
        Log.e(TAG, "installedVersionCode: package not found", e)
        Long.MAX_VALUE
    }

    /** The installed build's user-visible version name, e.g. "1.0.3". */
    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "installedVersionName: package not found", e)
        ""
    }

    /**
     * Fetches the gate and compares it against the installed build.
     *
     * **Fails open.** A missing document, offline device, or malformed config
     * returns [UpdateStatus.UpToDate]. Locking a clinician out of a patient's
     * exercise plan because Firestore was briefly unreachable is a worse
     * outcome than letting a stale build run for one more session — this gate
     * exists to retire old versions, not to be a second authentication layer.
     */
    suspend fun checkForUpdate(context: Context): UpdateStatus {
        val doc = try {
            FirebaseClients.db
                .collection(CONFIG_COLLECTION)
                .document(CONFIG_DOCUMENT)
                .get()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "checkForUpdate: config unreachable, allowing launch", e)
            return UpdateStatus.UpToDate
        }

        if (!doc.exists()) return UpdateStatus.UpToDate

        val minVersion = doc.getLong("minVersionCode") ?: 0L
        val latestVersion = doc.getLong("latestVersionCode") ?: minVersion
        val installed = installedVersionCode(context)

        val storeUrl = doc.getString("playStoreUrl")?.takeIf { it.isNotBlank() }
            ?: playStoreUrl(context.packageName)
        val configuredMessage = doc.getString("updateMessage")
            ?.takeIf { it.isNotBlank() }

        return when {
            installed < minVersion -> UpdateStatus.Required(
                message = configuredMessage ?: DEFAULT_REQUIRED_MESSAGE,
                storeUrl = storeUrl,
            )

            installed < latestVersion -> UpdateStatus.Optional(
                message = configuredMessage ?: DEFAULT_OPTIONAL_MESSAGE,
                storeUrl = storeUrl,
            )

            else -> UpdateStatus.UpToDate
        }
    }

    /** Play Store listing for a package, as an https URL. */
    private fun playStoreUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"
}
