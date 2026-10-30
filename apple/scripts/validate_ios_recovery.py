#!/usr/bin/env python3
"""Discovery and structured-result validation for the iOS recovery gate.

This helper is CI evidence tooling only: it picks a real testable package scheme and an
installed iOS 16+ simulator, and later parses the xcresult tree to confirm that the
requested recovery suite actually executed nonzero tests with zero failures. It never
claims interactive app acceptance and never inspects user directories.

Modes:
  discover  --schemes FILE --devices FILE --evidence DIR
            Writes picked-scheme.txt, picked-destination.txt and discovery.json.
  validate  --xcresult PATH --evidence DIR [--suite NAME]
            Writes ios-recovery-summary.json; exits nonzero when a gate fails.
  --self-test
            Runs the parser and gate logic against embedded synthetic fixtures
            (success, zero tests, failed test, wrong suite, legacy value wrappers).
"""

import argparse
import json
import os
import re
import subprocess
import sys

RUNTIME_PREFIX = "com.apple.CoreSimulator.SimRuntime.iOS-"


def _unwrap(node):
    """Recursively unwrap xcresulttool legacy {"_value": ...} wrappers."""
    if isinstance(node, dict):
        if set(node.keys()) >= {"_type", "_value"}:
            return _unwrap(node["_value"])
        return {key: _unwrap(value) for key, value in node.items()}
    if isinstance(node, list):
        return [_unwrap(item) for item in node]
    return node


def _suite_report(tree, suite_name):
    """Find the named suite in a normalized test tree and count its test cases."""
    results = {"executed": 0, "failed": 0, "skipped": 0, "suite_found": False}

    def walk(node, inside):
        if not isinstance(node, dict):
            return
        name = node.get("name") or node.get("title")
        node_type = node.get("nodeType") or node.get("node_type") or node.get("type")
        child_nodes = node.get("children") or node.get("testNodes") or []
        if name == suite_name:
            inside = True
            results["suite_found"] = True
        if inside and isinstance(name, str) and ("Test Case" in str(node_type) or node_type == "Test Case"):
            outcome = str(node.get("result") or node.get("outcome") or "")
            if outcome in ("Passed", "passed", "Success"):
                results["executed"] += 1
            elif outcome in ("Failed", "failed"):
                results["executed"] += 1
                results["failed"] += 1
            elif outcome in ("Skipped", "skipped"):
                results["skipped"] += 1
            else:
                results["executed"] += 1
                results["failed"] += 1
        for child in child_nodes if isinstance(child_nodes, list) else []:
            walk(child, inside)

    walk(tree, False)
    return results


def _read_xcresult_tree(path):
    """Return the normalized test tree for an xcresult, trying both tool APIs."""
    new = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", path, "--format", "json"],
        capture_output=True, text=True)
    if new.returncode == 0 and new.stdout.strip():
        return json.loads(new.stdout), "test-results-summary"
    legacy = subprocess.run(
        ["xcrun", "xcresulttool", "get", "--format", "json", "--path", path],
        capture_output=True, text=True)
    if legacy.returncode != 0:
        raise SystemExit(f"xcresulttool could not read {path}: {legacy.stderr.strip()[:400]}")
    top = json.loads(legacy.stdout)
    actions = _unwrap(top).get("actions", {})
    entries = actions.get("_values") if isinstance(actions, dict) else actions
    if not entries:
        raise SystemExit("xcresult has no recorded actions")
    tests_ref = entries[0].get("actionResult", {}).get("testsRef", {})
    ref_id = tests_ref.get("id")
    if not ref_id:
        raise SystemExit("xcresult has no tests reference")
    detail = subprocess.run(
        ["xcrun", "xcresulttool", "get", "--format", "json", "--path", path, "--id", str(ref_id)],
        capture_output=True, text=True)
    if detail.returncode != 0:
        raise SystemExit(f"xcresulttool could not read tests {ref_id}: {detail.stderr.strip()[:400]}")
    return _unwrap(json.loads(detail.stdout)), "legacy-json-object"


def _gate(report, suite_name):
    failures = []
    if not report["suite_found"]:
        failures.append(f"suite {suite_name} not present in the result tree")
    if report["executed"] <= 0:
        failures.append("zero test cases executed")
    if report["failed"] != 0:
        failures.append(f"{report['failed']} test case(s) failed")
    return failures


def _pick_scheme(schemes):
    for candidate in schemes:
        if candidate == "OJNexus-Package":
            return candidate
    for candidate in schemes:
        if candidate.endswith("-Package"):
            return candidate
    for candidate in schemes:
        if candidate == "OJNexusCore":
            return candidate
    if not schemes:
        raise SystemExit("xcodebuild reported no schemes")
    return schemes[0]


def _pick_simulator(devices):
    best = None
    for runtime_key, entries in devices.items():
        if not runtime_key.startswith(RUNTIME_PREFIX):
            continue
        parts = runtime_key[len(RUNTIME_PREFIX):].split("-")
        try:
            version = tuple(int(part) for part in parts)
        except ValueError:
            continue
        if version < (16, 0):
            continue
        for entry in entries if isinstance(entries, list) else []:
            if not isinstance(entry, dict):
                continue
            available = entry.get("isAvailable", entry.get("state") in ("Shutdown", "Booted", True))
            if available is not True and entry.get("state") not in ("Shutdown", "Booted"):
                continue
            name = str(entry.get("name") or "")
            udid = entry.get("udid")
            if not udid:
                continue
            rank = (1 if name.startswith("iPhone") else 0, version)
            if best is None or rank > best[0]:
                best = (rank, {"udid": udid, "name": name, "runtime": runtime_key})
    if best is None:
        raise SystemExit("no installed iOS 16+ simulator found")
    return best[1]


