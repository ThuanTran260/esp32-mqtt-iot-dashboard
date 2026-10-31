# Project: Integrated IoT System (Mosquitto Broker + Web Gauge Dashboard + ESP32 Wokwi Simulation)

## Architecture
The system integrates an IoT edge device (ESP32 running in Wokwi simulator), a central MQTT Broker (Eclipse Mosquitto with TCP and WebSocket listeners), a client Web Dashboard (pure HTML/CSS/JS with SVG dynamic gauges and WebSocket MQTT client), and verification CLI scripts.

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
|           ESP32 (Wokwi)            |   |            Web Dashboard             |
|                                    |   |                                      |
| Hardware Wiring:                   |   | Dual Dynamic SVG Gauges:             |
| - DHT22 Data: GPIO 16              |   | - Temp: -10°C to 60°C (70°C span)    |
| - LED Anode: GPIO 4                |   | - Humidity: 0% to 100%               |
| - Resistor: 220Ω (Cathode to GND)  |   | Interactive Controls:                |
| WiFi: Wokwi-GUEST                  |   | - LED Toggle Switch ("ON"/"OFF")     |
| Publishes:                         |   | - Connection Status Badge            |
| - sensor/data {"temp":x,"hum":y}   |   | - Real-time Message Log Table (<=50) |
| - sensor/status "ONLINE" (retain)  |   | Subscriptions:                       |
| Subscribes:                        |   | - sensor/data, sensor/led,           |
| - sensor/led ("ON"/"OFF")          |   |   sensor/status                      |
| LWT:                               |   | Publishes:                           |
| - sensor/status "OFFLINE" (retain) |   | - sensor/led ("ON"/"OFF")            |
+------------------------------------+   +--------------------------------------+
                       ^
                       | TCP: 1883
