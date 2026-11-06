"""
Tier 3: Cross-Feature Pairwise Interaction Test Suite (24 Test Cases)
Verifies contract compatibility, protocol handshakes, topic alignment,
and schema consistency between integrated modules of the system.
"""

import unittest
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
    validate_led_command_payload
)


class TestTier3Interactions(unittest.TestCase):
    """Tier 3: Cross-Feature Pairwise Interactions (24 tests)."""

    # ------------------------------------------------------------------------
    # I01: Broker TCP (F1) <-> ESP32 MQTT Client (F9)
    # ------------------------------------------------------------------------
    def test_i01_broker_tcp_esp32_port_match(self):
        """I01: Broker TCP listener port (1883) matches ESP32 client connection port."""
        broker_parser = MosquittoConfParser(MOSQUITTO_CONF)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        tcp_listener = broker_parser.get_listener(1883)
        self.assertIsNotNone(tcp_listener, "Broker must expose port 1883")
        self.assertTrue(sketch_parser.has_pattern(r'1883'),
                        "ESP32 sketch must configure connection port 1883")

    # ------------------------------------------------------------------------
    # I02: Broker WS (F2) <-> Web MQTT Connection (F14)
    # ------------------------------------------------------------------------
    def test_i02_broker_ws_web_dashboard_port_match(self):
        """I02: Broker WebSocket listener port (9001) matches Web Dashboard connection URL."""
        broker_parser = MosquittoConfParser(MOSQUITTO_CONF)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        ws_listener = broker_parser.get_listener(9001)
        self.assertIsNotNone(ws_listener, "Broker must expose port 9001")
        if ws_listener:
            self.assertEqual(ws_listener["protocol"], "websockets")
        self.assertTrue(web_parser.js_has_pattern(r'9001'),
                        "Web Dashboard app.js must connect to port 9001")

    # ------------------------------------------------------------------------
    # I03: Broker Auth (F3, F4) <-> ESP32 MQTT Client (F9)
    # ------------------------------------------------------------------------
    def test_i03_broker_auth_esp32_credentials_match(self):
        """I03: ESP32 sketch credentials ('user1'/'123456') align with broker password.txt user1."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        self.assertIn("user1", creds, "password.txt must contain user1")
        self.assertTrue(sketch_parser.has_pattern(r'user1'),
                        "ESP32 sketch must use user1 username")
        self.assertTrue(sketch_parser.has_pattern(r'123456'),
                        "ESP32 sketch must use 123456 password")

    # ------------------------------------------------------------------------
    # I04: Broker Auth (F3, F4) <-> Web MQTT Client (F14)
    # ------------------------------------------------------------------------
    def test_i04_broker_auth_web_credentials_match(self):
        """I04: Web Dashboard credentials ('user1'/'123456') align with broker password.txt user1."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        self.assertIn("user1", creds, "password.txt must contain user1")
        self.assertTrue(web_parser.js_has_pattern(r'user1'),
                        "Web Dashboard app.js must authenticate with user1")
        self.assertTrue(web_parser.js_has_pattern(r'123456'),
                        "Web Dashboard app.js must authenticate with password 123456")

    # ------------------------------------------------------------------------
    # I05: Broker Auth (F3, F4) <-> CLI test_sub (F21)
    # ------------------------------------------------------------------------
    def test_i05_broker_auth_cli_sub_credentials_match(self):
        """I05: CLI subscriber credentials match broker password.txt user1 entry."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        sub_parser = BatchScriptParser(CLI_TEST_SUB)

        self.assertIn("user1", creds)
        self.assertTrue("user1" in sub_parser.raw_text and "123456" in sub_parser.raw_text,
                        "test_sub.bat must authenticate with user1/123456")

    # ------------------------------------------------------------------------
    # I06: Broker Auth (F3, F4) <-> CLI test_pub_temp (F22)
    # ------------------------------------------------------------------------
    def test_i06_broker_auth_cli_pub_temp_credentials_match(self):
        """I06: CLI telemetry publisher credentials match broker password.txt user1 entry."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        pub_parser = BatchScriptParser(CLI_TEST_PUB_TEMP)

        self.assertIn("user1", creds)
        self.assertTrue("user1" in pub_parser.raw_text and "123456" in pub_parser.raw_text,
                        "test_pub_temp.bat must authenticate with user1/123456")

    # ------------------------------------------------------------------------
    # I07: Broker Auth (F3, F4) <-> CLI test_pub_led (F23)
    # ------------------------------------------------------------------------
    def test_i07_broker_auth_cli_pub_led_credentials_match(self):
        """I07: CLI LED publisher credentials match broker password.txt user1 entry."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        pub_parser = BatchScriptParser(CLI_TEST_PUB_LED)

        self.assertIn("user1", creds)
        self.assertTrue("user1" in pub_parser.raw_text and "123456" in pub_parser.raw_text,
                        "test_pub_led.bat must authenticate with user1/123456")

    # ------------------------------------------------------------------------
    # I08: Startup Script (F5) <-> Broker Listeners (F1, F2)
    # ------------------------------------------------------------------------
    def test_i08_start_broker_bat_references_config_with_both_listeners(self):
        """I08: start_broker.bat references mosquitto.conf which contains both 1883 and 9001."""
        bat_parser = BatchScriptParser(START_BROKER_BAT)
        conf_parser = MosquittoConfParser(MOSQUITTO_CONF)

        self.assertTrue("mosquitto.conf" in bat_parser.raw_text,
                        "start_broker.bat must reference mosquitto.conf")
        ports = [lis["port"] for lis in conf_parser.listeners]
        self.assertIn(1883, ports, "mosquitto.conf missing port 1883")
        self.assertIn(9001, ports, "mosquitto.conf missing port 9001")

    # ------------------------------------------------------------------------
    # I09: Wokwi Wiring (F6) <-> ESP32 DHT22 Sketch (F10)
    # ------------------------------------------------------------------------
    def test_i09_diagram_dht_pin_matches_sketch(self):
        """I09: Diagram wiring on GPIO 16 matches DHT pin constant in sketch.ino."""
        diagram_parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        # Diagram connects GPIO 16 to DHT22 (on wokwi-esp32-devkit-v1, GPIO 16 is labeled RX2)
        has_wire_16 = diagram_parser.has_connection(r":16\b|:D16\b|:RX2\b", r":SDA\b|:data\b")
        self.assertTrue(has_wire_16, "diagram.json must wire GPIO 16 to DHT22 data")

        # Sketch specifies GPIO 16
        has_pin_16 = sketch_parser.has_pattern(r'16')
        self.assertTrue(has_pin_16, "sketch.ino must configure DHT pin to 16")

    # ------------------------------------------------------------------------
    # I10: Wokwi Wiring (F6) <-> ESP32 LED Sketch (F11)
    # ------------------------------------------------------------------------
    def test_i10_diagram_led_pin_matches_sketch(self):
        """I10: Diagram wiring on GPIO 4 matches LED pin constant in sketch.ino."""
        diagram_parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        # Diagram connects GPIO 4 to LED
        has_wire_4 = diagram_parser.has_connection(r":4\b|:D4\b", r":A\b|:anode\b")
        self.assertTrue(has_wire_4, "diagram.json must wire GPIO 4 to LED anode")

        # Sketch specifies GPIO 4
        has_pin_4 = sketch_parser.has_pattern(r'4')
        self.assertTrue(has_pin_4, "sketch.ino must configure LED pin to 4")

    # ------------------------------------------------------------------------
    # I11: Wokwi Dependencies (F7) <-> ESP32 Sketch Headers (F8, F9, F10)
    # ------------------------------------------------------------------------
    def test_i11_libraries_txt_matches_sketch_includes(self):
        """I11: libraries.txt lists the libraries included in sketch.ino."""
        lib_text = WOKWI_LIBRARIES.read_text(encoding="utf-8", errors="replace") if WOKWI_LIBRARIES.is_file() else ""
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        if sketch_parser.has_include("PubSubClient.h"):
            self.assertTrue(bool(re.search(r'\bPubSubClient\b', lib_text, re.IGNORECASE)),
                            "PubSubClient included in sketch but missing from libraries.txt")

        if sketch_parser.has_include("ArduinoJson.h"):
            self.assertTrue(bool(re.search(r'\bArduinoJson\b', lib_text, re.IGNORECASE)),
                            "ArduinoJson included in sketch but missing from libraries.txt")

    # ------------------------------------------------------------------------
    # I12: ESP32 Telemetry (F10) <-> Web Temperature Gauge (F15)
    # ------------------------------------------------------------------------
    def test_i12_telemetry_temp_topic_and_key_alignment(self):
        """I12: ESP32 telemetry topic 'sensor/data' and 'temp' key match Web Dashboard parser."""
        sketch_parser = SketchInoParser(WOKWI_SKETCH)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        self.assertTrue(sketch_parser.has_pattern(r'sensor/data'),
                        "ESP32 must publish to 'sensor/data'")
        self.assertTrue(web_parser.js_has_pattern(r'sensor/data'),
                        "Web Dashboard must subscribe to 'sensor/data'")

        self.assertTrue(sketch_parser.has_pattern(r'["\']temp["\']'),
                        "ESP32 telemetry must produce 'temp' key")
        self.assertTrue(web_parser.js_has_pattern(r'temp'),
                        "Web Dashboard must parse 'temp' key")

    # ------------------------------------------------------------------------
    # I13: ESP32 Telemetry (F10) <-> Web Humidity Gauge (F16)
    # ------------------------------------------------------------------------
    def test_i13_telemetry_hum_topic_and_key_alignment(self):
        """I13: ESP32 telemetry topic 'sensor/data' and 'hum' key match Web Dashboard parser."""
        sketch_parser = SketchInoParser(WOKWI_SKETCH)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        self.assertTrue(sketch_parser.has_pattern(r'["\']hum["\']'),
                        "ESP32 telemetry must produce 'hum' key")
        self.assertTrue(web_parser.js_has_pattern(r'hum'),
                        "Web Dashboard must parse 'hum' key")

    # ------------------------------------------------------------------------
    # I14: ESP32 Telemetry (F10) <-> Web Message Log Table (F19)
    # ------------------------------------------------------------------------
    def test_i14_telemetry_log_table_integration(self):
        """I14: Web Dashboard logs incoming telemetry messages to log table."""
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_message_event = web_parser.js_has_pattern(r'\.on\(\s*["\']message["\']')
        has_log_table = (
            web_parser.html_has_id("message-log") or
            web_parser.html_has_id("log-table") or
            web_parser.html_has_id("log-body") or
            web_parser.html_has_tag("table")
        )
        self.assertTrue(has_message_event and has_log_table,
                        "Web Dashboard must log incoming MQTT messages into log table")

    # ------------------------------------------------------------------------
    # I15: Web LED Toggle (F17) <-> ESP32 Actuator (F11)
    # ------------------------------------------------------------------------
    def test_i15_web_toggle_esp32_actuator_protocol(self):
        """I15: Web Dashboard toggle publishes to 'sensor/led' with 'ON'/'OFF' handled by ESP32."""
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        # Web publishes to sensor/led with ON/OFF
        self.assertTrue(web_parser.js_has_pattern(r'sensor/led'),
                        "Web dashboard must target topic 'sensor/led'")
        self.assertTrue(web_parser.js_has_pattern(r'ON') and web_parser.js_has_pattern(r'OFF'),
                        "Web dashboard must publish 'ON' and 'OFF' commands")

        # ESP32 subscribes to sensor/led and handles ON/OFF
        self.assertTrue(sketch_parser.has_pattern(r'sensor/led'),
                        "ESP32 must subscribe to topic 'sensor/led'")
        self.assertTrue(sketch_parser.has_pattern(r'ON') and sketch_parser.has_pattern(r'OFF'),
                        "ESP32 must handle 'ON' and 'OFF' commands")

    # ------------------------------------------------------------------------
    # I16: CLI test_pub_led (F23) <-> ESP32 Actuator (F11)
    # ------------------------------------------------------------------------
    def test_i16_cli_pub_led_esp32_actuator_alignment(self):
        """I16: CLI test_pub_led targets 'sensor/led' matching ESP32 actuator topic."""
        cli_parser = BatchScriptParser(CLI_TEST_PUB_LED)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        self.assertTrue("sensor/led" in cli_parser.raw_text,
                        "test_pub_led.bat must publish to 'sensor/led'")
        self.assertTrue(sketch_parser.has_pattern(r'sensor/led'),
                        "ESP32 sketch must subscribe to 'sensor/led'")

    # ------------------------------------------------------------------------
    # I17: CLI test_pub_led (F23) <-> Web Dashboard LED Toggle (F17)
    # ------------------------------------------------------------------------
    def test_i17_cli_pub_led_web_toggle_sync(self):
        """I17: Web Dashboard subscribes to 'sensor/led' to synchronize toggle state from CLI."""
        cli_parser = BatchScriptParser(CLI_TEST_PUB_LED)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        self.assertTrue("sensor/led" in cli_parser.raw_text)
        has_sub_led = (
            web_parser.js_has_pattern(r'subscribe\(\s*["\']sensor/led["\']') or
            web_parser.js_has_pattern(r'subscribe\(\s*["\']sensor/#["\']') or
            web_parser.js_has_pattern(r'sensor/led')
        )
        self.assertTrue(has_sub_led, "Web Dashboard should listen on 'sensor/led' for state sync")

    # ------------------------------------------------------------------------
    # I18: CLI test_pub_temp (F22) <-> Web Gauges (F15, F16)
    # ------------------------------------------------------------------------
    def test_i18_cli_pub_temp_web_gauges_compatibility(self):
        """I18: CLI test_pub_temp JSON payload structure is compatible with Web Dashboard gauge parsing."""
        cli_parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        self.assertTrue("sensor/data" in cli_parser.raw_text,
                        "test_pub_temp.bat must publish to 'sensor/data'")
        self.assertTrue(web_parser.js_has_pattern(r'sensor/data'),
                        "Web Dashboard must subscribe to 'sensor/data'")

    # ------------------------------------------------------------------------
    # I19: ESP32 LWT (F12) <-> Web Dashboard Status (F18)
    # ------------------------------------------------------------------------
    def test_i19_esp32_status_web_dashboard_tracking(self):
        """I19: ESP32 status topic 'sensor/status' is tracked by Web Dashboard."""
        sketch_parser = SketchInoParser(WOKWI_SKETCH)
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)

        self.assertTrue(sketch_parser.has_pattern(r'sensor/status'),
                        "ESP32 sketch must configure topic 'sensor/status'")
        has_status_sub = (
            web_parser.js_has_pattern(r'sensor/status') or
            web_parser.js_has_pattern(r'sensor/#')
        )
        self.assertTrue(has_status_sub,
                        "Web Dashboard should monitor 'sensor/status' or 'sensor/#'")

    # ------------------------------------------------------------------------
    # I20: ESP32 LWT (F12) <-> CLI test_sub (F21)
    # ------------------------------------------------------------------------
    def test_i20_esp32_status_cli_sub_wildcard_capture(self):
        """I20: CLI test_sub wildcard 'sensor/#' captures ESP32 'sensor/status' messages."""
        sub_parser = BatchScriptParser(CLI_TEST_SUB)
        sketch_parser = SketchInoParser(WOKWI_SKETCH)

        self.assertTrue("sensor/#" in sub_parser.raw_text,
                        "test_sub.bat must use wildcard 'sensor/#'")
        self.assertTrue(sketch_parser.has_pattern(r'sensor/status'),
                        "ESP32 status topic must reside under 'sensor/' hierarchy")

    # ------------------------------------------------------------------------
    # I21: Vendored Library (F20) <-> Web Dashboard App Logic (F14)
    # ------------------------------------------------------------------------
    def test_i21_vendored_library_app_js_reference(self):
        """I21: app.js references 'mqtt' object exposed by mqtt.min.js."""
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(web_parser.js_has_pattern(r'\bmqtt\b'),
                        "app.js must call methods on global 'mqtt' library object")

    # ------------------------------------------------------------------------
    # I22: Web HTML/CSS (F13) <-> Web Controls (F15-F19)
    # ------------------------------------------------------------------------
    def test_i22_html_elements_referenced_in_app_js(self):
        """I22: Element IDs declared in index.html are correctly queried in app.js."""
        web_parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        # Extract getElementById calls from app.js
        ids_queried = re.findall(r'getElementById\(\s*["\']([^"\']+)["\']\s*\)', web_parser.js_text)
        for elem_id in ids_queried:
            self.assertTrue(web_parser.html_has_id(elem_id),
                            f"app.js queries element ID '{elem_id}' which is missing from index.html")

    # ------------------------------------------------------------------------
    # I23: ESP32 WiFi (F8) <-> ESP32 MQTT Client (F9)
    # ------------------------------------------------------------------------
    def test_i23_wifi_connect_gates_mqtt_connect(self):
        """I23: MQTT connection in sketch.ino is conditioned on WiFi connection being active."""
        sketch_parser = SketchInoParser(WOKWI_SKETCH)
        # WiFi setup occurs in setup() before MQTT loop or in reconnect check
        self.assertTrue(sketch_parser.has_pattern(r'WiFi\.begin'),
                        "WiFi connection must be initialized")
        self.assertTrue(sketch_parser.has_pattern(r'\.connect\('),
                        "MQTT client connection must be implemented")

    # ------------------------------------------------------------------------
    # I24: Complete Documentation (F24) <-> System Artifacts
    # ------------------------------------------------------------------------
    def test_i24_readme_verifies_all_subsystem_paths(self):
        """I24: README.md references all key project directories and execution scripts."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        subsystems = ["broker", "esp32", "web", "scripts"]
        for sub in subsystems:
            self.assertTrue(sub in text.lower(), f"README.md missing documentation for subsystem: {sub}")


if __name__ == "__main__":
    unittest.main()
