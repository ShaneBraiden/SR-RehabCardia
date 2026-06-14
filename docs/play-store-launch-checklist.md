# RehabCardia — Play Store Launch Checklist

## Done in code (June 2026)
- [x] `targetSdk 36`, `minSdk 26`, release build minified + resource-shrunk, non-debuggable
- [x] Release signing via `keystore.properties` (not in git)
- [x] Cleartext traffic blocked; HTTPS-only network security config
- [x] EncryptedSharedPreferences for local storage; Play Integrity App Check
- [x] Firestore rules: per-user list scoping + doctor role-escalation fix (deploy required)
- [x] WebView hardening: YouTube-only navigation allowlist, CSP, video-ID validation
- [x] Verbose/debug logs stripped from release builds (ProGuard)
- [x] Privacy policy + terms (markdown in `docs/`, hostable HTML in `hosting/public/`)
- [x] First-login onboarding flow (replaces old guided tour)

## Before you upload — do these once

1. **Create the release keystore** (if not already done):
   `keytool -genkeypair -v -keystore RehabCardia-release.jks -alias RehabCardia -keyalg RSA -keysize 2048 -validity 10000`
   Then copy `keystore.properties.template` → `Android/keystore.properties` and fill in the paths/passwords. **Back the keystore up somewhere safe — losing it means losing the app listing.**

2. **Deploy the updated security rules and the policy pages:**
   ```
   firebase deploy --only firestore:rules,hosting
   ```
   Privacy policy will be live at `https://sr-cardiocare.web.app/privacy-policy.html`.

3. **Build the App Bundle** (Play requires .aab, not .apk):
   ```
   cd Android
   .\gradlew.bat bundleRelease
   ```
   Output: `Android/app/build/outputs/bundle/release/app-release.aab`

4. **Smoke-test the release build** on a real device:
   ```
   .\gradlew.bat assembleRelease
   adb install app/build/outputs/apk/release/app-release.apk
   ```
   Log in as patient, doctor, and admin; complete a session; send a chat message; check push notifications. (R8 minification can break things debug builds don't catch.)

5. **App Check note:** the release build uses Play Integrity. Builds installed outside Play (adb sideload) may fail App Check — register the device's debug token in Firebase console if needed, and verify App Check enforcement settings before launch.

## Play Console setup

- [ ] Create app (Medical category, free, 18+)
- [ ] Store listing: title (max 30 chars: "RehabCardia"), short description (80), full description (4000)
- [ ] Graphics: app icon 512×512, feature graphic 1024×500, ≥4 phone screenshots (capture Login, Onboarding, Patient Home, Workout Player, Analytics)
- [ ] Privacy policy URL: `https://sr-cardiocare.web.app/privacy-policy.html`
- [ ] Data safety form: use `docs/play-store-data-safety.md`
- [ ] App access: provide demo patient + doctor credentials for review (seeded demo data only)
- [ ] Health apps declaration + Medical app disclosure
- [ ] Content rating questionnaire (IARC)
- [ ] Target audience: 18+
- [ ] Countries/regions
- [ ] Upload `.aab` to **internal testing** first; promote to production after a pass

## Recommended (not blocking)
- [ ] Add Firebase Crashlytics before wide rollout — there is currently no crash reporting, so production issues will be invisible (remember to update the data safety form when added)
- [ ] Generate a Baseline Profile for faster cold start: `.\gradlew.bat :app:generateBaselineProfile`
- [ ] Versioning for updates: bump `versionCode` on every upload (`Android/app/build.gradle.kts`)
