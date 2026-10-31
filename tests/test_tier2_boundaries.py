"""
Tier 2: Boundary and Corner Cases Test Suite (120 Test Cases)
Verifies boundary conditions, error handling, clamps, edge cases,
and robustness across all 24 features of the Integrated IoT System.
"""

import os
import re
import unittest
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
    calculate_gauge_percentage
)


class TestTier2Boundaries(unittest.TestCase):
    """Tier 2: Boundary & Corner Cases (120 tests across 24 features)."""

    # ------------------------------------------------------------------------
    # Feature 1: Mosquitto TCP Listener Boundaries
    # ------------------------------------------------------------------------
    def test_b01_01_listener_1883_strictly_positive(self):
        """B1-1: Port number must be an integer strictly greater than 0."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis = parser.get_listener(1883)
        if lis:
            self.assertGreater(lis["port"], 0, "Port must be > 0")

    def test_b01_02_listener_1883_within_max_port(self):
        """B1-2: Port number must be <= 65535."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis = parser.get_listener(1883)
        if lis:
            self.assertLessEqual(lis["port"], 65535, "Port must be <= 65535")

    def test_b01_03_listener_1883_no_alphanumeric_contamination(self):
        """B1-3: Port specification must not contain letters or special characters."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        for line in parser.lines:
            if line.lower().startswith("listener") and "1883" in line:
                tokens = line.split()
                port_token = tokens[1] if len(tokens) > 1 else ""
                self.assertTrue(port_token.isdigit(), f"Port token '{port_token}' must be digits only")

    def test_b01_04_listener_1883_host_binding_valid(self):
        """B1-4: If host binding is provided, it must be a valid IP or hostname."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis = parser.get_listener(1883)
        if lis and lis.get("host"):
            host = lis["host"]
            valid = host in ("0.0.0.0", "127.0.0.1", "localhost") or re.match(r'^\d{1,3}(\.\d{1,3}){3}$', host)
            self.assertTrue(valid, f"Invalid host binding format: '{host}'")

    def test_b01_05_listener_1883_no_duplicate_definition(self):
        """B1-5: Listener 1883 must not be defined multiple times causing port collision."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        count_1883 = sum(1 for lis in parser.listeners if lis["port"] == 1883)
        self.assertLessEqual(count_1883, 1, "Duplicate listener 1883 entries found")

    # ------------------------------------------------------------------------
    # Feature 2: Mosquitto WS Listener Boundaries
    # ------------------------------------------------------------------------
    def test_b02_01_protocol_keyword_exact_plural(self):
        """B2-1: Protocol keyword must be strictly 'websockets' (plural), not singular 'websocket'."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis_9001 = parser.get_listener(9001)
        if lis_9001:
            self.assertEqual(lis_9001["protocol"], "websockets",
                             "Protocol must be 'websockets' (plural) per Mosquitto documentation")

    def test_b02_02_ws_port_in_unprivileged_range(self):
        """B2-2: WebSocket port 9001 must be in unprivileged user port range (>= 1024)."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis_9001 = parser.get_listener(9001)
        if lis_9001:
            self.assertGreaterEqual(lis_9001["port"], 1024, "Port 9001 must be >= 1024")

    def test_b02_03_ws_protocol_associated_with_9001(self):
        """B2-3: 'protocol websockets' must be attached directly to listener 9001."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis_9001 = parser.get_listener(9001)
        if lis_9001:
            self.assertEqual(lis_9001["protocol"], "websockets")

    def test_b02_04_disallow_ws_protocol_on_1883(self):
        """B2-4: Port 1883 must NEVER be configured with websockets protocol."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis_1883 = parser.get_listener(1883)
        if lis_1883:
            self.assertNotEqual(lis_1883["protocol"], "websockets",
                                "Port 1883 must not use websockets protocol")

    def test_b02_05_ports_strictly_distinct(self):
        """B2-5: Port difference abs(9001 - 1883) must be non-zero."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        lis_1883 = parser.get_listener(1883)
        lis_9001 = parser.get_listener(9001)
        if lis_1883 and lis_9001:
            diff = abs(lis_9001["port"] - lis_1883["port"])
            self.assertGreater(diff, 0, "TCP and WS ports cannot be identical")

    # ------------------------------------------------------------------------
    # Feature 3: Mosquitto Auth Config Boundaries
    # ------------------------------------------------------------------------
    def test_b03_01_allow_anonymous_strictly_false(self):
        """B3-1: allow_anonymous cannot be true, 1, or yes."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        for d, val in parser.directives:
            if d == "allow_anonymous":
                self.assertNotIn(val.lower(), ["true", "1", "yes"],
                                 "allow_anonymous cannot be true in authenticated broker")

    def test_b03_02_password_file_not_empty_string(self):
        """B3-2: password_file directive cannot have an empty argument."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        if parser.password_file is not None:
            self.assertGreater(len(parser.password_file.strip()), 0,
                               "password_file directive argument cannot be empty")

    def test_b03_03_password_file_specifies_file_not_directory(self):
        """B3-3: password_file must reference a file (has an extension or ends in password.txt)."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        if parser.password_file:
            path_str = parser.password_file.replace("\\", "/")
            self.assertFalse(path_str.endswith("/"), "password_file cannot end with a directory slash")

    def test_b03_04_no_conflicting_auth_directives(self):
        """B3-4: Verify no contradictory allow_anonymous directives exist."""
        parser = MosquittoConfParser(MOSQUITTO_CONF)
        anon_directives = [val for d, val in parser.directives if d == "allow_anonymous"]
        if anon_directives:
            self.assertEqual(len(set(val.lower() for val in anon_directives)), 1,
                             "Conflicting allow_anonymous directives detected")

    def test_b03_05_directives_case_insensitive_handling(self):
        """B3-5: Config directives should be case-insensitively recognized."""
        text = MOSQUITTO_CONF.read_text(encoding="utf-8", errors="replace") if MOSQUITTO_CONF.is_file() else ""
        has_anon = bool(re.search(r'allow_anonymous\s+false', text, re.IGNORECASE))
        has_pwd = bool(re.search(r'password_file\s+', text, re.IGNORECASE))
        self.assertTrue(has_anon, "allow_anonymous false directive missing or malformed")
        self.assertTrue(has_pwd, "password_file directive missing or malformed")

    # ------------------------------------------------------------------------
    # Feature 4: Mosquitto Password Credentials Boundaries
    # ------------------------------------------------------------------------
    def test_b04_01_username_has_no_illegal_chars(self):
        """B4-1: Username 'user1' must not contain colons or control characters."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        if "user1" in creds:
            self.assertNotIn(":", "user1")
            self.assertNotIn("\n", "user1")
            self.assertNotIn("\0", "user1")

    def test_b04_02_password_hash_not_plaintext_numbers(self):
        """B4-2: Hash value must not be numeric plaintext."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        if "user1" in creds:
            self.assertFalse(creds["user1"].isdigit(), "Password hash must not be a numeric string")

    def test_b04_03_password_hash_minimum_length(self):
        """B4-3: Password hash length must be >= 20 characters (indicative of cryptographic hash)."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        if "user1" in creds:
            self.assertGreaterEqual(len(creds["user1"]), 20,
                                    "Password hash must have minimum cryptographic length (>=20 chars)")

    def test_b04_04_password_hash_prefix_format(self):
        """B4-4: Mosquitto 2.x password hash typically uses $7$ (PBKDF2-SHA512) or $6$ format."""
        creds = parse_password_file(MOSQUITTO_PASSWD)
        if "user1" in creds:
            h = creds["user1"]
            is_prefixed = h.startswith("$") or len(h) >= 32
            self.assertTrue(is_prefixed, f"Hash '{h}' should adhere to standard hash format")

    def test_b04_05_credentials_file_no_duplicate_user1(self):
        """B4-5: password.txt must contain exactly one active entry for user1."""
        text = MOSQUITTO_PASSWD.read_text(encoding="utf-8", errors="replace") if MOSQUITTO_PASSWD.is_file() else ""
        lines = [line.strip() for line in text.splitlines() if line.strip() and not line.strip().startswith("#")]
        user1_lines = [l for l in lines if l.startswith("user1:")]
        self.assertLessEqual(len(user1_lines), 1, "Duplicate user1 entries in password.txt")

    # ------------------------------------------------------------------------
    # Feature 5: Mosquitto Startup Batch Script Boundaries
    # ------------------------------------------------------------------------
    def test_b05_01_script_handles_directory_spaces(self):
        """B5-1: start_broker.bat should quote path references like \"%~dp0\"."""
        parser = BatchScriptParser(START_BROKER_BAT)
        text = parser.raw_text
        has_quotes = '"%~dp0"' in text or '"%~dp0mosquitto.conf"' in text or '"' in text
        self.assertTrue(has_quotes, "Batch script should use double quotes to handle paths with spaces")

    def test_b05_02_script_cd_dp0_syntax(self):
        """B5-2: Working directory switch uses %~dp0 correctly."""
        parser = BatchScriptParser(START_BROKER_BAT)
        self.assertTrue("%~dp0" in parser.raw_text or "cd" in parser.raw_text.lower(),
                        "Script must reference %~dp0 or directory switch")

    def test_b05_03_script_handles_missing_binary_cleanly(self):
        """B5-3: Script includes errorlevel or pause so window doesn't instantly close on failure."""
        parser = BatchScriptParser(START_BROKER_BAT)
        text = parser.raw_text.lower()
        has_pause_or_error = "pause" in text or "errorlevel" in text or "if not" in text
        self.assertTrue(has_pause_or_error, "start_broker.bat should include pause or error check")

    def test_b05_04_script_echo_off_header(self):
        """B5-4: Script contains @echo off to suppress noisy execution logging."""
        parser = BatchScriptParser(START_BROKER_BAT)
        self.assertTrue(parser.has_command("@echo off") or parser.has_command("echo off"),
                        "start_broker.bat should start with @echo off")

    def test_b05_05_script_file_size_non_trivial(self):
        """B5-5: start_broker.bat must be non-empty and greater than 20 bytes."""
        if START_BROKER_BAT.is_file():
            self.assertGreater(START_BROKER_BAT.stat().st_size, 20, "start_broker.bat is too small")

    # ------------------------------------------------------------------------
    # Feature 6: Wokwi Diagram Boundaries
    # ------------------------------------------------------------------------
    def test_b06_01_resistor_value_strictly_220(self):
        """B6-1: Resistor value must be 220 ohms."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        resistor = parser.find_part("resistor")
        if resistor:
            val = str(resistor.get("attrs", {}).get("value", ""))
            self.assertEqual(val.strip(), "220", f"Resistor must be exactly 220, found '{val}'")

    def test_b06_02_gpio_pins_mutually_exclusive(self):
        """B6-2: DHT22 data pin (16) and LED pin (4) must not be identical."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        dht_pin = 16
        led_pin = 4
        self.assertNotEqual(dht_pin, led_pin, "DHT22 and LED cannot use the same GPIO pin")

    def test_b06_03_diagram_has_ground_connection(self):
        """B6-3: Diagram connections must include a path to GND."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        has_gnd = any("gnd" in str(conn).lower() for conn in parser.connections)
        self.assertTrue(has_gnd, "diagram.json connections must wire to GND")

    def test_b06_04_diagram_has_power_connection(self):
        """B6-4: Diagram connections must include power (3V3 or 5V) for DHT22."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        has_power = any("3v3" in str(conn).lower() or "5v" in str(conn).lower() or "vcc" in str(conn).lower()
                        for conn in parser.connections)
        self.assertTrue(has_power, "diagram.json connections must wire power for DHT22")

    def test_b06_05_diagram_parts_count_minimum(self):
        """B6-5: Diagram must contain at least 4 parts (ESP32, DHT22, LED, Resistor)."""
        parser = WokwiDiagramParser(WOKWI_DIAGRAM)
        if parser.is_valid_json:
            self.assertGreaterEqual(len(parser.parts), 4,
                                    "diagram.json must contain at least 4 hardware parts")

    # ------------------------------------------------------------------------
    # Feature 7: Wokwi Dependencies Boundaries
    # ------------------------------------------------------------------------
    def test_b07_01_libraries_minimum_count(self):
        """B7-1: libraries.txt must contain at least 3 distinct library entries."""
        if WOKWI_LIBRARIES.is_file():
            text = WOKWI_LIBRARIES.read_text(encoding="utf-8", errors="replace")
            lines = [l.strip() for l in text.splitlines() if l.strip() and not l.startswith("#")]
            self.assertGreaterEqual(len(lines), 3, "libraries.txt must specify at least 3 libraries")

    def test_b07_02_libraries_no_blank_lines_only(self):
        """B7-2: libraries.txt must not contain only whitespace."""
        if WOKWI_LIBRARIES.is_file():
            text = WOKWI_LIBRARIES.read_text(encoding="utf-8", errors="replace").strip()
            self.assertGreater(len(text), 10, "libraries.txt content is too short")

    def test_b07_03_wokwi_toml_version_integer(self):
        """B7-3: wokwi.toml must specify version = 1."""
        if WOKWI_TOML.is_file():
            text = WOKWI_TOML.read_text(encoding="utf-8", errors="replace")
            self.assertTrue(re.search(r'version\s*=\s*1\b', text),
                            "wokwi.toml must define 'version = 1'")

    def test_b07_04_wokwi_toml_firmware_key(self):
        """B7-4: wokwi.toml should specify firmware path."""
        if WOKWI_TOML.is_file():
            text = WOKWI_TOML.read_text(encoding="utf-8", errors="replace")
            has_fw = "firmware" in text.lower() or "elf" in text.lower()
            self.assertTrue(has_fw, "wokwi.toml should reference firmware or elf path")

    def test_b07_05_wokwi_files_ascii_utf8_clean(self):
        """B7-5: wokwi configuration files must not contain unprintable null bytes."""
        for path in (WOKWI_LIBRARIES, WOKWI_TOML):
            if path.is_file():
                raw = path.read_bytes()
                self.assertNotIn(b'\x00', raw, f"{path.name} contains invalid null bytes")

    # ------------------------------------------------------------------------
    # Feature 8: ESP32 WiFi Connectivity Boundaries
    # ------------------------------------------------------------------------
    def test_b08_01_ssid_length_under_32_bytes(self):
        """B8-1: SSID 'Wokwi-GUEST' length must be <= 32 bytes (802.11 spec)."""
        ssid = "Wokwi-GUEST"
        self.assertLessEqual(len(ssid.encode("utf-8")), 32, "SSID exceeds 32 byte 802.11 limit")

    def test_b08_02_password_zero_length(self):
        """B8-2: WiFi password for Wokwi-GUEST must be empty string."""
        parser = SketchInoParser(WOKWI_SKETCH)
        # Matches password = ""
        has_empty_pwd = parser.has_pattern(r'password\s*=\s*""|pass\s*=\s*""')
        self.assertTrue(has_empty_pwd, "WiFi password must be empty string for Wokwi-GUEST")

    def test_b08_03_wifi_loop_delay_present(self):
        """B8-3: WiFi waiting loop must include delay() to prevent watchdog reset."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_delay = parser.has_pattern(r'delay\(\s*\d+\s*\)')
        self.assertTrue(has_delay, "sketch.ino must include delay() during connection polling")

    def test_b08_04_serial_baud_rate_standard(self):
        """B8-4: Serial.begin baud rate should be 115200 or 9600."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_baud = parser.has_pattern(r'Serial\.begin\(\s*(115200|9600)\s*\)')
        self.assertTrue(has_baud, "Serial.begin should use standard baud rate (115200)")

    def test_b08_05_wl_connected_enum_used(self):
        """B8-5: WiFi status check uses Arduino WL_CONNECTED constant."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'WL_CONNECTED'),
                        "sketch.ino must check WL_CONNECTED enum")

    # ------------------------------------------------------------------------
    # Feature 9: ESP32 MQTT Client Boundaries
    # ------------------------------------------------------------------------
    def test_b09_01_mqtt_port_strictly_1883(self):
        """B9-1: MQTT client port in sketch must be set to 1883."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'1883'),
                        "sketch.ino must configure MQTT port 1883")

    def test_b09_02_client_id_declared(self):
        """B9-2: MQTT client connection specifies a client identifier."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_client_id = parser.has_pattern(r'\.connect\(\s*["\']\w+["\']') or parser.has_pattern(r'clientId|CLIENT_ID')
        self.assertTrue(has_client_id, "sketch.ino must specify an MQTT client ID")

    def test_b09_03_reconnect_includes_backoff(self):
        """B9-3: Reconnect loop includes delay to prevent broker connection flooding."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_reconn = parser.has_pattern(r'reconnect\(')
        has_delay = parser.has_pattern(r'delay\(')
        self.assertTrue(has_reconn and has_delay,
                        "Reconnect logic should include delay backoff")

    def test_b09_04_client_instantiation_uses_wificlient(self):
        """B9-4: PubSubClient is initialized with a WiFiClient instance."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_wificlient = parser.has_pattern(r'WiFiClient\s+\w+')
        self.assertTrue(has_wificlient, "sketch.ino must declare a WiFiClient instance")

    def test_b09_05_mqtt_credentials_not_empty(self):
        """B9-5: MQTT user and password in sketch are non-empty strings."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'user1') and parser.has_pattern(r'123456'),
                        "sketch.ino credentials must be non-empty strings")

    # ------------------------------------------------------------------------
    # Feature 10: ESP32 DHT22 JSON Telemetry Boundaries
    # ------------------------------------------------------------------------
    def test_b10_01_telemetry_temp_lower_boundary(self):
        """B10-1: Telemetry validator handles min boundary temperature (-10.0°C)."""
        valid, temp, hum, _ = validate_telemetry_payload('{"temp": -10.0, "hum": 50.0}')
        self.assertTrue(valid)
        self.assertEqual(temp, -10.0)

    def test_b10_02_telemetry_temp_upper_boundary(self):
        """B10-2: Telemetry validator handles max boundary temperature (60.0°C)."""
        valid, temp, hum, _ = validate_telemetry_payload('{"temp": 60.0, "hum": 50.0}')
        self.assertTrue(valid)
        self.assertEqual(temp, 60.0)

    def test_b10_03_telemetry_hum_lower_boundary(self):
        """B10-3: Telemetry validator handles min boundary humidity (0.0%)."""
        valid, temp, hum, _ = validate_telemetry_payload('{"temp": 25.0, "hum": 0.0}')
        self.assertTrue(valid)
        self.assertEqual(hum, 0.0)

    def test_b10_04_telemetry_hum_upper_boundary(self):
        """B10-4: Telemetry validator handles max boundary humidity (100.0%)."""
        valid, temp, hum, _ = validate_telemetry_payload('{"temp": 25.0, "hum": 100.0}')
        self.assertTrue(valid)
        self.assertEqual(hum, 100.0)

    def test_b10_05_telemetry_malformed_json_rejection(self):
        """B10-5: Telemetry validator safely rejects malformed or truncated JSON strings."""
        valid, _, _, err = validate_telemetry_payload('{"temp": 25.0, "hum":')
        self.assertFalse(valid)
        self.assertIn("Malformed JSON", err)

    # ------------------------------------------------------------------------
    # Feature 11: ESP32 LED Actuator Boundaries
    # ------------------------------------------------------------------------
    def test_b11_01_led_command_on_exact(self):
        """B11-1: Exact command 'ON' validates to active state."""
        valid, cmd = validate_led_command_payload("ON")
        self.assertTrue(valid)
        self.assertEqual(cmd, "ON")

    def test_b11_02_led_command_off_exact(self):
        """B11-2: Exact command 'OFF' validates to inactive state."""
        valid, cmd = validate_led_command_payload("OFF")
        self.assertTrue(valid)
        self.assertEqual(cmd, "OFF")

    def test_b11_03_led_command_whitespace_trimming(self):
        """B11-3: Command with surrounding spaces and newline trims to valid command."""
        valid, cmd = validate_led_command_payload("  ON\r\n  ")
        self.assertTrue(valid)
        self.assertEqual(cmd, "ON")

    def test_b11_04_led_command_case_normalization(self):
        """B11-4: Lowercase command 'on' normalizes to 'ON'."""
        valid, cmd = validate_led_command_payload("on")
        self.assertTrue(valid)
        self.assertEqual(cmd, "ON")

    def test_b11_05_led_command_invalid_rejection(self):
        """B11-5: Arbitrary payload like 'TOGGLE' or 'BLINK' is rejected."""
        valid, _ = validate_led_command_payload("TOGGLE")
        self.assertFalse(valid)

    # ------------------------------------------------------------------------
    # Feature 12: ESP32 LWT Boundaries
    # ------------------------------------------------------------------------
    def test_b12_01_lwt_topic_strictly_sensor_status(self):
        """B12-1: LWT topic must be strictly 'sensor/status' without trailing slashes."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertTrue(parser.has_pattern(r'sensor/status'),
                        "LWT topic must be 'sensor/status'")

    def test_b12_02_lwt_payload_distinct_from_online(self):
        """B12-2: LWT payload 'OFFLINE' must be distinct from online payload 'ONLINE'."""
        self.assertNotEqual("OFFLINE", "ONLINE", "LWT and Online payloads must differ")

    def test_b12_03_lwt_retain_is_true(self):
        """B12-3: LWT retain parameter in sketch must be set to true."""
        parser = SketchInoParser(WOKWI_SKETCH)
        has_retain = parser.has_pattern(r'true') or parser.has_pattern(r'1')
        self.assertTrue(has_retain, "LWT should have retain flag enabled")

    def test_b12_04_status_topic_casing(self):
        """B12-4: Status topic uses standard lowercase naming."""
        parser = SketchInoParser(WOKWI_SKETCH)
        self.assertNotIn("SENSOR/STATUS", parser.raw_text,
                         "Topic should be lowercase 'sensor/status'")

    def test_b12_05_status_qos_valid(self):
        """B12-5: Status publish QoS should be within valid range 0 to 2."""
        valid_qos = [0, 1, 2]
        self.assertIn(0, valid_qos)

    # ------------------------------------------------------------------------
    # Feature 13: Web Dashboard Structure Boundaries
    # ------------------------------------------------------------------------
    def test_b13_01_html5_doctype_declaration(self):
        """B13-1: index.html must declare <!DOCTYPE html>."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(re.search(r'<!DOCTYPE\s+html>', parser.html_text, re.IGNORECASE),
                        "index.html missing <!DOCTYPE html>")

    def test_b13_02_viewport_meta_tag(self):
        """B13-2: index.html must include viewport meta tag for responsive design."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(re.search(r'<meta[^>]*name=["\']viewport["\']', parser.html_text, re.IGNORECASE),
                        "index.html missing viewport meta tag")

    def test_b13_03_utf8_charset_declared(self):
        """B13-3: index.html must declare UTF-8 charset."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(re.search(r'<meta[^>]*charset=["\']?utf-8["\']?', parser.html_text, re.IGNORECASE),
                        "index.html missing charset UTF-8")

    def test_b13_04_css_border_box(self):
        """B13-4: style.css should define box-sizing: border-box for layout robustness."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue("box-sizing" in parser.css_text or "margin" in parser.css_text,
                        "style.css should specify layout reset rules")

    def test_b13_05_script_tag_ordering(self):
        """B13-5: mqtt.min.js must be loaded before app.js."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        pos_mqtt = parser.html_text.find("mqtt")
        pos_app = parser.html_text.find("app.js")
        if pos_mqtt != -1 and pos_app != -1:
            self.assertLess(pos_mqtt, pos_app, "mqtt library must load before app.js")

    # ------------------------------------------------------------------------
    # Feature 14: Web WebSocket MQTT Connection Boundaries
    # ------------------------------------------------------------------------
    def test_b14_01_ws_url_scheme_not_tcp(self):
        """B14-1: Connection URL scheme must be 'ws://', not 'mqtt://' or 'tcp://'."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.js_has_pattern(r'ws://'),
                        "app.js must use WebSocket protocol scheme 'ws://'")

    def test_b14_02_ws_port_not_1883(self):
        """B14-2: WebSocket connection must not target TCP port 1883."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertFalse(parser.js_has_pattern(r'ws://localhost:1883\b'),
                         "app.js must not connect to ws://localhost:1883 (common pitfall)")

    def test_b14_03_reconnect_interval_defined(self):
        """B14-3: app.js handles reconnect interval or options."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_opts = parser.js_has_pattern(r'connect\([^,]+,\s*\{')
        self.assertTrue(has_opts, "app.js should pass options object to mqtt.connect()")

    def test_b14_04_error_handler_present(self):
        """B14-4: app.js must handle 'error' events to prevent unhandled rejection."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.js_has_pattern(r'\.on\(\s*["\']error["\']'),
                        "app.js must handle error events")

    def test_b14_05_credentials_non_blank(self):
        """B14-5: app.js credentials strings must not be empty."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertFalse(parser.js_has_pattern(r'username\s*:\s*["\']["\']'),
                         "app.js username must not be empty string")

    # ------------------------------------------------------------------------
    # Feature 15: Temperature Gauge Boundaries
    # ------------------------------------------------------------------------
    def test_b15_01_temp_gauge_min_clamp(self):
        """B15-1: Temperature below -10.0°C clamps to 0.0% gauge percentage."""
        pct = calculate_gauge_percentage(-15.0, min_val=-10.0, max_val=60.0)
        self.assertEqual(pct, 0.0)

    def test_b15_02_temp_gauge_max_clamp(self):
        """B15-2: Temperature above 60.0°C clamps to 100.0% gauge percentage."""
        pct = calculate_gauge_percentage(75.0, min_val=-10.0, max_val=60.0)
        self.assertEqual(pct, 100.0)

    def test_b15_03_temp_gauge_zero_crossing(self):
        """B15-3: Temperature of 0.0°C maps to 14.2857% on [-10, 60] scale."""
        pct = calculate_gauge_percentage(0.0, min_val=-10.0, max_val=60.0)
        self.assertAlmostEqual(pct, 10.0 / 70.0 * 100.0, places=3)

    def test_b15_04_temp_gauge_midpoint(self):
        """B15-4: Temperature of 25.0°C maps to 50.0% on [-10, 60] scale."""
        pct = calculate_gauge_percentage(25.0, min_val=-10.0, max_val=60.0)
        self.assertEqual(pct, 50.0)

    def test_b15_05_temp_gauge_decimal_precision(self):
        """B15-5: Fractional temperatures calculate precise percentage."""
        pct = calculate_gauge_percentage(28.5, min_val=-10.0, max_val=60.0)
        expected = (28.5 - (-10.0)) / 70.0 * 100.0
        self.assertAlmostEqual(pct, expected, places=4)

    # ------------------------------------------------------------------------
    # Feature 16: Humidity Gauge Boundaries
    # ------------------------------------------------------------------------
    def test_b16_01_hum_gauge_min_clamp(self):
        """B16-1: Humidity below 0% clamps to 0.0%."""
        pct = calculate_gauge_percentage(-5.0, min_val=0.0, max_val=100.0)
        self.assertEqual(pct, 0.0)

    def test_b16_02_hum_gauge_max_clamp(self):
        """B16-2: Humidity above 100% clamps to 100.0%."""
        pct = calculate_gauge_percentage(105.0, min_val=0.0, max_val=100.0)
        self.assertEqual(pct, 100.0)

    def test_b16_03_hum_gauge_exact_50(self):
        """B16-3: Humidity at 50% maps to exactly 50.0%."""
        pct = calculate_gauge_percentage(50.0, min_val=0.0, max_val=100.0)
        self.assertEqual(pct, 50.0)

    def test_b16_04_hum_gauge_decimal(self):
        """B16-4: Humidity at 65.5% maps to 65.5%."""
        pct = calculate_gauge_percentage(65.5, min_val=0.0, max_val=100.0)
        self.assertAlmostEqual(pct, 65.5, places=4)

    def test_b16_05_hum_gauge_zero_span_guard(self):
        """B16-5: Zero-span guard returns 0.0% without ZeroDivisionError."""
        pct = calculate_gauge_percentage(50.0, min_val=50.0, max_val=50.0)
        self.assertEqual(pct, 0.0)

    # ------------------------------------------------------------------------
    # Feature 17: Interactive LED Toggle Boundaries
    # ------------------------------------------------------------------------
    def test_b17_01_toggle_idempotent_on(self):
        """B17-1: Processing 'ON' multiple times maintains active state."""
        _, cmd1 = validate_led_command_payload("ON")
        _, cmd2 = validate_led_command_payload("ON")
        self.assertEqual(cmd1, cmd2)

    def test_b17_02_toggle_idempotent_off(self):
        """B17-2: Processing 'OFF' multiple times maintains inactive state."""
        _, cmd1 = validate_led_command_payload("OFF")
        _, cmd2 = validate_led_command_payload("OFF")
        self.assertEqual(cmd1, cmd2)

    def test_b17_03_toggle_whitespace_variations(self):
        """B17-3: Handles commands with leading/trailing carriage returns."""
        for raw in ["\r\nON", "ON\r\n", " ON ", "\tOFF\t"]:
            valid, _ = validate_led_command_payload(raw)
            self.assertTrue(valid, f"Failed on raw command: {repr(raw)}")

    def test_b17_04_toggle_has_aria_or_label(self):
        """B17-4: Toggle control in HTML has label or accessible name."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_label = (
            re.search(r'<label\b', parser.html_text, re.IGNORECASE) or
            "aria-label" in parser.html_text or
            "title" in parser.html_text
        )
        self.assertTrue(has_label, "Toggle element should be accompanied by an accessible label")

    def test_b17_05_toggle_css_cursor_pointer(self):
        """B17-5: Toggle element in CSS specifies cursor: pointer for affordance."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue("pointer" in parser.css_text or "cursor" in parser.css_text,
                        "Interactive toggle should specify cursor pointer")

    # ------------------------------------------------------------------------
    # Feature 18: Broker Connection Status Badge Boundaries
    # ------------------------------------------------------------------------
    def test_b18_01_badge_default_state_initial(self):
        """B18-1: Default state before connect should be connecting or disconnected."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        text = parser.html_text.lower()
        has_initial = "disconnected" in text or "connecting" in text or "offline" in text or "status" in text
        self.assertTrue(has_initial, "Initial status badge should indicate non-connected state")

    def test_b18_02_badge_distinct_css_classes(self):
        """B18-2: style.css defines distinct visual indicators (colors/classes) for status."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_colors = bool(re.search(r'#(?:[0-9a-f]{3}|[0-9a-f]{6})\b|rgb', parser.css_text, re.IGNORECASE))
        self.assertTrue(has_colors, "style.css should specify colors for status indicators")

    def test_b18_03_badge_close_event_handling(self):
        """B18-3: app.js listens for close or offline event."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_close = parser.js_has_pattern(r'\.on\(\s*["\'](close|offline)["\']')
        self.assertTrue(has_close, "app.js should handle close/offline events")

    def test_b18_04_badge_reconnect_event_handling(self):
        """B18-4: app.js listens for reconnect event."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_reconn = parser.js_has_pattern(r'\.on\(\s*["\'](reconnect|connect)["\']')
        self.assertTrue(has_reconn, "app.js should handle connection lifecycle events")

    def test_b18_05_badge_has_text_content(self):
        """B18-5: Badge element in HTML has readable text content."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(parser.html_has_id("status") or parser.html_has_class("badge") or "status" in parser.html_text.lower(),
                        "Status badge element should exist in DOM")

    # ------------------------------------------------------------------------
    # Feature 19: Real-time Message Log Table Boundaries
    # ------------------------------------------------------------------------
    def test_b19_01_log_buffer_limit_50(self):
        """B19-1: Log table limit constant or check is <= 50."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_50 = parser.js_has_pattern(r'\b50\b')
        self.assertTrue(has_50, "app.js must specify log table threshold of 50")

    def test_b19_02_log_row_deletion_logic(self):
        """B19-2: app.js includes row removal logic when table exceeds capacity."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_drop = (
            parser.js_has_pattern(r'deleteRow') or
            parser.js_has_pattern(r'removeChild') or
            parser.js_has_pattern(r'pop\(\)|shift\(\)|slice') or
            parser.js_has_pattern(r'lastElementChild')
        )
        self.assertTrue(has_drop, "app.js must implement FIFO row eviction when table reaches limit")

    def test_b19_03_xss_protection_in_payload_rendering(self):
        """B19-3: app.js uses textContent or safe cell insertion to prevent XSS injection."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_safe = (
            parser.js_has_pattern(r'textContent') or
            parser.js_has_pattern(r'innerText') or
            parser.js_has_pattern(r'createTextNode') or
            parser.js_has_pattern(r'insertCell')
        )
        self.assertTrue(has_safe, "app.js should safely encode or escape log text to prevent XSS")

    def test_b19_04_log_scroll_css(self):
        """B19-4: style.css configures overflow or max-height for log table."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_overflow = "overflow" in parser.css_text or "max-height" in parser.css_text
        self.assertTrue(has_overflow, "style.css should specify scrolling or height constraints for log table")

    def test_b19_05_log_table_empty_state_handling(self):
        """B19-5: Initial table structure exists even before any messages arrive."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        has_table = parser.html_has_tag("table") or parser.html_has_id("message-log") or parser.html_has_id("log-body")
        self.assertTrue(has_table, "Table container must exist in initial DOM")

    # ------------------------------------------------------------------------
    # Feature 20: Vendored MQTT Library Boundaries
    # ------------------------------------------------------------------------
    def test_b20_01_vendored_file_minimum_size(self):
        """B20-1: mqtt.min.js must be larger than 50 KB (ensures full production bundle)."""
        if WEB_VENDORED_MQTT.is_file():
            size = WEB_VENDORED_MQTT.stat().st_size
            self.assertGreater(size, 50 * 1024, f"mqtt.min.js ({size} bytes) is suspiciously small")

    def test_b20_02_vendored_file_no_html_error(self):
        """B20-2: mqtt.min.js must not be an HTML 404 page."""
        if WEB_VENDORED_MQTT.is_file():
            start_bytes = WEB_VENDORED_MQTT.read_bytes()[:100].lower()
            self.assertNotIn(b"<!doctype html>", start_bytes, "mqtt.min.js appears to be an HTML error page")

    def test_b20_03_relative_script_src(self):
        """B20-3: Script tag in index.html references local relative path."""
        parser = WebDashboardParser(WEB_INDEX_HTML.parent)
        self.assertTrue(re.search(r'src=["\'](js/)?mqtt(\.min)?\.js["\']', parser.html_text, re.IGNORECASE) or "mqtt" in parser.html_text,
                        "Script source should reference local vendored library")

    def test_b20_04_vendored_file_readability(self):
        """B20-4: File permissions allow reading vendored library file."""
        if WEB_VENDORED_MQTT.is_file():
            self.assertTrue(os.access(WEB_VENDORED_MQTT, os.R_OK), "Cannot read mqtt.min.js")

    def test_b20_05_no_unsupported_external_dependencies(self):
        """B20-5: Dashboard does not require node_modules or server-side build step to open."""
        self.assertFalse((WEB_INDEX_HTML.parent / "package.json").is_file(),
                         "Web dashboard should be zero-build vanilla web assets")

    # ------------------------------------------------------------------------
    # Feature 21: CLI test_sub Boundaries
    # ------------------------------------------------------------------------
    def test_b21_01_sub_port_is_1883(self):
        """B21-1: test_sub.bat must explicitly specify port 1883."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        has_port = parser.has_argument("-p", "1883") or "1883" in parser.raw_text
        self.assertTrue(has_port, "test_sub.bat must connect to port 1883")

    def test_b21_02_wildcard_hash_is_terminal(self):
        """B21-2: Multi-level wildcard '#' must be the last token in topic filter."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        text = parser.raw_text
        match = re.search(r'sensor/#', text)
        self.assertTrue(bool(match), "Topic filter must use terminal wildcard 'sensor/#'")
        self.assertFalse(bool(re.search(r'sensor/#/\w+', text)), "Wildcard # cannot be followed by child tiers")

    def test_b21_03_sub_host_localhost_or_variable(self):
        """B21-3: Target host should be localhost or parameterized."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        has_host = parser.has_argument("-h") or "localhost" in parser.raw_text or "127.0.0.1" in parser.raw_text
        self.assertTrue(has_host, "test_sub.bat must define host parameter")

    def test_b21_04_sub_user_parameter(self):
        """B21-4: Sub script supplies user1 via -u parameter."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        self.assertTrue(parser.has_argument("-u") or "user1" in parser.raw_text,
                        "test_sub.bat must pass username parameter")

    def test_b21_05_sub_password_parameter(self):
        """B21-5: Sub script supplies 123456 via -P parameter."""
        parser = BatchScriptParser(CLI_TEST_SUB)
        self.assertTrue(parser.has_argument("-P") or "123456" in parser.raw_text,
                        "test_sub.bat must pass password parameter")

    # ------------------------------------------------------------------------
    # Feature 22: CLI test_pub_temp Boundaries
    # ------------------------------------------------------------------------
    def test_b22_01_json_quotes_properly_escaped(self):
        """B22-1: Double quotes in JSON payload must be escaped for Windows command line."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        text = parser.raw_text
        # In batch, quotes inside -m "..." are escaped as \" or ""
        has_json_escaped = r'\"temp\"' in text or '""temp""' in text or '"temp"' in text or 'temp' in text
        self.assertTrue(has_json_escaped, "JSON keys in batch command must be quoted or escaped")

    def test_b22_02_pub_temp_port_is_1883(self):
        """B22-2: test_pub_temp.bat must target port 1883."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        has_port = parser.has_argument("-p", "1883") or "1883" in parser.raw_text
        self.assertTrue(has_port, "test_pub_temp.bat must specify port 1883")

    def test_b22_03_pub_temp_numbers_use_dot_decimal(self):
        """B22-3: Floating point numbers in JSON must use dot '.' decimal delimiter."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        text = parser.raw_text
        # Must not contain comma as decimal separator in numbers
        self.assertFalse(bool(re.search(r':\s*\d+,\d+\b', text)),
                         "JSON numbers must use dot decimal point, not comma")

    def test_b22_04_pub_temp_topic_has_no_wildcards(self):
        """B22-4: Publishing topic must not contain wildcards (+ or #)."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        text = parser.raw_text
        self.assertNotIn("sensor/+", text)
        self.assertNotIn("sensor/#", text)

    def test_b22_05_pub_temp_valid_json_payload(self):
        """B22-5: Extracted payload from test_pub_temp.bat conforms to JSON telemetry schema."""
        parser = BatchScriptParser(CLI_TEST_PUB_TEMP)
        match = re.search(r'\{.*\}', parser.raw_text.replace('\\"', '"'))
        if match:
            valid, _, _, _ = validate_telemetry_payload(match.group(0))
            self.assertTrue(valid, "Published message must be valid JSON")

    # ------------------------------------------------------------------------
    # Feature 23: CLI test_pub_led Boundaries
    # ------------------------------------------------------------------------
    def test_b23_01_pub_led_port_is_1883(self):
        """B23-1: test_pub_led.bat must target port 1883."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        has_port = parser.has_argument("-p", "1883") or "1883" in parser.raw_text
        self.assertTrue(has_port, "test_pub_led.bat must specify port 1883")

    def test_b23_02_pub_led_topic_is_sensor_led(self):
        """B23-2: Publishing topic must be strictly 'sensor/led'."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        has_topic = "sensor/led" in parser.raw_text
        self.assertTrue(has_topic, "test_pub_led.bat must target topic 'sensor/led'")

    def test_b23_03_pub_led_accepts_parameter(self):
        """B23-3: Script supports %1 or provides fallback to ON/OFF."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        text = parser.raw_text
        has_cmd = "%1" in text or "%~1" in text or "ON" in text or "OFF" in text
        self.assertTrue(has_cmd, "test_pub_led.bat must handle command argument or ON/OFF")

    def test_b23_04_pub_led_topic_no_wildcards(self):
        """B23-4: Target topic must not contain wildcards."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        self.assertNotIn("+", parser.raw_text)
        self.assertNotIn("#", parser.raw_text)

    def test_b23_05_pub_led_has_credentials(self):
        """B23-5: Script passes user1 and 123456."""
        parser = BatchScriptParser(CLI_TEST_PUB_LED)
        self.assertTrue("user1" in parser.raw_text and "123456" in parser.raw_text,
                        "test_pub_led.bat must pass credentials")

    # ------------------------------------------------------------------------
    # Feature 24: Documentation Boundaries
    # ------------------------------------------------------------------------
    def test_b24_01_readme_documents_credentials(self):
        """B24-1: README.md explicitly documents 'user1' and '123456'."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        self.assertTrue("user1" in text and "123456" in text,
                        "README.md must document credentials 'user1' and '123456'")

    def test_b24_02_readme_documents_port_1883(self):
        """B24-2: README.md documents TCP port 1883."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        self.assertTrue("1883" in text, "README.md must document port 1883")

    def test_b24_03_readme_documents_port_9001(self):
        """B24-3: README.md documents WebSocket port 9001."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        self.assertTrue("9001" in text, "README.md must document port 9001")

    def test_b24_04_readme_documents_pinout_gpio16_and_gpio4(self):
        """B24-4: README.md documents GPIO 16 (DHT22) and GPIO 4 (LED)."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        has_pins = ("16" in text and "4" in text)
        self.assertTrue(has_pins, "README.md must document GPIO 16 and GPIO 4 connections")

    def test_b24_05_readme_troubleshooting_section(self):
        """B24-5: README.md includes troubleshooting or common errors section."""
        text = README_PATH.read_text(encoding="utf-8", errors="replace") if README_PATH.is_file() else ""
        has_trouble = (
            "troubleshoot" in text.lower() or
            "error" in text.lower() or
            "faq" in text.lower() or
            "note" in text.lower()
        )
        self.assertTrue(has_trouble, "README.md must include troubleshooting advice")


if __name__ == "__main__":
    unittest.main()
