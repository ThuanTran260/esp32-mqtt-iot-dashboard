# TEST_READY: Integrated IoT System E2E Test Suite

**Status:** Published & Verified  
**Date:** 2026-10-31  
**Test Suite Directory:** `tests/`  
**Test Runner:** `tests/run_all_tests.py`  
**Framework:** Standard Python `unittest` (Zero external framework dependencies required)

---

## 1. Overview & Test Philosophy

The E2E Test Suite implements an opaque-box, requirements-driven verification harness derived strictly from:
- `ORIGINAL_REQUEST.md` (System Specifications & Acceptance Criteria)
- `PROJECT.md` (System Architecture & Interface Contracts)
- `TEST_INFRA.md` (Test Strategy & Tier Coverage Thresholds)
- University Reference Material: `docs/2.TH.docx`, `docs/13.wokwi.pptx`, `2.MOS - Copy.pptx`

The suite enforces black-box contracts across all subsystem boundaries (Mosquitto configuration directives, credential hashing, Wokwi circuit schematics, Arduino C++ firmware behaviors, HTML5/SVG web gauge displays, and CLI automation scripts).

---

## 2. Test Suite Architecture

```
tests/
├── __init__.py                 # Python package marker
├── test_helpers.py             # Domain parsers, validators, schema checkers & mathematical models
├── test_tier1_features.py      # Tier 1: Feature coverage (120 test cases, 5 per feature across 24 features)
├── test_tier2_boundaries.py    # Tier 2: Boundary & corner cases (120 test cases, 5 per feature)
├── test_tier3_interactions.py  # Tier 3: Cross-feature pairwise interactions (24 test cases)
├── test_tier4_scenarios.py     # Tier 4: Real-world application scenarios (5 comprehensive scenarios)
└── run_all_tests.py            # Master test runner with structured ASCII reporting and exit codes
```

---

## 3. Test Coverage Matrix

| # | Feature | Subsystem | Tier 1 (Features) | Tier 2 (Boundaries) | Tier 3 (Interactions) | Tier 4 (Scenarios) |
|---|---------|-----------|:-----------------:|:-------------------:|:---------------------:|:------------------:|
| F01 | Mosquitto TCP Listener (1883) | Broker | 5 tests | 5 tests | ✓ | S1, S4, S5 |
| F02 | Mosquitto WS Listener (9001) | Broker | 5 tests | 5 tests | ✓ | S1, S5 |
| F03 | Mosquitto Auth & Security Config | Broker | 5 tests | 5 tests | ✓ | S1, S4, S5 |
| F04 | Mosquitto Password Credentials | Broker | 5 tests | 5 tests | ✓ | S1, S4, S5 |
| F05 | Mosquitto Startup Batch Script | Broker | 5 tests | 5 tests | ✓ | S1, S5 |
| F06 | Wokwi Diagram Components & Wiring | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F07 | Wokwi Dependencies & Config | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F08 | ESP32 WiFi Connectivity Config | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F09 | ESP32 MQTT Authentication & Client | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F10 | ESP32 DHT22 JSON Telemetry | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F11 | ESP32 LED Actuator Subscription | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F12 | ESP32 LWT & Status Publishing | ESP32 | 5 tests | 5 tests | ✓ | S2, S5 |
| F13 | Web Dashboard File Structure | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F14 | Web WebSocket MQTT Connection | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F15 | Temperature Gauge Display (-10°C to 60°C) | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F16 | Humidity Gauge Display (0% to 100%) | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F17 | Interactive LED Toggle Switch | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F18 | Broker Connection Status Badge | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F19 | Real-time Message Log Table (<=50 rows) | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F20 | Vendored MQTT Library & Fallback | Web | 5 tests | 5 tests | ✓ | S3, S5 |
| F21 | CLI Subscribe Script (test_sub.bat) | CLI | 5 tests | 5 tests | ✓ | S4, S5 |
| F22 | CLI Publish Temp Script (test_pub_temp.bat) | CLI | 5 tests | 5 tests | ✓ | S4, S5 |
| F23 | CLI Publish LED Script (test_pub_led.bat) | CLI | 5 tests | 5 tests | ✓ | S4, S5 |
| F24 | Complete Documentation (README.md) | Docs | 5 tests | 5 tests | ✓ | S5 |
| **Total** | **All 24 Features** | **System** | **120 Tests** | **120 Tests** | **24 Tests** | **5 Scenarios** |

**Grand Total: 269 Automated Test Cases**

---

## 4. Real-World Application Scenarios (Tier 4)

1. **Scenario 1: Broker Cold-Boot & Configuration Validation**
   - Cold start directory context isolation (`%~dp0`), listener segregation (TCP 1883 vs WS 9001), strict auth enforcement, and password file PBKDF2 credential integrity.
2. **Scenario 2: ESP32 Telemetry & Actuation Cycle Simulation**
   - Microcontroller network boot on `Wokwi-GUEST`, MQTT auth (`user1`/`123456`), LWT registration, multi-climate telemetry streaming (sub-zero -8.5°C to greenhouse 59.8°C), and GPIO 4 actuator command execution.
3. **Scenario 3: Web Dashboard Offline Loading & Gauge Rendering**
   - Zero-internet offline asset verification, SVG dynamic gauge mathematical precision across full -10°C to 60°C span, bidirectional toggle sync, and high-frequency message logging with strict FIFO 50-row capacity limits.
4. **Scenario 4: CLI End-to-End Cross-Publish & Subscribe Loop**
   - Multi-tier wildcard observation (`sensor/#`), parameter forwarding (`%1`), JSON quotation escaping on Windows Command Prompt, and command dispatch.
5. **Scenario 5: Integrated Edge-to-Cloud-to-Dashboard Workflow (Full Stack)**
   - Complete lifecycle orchestration connecting Broker, ESP32, Web Dashboard, and CLI operators, followed by unexpected edge drop triggering LWT broadcast.

---

## 5. How to Run the Tests

### Master Test Runner (Recommended)
Run all 269 tests across all four tiers with structured diagnostic reporting:
```powershell
python tests/run_all_tests.py
```

### Running Specific Tiers
Execute an individual tier for targeted milestone verification:
```powershell
# Tier 1: Feature Coverage (120 tests)
python tests/run_all_tests.py --tier 1

# Tier 2: Boundary & Corner Cases (120 tests)
python tests/run_all_tests.py --tier 2

# Tier 3: Cross-Feature Pairwise Interactions (24 tests)
python tests/run_all_tests.py --tier 3

# Tier 4: Real-World Application Scenarios (5 scenarios)
python tests/run_all_tests.py --tier 4
```

### Standard Python Unittest Runner
```powershell
python -m unittest discover -s tests -p "test_*.py" -v
```

---

## 6. Verification Status & Exit Codes

- The test runner returns exit code **`0`** if and only if **100% of executed tests pass**.
- Any assertion failure or unexpected error returns exit code **`1`** and outputs formatted diagnostic tracebacks identifying the precise file path, line number, and requirement violation.
- The test harness is verified operational on Python 3.13.7 (Windows 11) with zero uncaught runtime exceptions.
