"""
E2E Test Suite Helpers and Opaque-Box Parsers
Provides robust inspection, parsing, validation, and simulation utilities
for the Integrated IoT System (Mosquitto + ESP32 Wokwi + Web Dashboard + CLI).
"""

import os
import sys
import json
import re
import subprocess
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple

# Base Project Paths
PROJECT_ROOT = Path(__file__).resolve().parent.parent
BROKER_DIR = PROJECT_ROOT / "broker"
ESP32_DIR = PROJECT_ROOT / "esp32_wokwi"
WEB_DIR = PROJECT_ROOT / "web_dashboard"
SCRIPTS_DIR = PROJECT_ROOT / "scripts"
README_PATH = PROJECT_ROOT / "README.md"

# System Files Under Test
MOSQUITTO_CONF = BROKER_DIR / "mosquitto.conf"
MOSQUITTO_PASSWD = BROKER_DIR / "password.txt"
START_BROKER_BAT = BROKER_DIR / "start_broker.bat"

WOKWI_DIAGRAM = ESP32_DIR / "diagram.json"
WOKWI_SKETCH = ESP32_DIR / "sketch.ino"
WOKWI_LIBRARIES = ESP32_DIR / "libraries.txt"
WOKWI_TOML = ESP32_DIR / "wokwi.toml"

WEB_INDEX_HTML = WEB_DIR / "index.html"
WEB_STYLE_CSS = WEB_DIR / "style.css"
WEB_APP_JS = WEB_DIR / "app.js"
WEB_VENDORED_MQTT = WEB_DIR / "js" / "mqtt.min.js"

CLI_TEST_SUB = SCRIPTS_DIR / "test_sub.bat"
CLI_TEST_PUB_TEMP = SCRIPTS_DIR / "test_pub_temp.bat"
CLI_TEST_PUB_LED = SCRIPTS_DIR / "test_pub_led.bat"

CHROME_PATH = Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")


# ============================================================================
# File Reading & Generic Utilities
# ============================================================================

def read_text_safe(path: Path, encoding: str = "utf-8") -> str:
    """Reads a file returning its content or empty string if not found."""
    if not path.is_file():
        return ""
    try:
        return path.read_text(encoding=encoding, errors="replace")
    except Exception:
        return ""


# ============================================================================
# Mosquitto Broker Parsers
# ============================================================================

class MosquittoConfParser:
    """Parses mosquitto.conf into structured listener and directive models."""

    def __init__(self, conf_path: Path = MOSQUITTO_CONF):
        self.path = conf_path
        self.raw_text = read_text_safe(conf_path)
        self.lines = [line.strip() for line in self.raw_text.splitlines()]
        self.directives: List[Tuple[str, str]] = []
        self.listeners: List[Dict[str, Any]] = []
        self.allow_anonymous: Optional[bool] = None
        self.password_file: Optional[str] = None
        self._parse()

    def _parse(self):
        current_listener = None
        for line in self.lines:
            if not line or line.startswith("#"):
                continue
            parts = line.split(maxsplit=1)
            directive = parts[0].lower()
            val = parts[1].strip() if len(parts) > 1 else ""
            self.directives.append((directive, val))

            if directive == "listener":
                if current_listener:
                    self.listeners.append(current_listener)
                # port and optional host
                listener_parts = val.split()
                port = int(listener_parts[0]) if listener_parts and listener_parts[0].isdigit() else 0
                host = listener_parts[1] if len(listener_parts) > 1 else ""
                current_listener = {
                    "port": port,
                    "host": host,
                    "protocol": "mqtt",  # default
                    "raw_line": line
                }
            elif directive == "protocol":
                if current_listener:
                    current_listener["protocol"] = val.lower()
            elif directive == "allow_anonymous":
                self.allow_anonymous = (val.lower() == "true")
            elif directive == "password_file":
                self.password_file = val

        if current_listener:
            self.listeners.append(current_listener)

    def get_listener(self, port: int) -> Optional[Dict[str, Any]]:
        for lis in self.listeners:
            if lis["port"] == port:
                return lis
        return None


def parse_password_file(pwd_path: Path = MOSQUITTO_PASSWD) -> Dict[str, str]:
    """Parses password.txt into username:hash mappings."""
    content = read_text_safe(pwd_path)
    creds = {}
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if ":" in line:
            user, pwd_hash = line.split(":", 1)
            creds[user.strip()] = pwd_hash.strip()
    return creds


# ============================================================================
# Wokwi Simulation Parsers
# ============================================================================

class WokwiDiagramParser:
    """Parses diagram.json to inspect ESP32 wiring, parts, and connections."""

    def __init__(self, diagram_path: Path = WOKWI_DIAGRAM):
        self.path = diagram_path
        self.raw_text = read_text_safe(diagram_path)
        self.is_valid_json = False
        self.data: Dict[str, Any] = {}
        self.parts: List[Dict[str, Any]] = []
        self.connections: List[List[str]] = []
        self._parse()

    def _parse(self):
        if not self.raw_text:
            return
        try:
            self.data = json.loads(self.raw_text)
            self.is_valid_json = True
            self.parts = self.data.get("parts", [])
            self.connections = self.data.get("connections", [])
        except Exception:
            self.is_valid_json = False

    def find_part(self, part_type_substring: str) -> Optional[Dict[str, Any]]:
        for part in self.parts:
            p_type = part.get("type", "").lower()
            p_id = part.get("id", "").lower()
            if part_type_substring.lower() in p_type or part_type_substring.lower() in p_id:
                return part
        return None

    def find_all_parts(self, part_type_substring: str) -> List[Dict[str, Any]]:
        matched = []
        for part in self.parts:
            p_type = part.get("type", "").lower()
            p_id = part.get("id", "").lower()
            if part_type_substring.lower() in p_type or part_type_substring.lower() in p_id:
                matched.append(part)
        return matched

    def has_connection(self, pin_a_pattern: str, pin_b_pattern: str) -> bool:
        """Checks if any connection links pin A to pin B (bidirectional matching)."""
        re_a = re.compile(pin_a_pattern, re.IGNORECASE)
        re_b = re.compile(pin_b_pattern, re.IGNORECASE)

        for conn in self.connections:
            if len(conn) >= 2:
                p1, p2 = str(conn[0]), str(conn[1])
                if (re_a.search(p1) and re_b.search(p2)) or (re_a.search(p2) and re_b.search(p1)):
                    return True
        return False


