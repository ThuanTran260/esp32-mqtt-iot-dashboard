# Integrated IoT System: Mosquitto Broker, ESP32 Wokwi Simulation & Web Gauge Dashboard

A complete end-to-end IoT monitoring and control solution developed for the **SGU Computer Network Programming Coursework** (based on `docs/2.TH.docx`, `docs/13.wokwi.pptx`, and `2.MOS - Copy.pptx`).

The system integrates an **ESP32 edge microcontroller** simulated in Wokwi (reading DHT22 temperature and humidity and driving an LED), a central **Eclipse Mosquitto MQTT Broker** supporting dual TCP and WebSocket listeners with credential authentication, a real-time **Web Gauge Dashboard** with SVG meters, and **CLI Verification Scripts** for automated testing and validation.

---

## Architecture & Subsystem Overview

```
+-------------------------------------------------------------------------------+
|                                  MQTT BROKER                                  |
|                             (Eclipse Mosquitto)                               |
|                                                                               |
|   TCP Listener: 1883 (auth: user1/123456)                                     |
|   WebSocket Listener: 9001 (protocol websockets, auth: user1/123456)          |
|   Security: allow_anonymous false, password_file password.txt                 |
+----------------------+-------------------------------+------------------------+
                       ^                               ^
                       | TCP: 1883                     | WS: 9001
                       v                               v
+----------------------+-------------+   +-------------+------------------------+
|        ESP32 Edge (Wokwi)          |   |          Web Dashboard               |
|                                    |   |                                      |
| Hardware Connections:              |   | Dual SVG Dynamic Gauges:             |
| - DHT22 SDA: GPIO 16               |   | - Temperature: -10°C to 60°C         |
| - LED Anode: GPIO 4                |   | - Humidity: 0% to 100%               |
| - Resistor: 220Ω (Cathode to GND)  |   | Interactive Controls:                |
| WiFi: Wokwi-GUEST                  |   | - LED Toggle Switch ("ON"/"OFF")     |
| Telemetry Publishing:              |   | - Connection Status Badge            |
| - sensor/data {"temp":x,"hum":y}   |   | - Rolling Log Table (capped at 50)   |
| - sensor/status "ONLINE" (retain)  |   | Subscriptions:                       |
| Actuator Subscription:             |   | - sensor/data, sensor/led,           |
| - sensor/led ("ON"/"OFF")          |   |   sensor/status                      |
| LWT (Last Will and Testament):     |   | Publishing:                          |
| - sensor/status "OFFLINE" (retain) |   | - sensor/led ("ON"/"OFF")            |
+------------------------------------+   +--------------------------------------+
                       ^
                       | TCP: 1883
+----------------------+--------------------------------------------------------+
|                             CLI Verification Scripts                          |
| - scripts/test_sub.bat      (subscribes to wildcard topic: sensor/#)          |
| - scripts/test_pub_temp.bat (publishes simulated telemetry to: sensor/data)   |
| - scripts/test_pub_led.bat  (publishes ON/OFF commands to: sensor/led)        |
+-------------------------------------------------------------------------------+
```

### Directory Structure & Subsystem Paths
- **`broker/`**: Eclipse Mosquitto configuration (`mosquitto.conf`), hashed credentials (`password.txt`), and launcher script (`start_broker.bat`).
- **`esp32_wokwi/`**: Wokwi simulation circuit (`diagram.json`), Arduino C++ sketch (`sketch.ino`), library manifest (`libraries.txt`), and simulation config (`wokwi.toml`).
- **`web_dashboard/`**: Responsive web UI (`index.html`), stylesheet (`style.css`), MQTT WebSocket application controller (`app.js`), and vendored MQTT client (`js/mqtt.min.js`).
- **`scripts/`**: Windows batch verification tools (`test_sub.bat`, `test_pub_temp.bat`, `test_pub_led.bat`).
- **`tests/`**: Automated opaque-box 4-tier test harness validating features, boundaries, interactions, and scenarios.

### MQTT Topics & Data Contracts
| Topic | Direction | Payload Format | Description |
|---|---|---|---|
| `sensor/data` | ESP32 / CLI -> Broker -> Web / CLI | `{"temp": 28.5, "hum": 65.0}` | Telemetry JSON (temperature -10°C to 60°C, humidity 0% to 100%) |
| `sensor/led` | Web / CLI -> Broker -> ESP32 / Web | `"ON"` or `"OFF"` | Actuator control for GPIO 4 LED |
| `sensor/status` | ESP32 -> Broker -> Web / CLI | `"ONLINE"` / `"OFFLINE"` | Device lifecycle presence (retained LWT) |

