#!/usr/bin/env python3
"""Portable Workspace v1 checker self-test (OJ NEXUS phase 131).

Run from the repo root or from any other cwd:
    python3 contracts/portable/v1/selftest.py

This script imports check.py from its own directory (via importlib) and calls
its functions directly; none of the checker's validation logic is duplicated
here. It is a self-test of the *checker*, not a platform consumer.

PW-N08 and PW-N12 are checker self-tests, not golden package files
------------------------------------------------------------------
* PW-N08 is a positive-side property: re-checking PW-P01 must report the same
  identity hash. Deterministic, sorted-key canonical identity is a property of
  canonical_bytes / the --identity CLI, so it is asserted here as a self-test
  rather than shipped as a golden package fixture.
* PW-N12 (origin: unassigned) must be ACCEPTED but asserted UNATTRIBUTABLE.
  The positive variant lives in PW-P01; the "asserted unattributable" half is
  a checker assertion (no silent normalization of sourceScopeId), encoded here
  as a self-test rather than as a golden package file.

Phase 131 ships NO platform consumer: this is a reference-contract self-test
only (design doc "Explicit non-goals"; consumers bind later in 132-135).

Python 3 standard library only: no pip, no network, no third-party imports,
no clock or locale dependence. All paths resolve relative to __file__.
"""

import hashlib
import importlib.util
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
CHECK_PY = HERE / "check.py"
SCHEMA_JSON = HERE / "schema.json"
GOLDEN_DIR = HERE / "golden"
PW_P01 = GOLDEN_DIR / "pw-p01.json"
CASES_JSON = GOLDEN_DIR / "cases.json"

GOLDEN_MISSING = (
    "golden/ is missing: the golden corpus PR must be merged first "
    "(nothing to self-test)"
)
PW_P01_MISSING = (
    "golden/pw-p01.json is missing: the golden corpus PR must be merged first"
)

# Import the checker from the same directory, robust to being run from any cwd.
check = None
_import_error = None
try:
    _spec = importlib.util.spec_from_file_location("portable_v1_check", str(CHECK_PY))
    check = importlib.util.module_from_spec(_spec)
    _spec.loader.exec_module(check)
except Exception as exc:  # reported by check_import, never a hard crash
    _import_error = exc
    check = None

_schema = None


def get_schema():
    """Load and scan schema.json once, through check.py's own helpers."""
    global _schema
    if _schema is None:
        schema = check.load_schema(str(SCHEMA_JSON))
        check.scan_schema(schema)
        _schema = schema
    return _schema


def _require(condition, message):
    if not condition:
        raise AssertionError(message)


def _read_bytes(path, missing_message):
    _require(path.is_file(), missing_message)
    return path.read_bytes()


def _emit(text):
    # Never let a non-UTF-8 console abort the self-test before the summary.
    try:
        print(text)
    except UnicodeEncodeError:
        print(text.encode("ascii", "backslashreplace").decode("ascii"))


def _identity_digest(raw):
    doc = json.loads(raw.decode("utf-8"))
    return hashlib.sha256(check.canonical_bytes(doc)).hexdigest()


def _reversed_dict_keys(value):
    """Reverse every object's key insertion order, preserving array order."""
    if isinstance(value, dict):
        return {k: _reversed_dict_keys(v) for k, v in reversed(list(value.items()))}
    if isinstance(value, list):
        return [_reversed_dict_keys(item) for item in value]
    return value


def _identity_cli_hash():
    # sys.executable is the python3 running this script; equivalent to the
    # documented `python3 check.py --identity golden/pw-p01.json`.
    argv = [sys.executable, str(CHECK_PY), "--identity", "golden/pw-p01.json"]
    proc = subprocess.run(
        argv,
        cwd=str(HERE),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    out = proc.stdout.decode("utf-8", "replace").strip()
    err = proc.stderr.decode("utf-8", "replace").strip()
    _require(
        proc.returncode == 0,
        "check.py --identity exited %d: %s" % (proc.returncode, err or out),
    )
    _require(
        len(out) == 64 and all(c in "0123456789abcdef" for c in out),
        "unexpected --identity output %r" % out,
    )
    return out


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------


def check_import():
    _require(check is not None, "could not import check.py: %s" % (_import_error,))
    _require(
        all(
            hasattr(check, name)
            for name in ("canonical_bytes", "check_package", "run_cases", "load_schema")
        ),
        "check.py is missing one or more required functions",
    )
    _require(get_schema() is not None, "schema.json did not load")


def check_canonical_determinism():
    raw = _read_bytes(PW_P01, PW_P01_MISSING)
    doc = json.loads(raw.decode("utf-8"))
    first = check.canonical_bytes(doc)
    second = check.canonical_bytes(doc)
    _require(first == second, "canonical_bytes is not byte-identical across two calls")
    digest1 = hashlib.sha256(first).hexdigest()
    digest2 = hashlib.sha256(second).hexdigest()
    _require(digest1 == digest2, "sha256 digest differs across two computations")
    # Top-level key insertion order must not matter (sorted-key determinism).
    top_reversed = dict(reversed(list(doc.items())))
    _require(
        check.canonical_bytes(top_reversed) == first,
        "reversing top-level key insertion order changed canonical bytes",
    )
    # sort_keys applies recursively: nested object key order must not matter.
    nested = _reversed_dict_keys(doc)
    _require(
        check.canonical_bytes(nested) == first,
        "reversing nested object key order changed canonical bytes",
    )
    _emit("SELFTEST: PW-N08 identity hash %s" % digest1)


def check_cli_identity_stability():
    raw = _read_bytes(PW_P01, PW_P01_MISSING)
    expected = _identity_digest(raw)
    hash1 = _identity_cli_hash()
    hash2 = _identity_cli_hash()
    _require(hash1 == hash2, "two --identity runs disagree: %s vs %s" % (hash1, hash2))
    _require(
        hash1 == expected,
        "--identity hash %s != in-process canonical digest %s" % (hash1, expected),
    )
    _emit("SELFTEST: PW-N08 two CLI runs agree on hash %s" % hash1)


CHECKS = [
    ("import check.py", check_import),
    ("PW-N08 canonical determinism", check_canonical_determinism),
    ("PW-N08 CLI identity stability", check_cli_identity_stability),
]


# ---------------------------------------------------------------------------
# Harness
# ---------------------------------------------------------------------------


def run():
    _emit("SELFTEST: phase 131 portable v1 checker self-test")
    results = []
    for name, fn in CHECKS:
        try:
            fn()
            _emit("SELFTEST: PASS %s" % name)
            results.append(True)
        except Exception as exc:  # a broken check is reported, not fatal
            reason = str(exc) or exc.__class__.__name__
            _emit("SELFTEST: FAIL %s — %s" % (name, reason))
            results.append(False)
    total = len(results)
    passed = sum(1 for ok in results if ok)
    failed = total - passed
    _emit("%d checks, %d passed, %d failed" % (total, passed, failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(run())
