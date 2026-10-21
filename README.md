# AeroTrack 🏃⚡
> **Advanced Running Telemetry Studio & Native Android Companion**  
> Direct Health Connect integration • Interactive GPS Route Heatmaps • Real-time Waveform Scrubbing • Circle Loop Laps Detection

[![Release](https://img.shields.io/github/v/release/AdityaHebballe/running-tracker?color=A855F7&label=Release)](https://github.com/AdityaHebballe/running-tracker/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Web-38BDF8)](https://github.com/AdityaHebballe/running-tracker)
[![Theme](https://img.shields.io/badge/Theme-Obsidian%20Violet-A855F7)](https://github.com/AdityaHebballe/running-tracker)
[![License](https://img.shields.io/badge/License-MIT-10B981)](LICENSE)

---

## 📱 Download Android Release APK

Get the high-performance, R8-optimized release build directly on your Android device:

👉 **[Download Latest AeroTrack Release APK (v1.0.0)](https://github.com/AdityaHebballe/running-tracker/releases/latest/download/AeroTrack-release.apk)**

*Min Android Version: Android 8.0 (API 26+) • Target: Android 14+ / Health Connect 1.1*

---

## ✨ Highlights & Key Features

### 1. 🔥 Speed & Pace Heatmap (Red = Fast, Green = Slow)
* **Dynamic Gradient Routing**: The route lines dynamically reflect your instantaneous running pace:
  - 🔴 **Fast (Peak Sprint)**: Glowing Hot Red (`#EF4444`)
  - 🟡 **Moderate (Cruise / Aerobic)**: Amber Yellow (`#FBBF24`)
  - 🟢 **Slow (Recovery / Warmup)**: Cool Emerald Green (`#10B981`)
* **Multi-Layer Contrast**: Underlying high-contrast dark casing guarantees visibility over both street maps and dark canvas backgrounds.

### 2. 🗺️ Route Studio: Native OpenStreetMap + Sharp Vector Track
* **Native OpenStreetMap (OSM)**: 
  - Zero WebView or sandboxing latency — powered by native hardware-accelerated tile streaming (`tile.openstreetmap.org`).
  - Deep pinch-to-zoom (levels 4 to 20) with retina DPI scaling.
  - Bold, density-scaled start (`#00E676`), finish (`#FF1744`), and scrubber beacon (`#A855F7`).
* **Crisp Vector Track Mode**:
  - Full equirectangular aspect ratio projection preserving exact geographic geometry.
  - Razor-sharp line rendering with interactive pan and multi-touch zoom.

### 3. 📈 Synchronized Dual Waveform Telemetry Scrubber
* **Dual Simultaneous Tracking**: Slide smoothly across your workout with an interactive tactile scrubber:
  - 🔵 **Cyan Dot**: Rides along the inverted **Pace curve** (faster pace shown higher).
  - 🟣 **Rose Dot**: Rides along the **Heart Rate waveform**.
* **4 Uniform Real-Time HUD Badges**: Instantaneous readout of `PACE`, `HEART RATE`, `DISTANCE`, and `TIME`.
* **Circle Loop / Lap Jumps**: Jump instantly to any detected lap or split with dedicated tap chips.

### 4. 🔄 Circle Loop & Lap Telemetry Detection
* Automatically detects circular loops and splits workouts into clean laps using GPS trackpoint clustering.
* Per-lap pace comparisons with visual performance badges (`Faster` vs `Slower`).

### 5. 💜 Obsidian-Violet Dark Theme
* Ultra-dark obsidian background (`#09080E`) with rich purple/violet accents (`#A855F7`, `#C084FC`).
* Edge-to-edge Android system navigation and status bar styling.
* Designed for low power consumption on OLED/AMOLED displays.

### 6. 🌐 Dual Ecosystem: Android App + Web Telemetry Dashboard
* **Native Android App**: Compose Material 3 Expressive UI with Google Health Connect integration.
* **Standalone Web Dashboard**: Runs offline in any browser with drag-and-drop `.tcx` / `.gpx` import, zone filters, and CSV/JSON export.

---

## 🛠️ Tech Stack & Architecture

### Android App (`android-app/`)
* **Language & Runtime**: Kotlin 1.9.22, JVM 17
* **UI Framework**: Jetpack Compose, Material 3 Expressive, Compose Animation
* **Mapping**: `org.osmdroid:osmdroid-android:6.1.18` (Hardware accelerated native map tiles & `PolychromaticPaintList`)
* **Health Data**: AndroidX Health Connect Client (`androidx.health.connect:connect-client:1.1.0-alpha11`)
* **Compilation**: R8 Minification, Tree-Shaking, and Dead Code Stripping (`isMinifyEnabled = true`, `isShrinkResources = true`)

### Web Dashboard
* **Engine**: Pure Vanilla JavaScript (zero external framework dependencies)
* **Visualization**: Leaflet.js (custom dark tiles), Chart.js 4 (dual-axis synchronized scrubbing)
* **Parsing**: In-browser XML TCX/GPX parser with haversine distance calculation

---

## 🚀 Building & Running

### Android App
```bash
cd android-app

# Build debug APK
./gradlew assembleDebug

# Build optimized Release APK
./gradlew assembleRelease

# Install directly to connected device
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Web Telemetry Dashboard
```bash
# Direct in browser
xdg-open index.html

# Or via bundled server
python3 server.py
# Serves at http://localhost:8080
```

---

## 📄 License
This project is open-source under the [MIT License](LICENSE).
