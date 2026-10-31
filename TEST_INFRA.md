# E2E Test Infra: Integrated IoT System

## Test Philosophy
- Opaque-box, requirement-driven. Derived from `ORIGINAL_REQUEST.md` and university lab standards (`docs/2.TH.docx`, `docs/13.wokwi.pptx`, `2.MOS - Copy.pptx`).
- No dependency on implementation design internals; tests interact with the system via standard external interfaces (files, configs, TCP port 1883, WebSocket port 9001, DOM structure, CLI batch scripts).
- Methodology: Category-Partition + Boundary Value Analysis (BVA) + Pairwise Combinatorial Testing + Real-World Workload Testing.

## Feature Inventory
| # | Feature | Source | Tier 1 | Tier 2 | Tier 3 |
|---|---------|--------|:------:|:------:|:------:|
| 1 | Mosquitto TCP Listener (1883) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | Mosquitto WS Listener (9001) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 3 | Mosquitto Auth & Security Config | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 4 | Mosquitto Password Credentials | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 5 | Mosquitto Startup Batch Script | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 6 | Wokwi Diagram Components & Wiring | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 7 | Wokwi Dependencies & Config | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 8 | ESP32 WiFi Connectivity Config | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 9 | ESP32 MQTT Authentication & Client | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 10 | ESP32 DHT22 JSON Telemetry | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 11 | ESP32 LED Actuator Subscription | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 12 | ESP32 LWT & Status Publishing | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 13 | Web Dashboard File Structure | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 14 | Web WebSocket MQTT Connection | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 15 | Temperature Gauge Display | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 16 | Humidity Gauge Display | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 17 | Interactive LED Toggle Switch | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 18 | Broker Connection Status Badge | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 19 | Real-time Message Log Table | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 20 | Vendored MQTT Library & Fallback | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 21 | CLI Subscribe Script (test_sub.bat) | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 22 | CLI Publish Temp Script (test_pub_temp.bat) | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 23 | CLI Publish LED Script (test_pub_led.bat) | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 24 | Complete Documentation (README.md) | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |

## Test Architecture
- Test Runner: Python test suite located in `tests/run_all_tests.py` using standard unittest/pytest patterns.
- Pass/Fail Semantics: Returns exit code 0 when all tests pass, non-zero on failure.
- Automated Testing Mechanisms:
  - Configuration & File Syntax Verification (static analysis of JSON, conf, bat, ino, html, css, js).
  - Headless DOM verification of `web_dashboard/index.html` via Chrome Headless (`--headless=new --dump-dom`).
  - WebSocket and MQTT protocol compliance testing via Python `websockets` and socket probes.
  - Command-line syntax and parameter verification for batch scripts.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Full Broker Cold-Boot & Config Validation | F1, F2, F3, F4, F5 | Medium |
| 2 | ESP32 Telemetry & Actuation Cycle Simulation | F6, F7, F8, F9, F10, F11, F12 | High |
| 3 | Web Dashboard Offline Loading & Gauge Rendering | F13, F14, F15, F16, F17, F18, F19, F20 | High |
| 4 | CLI End-to-End Cross-Publish & Subscribe Loop | F1, F3, F4, F21, F22, F23 | Medium |
| 5 | Integrated Edge-to-Cloud-to-Dashboard Workflow | All features (F1 to F24) | Very High |

## Coverage Thresholds
- Tier 1 (Feature Coverage): >= 5 tests per feature (>= 120 test cases).
- Tier 2 (Boundary & Corner Cases): >= 5 tests per feature where boundaries exist (>= 120 test cases).
- Tier 3 (Cross-Feature Combinations): Pairwise interaction tests (>= 24 test cases).
- Tier 4 (Real-World Application Scenarios): >= 5 scenarios.
- Minimum Target: ~270 comprehensive test cases across all tiers.