+----------------------+--------------------------------------------------------+
|                                CLI Test Scripts                               |
| - test_sub.bat (subscribes to sensor/#)                                       |
| - test_pub_temp.bat (publishes simulated sensor data to sensor/data)          |
| - test_pub_led.bat (publishes ON/OFF to sensor/led)                           |
+-------------------------------------------------------------------------------+
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Mosquitto TCP Listener | Port 1883 raw TCP listener for ESP32 and CLI tools | M1 | ORIGINAL_REQUEST §R1, docs/2.TH.docx |
| 2 | Mosquitto WebSocket Listener | Port 9001 listener with `protocol websockets` for Web Dashboard | M1 | ORIGINAL_REQUEST §R1, docs/2.TH.docx |
| 3 | Mosquitto Authentication Config | `allow_anonymous false` and `password_file password.txt` | M1 | ORIGINAL_REQUEST §R1, docs/2.TH.docx |
| 4 | Mosquitto Password Credentials | `user1` / `123456` hashed and structured in password.txt | M1 | ORIGINAL_REQUEST §R1, 2.MOS - Copy.pptx |
| 5 | Mosquitto Startup Batch Script | `start_broker.bat` with directory context and PATH fallback | M1 | ORIGINAL_REQUEST §R1 |
| 6 | Wokwi Diagram Components | ESP32 DevKit v1, DHT22 on GPIO 16, LED on GPIO 4 with 220Ω resistor | M2 | ORIGINAL_REQUEST §R2, docs/13.wokwi.pptx |
| 7 | Wokwi Dependencies & Config | `libraries.txt` (PubSubClient, DHTesp, ArduinoJson) and `wokwi.toml` | M2 | ORIGINAL_REQUEST §R2 |
| 8 | ESP32 WiFi Connectivity | Connects to `Wokwi-GUEST` SSID without password | M2 | ORIGINAL_REQUEST §R2, docs/13.wokwi.pptx |
| 9 | ESP32 MQTT Authentication | Connects to MQTT broker using `user1` / `123456` with reconnect logic | M2 | ORIGINAL_REQUEST §R2 |
| 10 | ESP32 Telemetry Publishing | Reads DHT22 every 3–5s, publishes JSON `{"temp": xx.x, "hum": yy.y}` to `sensor/data` | M2 | ORIGINAL_REQUEST §R2 |
| 11 | ESP32 Actuator Control | Subscribes to `sensor/led`, trims string, switches GPIO 4 HIGH/LOW | M2 | ORIGINAL_REQUEST §R2 |
| 12 | ESP32 LWT & Status | LWT payload "OFFLINE" (retain: true) on `sensor/status`, publishes "ONLINE" on connect | M2 | ORIGINAL_REQUEST §R2 |
| 13 | Web Dashboard Architecture | HTML5 structure (`index.html`), CSS styling (`style.css`), app logic (`app.js`) | M3 | ORIGINAL_REQUEST §R3 |
| 14 | Web MQTT Connection | Connects via WebSocket to `ws://localhost:9001` with `user1` / `123456` | M3 | ORIGINAL_REQUEST §R3, docs/2.TH.docx |
| 15 | Temperature Gauge | Dual dynamic SVG gauge displaying range -10°C to 60°C updated from `sensor/data` | M3 | ORIGINAL_REQUEST §R3 |
| 16 | Humidity Gauge | Dual dynamic SVG gauge displaying range 0% to 100% updated from `sensor/data` | M3 | ORIGINAL_REQUEST §R3 |
| 17 | Interactive LED Toggle | UI toggle switch publishing "ON" / "OFF" to `sensor/led`, synced to incoming state | M3 | ORIGINAL_REQUEST §R3 |
| 18 | Connection Status Badge | Visual indicator showing Disconnected, Connecting, Connected, Error states | M3 | ORIGINAL_REQUEST §R3 |
| 19 | Real-time Message Log Table | Rolling table recording timestamp, topic, payload (capped at 50 rows) | M3 | ORIGINAL_REQUEST §R3 |
| 20 | Vendored MQTT Client Library | Vendored `mqtt.min.js` in `web_dashboard/js/` with automatic CDN fallback | M3 | Survey Findings, docs/2.TH.docx |
| 21 | CLI Subscribe Script | `scripts/test_sub.bat` subscribing to `sensor/#` with auth | M4 | ORIGINAL_REQUEST §R4 |
| 22 | CLI Publish Temp Script | `scripts/test_pub_temp.bat` publishing simulated telemetry to `sensor/data` | M4 | ORIGINAL_REQUEST §R4 |
| 23 | CLI Publish LED Script | `scripts/test_pub_led.bat` publishing "ON"/"OFF" to `sensor/led` | M4 | ORIGINAL_REQUEST §R4 |
| 24 | Complete Documentation | `README.md` with broker startup, Wokwi guide, web dashboard guide, CLI verification | M4 | ORIGINAL_REQUEST §R5 |
| 25 | E2E Automated Test Suite | Opaque-box automated test harness covering Tiers 1-4 | E2E-TEST | ORIGINAL_REQUEST Acceptance Criteria |
| 26 | Final Integration & Hardening | 100% E2E pass + Tier 5 adversarial stress testing | M-FINAL | Orchestration Project Pattern |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Mosquitto Broker Setup | `broker/mosquitto.conf`, `broker/password.txt`, `broker/start_broker.bat` | none | DONE |
| M2 | ESP32 Wokwi Simulation | `esp32_wokwi/diagram.json`, `sketch.ino`, `libraries.txt`, `wokwi.toml` | M1 | DONE |
| M3 | Web Gauge Dashboard | `web_dashboard/index.html`, `style.css`, `app.js`, `js/mqtt.min.js` | M1 | IN_PROGRESS |
| M4 | CLI Scripts & Documentation | `scripts/test_sub.bat`, `test_pub_temp.bat`, `test_pub_led.bat`, `README.md` | M1, M2, M3 | PLANNED |
| E2E | E2E Testing Track | Test harness and test suites (Tiers 1-4), `TEST_READY.md` | none (runs in parallel) | PLANNED |
| M-FINAL | Final Milestone | Pass 100% E2E tests (Tiers 1-4) + Adversarial hardening (Tier 5) | M1, M2, M3, M4, E2E | PLANNED |

## Interface Contracts

### Broker ↔ Clients (ESP32, Web Dashboard, CLI)
- **Port 1883**: Raw TCP MQTT protocol (ESP32, CLI tools).
- **Port 9001**: WebSocket MQTT protocol (Web Dashboard `ws://localhost:9001`).
- **Authentication**: Username `user1`, Password `123456`. Anonymous access denied.

### MQTT Topics & Data Contracts
1. **Topic `sensor/data`**:
   - Direction: ESP32 / CLI Publisher $\rightarrow$ Broker $\rightarrow$ Web Dashboard / CLI Subscriber
   - Format: JSON string `{"temp": <float>, "hum": <float>}`
   - Range Constraints: `temp` between -10.0 and 60.0 (°C), `hum` between 0.0 and 100.0 (%)
   - Publishing Interval: Periodic 3 to 5 seconds from ESP32.
2. **Topic `sensor/led`**:
   - Direction: Web Dashboard / CLI Publisher $\rightarrow$ Broker $\rightarrow$ ESP32 / Web Dashboard
   - Format: Raw string `"ON"` or `"OFF"` (case-insensitive handling, whitespace trimmed)
   - Behavior: ESP32 sets GPIO 4 HIGH for `"ON"`, LOW for `"OFF"`. Web Dashboard reflects state on UI switch.
3. **Topic `sensor/status`**:
   - Direction: ESP32 $\rightarrow$ Broker $\rightarrow$ Web Dashboard / CLI
   - Format: Raw string `"ONLINE"` on connect (retain: true), `"OFFLINE"` configured as LWT (retain: true).

## Code Layout
```
e:\luyentaphe\LapTrinhMang\esp32\
├── broker/
│   ├── mosquitto.conf          # Port 1883 TCP, 9001 WS, auth config
│   ├── password.txt            # User user1 credentials
│   └── start_broker.bat        # Broker launcher with path resolution
├── esp32_wokwi/
│   ├── diagram.json            # ESP32 DevKit v1, DHT22 (GPIO16), LED (GPIO4, 220Ω)
│   ├── sketch.ino              # Arduino C++ sketch (WiFi, MQTT, DHTesp, ArduinoJson)
│   ├── libraries.txt           # PubSubClient, DHT sensor library for ESPx, ArduinoJson
│   └── wokwi.toml              # VS Code Wokwi simulation config
├── web_dashboard/
│   ├── index.html              # Modern dashboard layout
│   ├── style.css               # Clean styling for dual gauges, badge, toggle, log
│   ├── app.js                  # WebSocket MQTT client & SVG gauge logic
│   └── js/
│       └── mqtt.min.js         # Vendored MQTT client library
├── scripts/
│   ├── test_sub.bat            # mosquitto_sub listener on sensor/#
│   ├── test_pub_temp.bat       # mosquitto_pub simulated telemetry
│   └── test_pub_led.bat        # mosquitto_pub LED command
├── tests/                      # Automated E2E test harness
│   ├── run_all_tests.py        # Master test runner
│   ├── test_tier1_features.py  # Feature coverage tests (>=5 per feature)
│   ├── test_tier2_boundaries.py# Boundary & corner cases
│   ├── test_tier3_interactions.py # Cross-feature interactions
│   └── test_tier4_scenarios.py # Real-world simulation scenarios
├── docs/                       # University reference materials
├── README.md                   # Full step-by-step user and developer documentation
├── PROJECT.md                  # Project index and architecture
├── TEST_INFRA.md               # E2E test suite specifications
└── TEST_READY.md               # Signal when E2E test suite is published
```