class SketchInoParser:
    """Inspects sketch.ino Arduino C++ source code."""

    def __init__(self, sketch_path: Path = WOKWI_SKETCH):
        self.path = sketch_path
        self.raw_text = read_text_safe(sketch_path)

    def has_include(self, header: str) -> bool:
        pattern = rf'#\s*include\s*[<"]{re.escape(header)}[>"]'
        return bool(re.search(pattern, self.raw_text, re.IGNORECASE))

    def has_pattern(self, pattern: str) -> bool:
        return bool(re.search(pattern, self.raw_text, re.IGNORECASE))

    def get_matches(self, pattern: str) -> List[str]:
        return re.findall(pattern, self.raw_text, re.IGNORECASE)


# ============================================================================
# Web Dashboard Parsers
# ============================================================================

class WebDashboardParser:
    """Inspects index.html, style.css, and app.js."""

    def __init__(self, web_dir: Path = WEB_DIR):
        self.web_dir = web_dir
        self.html_text = read_text_safe(web_dir / "index.html")
        self.css_text = read_text_safe(web_dir / "style.css")
        self.js_text = read_text_safe(web_dir / "app.js")

    def html_has_id(self, elem_id: str) -> bool:
        return bool(re.search(rf'id=["\']{re.escape(elem_id)}["\']', self.html_text, re.IGNORECASE))

    def html_has_class(self, class_name: str) -> bool:
        return bool(re.search(rf'class=["\'][^"\']*\b{re.escape(class_name)}\b[^"\']*["\']', self.html_text, re.IGNORECASE))

    def html_has_tag(self, tag_name: str) -> bool:
        return bool(re.search(rf'<{re.escape(tag_name)}\b', self.html_text, re.IGNORECASE))

    def js_has_pattern(self, pattern: str) -> bool:
        return bool(re.search(pattern, self.js_text, re.IGNORECASE))

    def css_has_selector(self, selector: str) -> bool:
        return bool(re.search(rf'{re.escape(selector)}\s*\{{', self.css_text))


# ============================================================================
# Batch Script Parsers
# ============================================================================

class BatchScriptParser:
    """Inspects Windows batch (.bat) scripts."""

    def __init__(self, script_path: Path):
        self.path = script_path
        self.raw_text = read_text_safe(script_path)
        self.lines = [line.strip() for line in self.raw_text.splitlines()]

    def has_command(self, cmd_name: str) -> bool:
        pattern = rf'\b{re.escape(cmd_name)}\b'
        for line in self.lines:
            if not line.startswith("::") and not line.lower().startswith("rem "):
                if re.search(pattern, line, re.IGNORECASE):
                    return True
        return False

    def has_argument(self, flag: str, value: Optional[str] = None) -> bool:
        if value:
            pattern = rf'{re.escape(flag)}\s+["\']?{re.escape(value)}["\']?'
        else:
            pattern = rf'{re.escape(flag)}\b'
        return bool(re.search(pattern, self.raw_text, re.IGNORECASE))


# ============================================================================
# Domain Validators & Mathematical Models
# ============================================================================

def validate_telemetry_payload(json_str: str) -> Tuple[bool, Optional[float], Optional[float], str]:
    """
    Validates a JSON string conforms to the `sensor/data` schema:
    {"temp": <float>, "hum": <float>}
    """
    try:
        data = json.loads(json_str)
    except Exception as e:
        return False, None, None, f"Malformed JSON: {e}"

    if not isinstance(data, dict):
        return False, None, None, "Payload root must be a JSON object"

    if "temp" not in data or "hum" not in data:
        return False, None, None, "Payload missing 'temp' or 'hum' field"

    try:
        temp = float(data["temp"])
        hum = float(data["hum"])
    except (ValueError, TypeError):
        return False, None, None, "Values of 'temp' and 'hum' must be numeric"

    return True, temp, hum, "OK"


def validate_led_command_payload(payload: str) -> Tuple[bool, str]:
    """
    Validates payload on topic `sensor/led`:
    Must normalize to 'ON' or 'OFF'.
    """
    clean = payload.strip().upper()
    if clean in ("ON", "OFF"):
        return True, clean
    return False, clean


def calculate_gauge_percentage(val: float, min_val: float, max_val: float) -> float:
    """
    Calculates gauge fill percentage based on range [min_val, max_val].
    Clamps between 0.0 and 100.0%.
    """
    if max_val <= min_val:
        return 0.0
    pct = round((val - min_val) / (max_val - min_val) * 100.0, 4)
    return max(0.0, min(100.0, pct))


def dump_dom_with_chrome(html_file: Path) -> Optional[str]:
    """Uses Google Chrome headless mode to dump the rendered DOM if available."""
    if not CHROME_PATH.is_file() or not html_file.is_file():
        return None
    try:
        cmd = [
            str(CHROME_PATH),
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--dump-dom",
            html_file.as_uri()
        ]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        if res.returncode == 0:
            return res.stdout
    except Exception:
        pass
    return None
