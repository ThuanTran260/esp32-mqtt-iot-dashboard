#!/usr/bin/env python3
"""
Empirical Stress Test Harness & Adversarial Validation for Milestone 4
Targets:
- scripts/test_sub.bat
- scripts/test_pub_temp.bat
- scripts/test_pub_led.bat
- README.md

Author: m4_challenger_3 (teamwork_challenger / critic & specialist)
"""

import os
import sys
import re
import json
import subprocess
import tempfile
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = REPO_ROOT / "scripts"
TEST_SUB = SCRIPTS_DIR / "test_sub.bat"
TEST_PUB_TEMP = SCRIPTS_DIR / "test_pub_temp.bat"
TEST_PUB_LED = SCRIPTS_DIR / "test_pub_led.bat"
README_FILE = REPO_ROOT / "README.md"

CSC_PATH = r"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"

class EmpiricalVerifier:
    def __init__(self):
        self.results = []
        self.passed = 0
        self.failed = 0

    def record(self, test_id: str, desc: str, ok: bool, details: str = ""):
        if ok:
            self.passed += 1
            print(f"[+] PASS [{test_id}]: {desc}")
        else:
            self.failed += 1
            print(f"[-] FAIL [{test_id}]: {desc} -> {details}")
        self.results.append({"id": test_id, "desc": desc, "ok": ok, "details": details})

    def run_all(self):
        print("=" * 80)
        print("EMPIRICAL CHALLENGER 3: ADVERSARIAL STRESS TEST SUITE")
        print("=" * 80)

        self.test_1_crlf_line_endings()
        self.test_2_negative_assertions()
        self.test_3_native_mock_cmd_execution()
        self.test_4_readme_verification()

        print("=" * 80)
        print(f"EMPIRICAL SUMMARY: Total={len(self.results)}, Passed={self.passed}, Failed={self.failed}")
        print("=" * 80)
        return self.failed == 0

    def test_1_crlf_line_endings(self):
        print("\n--- Test Suite 1: CRLF Line Endings Verification ---")
        bat_files = [TEST_SUB, TEST_PUB_TEMP, TEST_PUB_LED]
        for fpath in bat_files:
            rel = fpath.relative_to(REPO_ROOT).as_posix()
            raw = open(fpath, "rb").read()
            crlf_count = raw.count(b"\r\n")
            total_lf = raw.count(b"\n")
            bare_lf = total_lf - crlf_count

            self.record(f"CRLF-{fpath.stem}",
                        f"{rel}: CRLF={crlf_count}, bare LF={bare_lf}",
                        crlf_count > 0 and bare_lf == 0,
                        f"Found {bare_lf} bare LF characters")

    def test_2_negative_assertions(self):
        print("\n--- Test Suite 2: Negative Constraints Verification ---")
        # test_pub_led.bat: strictly 0 '+' and 0 '#'
        led_text = open(TEST_PUB_LED, "r", encoding="utf-8").read()
        plus_count = led_text.count("+")
        hash_count = led_text.count("#")
        self.record("NEG-LED-PLUS", "test_pub_led.bat contains 0 '+' characters", plus_count == 0, f"Found {plus_count}")
        self.record("NEG-LED-HASH", "test_pub_led.bat contains 0 '#' characters", hash_count == 0, f"Found {hash_count}")

        # test_pub_temp.bat: strictly 1 pair of curly braces {}
        temp_text = open(TEST_PUB_TEMP, "r", encoding="utf-8").read()
        open_braces = temp_text.count("{")
        close_braces = temp_text.count("}")
        self.record("NEG-TEMP-BRACES", "test_pub_temp.bat contains exactly 1 '{' and 1 '}'",
                    open_braces == 1 and close_braces == 1,
                    f"Found {open_braces} open braces and {close_braces} close braces")

        # test_sub.bat: all '#' are terminal in 'sensor/#'
        sub_text = open(TEST_SUB, "r", encoding="utf-8").read()
        illegal_hashes = [m.start() for m in re.finditer(r'#', sub_text) if not sub_text[max(0, m.start()-7):m.start()+1].endswith("sensor/#")]
        self.record("NEG-SUB-HASH", "All '#' in test_sub.bat are strictly part of 'sensor/#'",
                    len(illegal_hashes) == 0, f"Found non-'sensor/#' hashes at offsets: {illegal_hashes}")

    def test_3_native_mock_cmd_execution(self):
        print("\n--- Test Suite 3: Native Mock CMD Execution & Runtime Defect Stress ---")
        tmpdir = tempfile.mkdtemp(prefix="emp_m4_test_")
        try:
            # 3.1 Compile native mock CLI executable
            cs_source = os.path.join(tmpdir, "MockMqttCli.cs")
            with open(cs_source, "w", encoding="utf-8") as f:
                f.write("""
using System;
class MockMqttCli {
    static int Main(string[] args) {
        Console.WriteLine("[MOCK_CLI] Invoked with args: " + string.Join(" ", args));
        string exitEnv = Environment.GetEnvironmentVariable("MOCK_EXIT_CODE");
        int code = 0;
        if (!string.IsNullOrEmpty(exitEnv) && int.TryParse(exitEnv, out code)) {
            return code;
        }
        return 0;
    }
}
""")
            mock_exe = os.path.join(tmpdir, "MockMqttCli.exe")
            compile_cmd = [CSC_PATH, "/nologo", f"/out:{mock_exe}", cs_source]
            comp_res = subprocess.run(compile_cmd, capture_output=True, text=True)
            self.record("EXEC-COMP-CSC", "Compile native mock Windows binary with csc.exe",
                        comp_res.returncode == 0, comp_res.stdout + comp_res.stderr)

            # Copy to mosquitto_sub.exe and mosquitto_pub.exe
            sub_bin = os.path.join(tmpdir, "mosquitto_sub.exe")
            pub_bin = os.path.join(tmpdir, "mosquitto_pub.exe")
            shutil.copy(mock_exe, sub_bin)
            shutil.copy(mock_exe, pub_bin)

            # Base environment with mock in PATH and CI guard enabled
            test_env = dict(os.environ)
            test_env["PATH"] = tmpdir + os.pathsep + test_env["PATH"]
            test_env["CI"] = "1"

            # 3.2 Help flag execution
            cmd_help = f'cmd.exe /c "{TEST_SUB} /?"'
            p_help = subprocess.run(cmd_help, shell=True, env=test_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-SUB-HELP", "scripts/test_sub.bat /? exits with code 0 and displays help banner",
                        p_help.returncode == 0 and "Usage:" in p_help.stdout,
                        f"Returncode={p_help.returncode}")

            # 3.3 Default execution of test_sub.bat (Checks line 30 goto :BIN_LOCATED)
            cmd_sub = f'cmd.exe /c "{TEST_SUB}"'
            p_sub = subprocess.run(cmd_sub, shell=True, env=test_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-SUB-LABEL", "test_sub.bat navigates goto :BIN_LOCATED without label seek error",
                        "The system cannot find the batch label specified" not in (p_sub.stdout + p_sub.stderr),
                        "Found label seek error!")
            self.record("EXEC-SUB-SUCCESS", "test_sub.bat exits 0 with '[INFO] Subscriber stopped cleanly.'",
                        p_sub.returncode == 0 and "[INFO] Subscriber stopped cleanly." in p_sub.stdout,
                        f"Returncode={p_sub.returncode}")

            # 3.4 Custom topic argument for test_sub.bat
            cmd_sub_arg = f'cmd.exe /c "{TEST_SUB} sensor/custom"'
            p_sub_arg = subprocess.run(cmd_sub_arg, shell=True, env=test_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-SUB-ARG", "test_sub.bat handles custom topic 'sensor/custom'",
                        p_sub_arg.returncode == 0 and "sensor/custom" in p_sub_arg.stdout,
                        f"Returncode={p_sub_arg.returncode}")

            # 3.5 Execution of test_pub_temp.bat (Checks parenthesized if-block parsing)
            cmd_temp = f'cmd.exe /c "{TEST_PUB_TEMP}"'
            p_temp = subprocess.run(cmd_temp, shell=True, env=test_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-TEMP-SUCCESS-BANNER", "test_pub_temp.bat prints '[INFO] Telemetry published successfully.'",
                        "[INFO] Telemetry published successfully." in p_temp.stdout,
                        f"Stdout: {p_temp.stdout}")
            self.record("EXEC-TEMP-NO-ERROR-BLEED", "test_pub_temp.bat does NOT execute inside error block on success",
                        "[ERROR] Publishing failed" not in p_temp.stdout and p_temp.returncode == 0,
                        f"Returncode={p_temp.returncode}, Stdout: {p_temp.stdout}")

            # 3.6 Execution of test_pub_led.bat (Checks parenthesized if-block parsing)
            cmd_led_on = f'cmd.exe /c "{TEST_PUB_LED} ON"'
            p_led_on = subprocess.run(cmd_led_on, shell=True, env=test_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-LED-SUCCESS-BANNER", "test_pub_led.bat ON prints '[INFO] LED command published successfully.'",
                        "[INFO] LED command published successfully." in p_led_on.stdout,
                        f"Stdout: {p_led_on.stdout}")
            self.record("EXEC-LED-NO-ERROR-BLEED", "test_pub_led.bat ON does NOT execute inside error block on success",
                        "[ERROR] Publishing failed" not in p_led_on.stdout and p_led_on.returncode == 0,
                        f"Returncode={p_led_on.returncode}, Stdout: {p_led_on.stdout}")

            # 3.7 LED Case normalization
            cmd_led_off = f'cmd.exe /c "{TEST_PUB_LED} off"'
            p_led_off = subprocess.run(cmd_led_off, shell=True, env=test_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-LED-CASE-NORM", "test_pub_led.bat normalizes lowercase 'off' to 'OFF'",
                        p_led_off.returncode == 0 and "Command:  OFF" in p_led_off.stdout,
                        f"Stdout: {p_led_off.stdout}")

            # 3.8 Failure path stress testing: MOCK_EXIT_CODE=2
            fail_env = dict(test_env)
            fail_env["MOCK_EXIT_CODE"] = "2"

            p_temp_fail = subprocess.run(cmd_temp, shell=True, env=fail_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-TEMP-FAIL-HANDLING", "test_pub_temp.bat cleanly triggers error block on nonzero exit code",
                        p_temp_fail.returncode == 2 and "[ERROR] Publishing failed with exit code: 2" in p_temp_fail.stdout
                        and "Authentication failure - check user1 / 123456 credentials" in p_temp_fail.stdout
                        and "[INFO] Telemetry published successfully." not in p_temp_fail.stdout,
                        f"Returncode={p_temp_fail.returncode}")

            p_led_fail = subprocess.run(cmd_led_on, shell=True, env=fail_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-LED-FAIL-HANDLING", "test_pub_led.bat cleanly triggers error block on nonzero exit code",
                        p_led_fail.returncode == 2 and "[ERROR] Publishing failed with exit code: 2" in p_led_fail.stdout
                        and "Authentication failure - check user1 / 123456 credentials" in p_led_fail.stdout
                        and "[INFO] LED command published successfully." not in p_led_fail.stdout,
                        f"Returncode={p_led_fail.returncode}")

            p_sub_fail = subprocess.run(cmd_sub, shell=True, env=fail_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-SUB-FAIL-HANDLING", "test_sub.bat cleanly triggers error block on nonzero exit code",
                        p_sub_fail.returncode == 1 and "[ERROR] mosquitto_sub exited with code: 2" in p_sub_fail.stdout
                        and "[INFO] Subscriber stopped cleanly." not in p_sub_fail.stdout,
                        f"Returncode={p_sub_fail.returncode}")

            # 3.9 Executable missing stress scenario (empty isolated PATH)
            empty_env = dict(os.environ)
            empty_env["PATH"] = r"C:\Windows\System32"
            empty_env["ProgramFiles"] = "C:\\NonExistent"
            empty_env["ProgramFiles(x86)"] = "C:\\NonExistent"
            empty_env["LOCALAPPDATA"] = "C:\\NonExistent"
            empty_env["USERPROFILE"] = "C:\\NonExistent"
            empty_env["SystemDrive"] = "C:"
            empty_env["CI"] = "1"

            p_miss_sub = subprocess.run(cmd_sub, shell=True, env=empty_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-MISS-SUB", "test_sub.bat missing binary triggers error banner with resolution advice",
                        p_miss_sub.returncode == 1 and "(mosquitto_sub.exe) was not found" in p_miss_sub.stdout,
                        f"Returncode={p_miss_sub.returncode}")

            p_miss_temp = subprocess.run(cmd_temp, shell=True, env=empty_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-MISS-TEMP", "test_pub_temp.bat missing binary triggers error banner with resolution advice",
                        p_miss_temp.returncode == 1 and "(mosquitto_pub.exe) was not found" in p_miss_temp.stdout,
                        f"Returncode={p_miss_temp.returncode}")

            p_miss_led = subprocess.run(cmd_led_on, shell=True, env=empty_env, capture_output=True, text=True, timeout=10)
            self.record("EXEC-MISS-LED", "test_pub_led.bat missing binary triggers error banner with resolution advice",
                        p_miss_led.returncode == 1 and "(mosquitto_pub.exe) was not found" in p_miss_led.stdout,
                        f"Returncode={p_miss_led.returncode}")

        finally:
            shutil.rmtree(tmpdir)

    def test_4_readme_verification(self):
        print("\n--- Test Suite 4: README Documentation Verification ---")
        self.record("DOC-EXISTS", "README.md exists", README_FILE.is_file(), "Missing README.md")
        text = open(README_FILE, "r", encoding="utf-8").read()

        # Step headers
        for step in ["Step 1", "Step 2", "Step 3", "Step 4", "Step 5"]:
            self.record(f"DOC-HEADER-{step}", f"README.md contains '{step}' header",
                        re.search(rf'##\s+{step}', text) is not None, f"Missing header {step}")

        # Subsystems
        for sub in ["Mosquitto", "Wokwi", "Dashboard", "scripts"]:
            self.record(f"DOC-SUB-{sub}", f"README.md covers subsystem '{sub}'",
                        sub.lower() in text.lower(), f"Missing subsystem {sub}")

        # Technical items
        for item in ["1883", "9001", "user1", "123456", "sensor/data", "sensor/led", "sensor/status"]:
            self.record(f"DOC-ITEM-{item}", f"README.md documents '{item}'",
                        item in text, f"Missing {item}")

if __name__ == "__main__":
    verifier = EmpiricalVerifier()
    success = verifier.run_all()
    sys.exit(0 if success else 1)
