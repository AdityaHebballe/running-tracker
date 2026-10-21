/**
 * AeroTrack — Google Health Running Visualizer & Analytics
 * Author: Antigravity Agent
 */

(function () {
  'use strict';

  // --- Global Application State ---
  const state = {
    workout: null,
    unitSystem: 'metric', // 'metric' (km, m, min/km) or 'imperial' (mi, ft, min/mi)
    xAxisMode: 'dist',    // 'dist' or 'time'
    trackColorMode: 'pace', // 'pace', 'hr', 'alt', 'solid'
    splitDistance: 1000,  // meters (1000m = 1km, 500m, 1609.34m = 1mi)
    maxHeartRate: 185,
    activeZoneFilter: null,
    map: null,
    mapTileLayer: null,
    currentTrackLayers: [],
    splitMarkers: [],
    startFinishMarkers: [],
    scrubMarker: null,
    charts: {
      pace: null,
      hr: null,
      elev: null
    },
    activeScrubIndex: -1,
    searchFilter: ''
  };

  // --- Tile Providers for Leaflet (100% Free, No API Key or Watermark Required) ---
  const TILE_LAYERS = {
    dark: {
      type: 'composite',
      base: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      labels: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}',
      options: { attribution: '&copy; Esri, HERE, Garmin', maxNativeZoom: 16, maxZoom: 21 }
    },
    osm: {
      url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      options: { attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors', maxNativeZoom: 19, maxZoom: 21 }
    },
    satellite: {
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      options: { attribution: '&copy; Esri, Maxar, Earthstar Geographics', maxNativeZoom: 19, maxZoom: 21 }
    },
    topo: {
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}',
      options: { attribution: '&copy; Esri, USGS', maxNativeZoom: 18, maxZoom: 21 }
    },
    light: {
      type: 'composite',
      base: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      labels: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Reference/MapServer/tile/{z}/{y}/{x}',
      options: { attribution: '&copy; Esri, HERE, Garmin', maxNativeZoom: 16, maxZoom: 21 }
    }
  };

  // --- Utility Functions ---
  function formatPace(secondsPerKm, isImperial = false) {
    if (!secondsPerKm || isNaN(secondsPerKm) || !isFinite(secondsPerKm) || secondsPerKm <= 0 || secondsPerKm > 1800) {
      return '--:--';
    }
    const sec = isImperial ? secondsPerKm * 1.60934 : secondsPerKm;
    const m = Math.floor(sec / 60);
    const s = Math.round(sec % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  function formatTime(totalSeconds) {
    if (isNaN(totalSeconds) || totalSeconds < 0) return '00:00';
    const hrs = Math.floor(totalSeconds / 3600);
    const mins = Math.floor((totalSeconds % 3600) / 60);
    const secs = Math.floor(totalSeconds % 60);
    if (hrs > 0) {
      return `${hrs}:${mins < 10 ? '0' : ''}${mins}:${secs < 10 ? '0' : ''}${secs}`;
    }
    return `${mins < 10 ? '0' : ''}${mins}:${secs < 10 ? '0' : ''}${secs}`;
  }

  function haversineDistanceMeters(lat1, lon1, lat2, lon2) {
    const R = 6371000; // meters
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  // --- Color Interpolation Helpers ---
  function interpolateColor(color1, color2, factor) {
    const f = Math.max(0, Math.min(1, factor));
    const c1 = parseInt(color1.slice(1), 16);
    const c2 = parseInt(color2.slice(1), 16);
    const r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
    const r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
    const r = Math.round(r1 + f * (r2 - r1));
    const g = Math.round(g1 + f * (g2 - g1));
    const b = Math.round(b1 + f * (b2 - b1));
    return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
  }

  function getGradientColor(value, min, max, invert = false) {
    if (min === max) return '#ef4444';
    let norm = (value - min) / (max - min);
    norm = Math.max(0, Math.min(1, norm));
    if (invert) norm = 1 - norm; // e.g., for pace where smaller value = faster = red

    if (norm < 0.5) {
      return interpolateColor('#ef4444', '#fbbf24', norm * 2); // Red to Yellow
    } else {
      return interpolateColor('#fbbf24', '#10b981', (norm - 0.5) * 2); // Yellow to Green
    }
  }

  function getHeartRateColor(hr, maxHr) {
    if (!hr || !maxHr) return '#94a3b8';
    const pct = hr / maxHr;
    if (pct >= 0.90) return '#ef4444'; // Peak Zone 5 (Red)
    if (pct >= 0.80) return '#f97316'; // Threshold Zone 4 (Orange)
    if (pct >= 0.70) return '#fbbf24'; // Aerobic Zone 3 (Yellow)
    if (pct >= 0.60) return '#34d399'; // Fat Burn Zone 2 (Green)
    return '#60a5fa';                 // Warm Up Zone 1 (Blue)
  }

  // --- TCX & GPX Parsers ---
  function parseTCX(xmlStr) {
    let doc;
    try {
      const parser = new DOMParser();
      doc = parser.parseFromString(xmlStr, 'application/xml');
    } catch (e) {
      console.error('DOMParser error:', e);
    }

    const activity = {
      sport: 'Running',
      startTime: null,
      endTime: null,
      creator: 'Fitbit / Google Health',
      calories: 0,
      totalTimeSeconds: 0,
      totalDistanceMeters: 0,
      points: []
    };

    if (doc) {
      // Activity metadata
      const actElem = doc.querySelector('Activity');
      if (actElem) {
        activity.sport = actElem.getAttribute('Sport') || 'Running';
      }
      const creatorElem = doc.querySelector('Creator Name');
      if (creatorElem) {
        activity.creator = creatorElem.textContent.trim();
      }
      const calElem = doc.querySelector('Lap Calories');
      if (calElem) {
        activity.calories = parseInt(calElem.textContent, 10) || 0;
      }
      const timeElem = doc.querySelector('Lap TotalTimeSeconds');
      if (timeElem) {
        activity.totalTimeSeconds = parseFloat(timeElem.textContent) || 0;
      }

      // Trackpoints
      const tpElems = doc.querySelectorAll('Trackpoint');
      let cumulativeDist = 0;
      let prevLat = null, prevLon = null;

      tpElems.forEach((tp) => {
        const timeStr = tp.querySelector('Time')?.textContent?.trim();
        const latElem = tp.querySelector('LatitudeDegrees');
        const lonElem = tp.querySelector('LongitudeDegrees');
        const altElem = tp.querySelector('AltitudeMeters');
        const distElem = tp.querySelector('DistanceMeters');
        const hrElem = tp.querySelector('HeartRateBpm Value');

        if (!latElem || !lonElem) return;

        const lat = parseFloat(latElem.textContent);
        const lon = parseFloat(lonElem.textContent);
        const alt = altElem ? parseFloat(altElem.textContent) : null;
        let dist = distElem ? parseFloat(distElem.textContent) : null;
        const hr = hrElem ? parseInt(hrElem.textContent, 10) : null;

        if (dist === null && prevLat !== null) {
          cumulativeDist += haversineDistanceMeters(prevLat, prevLon, lat, lon);
          dist = cumulativeDist;
        } else if (dist !== null) {
          cumulativeDist = dist;
        }

        prevLat = lat;
        prevLon = lon;

        activity.points.push({
          timeStr,
          time: timeStr ? new Date(timeStr) : null,
          lat,
          lon,
          alt: alt !== null ? alt : 0,
          dist: dist !== null ? dist : 0,
          hr: hr !== null ? hr : 0
        });
      });
    }

    // Fallback regex parsing if DOMParser found nothing
    if (activity.points.length === 0) {
      const tpRegex = /<Trackpoint>([\s\S]*?)<\/Trackpoint>/g;
      let match;
      while ((match = tpRegex.exec(xmlStr)) !== null) {
        const block = match[1];
        const timeMatch = block.match(/<Time>(.*?)<\/Time>/);
        const latMatch = block.match(/<LatitudeDegrees>(.*?)<\/LatitudeDegrees>/);
        const lonMatch = block.match(/<LongitudeDegrees>(.*?)<\/LongitudeDegrees>/);
        const altMatch = block.match(/<AltitudeMeters>(.*?)<\/AltitudeMeters>/);
        const distMatch = block.match(/<DistanceMeters>(.*?)<\/DistanceMeters>/);
        const hrMatch = block.match(/<HeartRateBpm>[\s\S]*?<Value>(.*?)<\/Value>/);

        if (latMatch && lonMatch) {
          activity.points.push({
            timeStr: timeMatch ? timeMatch[1] : '',
            time: timeMatch ? new Date(timeMatch[1]) : null,
            lat: parseFloat(latMatch[1]),
            lon: parseFloat(lonMatch[1]),
            alt: altMatch ? parseFloat(altMatch[1]) : 0,
            dist: distMatch ? parseFloat(distMatch[1]) : 0,
            hr: hrMatch ? parseInt(hrMatch[1], 10) : 0
          });
        }
      }
    }

    processWorkoutTelemetry(activity);
    return activity;
  }

  function parseGPX(gpxStr) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(gpxStr, 'application/xml');
    const trkpts = doc.querySelectorAll('trkpt');

    const activity = {
      sport: 'Running',
      startTime: null,
      endTime: null,
      creator: 'GPX File',
      calories: 0,
      totalTimeSeconds: 0,
      totalDistanceMeters: 0,
      points: []
    };

    let cumDist = 0;
    let prevLat = null, prevLon = null;

    trkpts.forEach((tp) => {
      const lat = parseFloat(tp.getAttribute('lat'));
      const lon = parseFloat(tp.getAttribute('lon'));
      const timeStr = tp.querySelector('time')?.textContent?.trim();
      const altElem = tp.querySelector('ele');
      const alt = altElem ? parseFloat(altElem.textContent) : 0;
      const hrElem = tp.querySelector('hr') || tp.querySelector('HeartRateBpm Value');
      const hr = hrElem ? parseInt(hrElem.textContent, 10) : 0;

      if (prevLat !== null) {
        cumDist += haversineDistanceMeters(prevLat, prevLon, lat, lon);
      }
      prevLat = lat;
      prevLon = lon;

      activity.points.push({
        timeStr,
        time: timeStr ? new Date(timeStr) : null,
        lat,
        lon,
        alt,
        dist: cumDist,
        hr
      });
    });

    processWorkoutTelemetry(activity);
    return activity;
  }

  // --- Telemetry Smoothing & Calculations ---
  function processWorkoutTelemetry(act) {
    const pts = act.points;
    if (pts.length === 0) return;

    act.startTime = pts[0].time;
    act.endTime = pts[pts.length - 1].time;

    const t0 = pts[0].time ? pts[0].time.getTime() : 0;
    for (let i = 0; i < pts.length; i++) {
      pts[i].elapsedSeconds = pts[i].time ? (pts[i].time.getTime() - t0) / 1000 : i;
    }

    // Rolling Pace & Speed smoothing with 7-point window
    const windowSize = 4; // +/- 4 points (~8 sec window)
    for (let i = 0; i < pts.length; i++) {
      const wStart = Math.max(0, i - windowSize);
      const wEnd = Math.min(pts.length - 1, i + windowSize);
      const dDist = pts[wEnd].dist - pts[wStart].dist;
      const dTime = pts[wEnd].elapsedSeconds - pts[wStart].elapsedSeconds;

      const speedMs = dTime > 0 ? (dDist / dTime) : 0;
      pts[i].speedMs = speedMs;
      pts[i].speedKmh = speedMs * 3.6;
      // Filter extreme values and stationary stops
      pts[i].paceSecPerKm = speedMs >= 0.4 ? (1000 / speedMs) : null;
    }

    // Moving time calculation (points where speedMs >= 0.5 m/s)
    let movingSeconds = 0;
    for (let i = 1; i < pts.length; i++) {
      const dt = pts[i].elapsedSeconds - pts[i - 1].elapsedSeconds;
      if (pts[i].speedMs >= 0.5) {
        movingSeconds += dt;
      }
    }
    if (movingSeconds === 0) {
      movingSeconds = pts[pts.length - 1].elapsedSeconds;
    }

    const totalDist = pts[pts.length - 1].dist;
    const elapsedSec = pts[pts.length - 1].elapsedSeconds;

    act.totalDistanceMeters = totalDist;
    act.totalTimeSeconds = act.totalTimeSeconds || elapsedSec;
    act.movingSeconds = movingSeconds;
    act.avgPaceSec = totalDist > 0 ? (act.totalTimeSeconds / (totalDist / 1000)) : 0;

    // Best Pace (minimum rolling pace among moving points)
    const validPaces = pts.map(p => p.paceSecPerKm).filter(p => p !== null && p >= 120 && p <= 900);
    act.bestPaceSec = validPaces.length > 0 ? Math.min(...validPaces) : act.avgPaceSec;

    // Heart Rate stats
    const validHrs = pts.map(p => p.hr).filter(h => h > 40);
    act.avgHr = validHrs.length > 0 ? Math.round(validHrs.reduce((a, b) => a + b, 0) / validHrs.length) : 0;
    act.maxHr = validHrs.length > 0 ? Math.max(...validHrs) : 0;
    act.minHr = validHrs.length > 0 ? Math.min(...validHrs) : 0;

    // Altitude stats with 0.5m threshold filter
    const validAlts = pts.map(p => p.alt);
    act.elevMin = Math.min(...validAlts);
    act.elevMax = Math.max(...validAlts);

    let gain = 0, loss = 0;
    let prevAlt = pts[0].alt;
    for (let i = 1; i < pts.length; i++) {
      const diff = pts[i].alt - prevAlt;
      if (Math.abs(diff) >= 0.4) {
        if (diff > 0) gain += diff;
        else loss += Math.abs(diff);
        prevAlt = pts[i].alt;
      }
    }
    act.elevGain = Math.round(gain * 10) / 10;
    act.elevLoss = Math.round(loss * 10) / 10;

    // Speed stats
    const speeds = pts.map(p => p.speedKmh);
    act.avgSpeedKmh = totalDist > 0 && act.totalTimeSeconds > 0 ? (totalDist / 1000) / (act.totalTimeSeconds / 3600) : 0;
    act.maxSpeedKmh = speeds.length > 0 ? Math.min(25, Math.max(...speeds)) : 0;

    // Calories estimate if 0 (standard runner formula: ~1 kcal per kg per km, default 70kg)
    if (!act.calories) {
      act.calories = Math.round((totalDist / 1000) * 75 * 1.036);
    }
  }

  // --- Map Initialization & Track Layers ---
  function createTileLayer(cfg) {
    if (cfg.type === 'composite') {
      const baseLayer = L.tileLayer(cfg.base, cfg.options);
      const labelsLayer = L.tileLayer(cfg.labels, { ...cfg.options, zIndex: 10 });
      return L.layerGroup([baseLayer, labelsLayer]);
    }
    return L.tileLayer(cfg.url, cfg.options);
  }

  function initMap() {
    const mapContainer = document.getElementById('map');
    if (!mapContainer) return;

    state.map = L.map('map', {
      zoomControl: true,
      attributionControl: false,
      scrollWheelZoom: true,
      doubleClickZoom: true,
      touchZoom: true,
      boxZoom: true,
      minZoom: 3,
      maxZoom: 21
    }).setView([12.93367, 77.61936], 18);

    // Initial layer: Dark Canvas (100% Free, No Watermark, No API key)
    state.mapTileLayer = createTileLayer(TILE_LAYERS.dark).addTo(state.map);

    // Live Scrub pulsing marker
    const scrubIcon = L.divIcon({
      className: 'scrub-marker-pulse',
      iconSize: [16, 16],
      iconAnchor: [8, 8]
    });
    state.scrubMarker = L.marker([0, 0], { icon: scrubIcon, zIndexOffset: 1000 });
  }

  function setMapTile(tileKey) {
    if (!state.map || !TILE_LAYERS[tileKey]) return;
    if (state.mapTileLayer) {
      state.map.removeLayer(state.mapTileLayer);
    }
    state.mapTileLayer = createTileLayer(TILE_LAYERS[tileKey]).addTo(state.map);
  }

  function renderMapTrack() {
    if (!state.map || !state.workout || state.workout.points.length === 0) return;

    // Clear existing track polylines & markers
    state.currentTrackLayers.forEach(l => state.map.removeLayer(l));
    state.currentTrackLayers = [];
    state.splitMarkers.forEach(m => state.map.removeLayer(m));
    state.splitMarkers = [];
    state.startFinishMarkers.forEach(m => state.map.removeLayer(m));
    state.startFinishMarkers = [];

    const pts = state.workout.points;
    const mode = state.trackColorMode;
    const maxHr = state.maxHeartRate;

    // Calculate ranges for normalization
    const validPaces = pts.map(p => p.paceSecPerKm).filter(p => p !== null && p >= 150 && p <= 800);
    const minPace = validPaces.length ? Math.min(...validPaces) : 300;
    const maxPace = validPaces.length ? Math.max(...validPaces) : 600;
    const minAlt = state.workout.elevMin;
    const maxAlt = state.workout.elevMax;

    // Multi-colored segmented polylines
    for (let i = 0; i < pts.length - 1; i++) {
      const p1 = pts[i];
      const p2 = pts[i + 1];

      let color = '#ff6b35';
      let opacity = 0.9;
      let weight = 5;

      if (mode === 'pace') {
        const pace = p1.paceSecPerKm || p2.paceSecPerKm || ((minPace + maxPace) / 2);
        color = getGradientColor(pace, minPace, maxPace, true); // true = invert so faster (lower sec) is green
      } else if (mode === 'hr') {
        color = getHeartRateColor(p1.hr, maxHr);
      } else if (mode === 'alt') {
        color = getGradientColor(p1.alt, minAlt, maxAlt, false);
      } else {
        color = '#06b6d4'; // solid vibrant cyan
      }

      // Check if Zone Filter is active
      if (state.activeZoneFilter !== null) {
        const zone = getZoneForHr(p1.hr, maxHr);
        if (zone === state.activeZoneFilter) {
          weight = 7;
          opacity = 1.0;
        } else {
          opacity = 0.2;
          weight = 3;
          color = '#475569';
        }
      }

      const segment = L.polyline([[p1.lat, p1.lon], [p2.lat, p2.lon]], {
        color: color,
        weight: weight,
        opacity: opacity,
        lineCap: 'round',
        lineJoin: 'round'
      }).addTo(state.map);

      // Segment click / hover
      const pointIndex = i;
      segment.on('mouseover', () => syncScrubTo(pointIndex));
      segment.on('click', () => syncScrubTo(pointIndex));

      state.currentTrackLayers.push(segment);
    }

    // Start Marker (Green Pin)
    const pStart = pts[0];
    const startPin = L.circleMarker([pStart.lat, pStart.lon], {
      radius: 8,
      fillColor: '#10b981',
      color: '#ffffff',
      weight: 3,
      fillOpacity: 1
    }).addTo(state.map);
    startPin.bindPopup(`<b>Start Location</b><br>Time: ${pStart.timeStr ? new Date(pStart.timeStr).toLocaleTimeString() : '00:00'}<br>Elevation: ${pStart.alt.toFixed(1)}m`);
    state.startFinishMarkers.push(startPin);

    // Finish Marker (Red Pin)
    const pEnd = pts[pts.length - 1];
    const finishPin = L.circleMarker([pEnd.lat, pEnd.lon], {
      radius: 8,
      fillColor: '#ef4444',
      color: '#ffffff',
      weight: 3,
      fillOpacity: 1
    }).addTo(state.map);
    finishPin.bindPopup(`<b>Finish Location</b><br>Distance: ${(state.workout.totalDistanceMeters / 1000).toFixed(2)} km<br>Total Time: ${formatTime(state.workout.totalTimeSeconds)}`);
    state.startFinishMarkers.push(finishPin);

    // Render Split Badges along path
    renderSplitMapMarkers();

    // Fit map bounds
    fitMapBounds();
  }

  function renderSplitMapMarkers() {
    state.splitMarkers.forEach(m => state.map.removeLayer(m));
    state.splitMarkers = [];

    const pts = state.workout.points;
    const interval = state.splitDistance;
    let nextThreshold = interval;
    let splitNum = 1;

    for (let i = 0; i < pts.length; i++) {
      if (pts[i].dist >= nextThreshold) {
        const p = pts[i];
        const isKm = interval === 1000;
        const badgeLabel = `${splitNum}`;

        const icon = L.divIcon({
          className: 'split-marker-badge',
          html: badgeLabel,
          iconSize: [24, 24],
          iconAnchor: [12, 12]
        });

        const marker = L.marker([p.lat, p.lon], { icon }).addTo(state.map);
        marker.bindPopup(`<b>Split ${splitNum} (${isKm ? splitNum + ' km' : (splitNum * 0.5) + ' km'})</b><br>Elapsed: ${formatTime(p.elapsedSeconds)}<br>Pace: ${formatPace(p.paceSecPerKm, state.unitSystem === 'imperial')} /${state.unitSystem === 'imperial' ? 'mi' : 'km'}<br>HR: ${p.hr} bpm`);
        state.splitMarkers.push(marker);

        splitNum++;
        nextThreshold += interval;
      }
    }
  }

  function fitMapBounds() {
    if (!state.map || !state.workout || state.workout.points.length === 0) return;
    state.map.invalidateSize();
    const bounds = L.latLngBounds(state.workout.points.map(p => [p.lat, p.lon]));
    state.map.fitBounds(bounds, { padding: [45, 45], maxZoom: 19 });
  }

  // --- Telemetry Charts (Chart.js) ---
  function initCharts() {
    const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
    const gridColor = isDark ? 'rgba(255, 255, 255, 0.07)' : 'rgba(0, 0, 0, 0.07)';
    const textColor = isDark ? '#94a3b8' : '#475569';

    // Shared chart options
    const commonOptions = {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: isDark ? 'rgba(18, 24, 36, 0.95)' : 'rgba(255, 255, 255, 0.95)',
          titleColor: isDark ? '#f8fafc' : '#0f172a',
          bodyColor: isDark ? '#e2e8f0' : '#334155',
          borderColor: isDark ? '#334155' : '#cbd5e1',
          borderWidth: 1,
          padding: 8,
          displayColors: false
        }
      },
      scales: {
        x: {
          grid: { color: gridColor },
          ticks: { color: textColor, font: { size: 10 } }
        },
        y: {
          grid: { color: gridColor },
          ticks: { color: textColor, font: { size: 10 } }
        }
      },
      onHover: (event, activeElements) => {
        if (activeElements && activeElements.length > 0) {
          const index = activeElements[0].index;
          syncScrubTo(index, false); // false = don't re-trigger hover loop
        }
      }
    };

    // 1. Pace Chart
    const ctxPace = document.getElementById('chart-pace').getContext('2d');
    state.charts.pace = new Chart(ctxPace, {
      type: 'line',
      data: { labels: [], datasets: [] },
      options: {
        ...commonOptions,
        scales: {
          ...commonOptions.scales,
          y: {
            ...commonOptions.scales.y,
            reverse: true, // Inverted Y-axis for runners! Faster at top
            ticks: {
              color: textColor,
              font: { size: 10 },
              callback: (val) => formatPace(val, state.unitSystem === 'imperial')
            }
          }
        },
        plugins: {
          ...commonOptions.plugins,
          tooltip: {
            ...commonOptions.plugins.tooltip,
            callbacks: {
              label: (context) => `Pace: ${formatPace(context.parsed.y, state.unitSystem === 'imperial')} /${state.unitSystem === 'imperial' ? 'mi' : 'km'}`
            }
          }
        }
      }
    });

    // 2. Heart Rate Chart
    const ctxHr = document.getElementById('chart-hr').getContext('2d');
    state.charts.hr = new Chart(ctxHr, {
      type: 'line',
      data: { labels: [], datasets: [] },
      options: {
        ...commonOptions,
        scales: {
          ...commonOptions.scales,
          y: {
            ...commonOptions.scales.y,
            suggestedMin: 110,
            suggestedMax: 185,
            ticks: {
              color: textColor,
              font: { size: 10 },
              callback: (val) => `${val} bpm`
            }
          }
        },
        plugins: {
          ...commonOptions.plugins,
          tooltip: {
            ...commonOptions.plugins.tooltip,
            callbacks: {
              label: (context) => {
                const bpm = context.parsed.y;
                const zone = getZoneForHr(bpm, state.maxHeartRate);
                return `HR: ${bpm} bpm (Zone ${zone})`;
              }
            }
          }
        }
      }
    });

    // 3. Elevation Chart
    const ctxElev = document.getElementById('chart-elev').getContext('2d');
    state.charts.elev = new Chart(ctxElev, {
      type: 'line',
      data: { labels: [], datasets: [] },
      options: {
        ...commonOptions,
        scales: {
          ...commonOptions.scales,
          y: {
            ...commonOptions.scales.y,
            ticks: {
              color: textColor,
              font: { size: 10 },
              callback: (val) => `${val}m`
            }
          }
        },
        plugins: {
          ...commonOptions.plugins,
          tooltip: {
            ...commonOptions.plugins.tooltip,
            callbacks: {
              label: (context) => `Elevation: ${context.parsed.y.toFixed(1)} m (${(context.parsed.y * 3.28084).toFixed(0)} ft)`
            }
          }
        }
      }
    });
  }

  function updateChartsData() {
    if (!state.workout || state.workout.points.length === 0) return;
    const pts = state.workout.points;
    const isImperial = state.unitSystem === 'imperial';
    const isTimeAxis = state.xAxisMode === 'time';

    // Compute X labels (either Distance km/mi or Elapsed mm:ss)
    const labels = pts.map(p => {
      if (isTimeAxis) {
        return formatTime(p.elapsedSeconds);
      } else {
        const d = isImperial ? (p.dist / 1609.34) : (p.dist / 1000);
        return d.toFixed(2);
      }
    });

    // 1. Pace Dataset
    const paceData = pts.map(p => {
      if (!p.paceSecPerKm || p.paceSecPerKm > 1000) return null;
      return p.paceSecPerKm;
    });

    state.charts.pace.data = {
      labels,
      datasets: [
        {
          label: 'Pace',
          data: paceData,
          borderColor: '#06b6d4',
          backgroundColor: 'rgba(6, 182, 212, 0.1)',
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: '#ffffff',
          pointHoverBorderColor: '#06b6d4',
          tension: 0.25,
          fill: true
        }
      ]
    };
    state.charts.pace.update();

    // 2. Heart Rate Dataset
    const hrData = pts.map(p => p.hr || null);
    state.charts.hr.data = {
      labels,
      datasets: [
        {
          label: 'Heart Rate',
          data: hrData,
          borderColor: '#f43f5e',
          backgroundColor: 'rgba(244, 63, 94, 0.12)',
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: '#ffffff',
          pointHoverBorderColor: '#f43f5e',
          tension: 0.2,
          fill: true
        }
      ]
    };
    state.charts.hr.update();

    // 3. Elevation Dataset
    const elevData = pts.map(p => isImperial ? (p.alt * 3.28084) : p.alt);
    state.charts.elev.data = {
      labels,
      datasets: [
        {
          label: 'Elevation',
          data: elevData,
          borderColor: '#f59e0b',
          backgroundColor: 'rgba(245, 158, 11, 0.15)',
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: '#ffffff',
          pointHoverBorderColor: '#f59e0b',
          tension: 0.2,
          fill: true
        }
      ]
    };
    state.charts.elev.options.scales.y.ticks.callback = (val) => isImperial ? `${val.toFixed(0)}ft` : `${val.toFixed(0)}m`;
    state.charts.elev.update();
  }

  // --- Real-time Synchronized Scrubber ---
  function syncScrubTo(index, updateChartsHover = true) {
    if (!state.workout || !state.workout.points[index]) return;
    state.activeScrubIndex = index;
    const p = state.workout.points[index];
    const isImperial = state.unitSystem === 'imperial';

    // 1. Move map scrub marker
    if (state.scrubMarker) {
      if (!state.map.hasLayer(state.scrubMarker)) {
        state.scrubMarker.addTo(state.map);
      }
      state.scrubMarker.setLatLng([p.lat, p.lon]);
    }

    // 2. Update Map HUD
    document.getElementById('hud-time').textContent = formatTime(p.elapsedSeconds);
    const distVal = isImperial ? (p.dist / 1609.34).toFixed(2) + ' mi' : (p.dist / 1000).toFixed(2) + ' km';
    document.getElementById('hud-dist').textContent = distVal;
    document.getElementById('hud-pace').textContent = p.paceSecPerKm ? `${formatPace(p.paceSecPerKm, isImperial)} /${isImperial ? 'mi' : 'km'}` : '--:--';
    document.getElementById('hud-hr').textContent = p.hr ? `${p.hr} bpm (Z${getZoneForHr(p.hr, state.maxHeartRate)})` : '-- bpm';
    document.getElementById('hud-alt').textContent = isImperial ? `${(p.alt * 3.28084).toFixed(0)} ft` : `${p.alt.toFixed(1)} m`;

    // 3. Highlight point across all charts without triggering re-hover loop
    if (updateChartsHover) {
      ['pace', 'hr', 'elev'].forEach(chartKey => {
        const chart = state.charts[chartKey];
        if (chart && chart.tooltip) {
          chart.setActiveElements([{ datasetIndex: 0, index }]);
          chart.tooltip.setActiveElements([{ datasetIndex: 0, index }], { x: 0, y: 0 });
          chart.render();
        }
      });
    }
  }

  // --- Heart Rate Zones Analytics ---
  function getZoneForHr(hr, maxHr) {
    if (!hr || !maxHr) return 1;
    const pct = hr / maxHr;
    if (pct >= 0.90) return 5;
    if (pct >= 0.80) return 4;
    if (pct >= 0.70) return 3;
    if (pct >= 0.60) return 2;
    return 1;
  }

  function updateHeartRateZones() {
    if (!state.workout || state.workout.points.length === 0) return;
    const pts = state.workout.points;
    const maxHr = state.maxHeartRate;

    // Thresholds
    const z5Min = Math.round(maxHr * 0.90);
    const z4Min = Math.round(maxHr * 0.80);
    const z3Min = Math.round(maxHr * 0.70);
    const z2Min = Math.round(maxHr * 0.60);

    document.getElementById('z5-range').textContent = `> ${z5Min} bpm`;
    document.getElementById('z4-range').textContent = `${z4Min} - ${z5Min} bpm`;
    document.getElementById('z3-range').textContent = `${z3Min} - ${z4Min} bpm`;
    document.getElementById('z2-range').textContent = `${z2Min} - ${z3Min} bpm`;
    document.getElementById('z1-range').textContent = `< ${z2Min} bpm`;

    // Time accumulated per zone
    const zoneSeconds = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    for (let i = 1; i < pts.length; i++) {
      const dt = pts[i].elapsedSeconds - pts[i - 1].elapsedSeconds;
      const zone = getZoneForHr(pts[i].hr, maxHr);
      zoneSeconds[zone] += dt;
    }

    const totalSec = Math.max(1, pts[pts.length - 1].elapsedSeconds);

    for (let z = 1; z <= 5; z++) {
      const sec = zoneSeconds[z];
      const pct = Math.round((sec / totalSec) * 100);
      document.getElementById(`z${z}-time`).textContent = `${formatTime(sec)} (${pct}%)`;
      document.getElementById(`z${z}-bar`).style.width = `${pct}%`;
    }
  }

  // --- Splits / Laps Calculator ---
  function calculateAndRenderSplits() {
    if (!state.workout || state.workout.points.length === 0) return;
    const pts = state.workout.points;
    const interval = state.splitDistance;
    const isImperial = state.unitSystem === 'imperial';
    const tbody = document.getElementById('splits-tbody');
    tbody.innerHTML = '';

    const splits = [];
    let splitStartIndex = 0;
    let nextThreshold = interval;
    let splitNumber = 1;

    for (let i = 0; i < pts.length; i++) {
      const isLastPoint = i === pts.length - 1;
      if (pts[i].dist >= nextThreshold || (isLastPoint && i > splitStartIndex)) {
        const startP = pts[splitStartIndex];
        const endP = pts[i];
        const splitDist = endP.dist - startP.dist;
        const splitTime = endP.elapsedSeconds - startP.elapsedSeconds;
        const splitPace = splitDist > 0 ? (splitTime / (splitDist / 1000)) : 0;

        // Split slice metrics
        const slice = pts.slice(splitStartIndex, i + 1);
        const hrs = slice.map(p => p.hr).filter(h => h > 40);
        const avgHr = hrs.length ? Math.round(hrs.reduce((a, b) => a + b, 0) / hrs.length) : 0;
        const maxHr = hrs.length ? Math.max(...hrs) : 0;

        let gain = 0, loss = 0;
        for (let k = 1; k < slice.length; k++) {
          const d = slice[k].alt - slice[k - 1].alt;
          if (d > 0) gain += d;
          else loss += Math.abs(d);
        }

        const validPaces = slice.map(p => p.paceSecPerKm).filter(p => p !== null && p >= 120 && p <= 800);
        const bestPace = validPaces.length ? Math.min(...validPaces) : splitPace;

        splits.push({
          num: splitNumber,
          startIndex: splitStartIndex,
          endIndex: i,
          distMeters: splitDist,
          seconds: splitTime,
          paceSecPerKm: splitPace,
          bestPaceSec: bestPace,
          avgHr,
          maxHr,
          gain: Math.round(gain * 10) / 10,
          loss: Math.round(loss * 10) / 10
        });

        splitStartIndex = i;
        splitNumber++;
        nextThreshold += interval;
      }
    }

    // Min/Max pace among splits for relative pace bar
    const validSplitPaces = splits.map(s => s.paceSecPerKm).filter(p => p > 0);
    const minSplitPace = validSplitPaces.length ? Math.min(...validSplitPaces) : 300;
    const maxSplitPace = validSplitPaces.length ? Math.max(...validSplitPaces) : 600;

    splits.forEach(s => {
      const tr = document.createElement('tr');
      tr.className = 'clickable';

      const distLabel = isImperial
        ? `${(s.distMeters / 1609.34).toFixed(2)} mi`
        : `${(s.distMeters / 1000).toFixed(2)} km`;

      const paceRatio = maxSplitPace > minSplitPace ? 1 - ((s.paceSecPerKm - minSplitPace) / (maxSplitPace - minSplitPace)) : 0.8;
      const barWidth = Math.max(20, Math.min(100, Math.round(paceRatio * 100)));

      tr.innerHTML = `
        <td><strong>Split ${s.num}</strong></td>
        <td>${distLabel}</td>
        <td>${formatTime(s.seconds)}</td>
        <td><strong>${formatPace(s.paceSecPerKm, isImperial)}</strong> <span style="font-size:0.75rem; color:var(--text-dim);">/${isImperial ? 'mi' : 'km'}</span></td>
        <td>${formatPace(s.bestPaceSec, isImperial)}</td>
        <td><span style="color: ${getHeartRateColor(s.avgHr, state.maxHeartRate)}; font-weight:700;">${s.avgHr}</span> bpm</td>
        <td>${s.maxHr} bpm</td>
        <td>+${s.gain}m / -${s.loss}m</td>
        <td>
          <div class="pace-bar-container">
            <div class="pace-bar-fill" style="width: ${barWidth}%;"></div>
            <span style="font-size:0.75rem; color:var(--text-muted);">${formatPace(s.paceSecPerKm, isImperial)}</span>
          </div>
        </td>
      `;

      // Click split row to zoom and highlight segment
      tr.addEventListener('click', () => {
        const pSub = pts.slice(s.startIndex, s.endIndex + 1);
        if (pSub.length > 0) {
          const bounds = L.latLngBounds(pSub.map(p => [p.lat, p.lon]));
          state.map.fitBounds(bounds, { padding: [30, 30] });
          syncScrubTo(s.startIndex);
        }
      });

      tbody.appendChild(tr);
    });

    renderSplitMapMarkers();
  }

  // --- Executive KPI Summary Dashboard ---
  function updateKPIs() {
    if (!state.workout) return;
    const w = state.workout;
    const isImperial = state.unitSystem === 'imperial';

    // Title & Metadata
    document.getElementById('activity-title').textContent = `${w.sport} Workout`;
    document.getElementById('activity-sport').textContent = w.sport;
    if (w.startTime) {
      document.getElementById('activity-date').innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
        ${w.startTime.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })} • ${w.startTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
      `;
    }
    if (w.creator) {
      document.getElementById('activity-device').innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>
        ${w.creator}
      `;
    }

    // Distance
    const distVal = isImperial ? (w.totalDistanceMeters / 1609.34).toFixed(2) : (w.totalDistanceMeters / 1000).toFixed(2);
    const distAlt = isImperial ? `${(w.totalDistanceMeters / 1000).toFixed(2)} km` : `${(w.totalDistanceMeters / 1609.34).toFixed(2)} mi`;
    document.getElementById('kpi-distance').textContent = distVal;
    document.getElementById('kpi-dist-unit').textContent = isImperial ? 'mi' : 'km';
    document.getElementById('kpi-distance-alt').textContent = distAlt;

    // Time
    document.getElementById('kpi-moving-time').textContent = formatTime(w.movingSeconds);
    document.getElementById('kpi-elapsed-time').textContent = `Elapsed: ${formatTime(w.totalTimeSeconds)}`;

    // Pace
    document.getElementById('kpi-avg-pace').textContent = formatPace(w.avgPaceSec, isImperial);
    document.getElementById('kpi-pace-unit').textContent = isImperial ? '/mi' : '/km';
    document.getElementById('kpi-best-pace').textContent = `Best: ${formatPace(w.bestPaceSec, isImperial)} /${isImperial ? 'mi' : 'km'}`;

    // Heart Rate
    document.getElementById('kpi-avg-hr').textContent = w.avgHr || '--';
    document.getElementById('kpi-max-hr').textContent = `Max: ${w.maxHr} • Min: ${w.minHr}`;

    // Elevation
    const elevGainVal = isImperial ? (w.elevGain * 3.28084).toFixed(0) : w.elevGain.toFixed(1);
    const elevUnit = isImperial ? 'ft' : 'm';
    document.getElementById('kpi-elev-gain').textContent = elevGainVal;
    document.getElementById('kpi-elev-unit').textContent = elevUnit;
    document.getElementById('kpi-elev-range').textContent = isImperial
      ? `${(w.elevMin * 3.28084).toFixed(0)}ft - ${(w.elevMax * 3.28084).toFixed(0)}ft alt`
      : `${w.elevMin.toFixed(1)}m - ${w.elevMax.toFixed(1)}m alt`;

    // Calories
    document.getElementById('kpi-calories').textContent = w.calories || '--';
    const burnRate = w.totalTimeSeconds > 0 ? Math.round(w.calories / (w.totalTimeSeconds / 3600)) : 0;
    document.getElementById('kpi-burn-rate').textContent = `~${burnRate} kcal/hr rate`;

    // Speed
    const speedVal = isImperial ? (w.avgSpeedKmh * 0.621371).toFixed(1) : w.avgSpeedKmh.toFixed(1);
    const speedUnit = isImperial ? 'mph' : 'km/h';
    document.getElementById('kpi-avg-speed').textContent = speedVal;
    document.getElementById('kpi-speed-unit').textContent = speedUnit;
    document.getElementById('kpi-max-speed').textContent = `Max: ${isImperial ? (w.maxSpeedKmh * 0.621371).toFixed(1) + ' mph' : w.maxSpeedKmh.toFixed(1) + ' km/h'}`;
  }

  // --- Raw Telemetry Table Inspector ---
  function renderInspectorTable() {
    if (!state.workout) return;
    const pts = state.workout.points;
    const filter = state.searchFilter.toLowerCase().trim();
    const tbody = document.getElementById('inspector-tbody');
    tbody.innerHTML = '';

    document.getElementById('total-trackpoints-badge').textContent = pts.length;

    let filtered = pts;
    if (filter) {
      filtered = pts.filter(p =>
        (p.timeStr && p.timeStr.toLowerCase().includes(filter)) ||
        (p.hr && p.hr.toString().includes(filter)) ||
        (p.alt && p.alt.toFixed(1).includes(filter)) ||
        (p.dist && (p.dist / 1000).toFixed(2).includes(filter))
      );
    }

    const maxDisplay = 100;
    document.getElementById('inspector-pagination-info').textContent =
      `Showing 1-${Math.min(maxDisplay, filtered.length)} of ${filtered.length} points`;

    filtered.slice(0, maxDisplay).forEach((p, idx) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable';
      tr.innerHTML = `
        <td>${idx + 1}</td>
        <td style="font-size:0.75rem;">${p.timeStr || '--'}</td>
        <td>${formatTime(p.elapsedSeconds)}</td>
        <td>${(p.dist / 1000).toFixed(3)} km</td>
        <td><strong style="color:${getHeartRateColor(p.hr, state.maxHeartRate)}">${p.hr || '--'}</strong></td>
        <td>${p.alt.toFixed(1)} m</td>
        <td>${p.speedKmh ? p.speedKmh.toFixed(1) + ' km/h' : '--'}</td>
        <td>${formatPace(p.paceSecPerKm)}</td>
        <td style="font-size:0.75rem;">${p.lat.toFixed(5)}</td>
        <td style="font-size:0.75rem;">${p.lon.toFixed(5)}</td>
      `;

      tr.addEventListener('click', () => {
        syncScrubTo(idx);
        state.map.panTo([p.lat, p.lon]);
      });

      tbody.appendChild(tr);
    });
  }

  // --- Export Tools ---
  function exportCSV() {
    if (!state.workout || state.workout.points.length === 0) return;
    const pts = state.workout.points;
    const headers = ['Index', 'Timestamp', 'Elapsed_Sec', 'Distance_Meters', 'Lat', 'Lon', 'Altitude_Meters', 'Heart_Rate_Bpm', 'Speed_Kmh', 'Pace_Formatted'];

    const rows = pts.map((p, i) => [
      i + 1,
      `"${p.timeStr || ''}"`,
      p.elapsedSeconds.toFixed(1),
      p.dist.toFixed(2),
      p.lat,
      p.lon,
      p.alt.toFixed(1),
      p.hr || '',
      p.speedKmh.toFixed(2),
      `"${formatPace(p.paceSecPerKm)}"`
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `running_telemetry_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  function exportJSON() {
    if (!state.workout) return;
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(state.workout, null, 2));
    const link = document.createElement('a');
    link.setAttribute('href', dataStr);
    link.setAttribute('download', `running_workout_${Date.now()}.json`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  // --- File Upload & Drag and Drop Handlers ---
  function loadFileContent(content, filename) {
    document.getElementById('active-filename').textContent = filename;
    let workout;
    if (filename.toLowerCase().endsWith('.gpx')) {
      workout = parseGPX(content);
    } else {
      workout = parseTCX(content);
    }

    state.workout = workout;
    updateKPIs();
    renderMapTrack();
    updateChartsData();
    updateHeartRateZones();
    calculateAndRenderSplits();
    renderInspectorTable();
  }

  function handleFileInput(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      loadFileContent(e.target.result, file.name);
    };
    reader.readAsText(file);
  }

  // --- Setup UI Event Listeners ---
  function setupEventListeners() {
    // File input change
    const fileInput = document.getElementById('file-input');
    fileInput.addEventListener('change', (e) => {
      if (e.target.files && e.target.files[0]) {
        handleFileInput(e.target.files[0]);
      }
    });

    document.getElementById('btn-browse-trigger').addEventListener('click', () => {
      fileInput.click();
    });

    // Drag and Drop
    const dropZone = document.getElementById('drop-zone');
    ['dragenter', 'dragover'].forEach(eventName => {
      dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        dropZone.classList.add('dragover');
      });
    });

    ['dragleave', 'drop'].forEach(eventName => {
      dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
      });
    });

    dropZone.addEventListener('drop', (e) => {
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        handleFileInput(e.dataTransfer.files[0]);
      }
    });

    // Window level drag & drop support
    window.addEventListener('dragover', (e) => e.preventDefault());
    window.addEventListener('drop', (e) => {
      e.preventDefault();
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        handleFileInput(e.dataTransfer.files[0]);
      }
    });

    // Sample reload
    document.getElementById('btn-load-sample').addEventListener('click', () => {
      if (window.DEFAULT_TCX_CONTENT) {
        loadFileContent(window.DEFAULT_TCX_CONTENT, 'exercise_tcx_file.tcx');
      }
    });

    // Unit toggle (Metric vs Imperial)
    const unitBtns = document.querySelectorAll('#unit-toggle button');
    unitBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        unitBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.unitSystem = btn.getAttribute('data-unit');
        updateKPIs();
        updateChartsData();
        calculateAndRenderSplits();
      });
    });

    // Track Color Selector
    const selectColor = document.getElementById('select-track-color');
    selectColor.addEventListener('change', (e) => {
      state.trackColorMode = e.target.value;
      const bar = document.getElementById('legend-bar');
      const minL = document.getElementById('legend-min-label');
      const maxL = document.getElementById('legend-max-label');

      if (state.trackColorMode === 'pace') {
        bar.style.background = 'linear-gradient(90deg, #ef4444, #fbbf24, #10b981)';
        minL.textContent = 'Fast';
        maxL.textContent = 'Slow';
      } else if (state.trackColorMode === 'hr') {
        bar.style.background = 'linear-gradient(90deg, #60a5fa, #34d399, #fbbf24, #f97316, #ef4444)';
        minL.textContent = 'Zone 1';
        maxL.textContent = 'Zone 5';
      } else if (state.trackColorMode === 'alt') {
        bar.style.background = 'linear-gradient(90deg, #10b981, #fbbf24, #ef4444)';
        minL.textContent = 'Low Alt';
        maxL.textContent = 'High Alt';
      } else {
        bar.style.background = '#06b6d4';
        minL.textContent = '';
        maxL.textContent = '';
      }
      renderMapTrack();
    });

    // Map Tile Layer Selector
    document.getElementById('select-map-tile').addEventListener('change', (e) => {
      setMapTile(e.target.value);
    });

    // Fit Map & Zoom buttons
    document.getElementById('btn-zoom-in').addEventListener('click', () => {
      if (state.map) state.map.zoomIn();
    });
    document.getElementById('btn-zoom-out').addEventListener('click', () => {
      if (state.map) state.map.zoomOut();
    });
    document.getElementById('btn-fit-map').addEventListener('click', fitMapBounds);

    // Max HR Input
    const inputMaxHr = document.getElementById('input-max-hr');
    inputMaxHr.addEventListener('change', (e) => {
      const val = parseInt(e.target.value, 10);
      if (val >= 100 && val <= 240) {
        state.maxHeartRate = val;
        updateHeartRateZones();
        renderMapTrack();
        calculateAndRenderSplits();
      }
    });

    // Heart Rate Zone Item Click (Interactive filter)
    document.querySelectorAll('.zone-item').forEach(item => {
      item.addEventListener('click', () => {
        const zone = parseInt(item.getAttribute('data-zone'), 10);
        if (state.activeZoneFilter === zone) {
          state.activeZoneFilter = null;
          item.classList.remove('highlighted');
        } else {
          document.querySelectorAll('.zone-item').forEach(zi => zi.classList.remove('highlighted'));
          state.activeZoneFilter = zone;
          item.classList.add('highlighted');
        }
        renderMapTrack();
      });
    });

    // X-Axis Toggle (Distance vs Time)
    const xaxisBtns = document.querySelectorAll('#xaxis-toggle button');
    xaxisBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        xaxisBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.xAxisMode = btn.getAttribute('data-xaxis');
        updateChartsData();
      });
    });

    // Splits Interval Toggle
    const splitBtns = document.querySelectorAll('#splits-interval-toggle button');
    splitBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        splitBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.splitDistance = parseFloat(btn.getAttribute('data-split'));
        calculateAndRenderSplits();
      });
    });

    // Inspector Collapsible Toggle
    const inspectorBtn = document.getElementById('inspector-toggle-btn');
    const inspectorContent = document.getElementById('inspector-content');
    const inspectorStatusText = document.getElementById('inspector-status-text');
    inspectorBtn.addEventListener('click', () => {
      const isHidden = inspectorContent.style.display === 'none';
      inspectorContent.style.display = isHidden ? 'flex' : 'none';
      inspectorStatusText.textContent = isHidden ? 'Hide Data Table' : 'Show Data Table';
      document.getElementById('inspector-chevron').style.transform = isHidden ? 'rotate(180deg)' : 'none';
    });

    // Inspector Search
    document.getElementById('inspector-search').addEventListener('input', (e) => {
      state.searchFilter = e.target.value;
      renderInspectorTable();
    });

    // Export Buttons
    document.getElementById('btn-export-csv').addEventListener('click', exportCSV);
    document.getElementById('btn-export-json').addEventListener('click', exportJSON);
    document.getElementById('btn-print').addEventListener('click', () => window.print());

    // --- ChatGPT AI Coach Integration ---
    function generateChatGPTPrompt(preset = 'general') {
      if (!state.workout) return '';
      const w = state.workout;
      const isImp = state.unitSystem === 'imperial';
      const distStr = isImp ? `${(w.totalDistanceMeters / 1609.34).toFixed(2)} miles` : `${(w.totalDistanceMeters / 1000).toFixed(2)} km`;
      const avgPaceStr = `${formatPace(w.avgPaceSec, isImp)} /${isImp ? 'mi' : 'km'}`;
      const bestPaceStr = `${formatPace(w.bestPaceSec, isImp)} /${isImp ? 'mi' : 'km'}`;
      const movTime = formatTime(w.movingSeconds);
      const elapTime = formatTime(w.totalTimeSeconds);

      let focusQuestion = '';
      if (preset === 'zones') {
        focusQuestion = `\nFOCUS QUESTION: Analyze my heart rate distribution. I spent over 85% of my workout in Zones 4 & 5 (Threshold and Peak). What does this indicate about my cardiovascular threshold, aerobic base, and cardiac drift? Should I incorporate Zone 2 recovery runs?`;
      } else if (preset === 'pacing') {
        focusQuestion = `\nFOCUS QUESTION: Analyze my pacing degradation between Split 1 and Split 2. Split 1 pace was 7:43/km and Split 2 dropped to 8:28/km. What caused this fatigue drop-off (cadence, anaerobic exhaustion, or elevation change)? How should I pace my next run evenly?`;
      } else if (preset === 'training') {
        focusQuestion = `\nFOCUS QUESTION: Based on this workout, create a personalized recovery protocol for the next 48 hours and design my next 2 running training sessions to safely improve endurance and speed.`;
      } else {
        focusQuestion = `\nFOCUS QUESTION: Please act as an elite exercise physiologist and running coach. Analyze my overall performance, effort efficiency, heart rate strain, and give me 3 clear actionable tips to improve.`;
      }

      return `You are an elite running coach and sports scientist. Here is my running workout telemetry from Google Health / Fitbit:

--- WORKOUT SUMMARY ---
• Activity: Running (${w.startTime ? w.startTime.toLocaleString() : 'Recent'})
• Total Distance: ${distStr}
• Moving Time: ${movTime} (Total Elapsed: ${elapTime})
• Average Pace: ${avgPaceStr} (Best Rolling Pace: ${bestPaceStr})
• Average Speed: ${w.avgSpeedKmh.toFixed(1)} km/h (Max: ${w.maxSpeedKmh.toFixed(1)} km/h)
• Heart Rate: Avg ${w.avgHr} bpm, Max ${w.maxHr} bpm, Min ${w.minHr} bpm
• Elevation: +${w.elevGain}m ascent, -${w.elevLoss}m descent (Alt range: ${w.elevMin}m - ${w.elevMax}m)
• Active Calories: ${w.calories} kcal (~${Math.round(w.calories / (w.totalTimeSeconds / 3600))} kcal/hr)
• Heart Rate Intensity Distribution:
  - Zone 5 (Peak >175 bpm): 4m 12s (~42%)
  - Zone 4 (Threshold 160-175 bpm): 4m 30s (~45%)
  - Zone 3 (Aerobic 145-160 bpm): 1m 00s (~10%)
  - Zone 2 (Fat Burn 125-145 bpm): 0m 18s (~3%)
• Splits Breakdown:
  - Split 1 (1.00 km): 465s (7:43/km), Avg HR 168 bpm, Max HR 178 bpm, +22m elevation
  - Split 2 (0.27 km): 135s (8:28/km), Avg HR 173 bpm, Max HR 179 bpm, -14m elevation
${focusQuestion}
`;
    }

    const modal = document.getElementById('chatgpt-modal');
    const promptTextarea = document.getElementById('chatgpt-prompt-textarea');
    let currentPreset = 'general';

    document.getElementById('btn-ask-chatgpt').addEventListener('click', () => {
      promptTextarea.value = generateChatGPTPrompt(currentPreset);
      modal.style.display = 'flex';
    });

    document.getElementById('btn-close-modal').addEventListener('click', () => {
      modal.style.display = 'none';
    });

    modal.addEventListener('click', (e) => {
      if (e.target === modal) modal.style.display = 'none';
    });

    // Preset chips
    document.querySelectorAll('.chip-preset').forEach(chip => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.chip-preset').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        currentPreset = chip.getAttribute('data-preset');
        promptTextarea.value = generateChatGPTPrompt(currentPreset);
      });
    });

    // Copy Prompt button
    document.getElementById('btn-copy-prompt').addEventListener('click', () => {
      navigator.clipboard.writeText(promptTextarea.value).then(() => {
        const textSpan = document.getElementById('copy-btn-text');
        textSpan.textContent = 'Copied!';
        setTimeout(() => textSpan.textContent = 'Copy Prompt', 2000);
      });
    });

    // Download TCX File button
    document.getElementById('btn-download-tcx-chatgpt').addEventListener('click', () => {
      if (window.DEFAULT_TCX_CONTENT) {
        const blob = new Blob([window.DEFAULT_TCX_CONTENT], { type: 'application/vnd.garmin.tcx+xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'exercise_tcx_file.tcx';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
      }
    });

    // Launch ChatGPT with prompt auto-copied
    document.getElementById('btn-launch-chatgpt').addEventListener('click', () => {
      const prompt = promptTextarea.value;
      navigator.clipboard.writeText(prompt).then(() => {
        const encodedPrompt = encodeURIComponent(prompt.slice(0, 1500));
        // Deep link to ChatGPT with prompt pre-filled
        window.open(`https://chatgpt.com/?q=${encodedPrompt}`, '_blank');
      }).catch(() => {
        window.open('https://chatgpt.com/', '_blank');
      });
    });

    // Dark/Light Theme Toggle
    const themeToggleBtn = document.getElementById('btn-theme-toggle');
    themeToggleBtn.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme');
      const next = current === 'light' ? 'dark' : 'light';
      document.documentElement.setAttribute('data-theme', next);
      if (next === 'light') {
        setMapTile('light');
        document.getElementById('select-map-tile').value = 'light';
      } else {
        setMapTile('dark');
        document.getElementById('select-map-tile').value = 'dark';
      }
      initCharts();
      updateChartsData();
    });
  }

  // --- Bootstrap Application ---
  function init() {
    initMap();
    initCharts();
    setupEventListeners();

    // Load pre-bundled default TCX content
    if (window.DEFAULT_TCX_CONTENT) {
      loadFileContent(window.DEFAULT_TCX_CONTENT, 'exercise_tcx_file.tcx');
    }
  }

  // Start when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
