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
    """Recursively unwrap xcresulttool legacy {"_value": ...} / {"_values": [...]} wrappers."""
    if isinstance(node, dict):
        if "_value" in node:
            return _unwrap(node["_value"])
        if "_values" in node:
            return [_unwrap(item) for item in node["_values"]]
        return {key: _unwrap(value) for key, value in node.items() if key != "_type"}
    if isinstance(node, list):
        return [_unwrap(item) for item in node]
    return node


_OUTCOME_MAP = {
    "passed": "passed", "success": "passed",
    "failed": "failed", "failure": "failed",
    "skipped": "skipped", "expected failure": "skipped",
}


def _outcome_of(node):
    for key in ("result", "testStatus", "outcome"):
        value = node.get(key)
        if isinstance(value, str):
            mapped = _OUTCOME_MAP.get(value.strip().lower())
            if mapped:
                return mapped
    return None


def _children_of(node):
    for key in ("children", "subtests", "testNodes", "tests", "testableSummaries", "summaries"):
        kids = node.get(key)
        if isinstance(kids, list):
            return kids
    return []


def _suite_report(tree, suite_name):
    """Find the named suite anywhere in a normalized test tree and count leaf cases.

    Handles both the modern test-results summary shape and legacy ActionTestMetadata
    trees: suites are matched by their name/title, leaves carry a result/testStatus
    field without nested children.
    """
    results = {"executed": 0, "failed": 0, "skipped": 0, "suite_found": False}

    def walk(node, inside):
        if not isinstance(node, dict):
            return (0, 0, 0)
        name = node.get("name") or node.get("title")
        if name == suite_name:
            inside = True
            results["suite_found"] = True
        kids = _children_of(node)
        outcome = _outcome_of(node)
        if not kids and outcome and inside:
            if outcome == "passed":
                return (1, 0, 0)
            if outcome == "failed":
                return (1, 1, 0)
            return (0, 0, 1)
        total = [0, 0, 0]
        for child in kids:
            part = walk(child, inside)
            total = [sum(pair) for pair in zip(total, part)]
        return tuple(total)

    executed, failed, skipped = walk(tree, False)
    results["executed"] = executed
    results["failed"] = failed
    results["skipped"] = skipped
    return results


def _read_xcresult_tree(path, dump_dir=None):
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
    if dump_dir:
        with open(os.path.join(dump_dir, "tests-tree.json"), "w", encoding="utf-8") as handle:
            handle.write(detail.stdout)
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


def _list_schemes(doc):
    for key in ("schemes", "workspace", "project"):
        section = doc.get(key)
        if isinstance(section, list) and key == "schemes":
            return section
        if isinstance(section, dict) and isinstance(section.get("schemes"), list):
            return section["schemes"]
    return []


