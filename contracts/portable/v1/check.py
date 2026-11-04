#!/usr/bin/env python3
r"""Portable Workspace v1 reference checker (OJ NEXUS phase 131).

Python 3 standard library only: no pip installs, no network access, no
locale or clock dependence. This is a reference implementation of the
contract in docs/superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md,
driven by the draft-07 subset schema.json that lives next to this script.

Usage:
    check.py <file>                 validate one package; exit 0 valid / 1 invalid
    check.py --cases CASES GOLDEN   run every golden case; exit 0 only if every
                                    actual outcome matches its expectation
    check.py --identity FILE        print the SHA-256 of the canonical re-encode
                                    of a valid package (deterministic across runs)
    check.py --schema PATH          explicit schema.json path (self-testing aid;
                                    default is schema.json next to this script)

Exit codes:
    0  valid package / every case matched its expectation
    1  invalid package (category + detail on stderr) / at least one case deviated
    2  checker misuse: bad arguments, unreadable package file, missing or
       unusable schema.json, unreadable/malformed cases file

Error categories (exact strings; the first failing category wins and at most
one error is reported, always with its JSONPath-ish location). The pipeline
order below is the precedence order:

    1. SIZE_LIMIT         package larger than 64 MiB (checked before decode)
    2. INVALID_INPUT      non-UTF-8 bytes, malformed JSON, trailing data,
                          NaN/Infinity, duplicate object keys anywhere, lone
                          surrogate escapes, non-object root, missing or
                          malformed formatVersion
    3. UNSUPPORTED_VERSION formatVersion present as an integer but != 1
    4. UNKNOWN_FIELD      any draft-07 subset schema violation (type, required,
                          additionalProperties:false, enum, const, pattern,
                          minLength/maxLength, minimum/maximum with the
                          draft-07 boolean exclusiveMinimum, items, minItems/
                          maxItems), credential-shaped keys anywhere (including
                          inside `extra`), `extra` values deeper than the
                          2-level limit (scalar or array of scalars)
    5. SIZE_LIMIT         entity collection longer than 50000 records, any
                          string longer than 16384 UTF-8 bytes
    6. PATH_UNSAFE        string value matching (^([A-Za-z]:\\)|^/|\.\./|^\.\.$)
                          - v1 has no file fields; this is defense in depth
    7. DUPLICATE_ID       duplicate stableId inside a collection, or duplicate
                          problemId inside notes[] (one note per problem)
    8. MISSING_REFERENCE  attempts[].problemId, notes[].problemId,
                          reviews[].problemId and sessions[].problemIds[]
                          entries absent from problems[].stableId
    9. COUNTS_MISMATCH    manifest counts disagreeing with actual array lengths

Mapping note (resolved ambiguity): the category list has no dedicated
"schema violation" category, so every draft-07 subset violation maps to
UNKNOWN_FIELD and its detail names the keyword, the offending key or value,
and the path. Enum violations are UNKNOWN_FIELD by contract.
"""

import argparse
import hashlib
import json
import os
import re
import sys

MAX_PACKAGE_BYTES = 64 * 1024 * 1024  # 64 MiB, inclusive
MAX_RECORDS = 50000                   # per entity collection
MAX_STRING_BYTES = 16384              # 16 KiB, UTF-8, any single string

CATEGORIES = (
    "INVALID_INPUT",
    "UNSUPPORTED_VERSION",
    "UNKNOWN_FIELD",
    "DUPLICATE_ID",
    "MISSING_REFERENCE",
    "SIZE_LIMIT",
    "PATH_UNSAFE",
    "COUNTS_MISMATCH",
)

COLLECTIONS = ("problems", "attempts", "notes", "reviews", "sessions")

# v1 has no file fields; any string shaped like a filesystem path is rejected
# as defense in depth. Applied to string values only.
PATH_UNSAFE_RE = re.compile(r"(^([A-Za-z]:\\)|^/|\.\./|^\.\.$)")

# Credential-shaped object keys are rejected wherever they appear, including
# inside the `extra` extension point: credentials are never portable. Keys are
# normalized (lowercased, "_"/"-"/"."/spaces removed) and matched by substring
# so `token`, `authToken`, `AUTH_TOKEN`, `my-secret` all trip the guard. No
# schema-declared key normalizes to anything containing these tokens.
CREDENTIAL_TOKENS = (
    "token",
    "password",
    "secret",
    "cookie",
    "credential",
    "apikey",
    "authorization",
    "sessionid",
    "privatekey",
    "accesskey",
)
_CREDENTIAL_NORMALIZE_RE = re.compile(r"[\s._\-]+")

