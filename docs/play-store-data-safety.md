# Google Play — Data Safety Form Answers (RehabCardia)

Fill the Play Console **App content → Data safety** section with the answers below.
They match what the app actually does as of June 2026.

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS/TLS enforced; cleartext blocked by network security config) |
| Do you provide a way for users to request that their data is deleted? | **Yes** — via clinic/administrator or email request (deletion contact: sr.cardiocare@gmail.com; policy URL covers this) |

## Data types to declare

### Personal info
- **Name** — Collected. Shared: No. Processed ephemerally: No. Required. Purpose: App functionality, Account management.
- **Email address** — Collected. Shared: No. Required. Purpose: App functionality, Account management.
- **User IDs** (Firebase UID) — Collected. Shared: No. Required. Purpose: App functionality, Account management.

### Health and fitness
- **Health info** (post-workout pain level, difficulty, notes; rehabilitation plan adherence) — Collected. Shared: No. Required. Purpose: App functionality.
- **Fitness info** (exercise sessions, sets/reps completed, history) — Collected. Shared: No. Required. Purpose: App functionality.

### Messages
- **Other in-app messages** (patient–doctor chat) — Collected. Shared: No. Required. Purpose: App functionality.

### Photos and videos
- **Videos** — Collected **only for doctor/admin accounts** that upload exercise demonstration videos (patient builds do not upload personal videos). If you keep video upload in the shipped app, declare: Collected, Shared: No, Optional, Purpose: App functionality.

### App activity
- **App interactions** (login timestamps for security audit) — Collected. Shared: No. Required. Purpose: App functionality, Fraud prevention/security.

### App info and performance
- Not collected (no Crashlytics/analytics SDK is integrated). If you add Crashlytics later, update this section.

### Device or other IDs
- **Device or other IDs** (Firebase Cloud Messaging registration token) — Collected. Shared: No. Required. Purpose: App functionality (push notifications).

## Not collected (answer "No" everywhere else)
Location, financial info, web browsing, contacts, calendar, SMS/call logs, files and docs (beyond explicit attachments), installed apps, advertising IDs.

## Other Play Console content declarations

- **Privacy policy URL:** `https://sr-cardiocare.web.app/privacy-policy.html` (deploy with `firebase deploy --only hosting` first)
- **App category:** Medical
- **Target audience:** 18+ (not directed at children → answer "No" to appealing to children)
- **Health apps declaration:** declare as a health app (cardiac rehabilitation / patient management). Not a Covid-19 app. Does not connect to Health Connect.
- **Login credentials for review:** Google requires reviewer access for login-gated apps. Create a **demo patient account** (and optionally a demo doctor) in Firebase and provide the credentials under *App access* in Play Console. Use seeded demo data, never a real patient.
- **Ads:** No, the app contains no ads.
- **Account deletion URL:** point to the privacy policy section 7 (or add a dedicated page later). Because accounts are provisioned by clinics (no in-app self-registration), Play's "account creation → in-app deletion" requirement does not strictly apply, but having the documented email path avoids review friction.