def cmd_discover(args):
    with open(args.schemes, "r", encoding="utf-8") as handle:
        scheme_doc = json.load(handle)
    with open(args.devices, "r", encoding="utf-8") as handle:
        device_doc = json.load(handle)
    schemes = scheme_doc.get("schemes") or []
    scheme = _pick_scheme(schemes)
    simulator = _pick_simulator(device_doc.get("devices") or {})
    destination = f"platform=iOS Simulator,id={simulator['udid']}"
    with open(os.path.join(args.evidence, "picked-scheme.txt"), "w", encoding="utf-8") as handle:
        handle.write(scheme + "\n")
    with open(os.path.join(args.evidence, "picked-destination.txt"), "w", encoding="utf-8") as handle:
        handle.write(destination + "\n")
    with open(os.path.join(args.evidence, "discovery.json"), "w", encoding="utf-8") as handle:
        json.dump({"scheme": scheme, "schemes_seen": schemes, "simulator": simulator}, handle, indent=2)
    print(f"picked scheme={scheme} simulator={simulator['name']} runtime={simulator['runtime']}")


def cmd_validate(args):
    tree, api = _read_xcresult_tree(args.xcresult)
    report = _suite_report(_unwrap(tree), args.suite)
    failures = _gate(report, args.suite)
    scheme = destination = ""
    for name, key in (("picked-scheme.txt", "scheme"), ("picked-destination.txt", "destination")):
        path = os.path.join(args.evidence, name)
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as handle:
                value = handle.read().strip()
            if key == "scheme":
                scheme = value
            else:
                destination = value
    summary = {
        "commit": os.environ.get("GITHUB_SHA", "local"),
        "suite": args.suite,
        "scheme": scheme,
        "destination": destination,
        "executed": report["executed"],
        "failed": report["failed"],
        "skipped": report["skipped"],
        "suite_found": report["suite_found"],
        "toolchain_api": api,
        "result": "pass" if not failures else "fail",
        "failures": failures,
    }
    out_path = os.path.join(args.evidence, "ios-recovery-summary.json")
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2)
    print(json.dumps(summary, indent=2))
    if failures:
        raise SystemExit("; ".join(failures))


def cmd_self_test():
    def suite(name, cases, node_type="Test Case"):
        return {"nodeType": "Test Suite", "name": name,
                "children": [{"nodeType": node_type, "name": case, "result": outcome}
                             for case, outcome in cases]}

    normalized_pass = {"testNodes": [suite("WorkspaceRecoveryTests", [
        ("testA", "Passed"), ("testB", "Passed")])]}
    normalized_zero = {"testNodes": [suite("WorkspaceRecoveryTests", [])]}
    normalized_failed = {"testNodes": [suite("WorkspaceRecoveryTests", [
        ("testA", "Passed"), ("testB", "Failed")])]}
    normalized_wrong = {"testNodes": [suite("OtherTests", [("testA", "Passed")])]}
    legacy_wrapped = _unwrap({"testNodes": [{"_type": {}, "_value": suite("WorkspaceRecoveryTests", [
        ("testA", {"_type": {}, "_value": "Passed"})])}]})

    expectations = [
        (normalized_pass, {"suite_found": True, "executed": 2, "failed": 0}, True),
        (normalized_zero, {"suite_found": True, "executed": 0, "failed": 0}, False),
        (normalized_failed, {"suite_found": True, "executed": 2, "failed": 1}, False),
        (normalized_wrong, {"suite_found": False, "executed": 0, "failed": 0}, False),
        (legacy_wrapped, {"suite_found": True, "executed": 1, "failed": 0}, True),
    ]
    for index, (tree, expected, should_pass) in enumerate(expectations):
        report = _suite_report(tree, "WorkspaceRecoveryTests")
        for key, value in expected.items():
            if report.get(key) != value:
                raise SystemExit(f"self-test {index}: {key}={report.get(key)} expected {value}")
        gate = _gate(report, "WorkspaceRecoveryTests")
        if bool(gate) == should_pass:
            raise SystemExit(f"self-test {index}: gate={gate} expected pass={should_pass}")
    print("self-test: 5/5 fixture expectations met")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--self-test", action="store_true", help="run embedded fixture checks")
    subparsers = parser.add_subparsers(dest="command")

    discover = subparsers.add_parser("discover")
    discover.add_argument("--schemes", required=True)
    discover.add_argument("--devices", required=True)
    discover.add_argument("--evidence", required=True)
    discover.set_defaults(func=cmd_discover)

    validate = subparsers.add_parser("validate")
    validate.add_argument("--xcresult", required=True)
    validate.add_argument("--evidence", required=True)
    validate.add_argument("--suite", default="WorkspaceRecoveryTests")
    validate.set_defaults(func=cmd_validate)

    args = parser.parse_args()
    if args.self_test:
        cmd_self_test()
        return
    if args.command == "discover":
        cmd_discover(args)
        return
    if args.command == "validate":
        cmd_validate(args)
        return
    parser.error("choose discover, validate or --self-test")


if __name__ == "__main__":
    sys.exit(main())