### System Ports & Authentication
- **TCP Port `1883`**: Standard unencrypted MQTT listener for ESP32 and CLI scripts.
- **WebSocket Port `9001`**: WebSocket MQTT listener for web browser dashboard.
- **Username**: `user1`
- **Password**: `123456`
- **Anonymous Access**: Disabled (`allow_anonymous false`).

---

## Step 1: Mosquitto Broker Setup (`broker/`)

The broker manages message routing between all subsystems.

### 1. Configuration Overview
The file `broker/mosquitto.conf` contains the listener and security directives:
```conf
# Standard MQTT TCP Listener (ESP32 DevKit & CLI verification scripts)
listener 1883
allow_anonymous false
password_file password.txt

# WebSocket MQTT Listener (Web Gauge Dashboard)
listener 9001
protocol websockets
```

### 2. Password File Generation
The credentials file `broker/password.txt` contains PBKDF2 hashed credentials for user `user1` with password `123456`. If you need to regenerate it:
```cmd
cd broker
mosquitto_passwd -b -c password.txt user1 123456
```

### 3. Launching the Broker
Launch the broker using the provided automated startup script:
```cmd
cd broker
start_broker.bat
```
The script automatically validates `mosquitto.conf` and `password.txt`, sets the working directory, locates `mosquitto.exe` (checking `%PATH%`, `C:\Program Files\mosquitto`, and `%ProgramFiles(x86)%\mosquitto`), and starts the service in verbose mode.

To launch manually via CLI:
```cmd
cd broker
mosquitto -c mosquitto.conf -v
```

Verify that the output displays:
```text
Opening ipv4 listen socket on port 1883.
Opening ipv4 listen socket on port 9001.
```

---

## Step 2: Wokwi ESP32 Simulation (`esp32_wokwi/`)

The ESP32 microcontroller collects sensor data from the DHT22 and controls the indicator LED.

### 1. Circuit Connections & Pinout
The wiring defined in `esp32_wokwi/diagram.json`:
- **DHT22 Temperature & Humidity Sensor**:
  - `SDA` (Data pin) -> ESP32 **GPIO 16**
  - `VCC` -> ESP32 **3V3**
  - `GND` -> ESP32 **GND**
- **Indicator LED**:
  - `Anode (+)` -> ESP32 **GPIO 4**
  - `Cathode (-)` -> **220Ω Resistor** -> ESP32 **GND**

### 2. Required Libraries (`libraries.txt`)
When opening the project in Wokwi, ensure the following libraries are installed:
```text
PubSubClient
DHT sensor library for ESPx
ArduinoJson
```

### 3. Firmware Features (`sketch.ino`)
- **WiFi Connection**: Automatically connects to the simulated open WiFi network `Wokwi-GUEST` (no password required).
- **MQTT Authentication**: Connects to the broker using username `user1` and password `123456`.
- **Telemetry Publishing**: Reads temperature and humidity every 3000ms and publishes JSON to topic `sensor/data` (e.g., `{"temp": 25.0, "hum": 60.0}`).
- **Actuator Control**: Subscribes to topic `sensor/led`. Setting the payload to `"ON"` drives GPIO 4 `HIGH`; `"OFF"` drives GPIO 4 `LOW`.
- **Presence & LWT**: Configures Last Will and Testament on topic `sensor/status` with payload `"OFFLINE"` (retained). Once connected, immediately publishes `"ONLINE"` (retained).

