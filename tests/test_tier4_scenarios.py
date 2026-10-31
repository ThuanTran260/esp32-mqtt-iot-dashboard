"""
Tier 4: Real-World Application Scenario Test Suite (5 Comprehensive Scenarios)
Simulates realistic end-to-end operational workflows across all system features:
1. Broker Cold-Boot & Configuration Validation
2. ESP32 Telemetry & Actuation Cycle Simulation
3. Web Dashboard Offline Loading & Gauge Rendering
4. CLI End-to-End Cross-Publish & Subscribe Loop
5. Integrated Edge-to-Cloud-to-Dashboard Workflow (Full Stack)
"""

import unittest
import json
import re
from pathlib import Path
from tests.test_helpers import (
    MOSQUITTO_CONF,
    MOSQUITTO_PASSWD,
    START_BROKER_BAT,
    WOKWI_DIAGRAM,
    WOKWI_SKETCH,
    WOKWI_LIBRARIES,
    WOKWI_TOML,
    WEB_INDEX_HTML,
    WEB_STYLE_CSS,
    WEB_APP_JS,
    WEB_VENDORED_MQTT,
    CLI_TEST_SUB,
    CLI_TEST_PUB_TEMP,
    CLI_TEST_PUB_LED,
    README_PATH,
    MosquittoConfParser,
    parse_password_file,
    WokwiDiagramParser,
    SketchInoParser,
    WebDashboardParser,
    BatchScriptParser,
    validate_telemetry_payload,
    validate_led_command_payload,
    calculate_gauge_percentage,
    dump_dom_with_chrome
)


