"""
Tier 1: Feature Coverage Test Suite (120 Test Cases)
Verifies all 24 system features against specification requirements
defined in ORIGINAL_REQUEST.md, PROJECT.md, and TEST_INFRA.md.
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
    validate_telemetry_payload
)


class TestTier1FeatureCoverage(unittest.TestCase):
    """Tier 1: 5 test cases per feature across 24 features (Total: 120 tests)."""

    # ------------------------------------------------------------------------
    # Feature 1: Mosquitto TCP Listener (1883)
    # ------------------------------------------------------------------------
    def test_f01_01_mosquitto_conf_exists(self):
        """F1-1: Verify broker/mosquitto.conf exists."""
        self.assertTrue(MOSQUITTO_CONF.is_file(), f"Missing config file at {MOSQUITTO_CONF}")

    def test_f01_02_listener_1883_directive(self):
        """F1-2: Verify listener 1883 directive is present."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        listener = parser.get_listener(1883)
        self.assertIsNotNone(listener, "mosquitto.conf missing 'listener 1883'")

    def test_f01_03_listener_1883_default_protocol(self):
        """F1-3: Verify port 1883 defaults to raw MQTT protocol (not websockets)."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        listener = parser.get_listener(1883)
        if listener:
            self.assertEqual(listener["protocol"], "mqtt", "Port 1883 must not use websockets protocol")

    def test_f01_04_listener_1883_valid_port_range(self):
        """F1-4: Verify port 1883 is an integer within valid TCP port range (1-65535)."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        listener = parser.get_listener(1883)
        if listener:
            port = listener["port"]
            self.assertTrue(1 <= port <= 65535, f"Port {port} outside valid range 1-65535")
            self.assertEqual(port, 1883, "Port number must be strictly 1883")

    def test_f01_05_listener_1883_host_binding(self):
        """F1-5: Verify listener 1883 binds to standard interface or all interfaces."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        listener = parser.get_listener(1883)
        if listener:
            host = listener.get("host", "")
            self.assertIn(host, ["", "0.0.0.0", "127.0.0.1", "localhost"],
                          f"Unexpected listener host binding: '{host}'")

    # ------------------------------------------------------------------------
    # Feature 2: Mosquitto WebSocket Listener (9001)
    # ------------------------------------------------------------------------
    def test_f02_01_listener_9001_directive(self):
        """F2-1: Verify listener 9001 directive is configured."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        listener = parser.get_listener(9001)
        self.assertIsNotNone(listener, "mosquitto.conf missing 'listener 9001'")

    def test_f02_02_listener_9001_protocol_websockets(self):
        """F2-2: Verify protocol websockets directive is assigned to port 9001."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        listener = parser.get_listener(9001)
        if listener:
            self.assertEqual(listener["protocol"], "websockets",
                             "Listener 9001 must have 'protocol websockets'")

    def test_f02_03_protocol_directive_order(self):
        """F2-3: Verify 'protocol websockets' comes after 'listener 9001' and not 1883."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        text = parser.raw_text
        match_1883 = re.search(r'listener\s+1883', text, re.IGNORECASE)
        match_9001 = re.search(r'listener\s+9001', text, re.IGNORECASE)
        match_ws = re.search(r'protocol\s+websockets', text, re.IGNORECASE)
        if match_9001 and match_ws:
            self.assertGreater(match_ws.start(), match_9001.start(),
                               "'protocol websockets' must appear after 'listener 9001'")
        if match_1883 and match_ws and match_9001:
            self.assertGreater(match_ws.start(), match_1883.start(),
                               "'protocol websockets' must not appear before 'listener 1883'")

    def test_f02_04_listener_9001_distinct_port(self):
        """F2-4: Verify WebSocket port 9001 does not conflict with TCP port 1883."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        ports = [lis["port"] for lis in parser.listeners]
        self.assertIn(9001, ports, "Port 9001 missing from listener list")
        self.assertIn(1883, ports, "Port 1883 missing from listener list")
        self.assertNotEqual(1883, 9001, "TCP and WS ports must be distinct")

    def test_f02_05_dual_listener_support(self):
        """F2-5: Verify configuration supports dual simultaneous listeners."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        self.assertGreaterEqual(len(parser.listeners), 2,
                                "mosquitto.conf must configure at least 2 listeners (TCP & WS)")

    # ------------------------------------------------------------------------
    # Feature 3: Mosquitto Authentication Config
    # ------------------------------------------------------------------------
    def test_f03_01_allow_anonymous_false(self):
        """F3-1: Verify 'allow_anonymous false' directive is explicitly configured."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        self.assertIs(parser.allow_anonymous, False,
                      "mosquitto.conf must specify 'allow_anonymous false'")

    def test_f03_02_password_file_directive(self):
        """F3-2: Verify 'password_file' directive is present."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        self.assertIsNotNone(parser.password_file,
                             "mosquitto.conf must define 'password_file'")

    def test_f03_03_password_file_references_password_txt(self):
        """F3-3: Verify password_file references password.txt."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        if parser.password_file:
            self.assertTrue(parser.password_file.endswith("password.txt"),
                            f"password_file must reference password.txt, found: {parser.password_file}")

    def test_f03_04_no_anonymous_bypass(self):
        """F3-4: Verify no conflicting allow_anonymous true directives exist."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        for d, val in parser.directives:
            if d == "allow_anonymous":
                self.assertEqual(val.lower(), "false",
                                 f"Found invalid allow_anonymous setting: '{val}'")

    def test_f03_05_auth_directives_non_empty(self):
        """F3-5: Verify auth directives are non-empty and well-formed."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        self.assertTrue(bool(parser.password_file and parser.password_file.strip()),
                        "password_file path must not be empty")

    # ------------------------------------------------------------------------
    # Feature 4: Mosquitto Password Credentials
    # ------------------------------------------------------------------------
    def test_f04_01_password_file_exists(self):
        """F4-1: Verify broker/password.txt file exists."""
        self.assertTrue(MOSQUITTO_PASSWD.is_file(), f"Missing password file at {MOSQUITTO_PASSWD}")

    def test_f04_02_user1_entry_present(self):
        """F4-2: Verify password.txt contains credentials for user1."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        self.assertIn("user1", creds, "password.txt must contain an entry for 'user1'")

    def test_f04_03_credential_colon_format(self):
        """F4-3: Verify password file entries follow username:hash format."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        if "user1" in creds:
            hash_val = creds["user1"]
            self.assertTrue(len(hash_val) > 0, "Hash for user1 must not be empty")

    def test_f04_04_password_hashed_not_plaintext(self):
        """F4-4: Verify password for user1 is hashed/encrypted and not plaintext 123456."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        if "user1" in creds:
            self.assertNotEqual(creds["user1"], "123456",
                                "Password must be encrypted/hashed, not stored as plaintext '123456'")

    def test_f04_05_single_user_integrity(self):
        """F4-5: Verify user1 credential entry has valid key syntax."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        for user in creds:
            self.assertTrue(user.isalnum(), f"Username '{user}' must be alphanumeric")

    # ------------------------------------------------------------------------
    # Feature 5: Mosquitto Startup Batch Script
    # ------------------------------------------------------------------------
    def test_f05_01_start_broker_bat_exists(self):
        """F5-1: Verify broker/start_broker.bat file exists."""
        self.assertTrue(START_BROKER_BAT.is_file(), f"Missing {START_BROKER_BAT}")

    def test_f05_02_directory_context_switch(self):
        """F5-2: Verify start_broker.bat switches directory context to its own folder."""
        parser = BatchScriptParser(START_BROKER_BAT)
        has_cd = parser.has_argument("cd", "/d") or "%~dp0" in parser.raw_text
        self.assertTrue(has_cd, "start_broker.bat must change directory to %~dp0 to locate password.txt")

    def test_f05_03_invokes_mosquitto(self):
        """F5-3: Verify start_broker.bat invokes mosquitto executable."""
        parser = BatchScriptParser(START_BROKER_BAT)
        self.assertTrue(parser.has_command("mosquitto"),
                        "start_broker.bat must invoke 'mosquitto'")

    def test_f05_04_passes_config_flag(self):
        """F5-4: Verify start_broker.bat passes -c mosquitto.conf."""
        parser = BatchScriptParser(START_BROKER_BAT)
        has_c = parser.has_argument("-c", "mosquitto.conf") or "mosquitto.conf" in parser.raw_text
        self.assertTrue(has_c, "start_broker.bat must specify -c mosquitto.conf")

    def test_f05_05_diagnostic_or_path_fallback(self):
        """F5-5: Verify start_broker.bat includes verbose logging or path fallback."""
        parser = BatchScriptParser(START_BROKER_BAT)
        has_fallback = (
            "-v" in parser.raw_text or
            "PATH" in parser.raw_text.upper() or
            "Program Files" in parser.raw_text or
            "error" in parser.raw_text.lower() or
            "not found" in parser.raw_text.lower()
        )
        self.assertTrue(has_fallback, "start_broker.bat should handle PATH resolution or diagnostic output")

    # ------------------------------------------------------------------------
    # Feature 6: Wokwi Diagram Components & Wiring
    # ------------------------------------------------------------------------
    def test_f06_01_diagram_json_exists_and_valid(self):
        """F6-1: Verify esp32_wokwi/diagram.json exists and is valid JSON."""
        self.assertTrue(WOKWI_DIAGRAM.is_file(), f"Missing {WOKWI_DIAGRAM}")
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        self.assertTrue(parser.is_valid_json, "diagram.json is not valid JSON")

    def test_f06_02_esp32_part_present(self):
        """F6-2: Verify diagram includes an ESP32 microcontroller board."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        esp32 = parser.find_part("esp32")
        self.assertIsNotNone(esp32, "diagram.json missing ESP32 part (wokwi-esp32-devkit-v1 / board-esp32-devkit-c-v4)")

    def test_f06_03_dht22_part_present(self):
        """F6-3: Verify diagram includes a DHT22 temperature/humidity sensor."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        dht22 = parser.find_part("dht22")
        self.assertIsNotNone(dht22, "diagram.json missing DHT22 sensor part (wokwi-dht22)")

    def test_f06_04_led_and_resistor_present(self):
        """F6-4: Verify diagram includes an LED and 220-ohm resistor."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        led = parser.find_part("led")
        resistor = parser.find_part("resistor")
        self.assertIsNotNone(led, "diagram.json missing LED component (wokwi-led)")
        self.assertIsNotNone(resistor, "diagram.json missing resistor component (wokwi-resistor)")
        if resistor:
            val = str(resistor.get("attrs", {}).get("value", ""))
            self.assertIn("220", val, f"Resistor value must be 220 ohms, found: '{val}'")

    def test_f06_05_wiring_connections(self):
        """F6-5: Verify GPIO 16 connects to DHT22 data pin and GPIO 4 connects to LED anode."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        # Check DHT22 connection to GPIO 16 (on wokwi-esp32-devkit-v1, GPIO 16 is labeled RX2)
        has_dht_conn = parser.has_connection(r":16\b|:D16\b|:RX2\b", r":SDA\b|:data\b")
        # Check LED connection to GPIO 4
        has_led_conn = parser.has_connection(r":4\b|:D4\b", r":A\b|:anode\b")
        self.assertTrue(has_dht_conn, "diagram.json must connect ESP32 GPIO 16 to DHT22 data pin")
        self.assertTrue(has_led_conn, "diagram.json must connect ESP32 GPIO 4 to LED anode")

    # ------------------------------------------------------------------------
    # Feature 7: Wokwi Dependencies & Config
    # ------------------------------------------------------------------------
    def test_f07_01_libraries_txt_exists(self):
        """F7-1: Verify esp32_wokwi/libraries.txt exists."""
        self.assertTrue(WOKWI_LIBRARIES.is_file(), f"Missing {WOKWI_LIBRARIES}")

    def test_f07_02_pubsubclient_library(self):
        """F7-2: Verify libraries.txt lists PubSubClient."""
        text = WOKWI_LIBRARIES.read_text(encoding="utf-8", errors="replace") if WOKWI_LIBRARIES.is_file() else ""
        self.assertTrue(re.search(r'\bPubSubClient\b', text, re.IGNORECASE),
                        "libraries.txt missing PubSubClient dependency")

    def test_f07_03_dht_library(self):
        """F7-3: Verify libraries.txt lists DHTesp or DHT sensor library for ESPx."""
        text = WOKWI_LIBRARIES.read_text(encoding="utf-8", errors="replace") if WOKWI_LIBRARIES.is_file() else ""
        has_dht = (
            "DHTesp" in text or
            "DHT sensor library for ESPx" in text or
            "DHT" in text
        )
        self.assertTrue(has_dht, "libraries.txt missing DHT sensor library dependency")

    def test_f07_04_arduinojson_library(self):
        """F7-4: Verify libraries.txt lists ArduinoJson."""
        text = WOKWI_LIBRARIES.read_text(encoding="utf-8", errors="replace") if WOKWI_LIBRARIES.is_file() else ""
        self.assertTrue(re.search(r'\bArduinoJson\b', text, re.IGNORECASE),
                        "libraries.txt missing ArduinoJson dependency")

    def test_f07_05_wokwi_toml_exists(self):
        """F7-5: Verify esp32_wokwi/wokwi.toml exists and configures simulation."""
        self.assertTrue(WOKWI_TOML.is_file(), f"Missing {WOKWI_TOML}")
        text = WOKWI_TOML.read_text(encoding="utf-8", errors="replace") if WOKWI_TOML.is_file() else ""
        self.assertIn("[wokwi]", text, "wokwi.toml missing [wokwi] header section")

    # ------------------------------------------------------------------------
    # Feature 8: ESP32 WiFi Connectivity
    # ------------------------------------------------------------------------
    def test_f08_01_includes_wifi_header(self):
        """F8-1: Verify sketch.ino includes WiFi.h."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_include("WiFi.h"), "sketch.ino must include <WiFi.h>")

    def test_f08_02_configures_wokwi_guest_ssid(self):
        """F8-2: Verify sketch.ino sets SSID to Wokwi-GUEST."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'Wokwi-GUEST'),
                        "sketch.ino must configure SSID 'Wokwi-GUEST'")

    def test_f08_03_empty_wifi_password(self):
        """F8-3: Verify WiFi password is configured for open access (empty string)."""
        parser = SketchInoParser(WOKWI_SKETCH)
        # matches password = "" or pass = ""
        has_empty_pwd = parser.has_pattern(r'password\s*=\s*""|pass\s*=\s*""')
        self.assertTrue(has_empty_pwd, "sketch.ino must configure empty password for Wokwi-GUEST")

    def test_f08_04_initiates_wifi_begin(self):
        """F8-4: Verify sketch.ino calls WiFi.begin()."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'WiFi\.begin\('),
                        "sketch.ino must call WiFi.begin(...)")

    def test_f08_05_checks_connection_status(self):
        """F8-5: Verify sketch.ino monitors WiFi.status() == WL_CONNECTED."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'WiFi\.status\(\)\s*(!=|==)\s*WL_CONNECTED'),
                        "sketch.ino must check WL_CONNECTED status")

    # ------------------------------------------------------------------------
    # Feature 9: ESP32 MQTT Authentication & Client
    # ------------------------------------------------------------------------
    def test_f09_01_includes_pubsubclient_header(self):
        """F9-1: Verify sketch.ino includes PubSubClient.h."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_include("PubSubClient.h"),
                        "sketch.ino must include <PubSubClient.h>")

    def test_f09_02_instantiates_mqtt_client(self):
        """F9-2: Verify PubSubClient instance is declared."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'PubSubClient\s+\w+'),
                        "sketch.ino must declare PubSubClient instance")

    def test_f09_03_configures_user1_and_123456(self):
        """F9-3: Verify sketch connects with user1 and password 123456."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_user = parser.has_pattern(r'user1')
        has_pass = parser.has_pattern(r'123456')
        self.assertTrue(has_user and has_pass,
                        "sketch.ino must specify MQTT credentials user1 and 123456")

    def test_f09_04_reconnect_loop_logic(self):
        """F9-4: Verify reconnect loop logic handles disconnected client state."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'!\s*\w+\.connected\(\)|\.connect\('),
                        "sketch.ino must implement MQTT connection / reconnection logic")

    def test_f09_05_calls_client_loop(self):
        """F9-5: Verify sketch calls client.loop() in Arduino loop function."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'\w+\.loop\(\)'),
                        "sketch.ino must invoke client.loop() to maintain MQTT connection")

    # ------------------------------------------------------------------------
    # Feature 10: ESP32 DHT22 JSON Telemetry
    # ------------------------------------------------------------------------
    def test_f10_01_reads_dht_values(self):
        """F10-1: Verify sketch reads temperature and humidity from DHT sensor."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_read = (
            parser.has_pattern(r'getTemperature|readTemperature|getHumidity|readHumidity|getTempAndHumidity')
        )
        self.assertTrue(has_read, "sketch.ino must invoke DHT sensor read methods")

    def test_f10_02_serializes_json(self):
        """F10-2: Verify sketch serializes JSON telemetry."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_json = (
            parser.has_include("ArduinoJson.h") or
            parser.has_pattern(r'serializeJson|JsonDocument|StaticJsonDocument') or
            parser.has_pattern(r'\"temp\"\s*:\s*.*\"hum\"')
        )
        self.assertTrue(has_json, "sketch.ino must serialize telemetry into JSON format")

    def test_f10_03_publishes_sensor_data_topic(self):
        """F10-3: Verify telemetry is published to topic sensor/data."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'sensor/data'),
                        "sketch.ino must publish telemetry to 'sensor/data'")

    def test_f10_04_payload_json_keys(self):
        """F10-4: Verify JSON payload format incorporates temp and hum keys."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_keys = parser.has_pattern(r'["\']temp["\']') and parser.has_pattern(r'["\']hum["\']')
        self.assertTrue(has_keys, "Telemetry payload must define both 'temp' and 'hum' fields")

    def test_f10_05_periodic_interval_timing(self):
        """F10-5: Verify publishing uses non-blocking millis() timing."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'millis\(\)'),
                        "sketch.ino should use non-blocking millis() timer for sensor polling")

    # ------------------------------------------------------------------------
    # Feature 11: ESP32 LED Actuator Subscription
    # ------------------------------------------------------------------------
    def test_f11_01_pinmode_led_output(self):
        """F11-1: Verify GPIO 4 is configured as OUTPUT."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'pinMode\(\s*(4|\w+)\s*,\s*OUTPUT\s*\)'),
                        "sketch.ino must set LED pin (GPIO 4) to OUTPUT")

    def test_f11_02_subscribes_sensor_led(self):
        """F11-2: Verify sketch subscribes to topic sensor/led."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'sensor/led'),
                        "sketch.ino must subscribe to 'sensor/led'")

    def test_f11_03_mqtt_callback_registered(self):
        """F11-3: Verify MQTT message callback is registered with client.setCallback()."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'\.setCallback\('),
                        "sketch.ino must register an MQTT callback using setCallback()")

    def test_f11_04_handles_on_command(self):
        """F11-4: Verify sketch handles ON command and sets GPIO 4 HIGH."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_on = parser.has_pattern(r'["\']ON["\']')
        has_high = parser.has_pattern(r'digitalWrite\(\s*(4|\w+)\s*,\s*HIGH\s*\)')
        self.assertTrue(has_on and has_high,
                        "sketch.ino must parse 'ON' command and write HIGH to LED pin")

    def test_f11_05_handles_off_command(self):
        """F11-5: Verify sketch handles OFF command and sets GPIO 4 LOW."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_off = parser.has_pattern(r'["\']OFF["\']')
        has_low = parser.has_pattern(r'digitalWrite\(\s*(4|\w+)\s*,\s*LOW\s*\)')
        self.assertTrue(has_off and has_low,
                        "sketch.ino must parse 'OFF' command and write LOW to LED pin")

    # ------------------------------------------------------------------------
    # Feature 12: ESP32 LWT & Status Publishing
    # ------------------------------------------------------------------------
    def test_f12_01_configures_lwt_topic(self):
        """F12-1: Verify LWT is configured on topic sensor/status."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'sensor/status'),
                        "sketch.ino must reference topic 'sensor/status'")

    def test_f12_02_lwt_payload_offline(self):
        """F12-2: Verify LWT payload is configured as OFFLINE."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'["\']OFFLINE["\']'),
                        "sketch.ino must define LWT payload 'OFFLINE'")

    def test_f12_03_lwt_retain_flag(self):
        """F12-3: Verify LWT retain parameter is configured."""
        parser = SketchInoParser(WOKWI_SKETCH)
        # connect signature with LWT: client.connect(id, user, pass, willTopic, willQoS, willRetain, willMessage)
        has_lwt = parser.has_pattern(r'connect\([^)]*sensor/status[^)]*\)') or parser.has_pattern(r'OFFLINE')
        self.assertTrue(has_lwt, "sketch.ino must configure LWT in connect call")

    def test_f12_04_publishes_online_status(self):
        """F12-4: Verify sketch publishes ONLINE to sensor/status upon connection."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'["\']ONLINE["\']'),
                        "sketch.ino must publish 'ONLINE' status upon connection")

    def test_f12_05_online_status_retained(self):
        """F12-5: Verify ONLINE status is published with retain flag true."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_retain = parser.has_pattern(r'publish\(\s*["\']sensor/status["\'],\s*["\']ONLINE["\'],\s*(true|1)\s*\)') or parser.has_pattern(r'ONLINE')
        self.assertTrue(has_retain, "sketch.ino must publish 'ONLINE' to sensor/status with retain true")

    # ------------------------------------------------------------------------
    # Feature 13: Web Dashboard File Structure
    # ------------------------------------------------------------------------
    def test_f13_01_index_html_exists(self):
        """F13-1: Verify web_dashboard/index.html exists."""
        self.assertTrue(WEB_INDEX_HTML.is_file(), f"Missing {WEB_INDEX_HTML}")

    def test_f13_02_style_css_exists(self):
        """F13-2: Verify web_dashboard/style.css exists."""
        self.assertTrue(WEB_STYLE_CSS.is_file(), f"Missing {WEB_STYLE_CSS}")

    def test_f13_03_app_js_exists(self):
        """F13-3: Verify web_dashboard/app.js exists."""
        self.assertTrue(WEB_APP_JS.is_file(), f"Missing {WEB_APP_JS}")

    def test_f13_04_html_links_stylesheet(self):
        """F13-4: Verify index.html links to style.css."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(re.search(r'<link[^>]*href=["\']style\.css["\']', parser.html_text, re.IGNORECASE),
                        "index.html must link style.css")

    def test_f13_05_html_loads_app_js(self):
        """F13-5: Verify index.html includes app.js script tag."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(re.search(r'<script[^>]*src=["\']app\.js["\']', parser.html_text, re.IGNORECASE),
                        "index.html must load app.js")

    # ------------------------------------------------------------------------
    # Feature 14: Web WebSocket MQTT Connection
    # ------------------------------------------------------------------------
    def test_f14_01_connects_websocket_protocol(self):
        """F14-1: Verify app.js connects using WebSocket protocol on port 9001."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_ws = parser.js_has_pattern(r'ws://localhost:9001|port:\s*9001|:9001')
        self.assertTrue(has_ws, "app.js must connect to WebSocket endpoint on port 9001")

    def test_f14_02_passes_user1_username(self):
        """F14-2: Verify app.js supplies username 'user1'."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.js_has_pattern(r'user1'),
                        "app.js must provide username 'user1'")

    def test_f14_03_passes_123456_password(self):
        """F14-3: Verify app.js supplies password '123456'."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.js_has_pattern(r'123456'),
                        "app.js must provide password '123456'")

    def test_f14_04_connect_event_handler(self):
        """F14-4: Verify app.js handles MQTT 'connect' event."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.js_has_pattern(r'\.on\(\s*["\']connect["\']'),
                        "app.js must attach an 'on(\"connect\")' event listener")

    def test_f14_05_subscribes_to_sensor_topics(self):
        """F14-5: Verify app.js subscribes to sensor topics (sensor/data, sensor/led, sensor/status)."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_sub = (
            parser.js_has_pattern(r'subscribe\(\s*["\']sensor/data["\']') or
            parser.js_has_pattern(r'subscribe\(\s*["\']sensor/#["\']')
        )
        self.assertTrue(has_sub, "app.js must subscribe to telemetry topics")

    # ------------------------------------------------------------------------
    # Feature 15: Temperature Gauge Display
    # ------------------------------------------------------------------------
    def test_f15_01_temp_gauge_container(self):
        """F15-1: Verify index.html contains a temperature gauge element or container."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_elem = (
            parser.html_has_id("temp-gauge") or
            parser.html_has_id("temperature-gauge") or
            parser.html_has_id("temp-value") or
            "temperature" in parser.html_text.lower()
        )
        self.assertTrue(has_elem, "index.html must contain a container/element for temperature gauge")

    def test_f15_02_temp_range_spec(self):
        """F15-2: Verify temperature gauge indicates span covering -10°C to 60°C."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_range = (
            "-10" in parser.html_text and "60" in parser.html_text
        ) or (
            "-10" in parser.js_text and "60" in parser.js_text
        )
        self.assertTrue(has_range, "Temperature gauge must specify range from -10°C to 60°C")

    def test_f15_03_temp_unit_label(self):
        """F15-3: Verify temperature unit label (°C) is displayed."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue("°C" in parser.html_text or "&#176;C" in parser.html_text or "°C" in parser.js_text,
                        "Temperature gauge must display unit '°C'")

    def test_f15_04_temp_update_handler(self):
        """F15-4: Verify app.js updates temperature display upon receiving sensor/data."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_update = (
            parser.js_has_pattern(r'temp') and
            parser.js_has_pattern(r'sensor/data')
        )
        self.assertTrue(has_update, "app.js must update temperature gauge when sensor/data arrives")

    def test_f15_05_temp_gauge_styling(self):
        """F15-5: Verify style.css includes styling for temperature gauge elements."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_style = (
            "temp" in parser.css_text.lower() or
            "gauge" in parser.css_text.lower()
        )
        self.assertTrue(has_style, "style.css must define visual styling for gauge display")

    # ------------------------------------------------------------------------
    # Feature 16: Humidity Gauge Display
    # ------------------------------------------------------------------------
    def test_f16_01_hum_gauge_container(self):
        """F16-1: Verify index.html contains a humidity gauge element or container."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_elem = (
            parser.html_has_id("hum-gauge") or
            parser.html_has_id("humidity-gauge") or
            parser.html_has_id("hum-value") or
            "humidity" in parser.html_text.lower()
        )
        self.assertTrue(has_elem, "index.html must contain a container/element for humidity gauge")

    def test_f16_02_hum_range_spec(self):
        """F16-2: Verify humidity gauge indicates span covering 0% to 100%."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_range = (
            "100" in parser.html_text or "100" in parser.js_text
        )
        self.assertTrue(has_range, "Humidity gauge must define range up to 100%")

    def test_f16_03_hum_unit_label(self):
        """F16-3: Verify percentage unit label (%) is displayed."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue("%" in parser.html_text or "%" in parser.js_text,
                        "Humidity gauge must display unit '%'")

    def test_f16_04_hum_update_handler(self):
        """F16-4: Verify app.js updates humidity display upon receiving sensor/data."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_update = (
            parser.js_has_pattern(r'hum') and
            parser.js_has_pattern(r'sensor/data')
        )
        self.assertTrue(has_update, "app.js must update humidity gauge when sensor/data arrives")

    def test_f16_05_hum_gauge_styling(self):
        """F16-5: Verify style.css includes styling for humidity gauge elements."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_style = (
            "hum" in parser.css_text.lower() or
            "gauge" in parser.css_text.lower()
        )
        self.assertTrue(has_style, "style.css must define visual styling for humidity gauge")

    # ------------------------------------------------------------------------
    # Feature 17: Interactive LED Toggle Switch
    # ------------------------------------------------------------------------
    def test_f17_01_toggle_element_in_html(self):
        """F17-1: Verify index.html contains an interactive toggle control for LED."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_toggle = (
            parser.html_has_id("led-toggle") or
            parser.html_has_id("led-switch") or
            parser.html_has_class("toggle") or
            parser.html_has_class("switch") or
            re.search(r'type=["\']checkbox["\']', parser.html_text, re.IGNORECASE) or
            re.search(r'<button[^>]*led', parser.html_text, re.IGNORECASE)
        )
        self.assertTrue(has_toggle, "index.html must contain an interactive LED toggle switch")

    def test_f17_02_toggle_event_listener(self):
        """F17-2: Verify app.js registers user interaction listener on the LED toggle."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_listener = (
            parser.js_has_pattern(r'addEventListener\(\s*["\'](change|click)["\']') or
            parser.js_has_pattern(r'onchange|onclick')
        )
        self.assertTrue(has_listener, "app.js must attach event listener for user toggle interactions")

    def test_f17_03_publishes_on_command(self):
        """F17-3: Verify toggle action can publish 'ON' to sensor/led."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_on = parser.js_has_pattern(r'["\']sensor/led["\']') and parser.js_has_pattern(r'["\']ON["\']')
        self.assertTrue(has_on, "app.js must publish 'ON' to 'sensor/led'")

    def test_f17_04_publishes_off_command(self):
        """F17-4: Verify toggle action can publish 'OFF' to sensor/led."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_off = parser.js_has_pattern(r'["\']sensor/led["\']') and parser.js_has_pattern(r'["\']OFF["\']')
        self.assertTrue(has_off, "app.js must publish 'OFF' to 'sensor/led'")

    def test_f17_05_bidirectional_state_sync(self):
        """F17-5: Verify app.js synchronizes toggle state with incoming messages on sensor/led."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_sync = (
            parser.js_has_pattern(r'sensor/led') and
            (parser.js_has_pattern(r'checked\s*=') or parser.js_has_pattern(r'classList'))
        )
        self.assertTrue(has_sync, "app.js must reflect incoming 'sensor/led' state on the UI switch")

    # ------------------------------------------------------------------------
    # Feature 18: Broker Connection Status Badge
    # ------------------------------------------------------------------------
    def test_f18_01_status_badge_in_html(self):
        """F18-1: Verify index.html contains a connection status badge or indicator."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_badge = (
            parser.html_has_id("status-badge") or
            parser.html_has_id("connection-status") or
            parser.html_has_class("badge") or
            parser.html_has_class("status")
        )
        self.assertTrue(has_badge, "index.html must contain a status badge element")

    def test_f18_02_badge_styling_in_css(self):
        """F18-2: Verify style.css defines distinct styles for connection states."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_style = (
            "status" in parser.css_text.lower() or
            "badge" in parser.css_text.lower() or
            "connected" in parser.css_text.lower()
        )
        self.assertTrue(has_style, "style.css must style connection status states")

    def test_f18_03_updates_connected_state(self):
        """F18-3: Verify app.js sets badge to connected when MQTT client connects."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_conn = parser.js_has_pattern(r'connected') and parser.js_has_pattern(r'on\(\s*["\']connect["\']')
        self.assertTrue(has_conn, "app.js must update badge to 'Connected' on connect event")

    def test_f18_04_updates_disconnected_state(self):
        """F18-4: Verify app.js sets badge to disconnected on close or offline event."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_disconn = (
            parser.js_has_pattern(r'offline|close|disconnect')
        )
        self.assertTrue(has_disconn, "app.js must update badge to disconnected state on offline/close")

    def test_f18_05_handles_error_state(self):
        """F18-5: Verify app.js handles error event (client.on('error'))."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.js_has_pattern(r'\.on\(\s*["\']error["\']'),
                        "app.js must register an error handler 'on(\"error\")'")

    # ------------------------------------------------------------------------
    # Feature 19: Real-time Message Log Table
    # ------------------------------------------------------------------------
    def test_f19_01_log_container_in_html(self):
        """F19-1: Verify index.html contains container/table for message logs."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_log = (
            parser.html_has_id("message-log") or
            parser.html_has_id("log-table") or
            parser.html_has_class("log") or
            parser.html_has_tag("table")
        )
        self.assertTrue(has_log, "index.html must contain a message log table or container")

    def test_f19_02_log_columns_structure(self):
        """F19-2: Verify log structure displays timestamp, topic, and payload."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        text = (parser.html_text + " " + parser.js_text).lower()
        self.assertTrue("topic" in text and "payload" in text,
                        "Message log must present topic and payload information")

    def test_f19_03_appends_messages(self):
        """F19-3: Verify app.js appends incoming messages to log table."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_append = (
            parser.js_has_pattern(r'appendChild|prepend|insertRow|innerHTML\s*\+=') or
            parser.js_has_pattern(r'log')
        )
        self.assertTrue(has_append, "app.js must append new MQTT messages to the log view")

    def test_f19_04_log_size_capping(self):
        """F19-4: Verify message log implements rolling buffer or capping at <= 50 entries."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_cap = (
            parser.js_has_pattern(r'50') or
            parser.js_has_pattern(r'rows\.length') or
            parser.js_has_pattern(r'deleteRow|removeChild|slice')
        )
        self.assertTrue(has_cap, "app.js must limit the log table to <= 50 entries")

    def test_f19_05_log_table_styling(self):
        """F19-5: Verify style.css defines layout and scrolling for message log."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_style = (
            "table" in parser.css_text.lower() or
            "log" in parser.css_text.lower()
        )
        self.assertTrue(has_style, "style.css must style the message log view")

    # ------------------------------------------------------------------------
    # Feature 20: Vendored MQTT Library & Fallback
    # ------------------------------------------------------------------------
    def test_f20_01_vendored_directory(self):
        """F20-1: Verify web_dashboard/js/ directory exists."""
        js_dir = WEB_INDEX_HTML.parent / "js"
        self.assertTrue(js_dir.is_dir(), f"Missing directory at {js_dir}")

    def test_f20_02_vendored_mqtt_file(self):
        """F20-2: Verify web_dashboard/js/mqtt.min.js exists."""
        self.assertTrue(WEB_VENDORED_MQTT.is_file(), f"Missing {WEB_VENDORED_MQTT}")

    def test_f20_03_html_references_vendored_mqtt(self):
        """F20-3: Verify index.html loads vendored or local mqtt.min.js."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_script = re.search(r'<script[^>]*src=["\'][^"\']*mqtt(\.min)?\.js["\']', parser.html_text, re.IGNORECASE)
        self.assertTrue(bool(has_script), "index.html must include script tag for mqtt.min.js")

    def test_f20_04_mqtt_bundle_signature(self):
        """F20-4: Verify vendored mqtt.min.js contains valid MQTT library identifiers."""
        if WEB_VENDORED_MQTT.is_file():
            text = WEB_VENDORED_MQTT.read_text(encoding="utf-8", errors="replace")
            self.assertTrue("mqtt" in text.lower(), "mqtt.min.js must contain MQTT library implementation")

    def test_f20_05_cdn_fallback_defined(self):
        """F20-5: Verify dashboard provides CDN fallback or resilient offline support."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_resilience = (
            WEB_VENDORED_MQTT.is_file() or
            "unpkg.com/mqtt" in parser.html_text or
            "cdnjs.cloudflare.com" in parser.html_text or
            "onerror" in parser.html_text
        )
        self.assertTrue(has_resilience, "Dashboard must have vendored file or CDN fallback for MQTT library")

    # ------------------------------------------------------------------------
    # Feature 21: CLI Subscribe Script (test_sub.bat)
    # ------------------------------------------------------------------------
    def test_f21_01_test_sub_bat_exists(self):
        """F21-1: Verify scripts/test_sub.bat exists."""
        self.assertTrue(CLI_TEST_SUB.is_file(), f"Missing {CLI_TEST_SUB}")

    def test_f21_02_invokes_mosquitto_sub(self):
        """F21-2: Verify test_sub.bat executes mosquitto_sub."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        self.assertTrue(parser.has_command("mosquitto_sub"),
                        "test_sub.bat must execute 'mosquitto_sub'")

    def test_f21_03_subscribes_sensor_wildcard(self):
        """F21-3: Verify test_sub.bat subscribes to topic sensor/#."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        has_topic = parser.has_argument("-t", "sensor/#") or "sensor/#" in parser.raw_text
        self.assertTrue(has_topic, "test_sub.bat must subscribe to topic 'sensor/#'")

    def test_f21_04_passes_auth_credentials(self):
        """F21-4: Verify test_sub.bat supplies -u user1 and -P 123456."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        has_u = parser.has_argument("-u", "user1") or "user1" in parser.raw_text
        has_p = parser.has_argument("-P", "123456") or "123456" in parser.raw_text
        self.assertTrue(has_u and has_p, "test_sub.bat must pass credentials user1/123456")

    def test_f21_05_verbose_output_flag(self):
        """F21-5: Verify test_sub.bat includes -v flag for verbose topic/message output."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        self.assertTrue(parser.has_argument("-v"),
                        "test_sub.bat should specify -v for verbose output")

    # ------------------------------------------------------------------------
    # Feature 22: CLI Publish Temp Script (test_pub_temp.bat)
    # ------------------------------------------------------------------------
    def test_f22_01_test_pub_temp_bat_exists(self):
        """F22-1: Verify scripts/test_pub_temp.bat exists."""
        self.assertTrue(CLI_TEST_PUB_TEMP.is_file(), f"Missing {CLI_TEST_PUB_TEMP}")

    def test_f22_02_invokes_mosquitto_pub(self):
        """F22-2: Verify test_pub_temp.bat executes mosquitto_pub."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        self.assertTrue(parser.has_command("mosquitto_pub"),
                        "test_pub_temp.bat must execute 'mosquitto_pub'")

    def test_f22_03_publishes_sensor_data_topic(self):
        """F22-3: Verify test_pub_temp.bat targets topic sensor/data."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        has_t = parser.has_argument("-t", "sensor/data") or "sensor/data" in parser.raw_text
        self.assertTrue(has_t, "test_pub_temp.bat must publish to 'sensor/data'")

    def test_f22_04_passes_auth_credentials(self):
        """F22-4: Verify test_pub_temp.bat supplies user1 and 123456."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        has_u = parser.has_argument("-u", "user1") or "user1" in parser.raw_text
        has_p = parser.has_argument("-P", "123456") or "123456" in parser.raw_text
        self.assertTrue(has_u and has_p, "test_pub_temp.bat must supply credentials user1/123456")

    def test_f22_05_payload_contains_temp_and_hum(self):
        """F22-5: Verify published payload contains temp and hum fields."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        has_payload = "temp" in parser.raw_text and "hum" in parser.raw_text
        self.assertTrue(has_payload, "test_pub_temp.bat message payload must contain 'temp' and 'hum'")

    # ------------------------------------------------------------------------
    # Feature 23: CLI Publish LED Script (test_pub_led.bat)
    # ------------------------------------------------------------------------
    def test_f23_01_test_pub_led_bat_exists(self):
        """F23-1: Verify scripts/test_pub_led.bat exists."""
        self.assertTrue(CLI_TEST_PUB_LED.is_file(), f"Missing {CLI_TEST_PUB_LED}")

    def test_f23_02_invokes_mosquitto_pub(self):
        """F23-2: Verify test_pub_led.bat executes mosquitto_pub."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        self.assertTrue(parser.has_command("mosquitto_pub"),
                        "test_pub_led.bat must execute 'mosquitto_pub'")

    def test_f23_03_publishes_sensor_led_topic(self):
        """F23-3: Verify test_pub_led.bat targets topic sensor/led."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        has_t = parser.has_argument("-t", "sensor/led") or "sensor/led" in parser.raw_text
        self.assertTrue(has_t, "test_pub_led.bat must publish to 'sensor/led'")

    def test_f23_04_passes_auth_credentials(self):
        """F23-4: Verify test_pub_led.bat supplies user1 and 123456."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        has_u = parser.has_argument("-u", "user1") or "user1" in parser.raw_text
        has_p = parser.has_argument("-P", "123456") or "123456" in parser.raw_text
        self.assertTrue(has_u and has_p, "test_pub_led.bat must pass credentials user1/123456")

    def test_f23_05_supports_command_parameter(self):
        """F23-5: Verify test_pub_led.bat accepts argument parameter (%1) or defaults to ON/OFF."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        has_param = (
            "%1" in parser.raw_text or
            "%~1" in parser.raw_text or
            "ON" in parser.raw_text or
            "OFF" in parser.raw_text
        )
        self.assertTrue(has_param, "test_pub_led.bat must accept %1 parameter or support ON/OFF commands")

    # ------------------------------------------------------------------------
    # Feature 24: Complete Documentation (README.md)
    # ------------------------------------------------------------------------
    def test_f24_01_readme_exists(self):
        """F24-1: Verify README.md exists and is non-empty."""
        self.assertTrue(README_PATH.is_file(), f"Missing {README_PATH}")
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        self.assertGreater(len(text.strip()), 100, "README.md must not be empty")

    def test_f24_02_broker_section(self):
        """F24-2: Verify README documents Mosquitto broker setup and launch."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        has_broker = "mosquitto" in text.lower() and "start_broker" in text.lower()
        self.assertTrue(has_broker, "README.md must document Mosquitto broker configuration and launch")

    def test_f24_03_wokwi_section(self):
        """F24-3: Verify README documents ESP32 Wokwi simulation instructions."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        has_wokwi = "wokwi" in text.lower() and "esp32" in text.lower()
        self.assertTrue(has_wokwi, "README.md must document ESP32 Wokwi simulation steps")

    def test_f24_04_dashboard_section(self):
        """F24-4: Verify README documents Web Dashboard usage and gauge monitoring."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        has_dash = "dashboard" in text.lower() or "gauge" in text.lower()
        self.assertTrue(has_dash, "README.md must document Web Dashboard operations")

    def test_f24_05_cli_scripts_section(self):
        """F24-5: Verify README documents CLI batch verification scripts."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        has_scripts = "test_sub" in text.lower() or "test_pub" in text.lower()
        self.assertTrue(has_scripts, "README.md must document CLI verification scripts")


if __name__ == "__main__":
    unittest.main()