# Identifier/annotation keywords that never constrain validation.
BENIGN_SCHEMA_KEYWORDS = frozenset(
    ("$schema", "$id", "$comment", "title", "description", "default", "examples")
)

# The deliberate draft-07 subset this checker implements. Anything else is a
# hard configuration error so the subset stays honest.
SUPPORTED_SCHEMA_KEYWORDS = frozenset(
    (
        "type",
        "properties",
        "required",
        "additionalProperties",
        "enum",
        "const",
        "pattern",
        "minLength",
        "maxLength",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "items",
        "minItems",
        "maxItems",
    )
)

JSON_TYPES = ("object", "array", "string", "integer", "number", "boolean", "null")

_KEY_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_-]*$")


class ContractError(Exception):
    """A package defect: one category, one detail, one location."""

    def __init__(self, category, detail, location="$"):
        super().__init__(detail)
        self.category = category
        self.detail = detail
        self.location = location


class ConfigError(Exception):
    """Checker misuse: unusable schema, cases file or invocation."""


# ---------------------------------------------------------------------------
# JSONPath-ish location helpers
# ---------------------------------------------------------------------------


def child_path(path, key):
    if _KEY_RE.match(key):
        return "%s.%s" % (path, key)
    return "%s[%s]" % (path, json.dumps(key, ensure_ascii=True))


def index_path(path, index):
    return "%s[%d]" % (path, index)