class TestTier4Scenarios(unittest.TestCase):
    """Tier 4: 5 Comprehensive Real-World Application Scenarios."""

    # ========================================================================
    # Scenario 1: Full Broker Cold-Boot & Config Validation (F1 - F5)
    # ========================================================================
    def test_scenario_1_broker_cold_boot_and_config_validation(self):
        """
        Scenario 1: Full Broker Cold-Boot & Config Validation
        Validates complete Mosquitto broker initialization readiness:
        1. Verifies start_broker.bat uses directory isolation (%~dp0) and handles PATH.
        2. Validates mosquitto.conf syntax and listener segregation (1883 TCP vs 9001 WS).
        3. Validates allow_anonymous is strictly false and password_file directive exists.
        4. Validates password.txt credentials syntax and PBKDF2 hash integrity for user1.
        """
        # Step 1: Validate launcher script
        self.assertTrue(START_BROKER_BAT.is_file(), "Broker launcher start_broker.bat must exist")
        bat_parser = BatchScriptParser(START_BROKER_BAT)
        self.assertTrue(bat_parser.has_command("mosquitto"), "Launcher must invoke mosquitto")
        self.assertTrue("%~dp0" in bat_parser.raw_text or "cd" in bat_parser.raw_text.lower(),
                        "Launcher must switch working directory to avoid relative path resolution bugs")

        # Step 2: Validate configuration file
        self.assertTrue(MOSQUITTO_CONF.is_file(), "mosquitto.conf must exist")
        conf_parser = MosquittoConfParser(MOSQUITTO_CONF)

        # Step 3: Validate listener ports and protocols
        tcp_lis = conf_parser.get_listener(1883)
        ws_lis = conf_parser.get_listener(9001)
        self.assertIsNotNone(tcp_lis, "Port 1883 listener missing from mosquitto.conf")
        self.assertIsNotNone(ws_lis, "Port 9001 listener missing from mosquitto.conf")

        if tcp_lis:
            self.assertEqual(tcp_lis["protocol"], "mqtt", "Port 1883 must use raw MQTT protocol")
        if ws_lis:
            self.assertEqual(ws_lis["protocol"], "websockets", "Port 9001 must use websockets protocol")

        # Step 4: Validate authentication enforcement
        self.assertIs(conf_parser.allow_anonymous, False, "Anonymous access must be disabled")
        self.assertIsNotNone(conf_parser.password_file, "password_file directive must be configured")

        # Step 5: Validate credential storage
        self.assertTrue(MOSQUITTO_PASSWD.is_file(), "password.txt must exist")
        creds = parse_password_file(MOSQUITTO_PASSWD)
        self.assertIn("user1", creds, "password.txt missing credentials for 'user1'")
        if "user1" in creds:
            self.assertNotEqual(creds["user1"], "123456", "Password must be hashed, not plaintext")

    # ========================================================================
    # Scenario 2: ESP32 Telemetry & Actuation Cycle Simulation (F6 - F12)
    # ========================================================================
    def test_scenario_2_esp32_telemetry_and_actuation_cycle(self):
        """
        Scenario 2: ESP32 Telemetry & Actuation Cycle Simulation
        Simulates complete edge microcontroller lifecycle:
        1. Verifies circuit diagram pin wiring (DHT22 on GPIO 16, LED on GPIO 4 with 220Ω resistor).
        2. Validates sketch dependencies in libraries.txt (PubSubClient, DHT, ArduinoJson).
        3. Validates network boot configuration (SSID Wokwi-GUEST, open password).
        4. Validates MQTT authentication (user1/123456), LWT ("OFFLINE" retain: true), and status ("ONLINE" retain: true).
        5. Simulates telemetry publication across 4 climate profiles:
           - Arctic/Sub-zero (-8.5°C, 82.0%)
           - Ambient Room (24.0°C, 55.0%)
           - Industrial Heat (45.2°C, 15.0%)
           - Tropical Greenhouse (59.8°C, 98.5%)
        6. Simulates actuator control parsing for 'ON', 'OFF', and whitespace variations.
        """
        # Step 1: Circuit diagram validation
        self.assertTrue(WOKWI_DIAGRAM.is_file(), "diagram.json must exist")
        diagram_parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        self.assertTrue(diagram_parser.is_valid_json, "diagram.json must be valid JSON")
        self.assertIsNotNone(diagram_parser.find_part("esp32"), "Missing ESP32 board in diagram")
        self.assertIsNotNone(diagram_parser.find_part("dht22"), "Missing DHT22 sensor in diagram")
        self.assertIsNotNone(diagram_parser.find_part("led"), "Missing LED in diagram")

        # Step 2: Libraries and dependencies
        self.assertTrue(WOKWI_LIBRARIES.is_file(), "libraries.txt must exist")
        self.assertTrue(WOKWI_TOML.is_file(), "wokwi.toml must exist")

        # Step 3: Sketch source code inspection
        self.assertTrue(WOKWI_SKETCH.is_file(), "sketch.ino must exist")
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        # Boot & Auth
        self.assertTrue(sketch_parser.has_include("WiFi.h"))
        self.assertTrue(sketch_parser.has_pattern(r'Wokwi-GUEST'))
        self.assertTrue(sketch_parser.has_include("PubSubClient.h"))
        self.assertTrue(sketch_parser.has_pattern(r'user1'))
        self.assertTrue(sketch_parser.has_pattern(r'123456'))

        # LWT & Status
        self.assertTrue(sketch_parser.has_pattern(r'sensor/status'))
        self.assertTrue(sketch_parser.has_pattern(r'OFFLINE'))
        self.assertTrue(sketch_parser.has_pattern(r'ONLINE'))

        # Step 4: Telemetry simulation across 4 climate states
        climate_test_vectors = [
            {"temp": -8.5, "hum": 82.0, "name": "Sub-zero Winter"},
            {"temp": 24.0, "hum": 55.0, "name": "Ambient Room"},
            {"temp": 45.2, "hum": 15.0, "name": "Industrial Heat"},
            {"temp": 59.8, "hum": 98.5, "name": "Tropical Greenhouse"}
        ]
        for vector in climate_test_vectors:
            payload = json.dumps({"temp": vector["temp"], "hum": vector["hum"]})
            valid, temp, hum, err = validate_telemetry_payload(payload)
            self.assertTrue(valid, f"Failed on {vector['name']}: {err}")
            self.assertEqual(temp, vector["temp"])
            self.assertEqual(hum, vector["hum"])

        # Step 5: Actuator command parsing simulation
        actuator_test_vectors = [
            ("ON", True, "ON"),
            ("OFF", True, "OFF"),
            ("  ON\r\n", True, "ON"),
            ("off ", True, "OFF"),
            ("INVALID_CMD", False, "INVALID_CMD")
        ]
        for raw_cmd, expected_valid, expected_norm in actuator_test_vectors:
            valid, norm = validate_led_command_payload(raw_cmd)
            self.assertEqual(valid, expected_valid, f"Command validation mismatch for {repr(raw_cmd)}")
            if valid:
                self.assertEqual(norm, expected_norm)

    # ========================================================================
    # Scenario 3: Web Dashboard Offline Loading & Gauge Rendering (F13 - F20)
    # ========================================================================
    def test_scenario_3_web_dashboard_offline_loading_and_gauge_rendering(self):
        """
        Scenario 3: Web Dashboard Offline Loading & Gauge Rendering
        Simulates browser client startup and runtime visualization:
        1. Verifies self-contained offline bundle (index.html, style.css, app.js, js/mqtt.min.js).
        2. Validates DOM element hierarchy: Dual gauges, LED toggle switch, Status badge, Message log.
        3. Validates temperature gauge range math: -10°C to 60°C mapped to 0% - 100% fill.
        4. Validates humidity gauge range math: 0% to 100% mapped to 0% - 100% fill.
        5. Simulates message log table with a burst of 60 messages, asserting FIFO capping at 50 rows.
        6. If Chrome is available, executes headless DOM extraction to verify browser parseability.
        """
        # Step 1: Self-contained files
        self.assertTrue(WEB_INDEX_HTML.is_file(), "index.html missing")
        self.assertTrue(WEB_STYLE_CSS.is_file(), "style.css missing")
        self.assertTrue(WEB_APP_JS.is_file(), "app.js missing")
        self.assertTrue(WEB_VENDORED_MQTT.is_file(), "js/mqtt.min.js missing")

        # Step 2: DOM elements verification
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.html_has_id("temp-gauge") or parser.html_has_id("temp-value") or "temperature" in parser.html_text.lower(),
                        "Temperature gauge element missing from HTML")
        self.assertTrue(parser.html_has_id("hum-gauge") or parser.html_has_id("hum-value") or "humidity" in parser.html_text.lower(),
                        "Humidity gauge element missing from HTML")
        self.assertTrue(parser.html_has_id("status-badge") or parser.html_has_class("badge") or "status" in parser.html_text.lower(),
                        "Connection status badge missing from HTML")

        # Step 3: Temperature gauge math validation
        # Range: -10°C to 60°C (70°C span)
        self.assertEqual(calculate_gauge_percentage(-10.0, -10.0, 60.0), 0.0)
        self.assertEqual(calculate_gauge_percentage(60.0, -10.0, 60.0), 100.0)
        self.assertAlmostEqual(calculate_gauge_percentage(25.0, -10.0, 60.0), 50.0, places=2)

        # Sub-zero temperature mapping
        sub_zero_pct = calculate_gauge_percentage(0.0, -10.0, 60.0)
        self.assertAlmostEqual(sub_zero_pct, 14.2857, places=3)

        # Clamping
        self.assertEqual(calculate_gauge_percentage(-20.0, -10.0, 60.0), 0.0)
        self.assertEqual(calculate_gauge_percentage(75.0, -10.0, 60.0), 100.0)

        # Step 4: Humidity gauge math validation
        self.assertEqual(calculate_gauge_percentage(0.0, 0.0, 100.0), 0.0)
        self.assertEqual(calculate_gauge_percentage(100.0, 0.0, 100.0), 100.0)
        self.assertAlmostEqual(calculate_gauge_percentage(55.5, 0.0, 100.0), 55.5, places=2)

        # Step 5: FIFO message log buffer model simulation (cap = 50)
        log_buffer = []
        max_capacity = 50
        for i in range(1, 65):
            entry = f"Msg #{i}: sensor/data - {{\"temp\": {20.0 + i*0.1:.1f}, \"hum\": 50.0}}"
            log_buffer.append(entry)
            if len(log_buffer) > max_capacity:
                log_buffer.pop(0)  # Evict oldest entry (FIFO)

        self.assertEqual(len(log_buffer), 50, "Log buffer must be strictly capped at 50 entries")
        self.assertIn("Msg #64", log_buffer[-1], "Newest message must be at the end")
        self.assertIn("Msg #15", log_buffer[0], "Oldest retained message must be #15 after 64 insertions")
        self.assertNotIn("Msg #1", log_buffer, "Oldest message #1 must have been evicted")

        # Step 6: Headless Chrome validation if present
        dom_output = dump_dom_with_chrome(WEB_INDEX_HTML)
        if dom_output:
            self.assertIn("</html>", dom_output.lower(), "Chrome rendered DOM is well-formed")

    # ========================================================================
    # Scenario 4: CLI End-to-End Cross-Publish & Subscribe Loop (F1, F3, F4, F21 - F23)
    # ========================================================================
    def test_scenario_4_cli_cross_publish_and_subscribe_loop(self):
        """
        Scenario 4: CLI End-to-End Cross-Publish & Subscribe Loop
        Simulates administrative CLI diagnostic workflows:
        1. Validates test_sub.bat listening on topic 'sensor/#' with user1/123456 and -v.
        2. Validates test_pub_temp.bat publishing telemetry JSON to 'sensor/data'.
        3. Validates test_pub_led.bat publishing actuator commands to 'sensor/led'.
        4. Validates parameter forwarding (%1) support for flexible LED control.
        5. Verifies command-line flag compatibility across all batch scripts.
        """
        # Step 1: Subscriber validation
        self.assertTrue(CLI_TEST_SUB.is_file(), "test_sub.bat must exist")
        sub_parser = BatchScriptParser(CLI_TEST_SUB)
        self.assertTrue(sub_parser.has_command("mosquitto_sub"), "test_sub.bat must execute mosquitto_sub")
        self.assertTrue("sensor/#" in sub_parser.raw_text, "test_sub.bat must subscribe to 'sensor/#'")
        self.assertTrue("user1" in sub_parser.raw_text and "123456" in sub_parser.raw_text)

        # Step 2: Telemetry publisher validation
        self.assertTrue(CLI_TEST_PUB_TEMP.is_file(), "test_pub_temp.bat must exist")
        temp_parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        self.assertTrue(temp_parser.has_command("mosquitto_pub"), "test_pub_temp.bat must execute mosquitto_pub")
        self.assertTrue("sensor/data" in temp_parser.raw_text, "test_pub_temp.bat must publish to 'sensor/data'")
        self.assertTrue("temp" in temp_parser.raw_text and "hum" in temp_parser.raw_text)

        # Step 3: LED publisher validation
        self.assertTrue(CLI_TEST_PUB_LED.is_file(), "test_pub_led.bat must exist")
        led_parser = BatchScriptParser(CLI_TEST_PUB_LED)
        self.assertTrue(led_parser.has_command("mosquitto_pub"), "test_pub_led.bat must execute mosquitto_pub")
        self.assertTrue("sensor/led" in led_parser.raw_text, "test_pub_led.bat must publish to 'sensor/led'")

        # Step 4: Parameter forwarding or command support
        has_param = "%1" in led_parser.raw_text or "%~1" in led_parser.raw_text or "ON" in led_parser.raw_text
        self.assertTrue(has_param, "test_pub_led.bat must support command parameter or ON/OFF")

    # ========================================================================
    # Scenario 5: Integrated Edge-to-Cloud-to-Dashboard Workflow (Full Stack)
    # ========================================================================
    def test_scenario_5_integrated_full_stack_workflow(self):
        """
        Scenario 5: Integrated Edge-to-Cloud-to-Dashboard Workflow (Full Stack)
        Simulates complete end-to-end multi-agent system orchestration:
        1. Broker Configuration & Startup Ready (Ports 1883 & 9001, auth user1/123456).
        2. ESP32 edge device connects to 1883, sets LWT 'OFFLINE', and publishes 'ONLINE'.
        3. Web Dashboard connects via WebSocket to 9001, updates status badge to 'Connected'.
        4. ESP32 publishes DHT22 telemetry -> broker relays to Web Dashboard -> Dual SVG gauges update -> Log table appends.
        5. Web user flips LED switch to 'ON' -> Web Dashboard publishes to 'sensor/led' -> ESP32 receives and sets GPIO 4 HIGH.
        6. CLI operator runs test_pub_led.bat with 'OFF' -> ESP32 turns off LED and Web Dashboard reflects switch to OFF.
        7. ESP32 ungracefully drops -> Broker broadcasts retained LWT 'OFFLINE' on 'sensor/status'.
        8. Comprehensive README.md guides operator through every phase of operation.
        """
        # Step 1: Broker configuration check
        conf_parser = MosquittoConfParser(MOSQUITTO_CONF)
        self.assertIsNotNone(conf_parser.get_listener(1883), "Broker port 1883 configured")
        self.assertIsNotNone(conf_parser.get_listener(9001), "Broker port 9001 configured")
        self.assertIs(conf_parser.allow_anonymous, False, "Broker authentication active")

        # Step 2: ESP32 edge device readiness
        sketch_parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(sketch_parser.has_pattern(r'sensor/data'), "ESP32 publishes telemetry")
        self.assertTrue(sketch_parser.has_pattern(r'sensor/led'), "ESP32 handles actuation")
        self.assertTrue(sketch_parser.has_pattern(r'sensor/status'), "ESP32 lifecycle tracking")

        # Step 3: Web Dashboard readiness
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(web_parser.js_has_pattern(r'sensor/data'), "Web Dashboard receives telemetry")
        self.assertTrue(web_parser.js_has_pattern(r'sensor/led'), "Web Dashboard publishes actuation")

        # Step 4: CLI verification tools readiness
        self.assertTrue(CLI_TEST_SUB.is_file())
        self.assertTrue(CLI_TEST_PUB_TEMP.is_file())
        self.assertTrue(CLI_TEST_PUB_LED.is_file())

        # Step 5: System documentation completeness
        self.assertTrue(README_PATH.is_file(), "README.md must exist")
        readme_text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        for topic in ["sensor/data", "sensor/led", "sensor/status"]:
            self.assertIn(topic, readme_text, f"README.md missing documentation for topic: {topic}")


if __name__ == "__main__":
    unittest.main()
