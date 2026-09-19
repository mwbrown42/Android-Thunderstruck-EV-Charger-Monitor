# Android Thunderstruck EV Charger Monitor

A high-performance, standalone Android tablet and smartphone application engineered specifically to monitor, control, and analyze electric vehicle charging sessions powered by the **Thunderstruck Motors EV Charge Controller (EVCC)** and dual (or single) **Thunderstruck / Lear TSM-2500 HF CANbus Chargers**.

This project provides an open-source, generic dashboard interface completely decoupled from any specific vehicle platform, CAN bus logger, or custom vehicle dashboard.

---

## ⚡ Companion ESP32 Gateway Firmware

This app communicates seamlessly over Wi-Fi / Local Area Network with the **Thunderstruck EVCC Serial to Wi-Fi Gateway** running on an ESP32-S3:
👉 **[ESP32 Gateway Firmware Repository](https://github.com/mwbrown42/Thunderstruck-EVCC-Serial-to-Wifi-Gateway)**

---

## 🚀 Key Features

- **Zero-Configuration Automatic Gateway Discovery**:
  - Listens asynchronously on UDP broadcast port `8888` for periodic JSON beacon packets (`{"status":"heartbeat","ip":"..."}`) broadcasted by the ESP32 Gateway.
  - Automatically identifies the gateway IP and connects via WebSocket (`ws://<ip>:80/ws`) without requiring the user to look up DHCP leases or configure static IPs.
  - Features an active 4-second watchdog that updates an **Online / Offline** status pill in real-time.

- **Dual Charger Telemetry Monitoring**:
  - **Charger 1 (`charger`, `tsm2500`, CAN ID 0x40)**
  - **Charger 2 (`charger2`, `tsm2500_41`, CAN ID 0x41)**
  - Real-time digital gauges for:
    - **Voltage** (V)
    - **Current** (A)
    - **Instant Power** (Watts)
    - **Accumulated Session Energy** (Watt-hours / kWh)
    - **Internal Heatsink Temperature** (°C)
  - **Adaptive Card Visibility**: Automatically adapts between single-charger and dual-charger configurations based on detected CAN bus activity and persists the layout across restarts.

- **Fault & Diagnostic Badges**:
  - Instant visual indicators for all EVCC and TSM-2500 hardware alert states:
    - `rxerr` (CANbus receive error)
    - `hwfail` (Internal hardware failure)
    - `overtemp` (Charger thermal trip / shutdown)
    - `not chg` (Charger not delivering current)
    - `input err` (AC line input voltage out of bounds)
    - `pack err` (Traction battery pack voltage error)

- **Real-Time Synchronized Charging Session Graphs**:
  - Dual-curve canvas graphs overlaying **Voltage** (violet) and **Current** (emerald green) in real-time.
  - Dynamic dual-axis auto-scaling on both voltage and current ranges as charging progresses from Constant Current (CC) to Constant Voltage (CV) taper.

- **Multi-Zone Temperature History Chart**:
  - Displays session temperature traces for each charger against industry-standard safety thresholds:
    - **53°C Derate Warning Threshold** (amber dash line)
    - **64°C Emergency Trip Boundary** (red dash line)

- **Intelligent Thermal Governor Integration**:
  - Real-time governor tracking with status pills (`🛡️ Gov: OPTIMAL`, `⚠️ Gov: 75%`, `⚪ Gov: OFF`).
  - Proactively alerts when current has been throttled to prevent thermal shutdowns, showing exact baseline vs derated amperage.

- **Bidirectional EVCC Command Console**:
  - Full ASCII serial terminal connected directly to the EVCC at 9600 baud.
  - Quick momentary buttons for standard queries:
    - `SHOW` (Overall EVCC runtime state)
    - `CONFIG` (Active configuration parameters)
    - `HISTORY` (Past charge cycle records)
  - Toggle buttons for EVCC trace modes (`TR CAN`, `TR STATE`, `TR CHG`, `TR OFF`).
  - Input field to adjust `maxv`, `maxc`, or send any arbitrary EVCC CLI command.
  - Built-in session logging with copy-to-clipboard and export.

- **Built-in Interactive Simulator Engine**:
  - Integrated offline simulation engine with 8 realistic scenario presets:
    - **Dual Chg**: Full 44A dual-charger 6.3 kW session.
    - **Single Chg**: 22A single-charger session.
    - **CV Taper**: Constant voltage current ramp-down.
    - **Overtemp**: Heatsink thermal ramp with governor intervention.
    - **CAN Rxerr**: Simulated CAN bus communication fault.
    - **Input Err**: AC mains low-voltage condition.
    - **Pack Err**: Traction battery voltage fault.
    - **Standby**: Idle disconnected state.
  - Interactive voltage and current sliders for dynamic testing without vehicle hardware.

- **Tablet-Optimized Responsive UI**:
  - Custom dark theme UI designed for automotive tablet installations (e.g. 1920x1200, 1280x800).
  - Clean edge-to-edge layout engineered with zero clipping and zero vertical scrolling.

---

## 🛠️ Project Structure

```
Android Thunderstruck EV Charger Monitor/
├── app/
│   ├── src/main/
│   │   ├── java/com/thunderstruck/evcc/monitor/
│   │   │   ├── MainActivity.java                # Host activity & lifecycle manager
│   │   │   ├── SettingsActivity.java            # App configuration preferences
│   │   │   ├── controller/
│   │   │   │   ├── EvccGatewayClient.java       # UDP auto-discovery & WebSocket client
│   │   │   │   ├── EvccSimulatorEngine.java     # Offline simulation test harness
│   │   │   │   └── EvccSessionLogger.java       # Session telemetry logging to disk
│   │   │   ├── model/
│   │   │   │   ├── EvccTelemetry.java           # Composite EVCC telemetry model
│   │   │   │   ├── ChargerTelemetry.java        # Per-charger voltage/current/temp model
│   │   │   │   ├── SessionDataPoint.java        # Time-series graph data point
│   │   │   │   └── ThermalGovernorTelemetry.java# Thermal governor state model
│   │   │   └── view/
│   │   │       ├── ChargingTabViewController.java # Full tab UI controller & event binder
│   │   │       ├── ChargingChartView.java       # Custom Canvas dual-axis chart
│   │   │       └── TemperatureChartView.java    # Custom Canvas temperature chart
│   │   ├── res/
│   │   │   ├── layout/                          # Portrait responsive layouts
│   │   │   ├── layout-land/                     # Automotive landscape tablet layouts
│   │   │   ├── values/                          # Colors, styles, strings
│   │   │   └── xml/preferences.xml              # User preferences
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 📡 Network & Protocol Specifications

### 1. UDP Auto-Discovery Beacon (Port 8888)
The companion ESP32 Gateway broadcasts subnet UDP packets on port 8888 every second:
```json
{
  "status": "heartbeat",
  "ip": "192.168.2.148",
  "uptime_s": 3600,
  "wifi_rssi": -48,
  "ws_clients": 1
}
```
The app parses this packet, updates the gateway IP, and connects to the WebSocket endpoint automatically.

### 2. WebSocket Telemetry Stream (`ws://<ip>:80/ws`)
Full telemetry broadcasted at 1 Hz during active sessions:
```json
{
  "type": "telemetry",
  "state": "CHARGE",
  "j1772": "LOCKED",
  "maxv": 142.0,
  "maxc": 40.0,
  "governor": {
    "enabled": true,
    "active_maxc": 20.0,
    "baseline_maxc": 40.0,
    "derate_percent": 100,
    "is_derated": false,
    "peak_temp": 38.5,
    "hottest_charger": "C2",
    "status": "OPTIMAL"
  },
  "c1": {
    "active": true,
    "voltage": 142.5,
    "current": 20.0,
    "power": 2850,
    "watt_hours": 1250,
    "temperature": 38.2,
    "rxerr": false,
    "hwfail": false,
    "overtemp": false,
    "not_charging": false,
    "input_voltage_err": false,
    "pack_voltage_err": false
  },
  "c2": {
    "active": true,
    "voltage": 142.5,
    "current": 20.0,
    "power": 2850,
    "watt_hours": 1250,
    "temperature": 39.1,
    "rxerr": false,
    "hwfail": false,
    "overtemp": false,
    "not_charging": false,
    "input_voltage_err": false,
    "pack_voltage_err": false
  }
}
```

---

## 🔨 Build & Installation

### Requirements
- Android SDK 29+ (Target SDK: 35)
- Java 11
- Gradle 8.9+ (included wrapper)

### Compiling via Command Line
```powershell
cd "Z:\Personal\Mike\AndroidDevelopment\Android Thunderstruck EV Charger Monitor"
.\gradlew.bat assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Installing onto Android Device via ADB
```powershell
# Connect to tablet over Wi-Fi ADB
adb connect <tablet-ip>:5555

# Install or upgrade
adb -s <tablet-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb -s <tablet-ip>:5555 shell am start -n com.thunderstruck.evcc.monitor/.MainActivity
```

---

## 📄 License
Open-source software developed for electric vehicle builders and EVCC enthusiasts. Released under the MIT License.
