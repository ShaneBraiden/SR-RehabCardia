# SR-Cardiocare

A cardiac rehabilitation mobile application connecting physiotherapists with patients. Doctors create exercise plans, upload instructional videos, and track patient progress. Patients follow guided workouts and submit recovery feedback.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android | Kotlin, Jetpack Compose, Material3 |
| iOS | Swift, UIKit |
| Backend | Firebase (Auth, Cloud Firestore) |
| Video | YouTube Data API v3 |
| Admin Scripts | Python 3, Firebase Admin SDK |

## Features

- **Authentication** — Email/password login with role-based access (Admin, Doctor, Patient)
- **Patient Onboarding** — Injury selection and goal setting flow
- **Exercise Library** — Categorized exercises (Knee, Shoulder, Back) with video playback
- **Workout Plans** — Doctor-created plans with custom sets, reps, and scheduling
- **Video Upload** — YouTube integration for instructional content
- **Progress Tracking** — Post-workout feedback (pain level, difficulty) and analytics dashboard
- **Appointments** — Scheduling with status management
- **Notifications** — In-app notification system
- **Release Gate** — Server-controlled minimum app version; old builds are prompted or blocked

## Project Structure

```
Android/          Kotlin + Jetpack Compose app
IOS/              Swift + UIKit app
backend/          Python admin scripts & data seeding
functions/        Firebase Cloud Functions (Node.js)
Dev/              Design docs, mockups & build checklist
```

## Setup

### Prerequisites

- Android Studio (Kotlin 2.2+) / Xcode
- Firebase project with Auth & Firestore enabled
- Python 3.9+ (for admin scripts)
- Node.js 24 (for Cloud Functions)

### Quick Start

1. **Firebase** — Create a project, enable Email/Password auth and Firestore. Download `google-services.json` (Android) and `GoogleService-Info.plist` (iOS). See `Dev/FIREBASE_SETUP.md` for details.

2. **Seed Data**
   ```bash
   cd backend
   pip install -r requirements.txt
   python seed_data.py
   ```

3. **Android** — Open `Android/` in Android Studio, place `google-services.json` in `Android/app/`, build and run.

4. **iOS** — Open the project in Xcode, add `GoogleService-Info.plist`, build and run.

## Releasing an Android update

The Android app reads `config/appVersion` in Firestore at launch and on every
resume, and compares it to the installed `versionCode`:

| Condition | What the user sees |
|-----------|--------------------|
| `installed < minVersionCode` | Full-screen blocker. No dismiss — the app is unusable until they update. |
| `installed < latestVersionCode` | "Update available" dialog with **Update** and **Maybe later**. |
| otherwise | Nothing. |

Because the gate lives on the server, a release can be made mandatory *after*
it has shipped — which is the case that matters, since a build you need to pull
is one you did not know was bad when you built it.

```bash
cd functions
export GOOGLE_APPLICATION_CREDENTIALS=../backend/service-account-key.json

# See the current gate
node scripts/set-app-version.js --show

# Normal release: announce build 5, leave build 4 usable
node scripts/set-app-version.js --latest 5

# Pull build 4: everyone must move to 5
node scripts/set-app-version.js --min 5 --latest 5 \
  --message "This update fixes patient login. Please install it."
```

The check fails open: if Firestore is unreachable the app launches normally.
Locking a clinician out of a patient's plan over a flaky network is worse than
letting a stale build run one more session.

## Deleting a user

Deletion runs through the `deleteUserAccount` callable, **not** the client SDK.
The client can only delete the account it is signed in as, so a Firestore-only
delete left the Auth record behind and the email stayed claimed — re-adding the
same person failed with "email already in use". The callable removes the
Firestore documents *and* the Auth account, so the address is free again.

```bash
cd functions
firebase deploy --only functions:deleteUserAccount
```

Accounts orphaned by the old client-side delete can be cleared by email through
the same callable (`{email: "..."}`), admin only.