def _type_name(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def _json_repr(value):
    try:
        return json.dumps(value, ensure_ascii=False)
    except (TypeError, ValueError):
        return repr(value)


def _truncate(text, limit=120):
    if len(text) <= limit:
        return text
    return text[: limit - 3] + "..."


# ---------------------------------------------------------------------------
# Strict JSON decode with duplicate-key locations
# ---------------------------------------------------------------------------


class _ObjNode(object):
    """Placeholder for a decoded JSON object; pairs keep document order."""

    __slots__ = ("pairs",)

    def __init__(self, pairs):
        self.pairs = pairs


def _object_pairs_hook(pairs):
    return _ObjNode(pairs)


def _reject_constant(name):
    raise ValueError("JSON constant %s is not valid strict JSON" % name)


def _has_surrogate(text):
    for char in text:
        if 0xD800 <= ord(char) <= 0xDFFF:
            return True
    return False


def _materialize(node, path):
    """Convert the _ObjNode tree into plain containers in document order.

    Duplicate object keys and lone surrogate escapes are raised here, where
    the exact location is known. This walk is the only place duplicate keys
    can be detected with a path: the stdlib object_pairs_hook fires bottom-up
    without parent context, so detection is deferred to this top-down pass.
    """
    if isinstance(node, _ObjNode):
        out = {}
        seen = set()
        for key, value in node.pairs:
            key_path = child_path(path, key)
            if key in seen:
                raise ContractError(
                    "INVALID_INPUT", 'duplicate object key "%s"' % key, key_path
                )
            seen.add(key)
            if _has_surrogate(key):
                raise ContractError(
                    "INVALID_INPUT",
                    'object key "%s" contains a lone surrogate escape' % key,
                    key_path,
                )
            out[key] = _materialize(value, key_path)
        return out
    if isinstance(node, list):
        return [
            _materialize(item, index_path(path, index))
            for index, item in enumerate(node)
        ]
    if isinstance(node, str):
        if _has_surrogate(node):
            raise ContractError(
                "INVALID_INPUT", "string value contains a lone surrogate escape", path
            )
        return node
    if node is None or isinstance(node, (bool, int, float)):
        return node
    raise ContractError("INVALID_INPUT", "unsupported JSON value", path)


def decode_package(raw_bytes):
    """Strictly decode one package into plain Python containers.

    Raises ContractError(SIZE_LIMIT) for an over-limit file and
    ContractError(INVALID_INPUT) for every decode-level problem.
    """
    if len(raw_bytes) > MAX_PACKAGE_BYTES:
        raise ContractError(
            "SIZE_LIMIT",
            "package is %d bytes, limit is %d bytes (%d MiB)"
            % (len(raw_bytes), MAX_PACKAGE_BYTES, MAX_PACKAGE_BYTES // (1024 * 1024)),
            "$",
        )
    try:
        text = raw_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ContractError("INVALID_INPUT", "package is not valid UTF-8: %s" % exc, "$")
    try:
        root = json.loads(
            text,
            object_pairs_hook=_object_pairs_hook,
            parse_constant=_reject_constant,
        )
    except json.JSONDecodeError as exc:
        raise ContractError(
            "INVALID_INPUT",
            "malformed JSON: %s" % exc.msg,
            "$(line %d, column %d)" % (exc.lineno, exc.colno),
        )
    except ValueError as exc:  # NaN/Infinity guard
        raise ContractError("INVALID_INPUT", str(exc), "$")
    return _materialize(root, "$")


# ---------------------------------------------------------------------------
# formatVersion gate (before schema validation: whole-package versioning)
# ---------------------------------------------------------------------------


def check_version_gate(doc):
    if not isinstance(doc, dict):
        raise ContractError(
            "INVALID_INPUT",
            "package root must be a JSON object, got %s" % _type_name(doc),
            "$",
        )
    if "formatVersion" not in doc:
        raise ContractError(
            "INVALID_INPUT", 'missing required manifest key "formatVersion"', "$"
        )
    version = doc["formatVersion"]
    if isinstance(version, bool) or not isinstance(version, int):
        raise ContractError(
            "INVALID_INPUT",
            "formatVersion must be the integer 1, got %s" % _json_repr(version),
            "$.formatVersion",
        )
    if version != 1:
        raise ContractError(
            "UNSUPPORTED_VERSION",
            "formatVersion %d is not supported by this contract (expected 1); "
            "future majors are rejected whole-package, never best-effort decoded"
            % version,
            "$.formatVersion",
        )


# ---------------------------------------------------------------------------
# Schema loading and subset scan (configuration-level honesty check)
# ---------------------------------------------------------------------------


def load_schema(path):
    try:
        with open(path, "rb") as handle:
            raw = handle.read()
    except OSError as exc:
        raise ConfigError("cannot read schema %s: %s" % (path, exc))
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ConfigError("schema %s is not valid UTF-8: %s" % (path, exc))

    def pairs_guard(pairs):
        seen = set()
        for key, _ in pairs:
            if key in seen:
                raise ConfigError('schema contains duplicate object key "%s"' % key)
            seen.add(key)
        return dict(pairs)

    try:
        schema = json.loads(text, object_pairs_hook=pairs_guard)
    except ValueError as exc:
        raise ConfigError("schema %s is not valid JSON: %s" % (path, exc))
    if not isinstance(schema, dict):
        raise ConfigError("schema root must be a JSON object")
    return schema


def scan_schema(schema):
    """Fail fast on anything outside the deliberate draft-07 subset.

    The whole schema is scanned up front, so an unsupported keyword is a hard
    error even if validation would never reach that branch.
    """
    if not isinstance(schema, dict):
        raise ConfigError("schema subschema must be a JSON object")
    for key in schema:
        if key not in SUPPORTED_SCHEMA_KEYWORDS and key not in BENIGN_SCHEMA_KEYWORDS:
            raise ConfigError(
                "unsupported schema keyword %r (checker implements the "
                "deliberate draft-07 subset only)" % key
            )
    declared = schema.get("type")
    if declared is not None:
        names = declared if isinstance(declared, list) else [declared]
        for name in names:
            if not isinstance(name, str) or name not in JSON_TYPES:
                raise ConfigError("schema declares unknown type %r" % (name,))
    properties = schema.get("properties")
    if properties is not None:
        if not isinstance(properties, dict):
            raise ConfigError("schema properties must be an object")
        for key in properties:
            scan_schema(properties[key])
    required = schema.get("required")
    if required is not None:
        if not (isinstance(required, list) and all(isinstance(k, str) for k in required)):
            raise ConfigError("schema required must be a list of strings")
    additional = schema.get("additionalProperties")
    if additional is not None and additional is not False and additional is not True:
        raise ConfigError(
            "schema additionalProperties must be the literal false (subset); "
            "schema-as-value form is not implemented"
        )
    if "enum" in schema and not isinstance(schema["enum"], list):
        raise ConfigError("schema enum must be a list")
    pattern = schema.get("pattern")
    if pattern is not None:
        if not isinstance(pattern, str):
            raise ConfigError("schema pattern must be a string")
        try:
            re.compile(pattern)
        except re.error as exc:
            raise ConfigError("schema pattern %r does not compile: %s" % (pattern, exc))
    for keyword in ("minLength", "maxLength", "minItems", "maxItems"):
        if keyword in schema:
            value = schema[keyword]
            if isinstance(value, bool) or not isinstance(value, int):
                raise ConfigError("schema %s must be an integer" % keyword)
    for keyword in ("minimum", "maximum"):
        if keyword in schema:
            value = schema[keyword]
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise ConfigError("schema %s must be a number" % keyword)
    exclusive = schema.get("exclusiveMinimum")
    if exclusive is not None and not isinstance(exclusive, bool):
        raise ConfigError(
            "schema exclusiveMinimum must be the draft-07 boolean form "
            "modifying minimum"
        )
    items = schema.get("items")
    if items is not None:
        if not isinstance(items, dict):
            raise ConfigError(
                "schema items must be a single object schema (tuple form is "
                "outside the subset)"
            )
        scan_schema(items)


# ---------------------------------------------------------------------------
# Draft-07 subset validation (find-first, deterministic order)
# ---------------------------------------------------------------------------


def _json_type_of(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"


def _type_matches(value, declared):
    names = declared if isinstance(declared, list) else [declared]
    actual = _json_type_of(value)
    for name in names:
        if name == actual:
            return True
        if name == "number" and actual == "integer":
            return True
    return False


def _strictly_equal(a, b):
    """JSON-value equality where booleans never equal numbers."""
    if isinstance(a, bool) or isinstance(b, bool):
        return isinstance(a, bool) and isinstance(b, bool) and a == b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return a == b
    return type(a) is type(b) and a == b


def _validate(value, schema, path):
    declared = schema.get("type")
    if declared is not None and not _type_matches(value, declared):
        expected = declared if isinstance(declared, str) else " or ".join(declared)
        return (
            "type",
            path,
            "expected type %s, got %s" % (expected, _type_name(value)),
        )
    if isinstance(value, dict):
        violation = _validate_object(value, schema, path)
        if violation is not None:
            return violation
    if isinstance(value, list):
        violation = _validate_array(value, schema, path)
        if violation is not None:
            return violation
    if isinstance(value, str):
        violation = _validate_string(value, schema, path)
        if violation is not None:
            return violation
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        violation = _validate_number(value, schema, path)
        if violation is not None:
            return violation
    return _validate_enum_const(value, schema, path)


def _validate_object(value, schema, path):
    required = schema.get("required")
    if required:
        for key in required:  # schema-declared order
            if key not in value:
                return ("required", path, 'missing required key "%s"' % key)
    properties = schema.get("properties") or {}
    if schema.get("additionalProperties") is False:
        for key in value:  # document order
            if key not in properties:
                return (
                    "additionalProperties",
                    child_path(path, key),
                    'unknown key "%s" (the schema declares additionalProperties: '
                    "false; nothing is silently ignored)" % key,
                )
    for key in properties:  # schema-declared order
        if key in value:
            violation = _validate(value[key], properties[key], child_path(path, key))
            if violation is not None:
                return violation
    return None


def _validate_array(value, schema, path):
    items = schema.get("items")
    if isinstance(items, dict):
        for index, item in enumerate(value):
            violation = _validate(item, items, index_path(path, index))
            if violation is not None:
                return violation
    min_items = schema.get("minItems")
    if min_items is not None and len(value) < min_items:
        return (
            "minItems",
            path,
            "array must have at least %d items, got %d" % (min_items, len(value)),
        )
    max_items = schema.get("maxItems")
    if max_items is not None and len(value) > max_items:
        return (
            "maxItems",
            path,
            "array must have at most %d items, got %d" % (max_items, len(value)),
        )
    return None


def _validate_string(value, schema, path):
    pattern = schema.get("pattern")
    if pattern is not None and re.search(pattern, value) is None:
        return (
            "pattern",
            path,
            "value %s does not match pattern %r" % (_truncate(_json_repr(value)), pattern),
        )
    min_length = schema.get("minLength")
    if min_length is not None and len(value) < min_length:
        return (
            "minLength",
            path,
            "string must have at least %d characters, got %d"
            % (min_length, len(value)),
        )
    max_length = schema.get("maxLength")
    if max_length is not None and len(value) > max_length:
        return (
            "maxLength",
            path,
            "string must have at most %d characters, got %d"
            % (max_length, len(value)),
        )
    return None


def _validate_number(value, schema, path):
    minimum = schema.get("minimum")
    if minimum is not None:
        if schema.get("exclusiveMinimum") is True:
            if not value > minimum:
                return (
                    "exclusiveMinimum",
                    path,
                    "value %s must be > %s" % (value, minimum),
                )
        elif not value >= minimum:
            return ("minimum", path, "value %s must be >= %s" % (value, minimum))
    maximum = schema.get("maximum")
    if maximum is not None and not value <= maximum:
        return ("maximum", path, "value %s must be <= %s" % (value, maximum))
    return None


def _validate_enum_const(value, schema, path):
    if "const" in schema and not _strictly_equal(value, schema["const"]):
        return (
            "const",
            path,
            "value %s must be exactly %s"
            % (_json_repr(value), _json_repr(schema["const"])),
        )
    enum = schema.get("enum")
    if enum is not None and not any(_strictly_equal(value, option) for option in enum):
        return (
            "enum",
            path,
            "value %s is not one of the admitted values %s"
            % (_json_repr(value), _json_repr(enum)),
        )
    return None


def validate_against_schema(value, schema, path="$"):
    """Raise ContractError(UNKNOWN_FIELD) on the first schema violation."""
    violation = _validate(value, schema, path)
    if violation is not None:
        keyword, where, message = violation
        raise ContractError("UNKNOWN_FIELD", "%s: %s" % (keyword, message), where)


# ---------------------------------------------------------------------------
# Contract rules beyond the schema subset
# ---------------------------------------------------------------------------


def _is_credential_shaped(key):
    normalized = _CREDENTIAL_NORMALIZE_RE.sub("", key).lower()
    return any(token in normalized for token in CREDENTIAL_TOKENS)


def _check_credential_keys(node, path="$"):
    if isinstance(node, dict):
        for key, value in node.items():
            key_path = child_path(path, key)
            if _is_credential_shaped(key):
                raise ContractError(
                    "UNKNOWN_FIELD",
                    'credential-shaped key "%s" — credentials are never portable'
                    % key,
                    key_path,
                )
            _check_credential_keys(value, key_path)
    elif isinstance(node, list):
        for index, item in enumerate(node):
            _check_credential_keys(item, index_path(path, index))


def _check_extra_depth(node, path="$"):
    if isinstance(node, dict):
        for key, value in node.items():
            key_path = child_path(path, key)
            if key == "extra" and isinstance(value, dict):
                for extra_key, extra_value in value.items():
                    extra_path = child_path(key_path, extra_key)
                    if isinstance(extra_value, dict):
                        raise ContractError(
                            "UNKNOWN_FIELD",
                            "extra values may be scalars or arrays of scalars; "
                            "the 2-level depth limit is exceeded",
                            extra_path,
                        )
                    if isinstance(extra_value, list):
                        for index, item in enumerate(extra_value):
                            if isinstance(item, (dict, list)):
                                raise ContractError(
                                    "UNKNOWN_FIELD",
                                    "extra values may be scalars or arrays of "
                                    "scalars; the 2-level depth limit is exceeded",
                                    index_path(extra_path, index),
                                )
            else:
                _check_extra_depth(value, key_path)
    elif isinstance(node, list):
        for index, item in enumerate(node):
            _check_extra_depth(item, index_path(path, index))


def _check_collection_sizes(doc):
    for name in COLLECTIONS:
        value = doc.get(name)
        if isinstance(value, list) and len(value) > MAX_RECORDS:
            raise ContractError(
                "SIZE_LIMIT",
                "collection %s holds %d records, limit is %d"
                % (name, len(value), MAX_RECORDS),
                "$.%s" % name,
            )


def _check_string_sizes(node, path="$"):
    if isinstance(node, dict):
        for key, value in node.items():
            key_path = child_path(path, key)
            size = len(key.encode("utf-8"))
            if size > MAX_STRING_BYTES:
                raise ContractError(
                    "SIZE_LIMIT",
                    "object key is %d UTF-8 bytes, limit is %d"
                    % (size, MAX_STRING_BYTES),
                    key_path,
                )
            _check_string_sizes(value, key_path)
    elif isinstance(node, list):
        for index, item in enumerate(node):
            _check_string_sizes(item, index_path(path, index))
    elif isinstance(node, str):
        size = len(node.encode("utf-8"))
        if size > MAX_STRING_BYTES:
            raise ContractError(
                "SIZE_LIMIT",
                "string is %d UTF-8 bytes, limit is %d" % (size, MAX_STRING_BYTES),
                path,
            )


def _check_path_shapes(node, path="$"):
    if isinstance(node, dict):
        for key, value in node.items():
            _check_path_shapes(value, child_path(path, key))
    elif isinstance(node, list):
        for index, item in enumerate(node):
            _check_path_shapes(item, index_path(path, index))
    elif isinstance(node, str):
        if PATH_UNSAFE_RE.search(node):
            raise ContractError(
                "PATH_UNSAFE",
                "string value looks like a filesystem path: %s" % _truncate(node),
                path,
            )


def _check_duplicates(doc):
    for name in COLLECTIONS:
        records = doc.get(name)
        if not isinstance(records, list):
            continue
        seen_stable = {}
        seen_problem = {}
        for index, record in enumerate(records):
            if not isinstance(record, dict):
                continue  # schema already rejects non-object records
            record_path = "$.%s[%d]" % (name, index)
            stable_id = record.get("stableId")
            if isinstance(stable_id, str):
                if stable_id in seen_stable:
                    raise ContractError(
                        "DUPLICATE_ID",
                        'duplicate stableId "%s" in collection %s (first seen at %s)'
                        % (stable_id, name, seen_stable[stable_id]),
                        child_path(record_path, "stableId"),
                    )
                seen_stable[stable_id] = record_path
            if name == "notes":
                # One note per problem per package; reviews may legitimately
                # repeat a problemId (one record per stage).
                problem_id = record.get("problemId")
                if isinstance(problem_id, str):
                    if problem_id in seen_problem:
                        raise ContractError(
                            "DUPLICATE_ID",
                            'duplicate problemId "%s" in notes[] (one note per '
                            "problem per package; first seen at %s)"
                            % (problem_id, seen_problem[problem_id]),
                            child_path(record_path, "problemId"),
                        )
                    seen_problem[problem_id] = record_path


def _collect_problem_ids(doc):
    problems = doc.get("problems")
    ids = set()
    if isinstance(problems, list):
        for record in problems:
            if isinstance(record, dict):
                stable_id = record.get("stableId")
                if isinstance(stable_id, str):
                    ids.add(stable_id)
    return ids


def _check_references(doc, problem_ids):
    for name in ("attempts", "notes", "reviews"):
        records = doc.get(name)
        if not isinstance(records, list):
            continue
        for index, record in enumerate(records):
            if not isinstance(record, dict):
                continue
            problem_id = record.get("problemId")
            if isinstance(problem_id, str) and problem_id not in problem_ids:
                raise ContractError(
                    "MISSING_REFERENCE",
                    '%s[%d] references problemId "%s" which does not exist in '
                    "problems[].stableId" % (name, index, problem_id),
                    "$.%s[%d].problemId" % (name, index),
                )
    sessions = doc.get("sessions")
    if isinstance(sessions, list):
        for index, record in enumerate(sessions):
            if not isinstance(record, dict):
                continue
            referenced = record.get("problemIds")
            if not isinstance(referenced, list):
                continue
            for position, problem_id in enumerate(referenced):
                if isinstance(problem_id, str) and problem_id not in problem_ids:
                    raise ContractError(
                        "MISSING_REFERENCE",
                        'sessions[%d].problemIds[%d] references "%s" which does '
                        "not exist in problems[].stableId"
                        % (index, position, problem_id),
                        "$.sessions[%d].problemIds[%d]" % (index, position),
                    )


def _check_counts(doc):
    counts = doc.get("counts")
    if not isinstance(counts, dict):
        return  # a missing/malformed counts object is the schema's duty
    for name in COLLECTIONS:
        if name not in counts:
            continue  # nothing declared for this collection
        declared = counts[name]
        if isinstance(declared, bool) or not isinstance(declared, int):
            continue  # non-integer counts are the schema's type duty
        actual_value = doc.get(name)
        actual = len(actual_value) if isinstance(actual_value, list) else 0
        if declared != actual:
            raise ContractError(
                "COUNTS_MISMATCH",
                "counts.%s declares %d but the %s array holds %d records"
                % (name, declared, name, actual),
                "$.counts.%s" % name,
            )


def check_contract_rules(doc):
    """Contract rules the draft-07 subset cannot express, in precedence order."""
    _check_extra_depth(doc)   # UNKNOWN_FIELD (extension-point depth limit)
    _check_collection_sizes(doc)  # SIZE_LIMIT (records)
    _check_string_sizes(doc)      # SIZE_LIMIT (UTF-8 bytes)
    _check_path_shapes(doc)       # PATH_UNSAFE
    _check_duplicates(doc)        # DUPLICATE_ID
    _check_references(doc, _collect_problem_ids(doc))  # MISSING_REFERENCE
    _check_counts(doc)            # COUNTS_MISMATCH


# ---------------------------------------------------------------------------
# Pipeline and canonical identity
# ---------------------------------------------------------------------------


def check_package(raw_bytes, schema):
    """Validate one package. Returns the decoded document on success.

    Raises ContractError carrying the first failing category otherwise.
    """
    try:
        doc = decode_package(raw_bytes)   # 1. SIZE_LIMIT  2. INVALID_INPUT
        check_version_gate(doc)           # 3. INVALID_INPUT / UNSUPPORTED_VERSION
        validate_against_schema(doc, schema)  # 4. UNKNOWN_FIELD (subset)
        _check_credential_keys(doc)       # 4. UNKNOWN_FIELD (credential keys)
        check_contract_rules(doc)         # 5-9, in documented order
        return doc
    except RecursionError:
        raise ContractError(
            "INVALID_INPUT", "document nesting is too deep to decode safely", "$"
        )


def canonical_bytes(doc):
    """Canonical re-encode: sorted keys, fixed separators, UTF-8, newline."""
    text = json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return (text + "\n").encode("utf-8")


def read_package_file(path):
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except OSError as exc:
        raise ConfigError("cannot read package %s: %s" % (path, exc))


# ---------------------------------------------------------------------------
# Golden case runner
# ---------------------------------------------------------------------------


def load_cases(cases_path):
    """Load and shape-check the cases file. Raises ConfigError on any defect."""
    try:
        with open(cases_path, "rb") as handle:
            raw = handle.read()
    except OSError as exc:
        raise ConfigError("cannot read cases file %s: %s" % (cases_path, exc))
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ConfigError("cases file is not valid UTF-8: %s" % exc)

    def pairs_guard(pairs):
        seen = set()
        for key, _ in pairs:
            if key in seen:
                raise ConfigError('cases file has duplicate object key "%s"' % key)
            seen.add(key)
        return dict(pairs)

    try:
        parsed = json.loads(text, object_pairs_hook=pairs_guard)
    except ValueError as exc:
        raise ConfigError("cases file is not valid JSON: %s" % exc)
    if isinstance(parsed, list):
        entries = parsed
    elif isinstance(parsed, dict) and isinstance(parsed.get("cases"), list):
        entries = parsed["cases"]
    elif isinstance(parsed, dict) and isinstance(parsed.get("vectors"), list):
        # House style alias (contracts/recovery/v1/vectors.json uses "vectors").
        entries = parsed["vectors"]
    else:
        raise ConfigError(
            'cases file must be a JSON array or an object with a "cases" array'
        )

    shaped = []
    for position, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ConfigError("case #%d is not an object" % position)
        case_id = entry.get("caseId", entry.get("id"))
        file_name = entry.get("file")
        expect = entry.get("expect")
        category = entry.get("errorCategory")
        if not isinstance(case_id, str) or not case_id:
            raise ConfigError("case #%d lacks a non-empty string caseId" % position)
        if not isinstance(file_name, str) or not file_name:
            raise ConfigError("case %s lacks a non-empty string file" % case_id)
        if expect not in ("valid", "invalid"):
            raise ConfigError(
                'case %s: expect must be "valid" or "invalid"' % case_id
            )
        if expect == "invalid":
            if category not in CATEGORIES:
                raise ConfigError(
                    "case %s: errorCategory must be one of: %s"
                    % (case_id, ", ".join(CATEGORIES))
                )
        elif category is not None:
            raise ConfigError(
                "case %s: a valid case must not declare an errorCategory" % case_id
            )
        shaped.append(
            {"caseId": case_id, "file": file_name, "expect": expect, "category": category}
        )
    if not shaped:
        raise ConfigError("cases file contains no cases")
    return shaped


def run_cases(cases_path, golden_dir, schema):
    entries = load_cases(cases_path)
    failures = 0
    for entry in entries:
        case_id = entry["caseId"]
        package_path = entry["file"]
        if not os.path.isabs(package_path):
            package_path = os.path.join(golden_dir, package_path)
        try:
            raw = read_package_file(package_path)
        except ConfigError as exc:
            print("%s: FAIL (%s)" % (case_id, exc))
            failures += 1
            continue
        try:
            check_package(raw, schema)
            actual, category = "valid", None
        except ContractError as exc:
            actual, category = "invalid", exc.category
        if entry["expect"] == "valid":
            expected_desc = "valid"
            matched = actual == "valid"
        else:
            expected_desc = "invalid (%s)" % entry["category"]
            matched = actual == "invalid" and category == entry["category"]
        if matched:
            print("%s: PASS (%s)" % (case_id, expected_desc))
        else:
            actual_desc = "valid" if actual == "valid" else "invalid (%s)" % category
            print("%s: FAIL expected %s, got %s" % (case_id, expected_desc, actual_desc))
            failures += 1
    print(
        "%d cases, %d passed, %d failed" % (len(entries), len(entries) - failures, failures)
    )
    return 0 if failures == 0 else 1


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def emit_error(exc):
    print("INVALID: %s" % exc.category, file=sys.stderr)
    print("location: %s" % exc.location, file=sys.stderr)
    print("detail: %s" % exc.detail, file=sys.stderr)


def run_single(path, schema):
    raw = read_package_file(path)  # ConfigError → exit 2
    check_package(raw, schema)     # ContractError → exit 1
    print("VALID")
    return 0


def run_identity(path, schema):
    raw = read_package_file(path)  # ConfigError → exit 2
    doc = check_package(raw, schema)  # ContractError → exit 1
    print(hashlib.sha256(canonical_bytes(doc)).hexdigest())
    return 0


def build_parser():
    parser = argparse.ArgumentParser(
        prog="check.py",
        description="Portable Workspace v1 reference checker (Python stdlib only).",
    )
    parser.add_argument("file", nargs="?", help="package file to validate")
    parser.add_argument(
        "--cases",
        nargs=2,
        metavar=("CASES_JSON", "GOLDEN_DIR"),
        help="run every golden case and compare against expectations",
    )
    parser.add_argument(
        "--identity",
        metavar="FILE",
        help="print the SHA-256 of the canonical re-encode of a valid package",
    )
    parser.add_argument(
        "--schema",
        metavar="PATH",
        help="explicit schema.json path (self-testing aid; default is "
        "schema.json next to check.py)",
    )
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    chosen = sum(1 for value in (args.file, args.cases, args.identity) if value)
    if chosen != 1:
        parser.error("choose exactly one of <file>, --cases CASES GOLDEN_DIR, --identity FILE")

    default_schema = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "schema.json"
    )
    schema_path = args.schema or default_schema
    try:
        schema = load_schema(schema_path)
        scan_schema(schema)
    except ConfigError as exc:
        print("checker configuration error: %s" % exc, file=sys.stderr)
        print(
            "hint: the schema normally lives at %s; pass --schema PATH only for "
            "self-testing against a schema outside the contract directory"
            % default_schema,
            file=sys.stderr,
        )
        return 2

    try:
        if args.file:
            return run_single(args.file, schema)
        if args.identity:
            return run_identity(args.identity, schema)
        return run_cases(args.cases[0], args.cases[1], schema)
    except ConfigError as exc:
        print("checker configuration error: %s" % exc, file=sys.stderr)
        return 2
    except ContractError as exc:
        emit_error(exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())
