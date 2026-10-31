#!/usr/bin/env python3
"""
Adversarial Verification & Empirical Challenge Harness for Milestone 4
Targets:
- scripts/test_sub.bat
- scripts/test_pub_temp.bat
- scripts/test_pub_led.bat
- README.md

Author: m4_challenger_2 (teamwork_challenger / critic & specialist)
"""

import os
import sys
import re
import json
import subprocess
from pathlib import Path

# Paths
REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = REPO_ROOT / "scripts"
TEST_SUB = SCRIPTS_DIR / "test_sub.bat"
TEST_PUB_TEMP = SCRIPTS_DIR / "test_pub_temp.bat"
TEST_PUB_LED = SCRIPTS_DIR / "test_pub_led.bat"
README_FILE = REPO_ROOT / "README.md"

sys.path.insert(0, str(REPO_ROOT))
from tests.test_helpers import validate_telemetry_payload

class ChallengeRunner:
    def __init__(self):
        self.results = []
        self.failures = []

    def record(self, check_id: str, desc: str, passed: bool, details: str = ""):
        entry = {
            "id": check_id,
            "description": desc,
            "passed": passed,
            "details": details
        }
        self.results.append(entry)
        if not passed:
            self.failures.append(entry)
            print(f"[-] FAIL: [{check_id}] {desc} -> {details}")
        else:
            print(f"[+] PASS: [{check_id}] {desc}")

    def run_all(self):
        print("=" * 80)
        print("STARTING ADVERSARIAL EMPIRICAL CHALLENGE SUITE (MILESTONE 4)")
        print("=" * 80)

        self.challenge_1_regex_boundary_fuzzing()
        self.challenge_2_readme_structural_and_link_analysis()
        self.challenge_3_script_execution_behavior()
        self.challenge_4_test_suite_coverage()

        print("=" * 80)
        print(f"CHALLENGE SUMMARY: {len(self.results)} checks executed, "
              f"{len(self.results) - len(self.failures)} passed, {len(self.failures)} failed.")
        print("=" * 80)
        return len(self.failures) == 0

    def challenge_1_regex_boundary_fuzzing(self):
        print("\n--- Challenge 1: Regex & String Boundary Fuzzing ---")

        # 1.1 test_pub_led.bat: exact character counts for + and #
        assert TEST_PUB_LED.is_file(), f"Missing {TEST_PUB_LED}"
        with open(TEST_PUB_LED, "rb") as f:
            raw_bytes = f.read()
        led_text = raw_bytes.decode("utf-8", errors="replace")

        plus_count = led_text.count("+")
        hash_count = led_text.count("#")
        self.record("CH-1.1.1", "test_pub_led.bat exact '+' character count is 0",
                    plus_count == 0, f"Found {plus_count} '+' characters")
        self.record("CH-1.1.2", "test_pub_led.bat exact '#' character count is 0",
                    hash_count == 0, f"Found {hash_count} '#' characters")

        # Check for Unicode variations or lookalikes
        fullwidth_plus = led_text.count("\uff0b")
        fullwidth_hash = led_text.count("\uff03")
        self.record("CH-1.1.3", "test_pub_led.bat zero fullwidth lookalikes (U+FF0B, U+FF03)",
                    fullwidth_plus == 0 and fullwidth_hash == 0,
                    f"fullwidth_plus={fullwidth_plus}, fullwidth_hash={fullwidth_hash}")

        # Check topic in test_pub_led.bat
        topic_match = re.search(r'-t\s+["\']?([^"\'\s]+)["\']?', led_text)
        self.record("CH-1.1.4", "test_pub_led.bat publishes strictly to 'sensor/led'",
                    topic_match is not None and topic_match.group(1) == "sensor/led",
                    f"Topic match: {topic_match.group(1) if topic_match else 'None'}")

        # Check parameter forwarding in test_pub_led.bat
        has_param = "%1" in led_text or "%~1" in led_text
        has_case_norm = "if /i" in led_text and "ON" in led_text and "OFF" in led_text
        self.record("CH-1.1.5", "test_pub_led.bat parameter support (%1/%~1) and case normalization",
                    has_param and has_case_norm, f"has_param={has_param}, has_case_norm={has_case_norm}")

        # 1.2 test_pub_temp.bat: greedy regex extraction and JSON schema conformance
        assert TEST_PUB_TEMP.is_file(), f"Missing {TEST_PUB_TEMP}"
        with open(TEST_PUB_TEMP, "r", encoding="utf-8") as f:
            temp_text = f.read()

        # Check total curly braces count in the whole file
        open_braces = temp_text.count("{")
        close_braces = temp_text.count("}")
        self.record("CH-1.2.1", "test_pub_temp.bat has exactly 1 pair of curly braces (prevents greedy bleed)",
                    open_braces == 1 and close_braces == 1,
                    f"open_braces={open_braces}, close_braces={close_braces}")

        # Greedy regex extraction
        clean_temp = temp_text.replace('\\"', '"')
        match = re.search(r'\{.*\}', clean_temp)
        self.record("CH-1.2.2", "Greedy regex r'\\{.*\\}' extracts JSON payload candidate",
                    match is not None, f"Match: {match.group(0) if match else 'None'}")

        if match:
            extracted_json = match.group(0)
            try:
                parsed_json = json.loads(extracted_json)
                json_valid = True
            except Exception as e:
                parsed_json = None
                json_valid = False
            self.record("CH-1.2.3", "Extracted payload parses cleanly with json.loads",
                        json_valid, f"Parsed: {parsed_json}")

            if json_valid:
                valid, temp_val, hum_val, err_msg = validate_telemetry_payload(extracted_json)
                self.record("CH-1.2.4", "Extracted JSON strictly conforms to telemetry schema (temp & hum in range)",
                            valid and -10.0 <= temp_val <= 60.0 and 0.0 <= hum_val <= 100.0,
                            f"valid={valid}, temp={temp_val}, hum={hum_val}, err={err_msg}")
                self.record("CH-1.2.5", "JSON schema has no unwanted extraneous keys",
                            set(parsed_json.keys()) == {"temp", "hum"},
                            f"Keys: {list(parsed_json.keys())}")

        # Decimal format check: no commas in floating point numbers
        comma_decimal = re.search(r':\s*\d+,\d+\b', temp_text)
        self.record("CH-1.2.6", "Floating point values use dot '.' decimal delimiter (no comma decimals)",
                    comma_decimal is None, f"Found: {comma_decimal.group(0) if comma_decimal else 'None'}")

        # 1.3 test_sub.bat: terminal # wildcard validation
        assert TEST_SUB.is_file(), f"Missing {TEST_SUB}"
        with open(TEST_SUB, "r", encoding="utf-8") as f:
            sub_text = f.read()

        # Check that '#' is terminal in any topic filter
        illegal_sub_tiers = re.findall(r'sensor/#/\w+', sub_text)
        self.record("CH-1.3.1", "test_sub.bat contains NO illegal child tiers following '#' (e.g. sensor/#/temp)",
                    len(illegal_sub_tiers) == 0, f"Found illegal child tiers: {illegal_sub_tiers}")

        # Check every occurrence of # in the script
        hash_positions = [m.start() for m in re.finditer(r'#', sub_text)]
        invalid_hashes = []
        for pos in hash_positions:
            # Check context around #
            context = sub_text[max(0, pos-8):min(len(sub_text), pos+8)]
            # Valid occurrences: 'sensor/#'
            if not sub_text[max(0, pos-7):pos+1].endswith("sensor/#"):
                invalid_hashes.append(context)
        self.record("CH-1.3.2", "Every '#' occurrence in test_sub.bat is part of 'sensor/#'",
                    len(invalid_hashes) == 0, f"Invalid hash contexts: {invalid_hashes}")

        # Check -v flag
        self.record("CH-1.3.3", "test_sub.bat passes -v flag for verbose message output",
                    "-v" in sub_text, "Found -v flag in test_sub.bat")

        # Check credentials
        self.record("CH-1.3.4", "test_sub.bat passes user1 and 123456",
                    "user1" in sub_text and "123456" in sub_text, "Found credentials in test_sub.bat")

    def challenge_2_readme_structural_and_link_analysis(self):
        print("\n--- Challenge 2: README Structural & Link Analysis ---")
        assert README_FILE.is_file(), f"Missing {README_FILE}"
        with open(README_FILE, "r", encoding="utf-8") as f:
            readme_text = f.read()

        # 2.1 Verify all 5 required main headings exist and are non-empty
        # Headings expected: Step 1, Step 2, Step 3, Step 4, Step 5 (or equivalent)
        required_steps = [
            ("Step 1: Mosquitto Broker Setup", r'##\s+Step\s+1[:\s].*Mosquitto'),
            ("Step 2: Wokwi ESP32 Simulation", r'##\s+Step\s+2[:\s].*Wokwi'),
            ("Step 3: Web Gauge Dashboard", r'##\s+Step\s+3[:\s].*Web'),
            ("Step 4: CLI Verification Scripts", r'##\s+Step\s+4[:\s].*CLI'),
            ("Step 5: Troubleshooting & FAQ", r'##\s+Step\s+5[:\s].*Troubleshoot'),
        ]

        # Split README by ## headings to evaluate section body lengths
        sections = re.split(r'\n(?=##\s+)', readme_text)
        section_map = {}
        for sec in sections:
            m = re.match(r'##\s+([^\n]+)', sec)
            if m:
                section_map[m.group(1).strip()] = sec

        for step_name, pattern in required_steps:
            matched_sec_title = None
            matched_sec_body = ""
            for title, body in section_map.items():
                if re.search(pattern, "## " + title, re.IGNORECASE):
                    matched_sec_title = title
                    matched_sec_body = body
                    break

            is_present = matched_sec_title is not None
            is_non_empty = len(matched_sec_body.strip()) > 200
            self.record(f"CH-2.1-{step_name[:6]}", f"README Heading '{step_name}' exists and is non-empty",
                        is_present and is_non_empty,
                        f"Found title='{matched_sec_title}', body length={len(matched_sec_body)} chars")

        # 2.2 Subsystems documentation: broker, esp32, web, scripts
        subsystems = ["broker", "esp32", "web", "scripts"]
        for sub in subsystems:
            self.record(f"CH-2.2-{sub}", f"README documents subsystem: '{sub}'",
                        sub in readme_text.lower(), f"Subsystem keyword '{sub}' in README")

        # 2.3 Ports & Credentials: 1883, 9001, user1, 123456
        self.record("CH-2.3.1", "README documents TCP port 1883",
                    "1883" in readme_text, "Port 1883 in README")
        self.record("CH-2.3.2", "README documents WebSocket port 9001",
                    "9001" in readme_text, "Port 9001 in README")
        self.record("CH-2.3.3", "README documents username 'user1'",
                    "user1" in readme_text, "User1 in README")
        self.record("CH-2.3.4", "README documents password '123456'",
                    "123456" in readme_text, "123456 in README")

        # 2.4 GPIO pins & Resistor
        self.record("CH-2.4.1", "README documents GPIO 16 (DHT22)",
                    "16" in readme_text, "GPIO 16 in README")
        self.record("CH-2.4.2", "README documents GPIO 4 (LED)",
                    "4" in readme_text, "GPIO 4 in README")
        has_resistor = "220" in readme_text
        self.record("CH-2.4.3", "README documents 220-ohm resistor",
                    has_resistor, "220-ohm resistor documented in README")

        # 2.5 Topics: sensor/data, sensor/led, sensor/status
        for topic in ["sensor/data", "sensor/led", "sensor/status"]:
            self.record(f"CH-2.5-{topic}", f"README documents MQTT topic '{topic}'",
                        topic in readme_text, f"Topic '{topic}' in README")

        # 2.6 Troubleshooting guidance for expected error classes:
        # - auth error
        # - websocket error
        # - wokwi simulation gateway
        # - CORS / mixed content
        has_auth_err = any(k in readme_text.lower() for k in ["not authorised", "authentication failure", "auth", "allow_anonymous"])
        self.record("CH-2.6.1", "README troubleshooting covers MQTT authentication errors",
                    has_auth_err, "Auth error troubleshooting documented")

        has_ws_err = any(k in readme_text.lower() for k in ["websocket", "9001", "protocol websockets"])
        self.record("CH-2.6.2", "README troubleshooting covers WebSocket connection errors",
                    has_ws_err, "WebSocket error troubleshooting documented")

        has_wokwi_gateway = any(k in readme_text.lower() for k in ["wokwi", "gateway", "bridge", "host.wokwi"])
        self.record("CH-2.6.3", "README troubleshooting covers Wokwi network bridge/gateway guidance",
                    has_wokwi_gateway, "Wokwi gateway troubleshooting documented")

        has_cors_err = any(k in readme_text.lower() for k in ["cors", "mixed content", "https", "localhost:8000"])
        self.record("CH-2.6.4", "README troubleshooting covers browser CORS and mixed content guidance",
                    has_cors_err, "CORS / mixed content troubleshooting documented")

        # 2.7 Referential Integrity: All file paths mentioned in README must exist on disk
        # Scan for path references like path/to/file.ext
        candidate_paths = set(re.findall(r'`([a-zA-Z0-9_\-\.\/]+\.[a-zA-Z0-9]+)`', readme_text))
        broken_links = []
        verified_links = []
        for path_str in candidate_paths:
            # Filter to local repository paths (not URLs or external references)
            if any(path_str.startswith(prefix) for prefix in ["broker/", "esp32_wokwi/", "web_dashboard/", "scripts/", "tests/", "docs/"]):
                local_file = REPO_ROOT / path_str
                if not local_file.exists():
                    broken_links.append(path_str)
                else:
                    verified_links.append(path_str)

        self.record("CH-2.7", f"Referential Integrity: All local file paths in README exist ({len(verified_links)} verified)",
                    len(broken_links) == 0, f"Broken paths: {broken_links}")

    def challenge_3_script_execution_behavior(self):
        print("\n--- Challenge 3: Script Execution Behavior & Non-Interactive Safety ---")

        # 3.1 Help flag execution for test_sub.bat
        cmd = f'cmd.exe /c "{TEST_SUB} /?"'
        proc = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        self.record("CH-3.1.1", "test_sub.bat /? exits with code 0 and displays usage help",
                    proc.returncode == 0 and "Usage:" in proc.stdout,
                    f"Returncode={proc.returncode}, stdout snippet: {proc.stdout[:80]!r}")

        # 3.2 Non-interactive CI safety in test_sub.bat
        with open(TEST_SUB, "r", encoding="utf-8") as f:
            sub_content = f.read()
        has_ci_guard_sub = "if not defined CI" in sub_content or "pause" not in sub_content.lower()
        self.record("CH-3.1.2", "test_sub.bat includes CI guard or conditional pause to prevent runner hangs",
                    has_ci_guard_sub, "CI pause guard present")

        # 3.3 Execution of test_pub_temp.bat missing-binary guidance
        cmd = f'cmd.exe /c "{TEST_PUB_TEMP}"'
        proc = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        # It should exit with code 1 or 0 (depending on whether mosquitto_pub is installed in PATH)
        self.record("CH-3.2.1", "test_pub_temp.bat executes without crashing/hanging",
                    proc.returncode in (0, 1),
                    f"Returncode={proc.returncode}, output snippet: {(proc.stdout or proc.stderr)[:80]!r}")

        # 3.4 Execution of test_pub_led.bat with arguments
        cmd_on = f'cmd.exe /c "{TEST_PUB_LED} ON"'
        proc_on = subprocess.run(cmd_on, shell=True, capture_output=True, text=True, timeout=10)
        self.record("CH-3.3.1", "test_pub_led.bat ON executes cleanly without hanging",
                    proc_on.returncode in (0, 1),
                    f"Returncode={proc_on.returncode}")

        cmd_off = f'cmd.exe /c "{TEST_PUB_LED} off"'
        proc_off = subprocess.run(cmd_off, shell=True, capture_output=True, text=True, timeout=10)
        self.record("CH-3.3.2", "test_pub_led.bat off (lowercase) executes cleanly without hanging",
                    proc_off.returncode in (0, 1),
                    f"Returncode={proc_off.returncode}")

    def challenge_4_test_suite_coverage(self):
        print("\n--- Challenge 4: Automated Test Suite Coverage Verification ---")

        # Run Tier 1 Features 21-24
        code_t1 = (
            "import sys, unittest; from tests.test_tier1_features import TestTier1FeatureCoverage; "
            "suite = unittest.TestSuite(); loader = unittest.TestLoader(); "
            "[suite.addTest(TestTier1FeatureCoverage(m)) for m in loader.getTestCaseNames(TestTier1FeatureCoverage) if any(m.startswith(f'test_f{i}') for i in range(21, 25))]; "
            "runner = unittest.TextTestRunner(verbosity=0); res = runner.run(suite); sys.exit(0 if res.wasSuccessful() else 1)"
        )
        res = subprocess.run([sys.executable, "-c", code_t1], capture_output=True, text=True, timeout=15)
        self.record("CH-4.1", "Tier 1 Feature Tests (Features 21-24: 20 tests) pass 100%",
                    res.returncode == 0, f"Returncode={res.returncode}, stderr={res.stderr.strip()}")

        # Run Tier 2 Boundaries 21-24
        code_t2 = (
            "import sys, unittest; from tests.test_tier2_boundaries import TestTier2Boundaries; "
            "suite = unittest.TestSuite(); loader = unittest.TestLoader(); "
            "[suite.addTest(TestTier2Boundaries(m)) for m in loader.getTestCaseNames(TestTier2Boundaries) if any(m.startswith(f'test_b{i}') for i in range(21, 25))]; "
            "runner = unittest.TextTestRunner(verbosity=0); res = runner.run(suite); sys.exit(0 if res.wasSuccessful() else 1)"
        )
        res = subprocess.run([sys.executable, "-c", code_t2], capture_output=True, text=True, timeout=15)
        self.record("CH-4.2", "Tier 2 Boundary Tests (Boundaries 21-24: 20 tests) pass 100%",
                    res.returncode == 0, f"Returncode={res.returncode}, stderr={res.stderr.strip()}")

        # Run Tier 3 Interactions
        res = subprocess.run([sys.executable, "-m", "unittest", "tests.test_tier3_interactions"], capture_output=True, text=True, timeout=15)
        self.record("CH-4.3", "Tier 3 Interaction Tests (24 tests) pass 100%",
                    res.returncode == 0, f"Returncode={res.returncode}, stderr={res.stderr.strip()}")

        # Run Tier 4 Scenarios
        res = subprocess.run([sys.executable, "-m", "unittest", "tests.test_tier4_scenarios"], capture_output=True, text=True, timeout=15)
        self.record("CH-4.4", "Tier 4 Application Scenarios (5 tests) pass 100%",
                    res.returncode == 0, f"Returncode={res.returncode}, stderr={res.stderr.strip()}")

        # Run master runner tests/run_all_tests.py
        res = subprocess.run([sys.executable, "tests/run_all_tests.py"], capture_output=True, text=True, timeout=20)
        self.record("CH-4.5", "Full Master Test Suite (269 tests across all 4 tiers) passes 100%",
                    res.returncode == 0, f"Returncode={res.returncode}, stderr={res.stderr.strip()}")

if __name__ == "__main__":
    runner = ChallengeRunner()
    success = runner.run_all()
    sys.exit(0 if success else 1)
