#!/usr/bin/env python3
"""
Master Test Runner: Integrated IoT System E2E Test Suite
Executes test suites across Tiers 1-4 and reports structured diagnostic summaries.

Usage:
    python tests/run_all_tests.py [--tier 1|2|3|4] [--verbose]

Exit codes:
    0: All executed tests passed.
    1: One or more tests failed or encountered errors.
"""

import sys
import os
import time
import argparse
import unittest
from pathlib import Path

# Add project root to path for imports
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from tests.test_tier1_features import TestTier1FeatureCoverage
from tests.test_tier2_boundaries import TestTier2Boundaries
from tests.test_tier3_interactions import TestTier3Interactions
from tests.test_tier4_scenarios import TestTier4Scenarios


class TierSummary:
    def __init__(self, name: str, test_class):
        self.name = name
        self.test_class = test_class
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.errors = 0
        self.duration = 0.0
        self.failure_details = []


def run_tier(tier: TierSummary, verbosity: int = 1) -> unittest.TestResult:
    """Executes a single test tier and records summary metrics."""
    loader = unittest.TestLoader()
    suite = loader.loadTestsFromTestCase(tier.test_class)
    tier.total = suite.countTestCases()

    # Custom runner to capture output cleanly
    runner = unittest.TextTestRunner(verbosity=verbosity, stream=sys.stdout if verbosity > 1 else open(os.devnull, "w"))

    start_time = time.time()
    result = runner.run(suite)
    tier.duration = time.time() - start_time

    tier.failed = len(result.failures)
    tier.errors = len(result.errors)
    tier.passed = tier.total - tier.failed - tier.errors

    for test_case, traceback_str in result.failures:
        tier.failure_details.append(("FAIL", test_case.id(), traceback_str))
    for test_case, traceback_str in result.errors:
        tier.failure_details.append(("ERROR", test_case.id(), traceback_str))

    return result


def print_banner():
    print("=" * 80)
    print("      INTEGRATED IoT SYSTEM: OPAQUE-BOX E2E TEST RUNNER (TIERS 1-4)      ")
    print("=" * 80)
    print("  Target: Mosquitto Broker + ESP32 Wokwi + Web Dashboard + CLI Scripts")
    print("  Standard: docs/2.TH.docx | docs/13.wokwi.pptx | docs/2.MOS - Copy.pptx")
    print("=" * 80)
    print()


def print_results_table(tiers):
    print("+" + "-" * 40 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 10 + "+")
    print(f"| {'Test Suite / Tier':<38} | {'Total':>6} | {'Pass':>6} | {'Fail':>6} | {'Err':>6} | {'Time (s)':>8} |")
    print("+" + "=" * 40 + "+" + "=" * 8 + "+" + "=" * 8 + "+" + "=" * 8 + "+" + "=" * 8 + "+" + "=" * 10 + "+")

    total_all = 0
    passed_all = 0
    failed_all = 0
    errors_all = 0
    time_all = 0.0

    for t in tiers:
        print(f"| {t.name:<38} | {t.total:>6} | {t.passed:>6} | {t.failed:>6} | {t.errors:>6} | {t.duration:>8.2f} |")
        total_all += t.total
        passed_all += t.passed
        failed_all += t.failed
        errors_all += t.errors
        time_all += t.duration

    print("+" + "-" * 40 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 8 + "+" + "-" * 10 + "+")
    print(f"| {'TOTAL / COMBINED':<38} | {total_all:>6} | {passed_all:>6} | {failed_all:>6} | {errors_all:>6} | {time_all:>8.2f} |")
    print("+" + "=" * 40 + "+" + "=" * 8 + "+" + "=" * 8 + "+" + "=" * 8 + "+" + "=" * 8 + "+" + "=" * 10 + "+")
    print()

    return failed_all + errors_all


def print_failure_details(tiers):
    has_issues = False
    for t in tiers:
        if t.failure_details:
            if not has_issues:
                print("=" * 80)
                print("                        DIAGNOSTIC FAILURE DETAILS                        ")
                print("=" * 80)
                has_issues = True
            print(f"\n--- Suite: {t.name} ({len(t.failure_details)} issues) ---")
            for status, test_id, tb in t.failure_details:
                print(f"\n[{status}] {test_id}")
                # Print indented traceback
                for line in tb.strip().splitlines():
                    print(f"    {line}")
    if has_issues:
        print("\n" + "=" * 80)


def main():
    parser = argparse.ArgumentParser(description="Integrated IoT System Master Test Runner")
    parser.add_argument("--tier", type=int, choices=[1, 2, 3, 4], help="Run a specific tier (1, 2, 3, or 4)")
    parser.add_argument("-v", "--verbose", action="store_true", help="Enable verbose test output")
    args = parser.parse_args()

    all_tier_configs = [
        TierSummary("Tier 1: Feature Coverage (24 features)", TestTier1FeatureCoverage),
        TierSummary("Tier 2: Boundary & Corner Cases", TestTier2Boundaries),
        TierSummary("Tier 3: Cross-Feature Interactions", TestTier3Interactions),
        TierSummary("Tier 4: Real-World Application Scenarios", TestTier4Scenarios),
    ]

    selected_tiers = []
    if args.tier:
        selected_tiers = [all_tier_configs[args.tier - 1]]
    else:
        selected_tiers = all_tier_configs

    print_banner()
    verbosity = 2 if args.verbose else 1

    for tier in selected_tiers:
        print(f"[*] Executing {tier.name}...")
        run_tier(tier, verbosity=verbosity)

    print()
    total_failures = print_results_table(selected_tiers)

    if total_failures > 0:
        print_failure_details(selected_tiers)
        print(f"\n[!] RESULT: OVERALL TEST SUITE FAILED with {total_failures} issue(s).")
        return 1
    else:
        print("\n[+] RESULT: ALL TESTS PASSED SUCCESSFULLY! (100% Pass Rate)")
        return 0


if __name__ == "__main__":
    sys.exit(main())