### 4. Running the Simulation
You can run the simulation in one of two ways:
- **Web Browser (Wokwi.com)**:
  1. Open [https://wokwi.com](https://wokwi.com) and start a new **ESP32** project.
  2. Copy `esp32_wokwi/diagram.json` into the **diagram.json** tab.
  3. Copy `esp32_wokwi/sketch.ino` into the **sketch.ino** tab.
  4. Paste the library names into **libraries.txt**.
  5. Click **Play (Start Simulation)**.
- **VS Code with Wokwi Extension**:
  1. Install the official **Wokwi Simulator** extension in VS Code.
  2. Open the project folder and click on `esp32_wokwi/diagram.json`.
  3. Press `F1` and select **Wokwi: Start Simulator**.

*Note: For connecting Wokwi web simulation to your local Mosquitto broker on `localhost:1883`, see Step 5 (Troubleshooting).*

---

## Step 3: Web Gauge Dashboard (`web_dashboard/`)

The web dashboard provides real-time visualization and bi-directional control from any modern web browser.

### 1. Launching the Dashboard
You can open `web_dashboard/index.html` directly in your browser or serve it via a lightweight local HTTP server:
```cmd
cd web_dashboard
python -m http.server 8000
```
Then navigate to: `http://localhost:8000`

### 2. Dashboard Features & Operation
- **Broker Connection Badge**: Displays connection state (`Connected` in green, `Connecting` in amber, `Disconnected` or `Error` in red) to `ws://localhost:9001`.
- **ESP32 Device Status**: Dynamically tracks `sensor/status` showing `ESP32: Online` or `ESP32: Offline`.
- **Dual Dynamic SVG Gauges**:
  - **Temperature Gauge**: Visualizes values from **-10°C to 60°C** with colored temperature gradient arc.
  - **Humidity Gauge**: Visualizes values from **0% to 100%** with cyan gradient arc.
- **Interactive LED Toggle Switch**:
  - Clicking the toggle publishes `"ON"` or `"OFF"` to `sensor/led`.
  - The toggle also listens on `sensor/led` to mirror state changes commanded by external CLI scripts or other clients.
- **Real-Time MQTT Message Log Table**:
  - Displays incoming and outgoing MQTT packets with timestamp, topic, and payload.
  - Automatically capped at the **50** most recent messages with a "Clear Log" button.

---

## Step 4: CLI Verification Scripts (`scripts/`)

Pre-configured Windows batch verification scripts enable manual and automated testing of MQTT communication without launching the full web or ESP32 front-ends.

### 1. Listening to All Topics (`test_sub.bat`)
Launches `mosquitto_sub` subscribed to the wildcard topic `sensor/#` with verbose output:
```cmd
cd scripts
test_sub.bat
```
- **Default arguments**: `-h localhost -p 1883 -t "sensor/#" -u user1 -P 123456 -v`
- **Topic override**: You can pass a specific topic as parameter `%1`:
  ```cmd
  test_sub.bat sensor/data
  ```
- **Expected output**:
  ```text
  sensor/status ONLINE
  sensor/data {"temp": 25.0, "hum": 60.0}
  sensor/led ON
  ```

### 2. Simulating Sensor Telemetry (`test_pub_temp.bat`)
Publishes sample temperature and humidity data to verify that gauges and log tables update:
```cmd
cd scripts
test_pub_temp.bat
```
- **Default command**: `mosquitto_pub -h localhost -p 1883 -t "sensor/data" -u user1 -P 123456 -m "{\"temp\": 28.5, \"hum\": 65.0}"`
- **Custom payload override**: Pass custom JSON or temperature:
  ```cmd
  test_pub_temp.bat "{\"temp\": 35.0, \"hum\": 80.0}"
  ```

### 3. Controlling the LED Actuator (`test_pub_led.bat`)
Publishes actuator control commands to `sensor/led` to toggle the hardware LED and web switch:
```cmd
cd scripts
test_pub_led.bat ON
test_pub_led.bat OFF
```
- **Default command**: `mosquitto_pub -h localhost -p 1883 -t "sensor/led" -u user1 -P 123456 -m "ON"`
- **Parameters**: Accepts `ON` or `OFF` as parameter `%1`.
- **Expected effect**:
  - ESP32 hardware LED on GPIO 4 turns ON or OFF.
  - Web Dashboard LED toggle switch smoothly slides to the matching state.

### 4. End-to-End Verification Walkthrough
To verify the full integration across all 4 subsystems:
1. Terminal 1: Run `broker\start_broker.bat`.
2. Terminal 2: Run `scripts\test_sub.bat` (subscriber listening to `sensor/#`).
3. Browser: Open `http://localhost:8000` (Web Dashboard connects to `ws://localhost:9001`).
4. Terminal 3: Run `scripts\test_pub_temp.bat`.
   - Observer Terminal 2 prints: `sensor/data {"temp": 28.5, "hum": 65.0}`.
   - Observer Web Dashboard: Temperature gauge moves to 28.5°C, humidity gauge moves to 65.0%, log table adds row.
5. Terminal 3: Run `scripts\test_pub_led.bat ON`.
   - Observer Terminal 2 prints: `sensor/led ON`.
   - Observer Web Dashboard: Switch turns ON, LED indicator lights up.
6. Web Dashboard: Flip the LED switch to OFF.
   - Observer Terminal 2 prints: `sensor/led OFF`.

---

## Step 5: Troubleshooting & FAQ

### 1. Authentication Failure (`Connection refused: not authorised`)
- **Cause**: Mosquitto has `allow_anonymous false` enabled and incoming requests lack valid credentials.
- **Fix**:
  1. Confirm your client supplies username `user1` and password `123456`.
  2. Verify that `broker/password.txt` exists and contains the hashed credentials.
  3. Re-create the credentials file using:
     ```cmd
     cd broker
     mosquitto_passwd -b -c password.txt user1 123456
     ```
  4. Ensure `mosquitto.conf` references `password_file password.txt` and that Mosquitto is launched from the `broker/` directory.

### 2. WebSocket Connection Refused (`ws://localhost:9001`)
- **Cause**: The WebSocket listener is missing, misplaced in configuration, or blocked by a firewall.
- **Fix**:
  1. Open `broker/mosquitto.conf` and verify that `protocol websockets` is placed **directly underneath** `listener 9001`:
     ```conf
     listener 9001
     protocol websockets
     ```
     *(Note: If `protocol websockets` is placed under `listener 1883`, raw TCP MQTT will fail).*
  2. Check if port 9001 is listening:
     ```cmd
     netstat -ano | findstr 9001
     ```
  3. Make sure the dashboard connects to `ws://localhost:9001` (and NOT `ws://localhost:1883` and NOT `wss://`).

### 3. Wokwi Simulation Cannot Reach Local Broker (`host.wokwi` / `localhost`)
- **Cause**: The browser-based Wokwi simulator runs inside a secure web sandbox. The virtual network `Wokwi-GUEST` cannot directly access `localhost:1883` on your workstation without a gateway bridge.
- **Fix**:
  - **Solution A (Recommended for Local Broker)**: Install and run the **Wokwi IoT Gateway** (`wokwi-bridge`). Once running, the ESP32 sketch connects to `host.wokwi:1883` which forwards traffic directly to your PC's local Mosquitto broker.
  - **Solution B (VS Code Extension)**: Run the simulation locally inside VS Code using the Wokwi extension and `esp32_wokwi/wokwi.toml`.
  - **Solution C (Zero-Gateway Public Broker)**: If you do not have the Wokwi IoT Gateway installed, you can test cloud telemetry by setting `mqtt_server = "broker.emqx.io"` in `sketch.ino` and updating `app.js` to connect to `ws://broker.emqx.io:8083/mqtt`.

### 4. Browser Mixed Content & CORS Restrictions
- **Cause**: If the Web Dashboard is hosted on an HTTPS server (e.g., `https://example.com`), modern browsers block unencrypted WebSocket connections (`ws://`).
- **Fix**:
  - Always open the dashboard locally over HTTP (`http://localhost:8000`) via `python -m http.server 8000` or open `index.html` directly from disk (`file:///`).

### 5. Mosquitto Binary Not Found
- **Cause**: `mosquitto.exe`, `mosquitto_sub.exe`, or `mosquitto_pub.exe` are not in the Windows system `%PATH%`.
- **Fix**:
  - Use `start_broker.bat` and `scripts/*.bat`, which automatically inspect `%PATH%` and probe standard installation directories (`C:\Program Files\mosquitto\`).
  - To install Mosquitto on Windows via Windows Package Manager:
     ```cmd
     winget install EclipseFoundation.Mosquitto
     ```
  - Add `C:\Program Files\mosquitto` to your system Environment Variables `PATH`.

### 6. Port Already in Use (1883 or 9001)
- **Cause**: A background Mosquitto Windows service is already running on port 1883 or 9001.
- **Fix**:
  - Stop the background Windows service before running `start_broker.bat`:
     ```cmd
     net stop mosquitto
     ```
  - Or terminate the conflicting PID discovered via `netstat -ano | findstr 1883`.

---

## Automated Test Harness (`tests/`)

The project includes an opaque-box automated test suite validating 100% of functional requirements across 4 tiers:

### Running the Tests
To execute all tests across all tiers:
```cmd
python -m unittest discover -s tests -v
```
Or execute specific tiers:
```cmd
python -m unittest tests.test_tier1_features
python -m unittest tests.test_tier2_boundaries
python tests/test_tier3_interactions.py
python -m unittest tests.test_tier4_scenarios
```

### Test Suite Structure
- **Tier 1 (Features)**: Tests F01-F24 ensuring all components exist and satisfy functional specifications.
- **Tier 2 (Boundaries)**: Tests B01-B24 validating edge cases, escaping, numeric ranges, and error handling.
- **Tier 3 (Interactions)**: Tests I01-I24 verifying cross-subsystem protocol alignments (ports, topics, credentials, pinouts).
- **Tier 4 (Scenarios)**: Tests S01-S05 validating complete real-world simulation and operational lifecycles.
