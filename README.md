# AeroTrack — Google Health Running & TCX Visualizer

A rich, interactive telemetry dashboard and analytics suite for running workouts exported from **Google Health / Fitbit / Garmin / Apple Watch**.

## 🚀 Quick Start

You can open the app in two ways:

### Option 1: Direct in Browser (No setup needed)
Simply double-click or open `index.html` in your favorite web browser (Chrome, Firefox, Edge, Brave, Safari):
```bash
firefox index.html
# or
xdg-open index.html
```

### Option 2: Local Python Server (Recommended)
Run the bundled lightweight server:
```bash
python3 server.py
```
This automatically serves the app on `http://localhost:8080/index.html` and launches your default browser.

---

## 🏃 Pre-Loaded Workout Data
Your file `Telegram Desktop/exercise_tcx_file.tcx` is automatically loaded into the app when launched:
- **Sport**: Running
- **Distance**: 1.27 km (0.79 mi)
- **Duration**: 10m 00s moving time (10m 02s total)
- **Avg Pace**: 7:53 min/km (12:41 min/mi)
- **Best Pace**: 5:14 min/km
- **Avg Heart Rate**: 169 bpm (Max: 179 bpm, Min: 122 bpm)
- **Elevation**: 801.5m to 837.3m (Ascent: +89.8m, Descent: -89.7m)
- **Calories**: 109 kcal (~651 kcal/hr burn rate)
- **Device**: Fitbit Air / Google Health

---

## ✨ Features & Deep Metrics

### 1. Interactive GPS Map
- **Segment Color Gradients**:
  - **By Pace**: Green/Cyan (fastest) ➔ Yellow (moderate) ➔ Red (slowest)
  - **By Heart Rate**: Zone 1 (Blue) ➔ Zone 2 (Green) ➔ Zone 3 (Yellow) ➔ Zone 4 (Orange) ➔ Zone 5 (Red)
  - **By Elevation**: Gradient from valley (lowest altitude) to peak (highest altitude)
  - **Solid Route**: Vibrant electric line
- **Map Layers**: Esri Dark Canvas, OpenStreetMap, Satellite Imagery, Topographic Map, and Light Canvas (100% Free, No API Key, No Watermark)
- **Start / Finish Pins & Split Badges**: Pinpoint markers indicating every kilometer or mile marker
- **Interactive Live Scrubber**: Hovering or scrubbing across any chart moves an animated marker along the map and shows a live HUD with:
  - Distance & Elapsed Time
  - Current Pace (min/km)
  - Heart Rate & Active Zone
  - Altitude (m)

### 2. Synchronized Telemetry Charts
- **Pace Chart**: Features standard runner's inverted Y-axis (faster paces appear higher) with rolling 7-point smoothing to eliminate GPS jitter.
- **Heart Rate Chart**: BPM tracking with colored zone threshold background bands.
- **Elevation Profile**: Detailed altitude graph with ascent/descent contours.
- **Toggle X-Axis**: Switch between **Distance** (km/mi) and **Time** (mm:ss).
- **Synchronized Hover**: Moving cursor across any chart simultaneously scrubs all charts and syncs with the map pinpoint.

### 3. Heart Rate Zone Distribution
- 5 Standard Intensity Zones (Peak, Threshold, Aerobic, Fat Burn, Warm-up).
- Time spent and percentage of total workout in each zone.
- **Interactive Zone Filter**: Click any zone card to highlight only those parts of your running route on the map!
- **Custom Max HR**: Adjust your personal Max HR input to recalculate zones on the fly.

### 4. Splits & Laps Breakdown
- Automatic interval calculations: **1 km**, **500 m**, or **1 mile** splits.
- For each split: Distance, Split Time, Average Pace, Best Pace, Avg HR, Max HR, Elevation Change (+/-), and relative Pace comparison bar.
- Clicking any split row automatically zooms and highlights that segment on the map!

### 5. Universal Drag & Drop Support
- Drag and drop any `.tcx` or `.gpx` file anywhere onto the page to inspect past or future workouts.
- 100% private and runs offline inside your browser.

### 6. Export & Print
- **CSV Export**: Full raw telemetry table (Time, Lat, Lon, Altitude, Distance, Speed, Pace, Heart Rate).
- **JSON Export**: Complete structured workout object.
- **Print / PDF**: Clean printable format for coaching logs.
