# AeroTrack — Android Health Connect & Running Analytics App

A 100% native Android app built with **Jetpack Compose**, **Material 3 Expressive**, and the **Google Health Connect API** to read live running workouts, GPS routes, heart rate zones, and splits directly from Google Health / Fitbit. Includes a **one-click "Ask ChatGPT"** button for instant AI coaching.

---

## 📱 How to Install the Android SDK & Run This App

Because you are on **CachyOS / Arch Linux**, installing Android Studio (which automatically configures the Android SDK, Java, Kotlin, and tools) takes just one command:

### Step 1: Install Android Studio
Run this in your terminal:
```bash
sudo pacman -S android-studio
```
*(or via your AUR helper: `paru -S android-studio`)*

### Step 2: Open this Project
1. Open **Android Studio** from your application menu or terminal (`android-studio`).
2. When the initial setup wizard asks you to download the **Android SDK**, click **Next / Finish** (it will automatically install the SDK to `~/Android/Sdk`).
3. In Android Studio, click **Open** and select this directory:
   `/home/aditya/Downloads/running-tracker/android-app`

### Step 3: Run on your Phone or Emulator
1. Connect your Android phone via USB and enable **USB Debugging** (in Developer Options), OR create an Android Virtual Device (AVD Emulator).
2. Click the green **▶ Run** button at the top of Android Studio.
3. The app will launch, check Health Connect status, request permissions, and read your live running workouts!

---

## 🌟 App Architecture & State Machine

- **Proper State Handling (Zero Fake/Mock Data)**:
  - **Permission Onboarding State** (`HealthConnectPermissionScreen`): Explains exactly what permissions are required (Exercise sessions, Heart Rate telemetry, GPS routes) with a one-tap trigger for Android's official Health Connect permission dialog.
  - **Install State** (`HealthConnectNotAvailableScreen`): Detects if Health Connect needs to be installed or updated, offering a direct Google Play Store action.
  - **Empty State** (`NoWorkoutsScreen`): Informs you if no runs were found in Health Connect over the last 30 days, with a live "Sync Now" button.
  - **Ready State** (`WorkoutDetailScreen`): Full Material 3 Expressive interface displaying live running sessions. Includes a top carousel chip selector when multiple runs exist.
- **Health Connect Integration** (`com.aerotrack.running.health.HealthConnectManager`):
  - Queries Google Health Connect for `ExerciseSessionRecord` (Running)
  - Reads `ExerciseRoute` GPS points (lat, lon, altitude, time)
  - Reads `HeartRateRecord` continuous samples and maps them to GPS timestamps
  - Calculates 1 km splits, elevation gain/loss, and 5 HR intensity zones
- **Material 3 Expressive UI** (`com.aerotrack.running.ui`):
  - Large expressive TopAppBar with workout badges
  - 24dp rounded Hero Metric Cards (Distance, Moving Time, Avg Pace, Avg HR)
  - Heart Rate Zone breakdown cards with colored progress bars
  - Kilometer Splits list
- **One-Click "Ask ChatGPT" Floating Action Button**:
  - Automatically formats the full workout telemetry, splits, and HR zones into a coaching prompt
  - Copies to Android system clipboard
  - Directly launches the ChatGPT app or `https://chatgpt.com/` ready to paste and ask questions!