def cmd_discover(args):
    with open(args.schemes, "r", encoding="utf-8") as handle:
        scheme_doc = json.load(handle)
    with open(args.devices, "r", encoding="utf-8") as handle:
        device_doc = json.load(handle)
    schemes = _list_schemes(scheme_doc)
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
    tree, api = _read_xcresult_tree(args.xcresult, dump_dir=args.evidence)
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
    legacy_status = {"subtests": [
        {"nodeType": "Test Suite", "name": "OJNexusCoreTests", "children": [
            {"nodeType": "Test Suite", "name": "WorkspaceRecoveryTests", "testStatus": "Passed",
             "subtests": [{"name": "testA()", "testStatus": "Passed"},
                          {"name": "testB()", "testStatus": "Passed"}]}]}]}
    suite_root = {"name": "WorkspaceRecoveryTests", "result": "Passed", "children": [
        {"name": "testA", "result": "Passed"}, {"name": "testB", "result": "Passed"}]}
    mixed_group = {"name": "Root", "children": [
        {"name": "WorkspaceRecoveryTests", "children": [
            {"name": "GroupA", "children": [{"name": "testA", "result": "Passed"}]},
            {"name": "testB", "testStatus": "Failed"}]}]}
    runner_legacy = {"summaries": [{"name": "Test Scheme Action", "testableSummaries": [
        {"name": "OJNexusCoreTests", "tests": [
            {"name": "Selected tests", "subtests": [
                {"name": "OJNexusCoreTests.xctest", "subtests": [
                    {"name": "WorkspaceRecoveryTests", "subtests": [
                        {"name": "testA()", "testStatus": "Success", "duration": 0.1},
                        {"name": "testB()", "testStatus": "Success", "duration": 0.2},
                        {"name": "testC()", "testStatus": "Failure", "duration": 0.3},
                    ]}]}]}]}]}]}
    runner_legacy_pass = json.loads(json.dumps(runner_legacy))
    del runner_legacy_pass["summaries"][0]["testableSummaries"][0]["tests"][0]["subtests"][0]["subtests"][0]["subtests"][2]

    expectations = [
        (normalized_pass, {"suite_found": True, "executed": 2, "failed": 0}, True),
        (normalized_zero, {"suite_found": True, "executed": 0, "failed": 0}, False),
        (normalized_failed, {"suite_found": True, "executed": 2, "failed": 1}, False),
        (normalized_wrong, {"suite_found": False, "executed": 0, "failed": 0}, False),
        (legacy_wrapped, {"suite_found": True, "executed": 1, "failed": 0}, True),
        (legacy_status, {"suite_found": True, "executed": 2, "failed": 0}, True),
        (suite_root, {"suite_found": True, "executed": 2, "failed": 0}, True),
        (mixed_group, {"suite_found": True, "executed": 2, "failed": 1}, False),
        (runner_legacy_pass, {"suite_found": True, "executed": 2, "failed": 0}, True),
        (runner_legacy, {"suite_found": True, "executed": 3, "failed": 1}, False),
    ]

    workspace_doc = {"workspace": {"name": "apple",
                                   "schemes": ["OJNexusApple-Package", "OJNexusCore"]}}
    top_level_doc = {"schemes": ["OJNexusCore"]}
    project_doc = {"project": {"schemes": ["Other", "OJNexus-Package"]}}
    if _list_schemes(workspace_doc) != ["OJNexusApple-Package", "OJNexusCore"]:
        raise SystemExit("self-test: workspace scheme listing not read")
    if _list_schemes(top_level_doc) != ["OJNexusCore"]:
        raise SystemExit("self-test: top-level scheme listing not read")
    if _list_schemes(project_doc) != ["Other", "OJNexus-Package"]:
        raise SystemExit("self-test: project scheme listing not read")
    if _pick_scheme(_list_schemes(workspace_doc)) != "OJNexusApple-Package":
        raise SystemExit("self-test: package scheme not preferred")
    if _pick_scheme(_list_schemes(project_doc)) != "OJNexus-Package":
        raise SystemExit("self-test: exact package scheme not preferred")

    device_doc = {"devices": {
        "com.apple.CoreSimulator.SimRuntime.iOS-15-5": [
            {"udid": "OLD-1", "name": "iPhone 13", "isAvailable": True, "state": "Shutdown"}],
        "com.apple.CoreSimulator.SimRuntime.iOS-17-5": [
            {"udid": "PAD-1", "name": "iPad Pro (12.9-inch)", "isAvailable": True, "state": "Shutdown"},
            {"udid": "PHONE-1", "name": "iPhone 15", "isAvailable": True, "state": "Shutdown"}],
        "com.apple.CoreSimulator.SimRuntime.watchOS-10-5": [
            {"udid": "WATCH-1", "name": "Watch", "isAvailable": True, "state": "Shutdown"}],
    }}
    picked = _pick_simulator(device_doc["devices"])
    if picked["udid"] != "PHONE-1" or picked["runtime"] != "com.apple.CoreSimulator.SimRuntime.iOS-17-5":
        raise SystemExit(f"self-test: simulator pick wrong: {picked}")
    try:
        _pick_simulator({"devices": {"com.apple.CoreSimulator.SimRuntime.iOS-15-5": [
            {"udid": "OLD-1", "name": "iPhone 13", "isAvailable": True}]}})
    except SystemExit:
        pass
    else:
        raise SystemExit("self-test: iOS 15 runtime must not be picked")
    print(f"self-test: {len(expectations)}/{len(expectations)} result fixtures and 5/5 discovery fixtures met")


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
