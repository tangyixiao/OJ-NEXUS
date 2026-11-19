#!/usr/bin/env python3
"""Problem identity and retrieval contract — reference implementation and gate.

This checker is the executable form of the frozen contract documented in
README.md (phase 136). It is Python 3 stdlib only and touches no network.

Two modes:

  check.py --cases golden/cases.json golden/
      Gate mode. Loads every vector listed in the cases file, recomputes the
      expected result from the dataset with the reference implementation, and
      exits non-zero on any mismatch or invalid input.

  check.py --run <vector.json>
      Reference mode. Prints the computed result for one vector as a JSON
      array of key strings (platform test harnesses compare their own output
      against this).

Frozen semantics (README.md is authoritative; this file is the oracle):

- Identity: (judge, externalId) exact byte equality over the frozen judge set;
  no case folding, no trimming, no numeric coercion; duplicate identities in
  one dataset are invalid.
- Status is derived: inReview -> REVIEW, else solved -> SOLVED, else
  attemptCount > 0 -> ATTEMPTED, else UNSOLVED.
- Search: query stripped, then Unicode simple lowercase; substring match over
  the lowered title and the lowered externalId; empty query matches all.
- Filters AND-combined: status equality (derived), judge equality,
  favoriteOnly, tag membership (exact, case-sensitive), difficulty bounds
  (inclusive; unknown difficulty excluded when either bound is set).
- Sorts: UPDATED updatedAt descending; DIFFICULTY nulls last then ascending;
  TITLE lowered title code-point ascending; ATTEMPTS attemptCount descending.
  Every sort ends with the stable tie-break: code-point (UTF-8 byte) order of
  the key string "judge/externalId". Code-point order, NOT UTF-16 code-unit
  order — one golden vector pins this with an astral-plane character.
"""

import argparse
import json
import sys
from pathlib import Path

JUDGES = ("local", "codeforces", "atcoder", "luogu")
STATUSES = ("UNSOLVED", "ATTEMPTED", "SOLVED", "REVIEW")
SORTS = ("UPDATED", "DIFFICULTY", "TITLE", "ATTEMPTS")

REQUIRED_FIELDS = (
    "judge",
    "externalId",
    "title",
    "difficulty",
    "favorite",
    "solved",
    "attemptCount",
    "inReview",
    "tags",
    "createdAt",
    "updatedAt",
)

OPTIONAL_FIELDS = ("difficultySource",)


class ContractError(Exception):
    """Raised on any contract violation in a dataset, query or vector."""


def _is_int(value):
    # bool is a subclass of int in Python; JSON booleans must not pass as ints.
    return isinstance(value, int) and not isinstance(value, bool)


def _is_str(value):
    return isinstance(value, str)


def _is_bool(value):
    return isinstance(value, bool)


def key_string(record):
    """The vector address of a problem: "judge/externalId"."""
    return "{}/{}".format(record["judge"], record["externalId"])


def derive_status(record):
    """Frozen status derivation. REVIEW overrides, independent of solved."""
    if record["inReview"]:
        return "REVIEW"
    if record["solved"]:
        return "SOLVED"
    if record["attemptCount"] > 0:
        return "ATTEMPTED"
    return "UNSOLVED"


def _validate_record(record):
    if not isinstance(record, dict):
        raise ContractError("dataset record must be a JSON object")
    for field in REQUIRED_FIELDS:
        if field not in record:
            raise ContractError("record missing field {!r}".format(field))
    if not _is_str(record["judge"]) or record["judge"] not in JUDGES:
        raise ContractError(
            "judge must be one of {}, got {!r}".format(list(JUDGES), record["judge"])
        )
    if not _is_str(record["externalId"]) or record["externalId"] == "":
        raise ContractError("externalId must be a non-empty string")
    if not _is_str(record["title"]):
        raise ContractError("title must be a string")
    difficulty = record["difficulty"]
    if difficulty is not None and not _is_int(difficulty):
        raise ContractError("difficulty must be an integer or null (never a float)")
    if not _is_bool(record["favorite"]):
        raise ContractError("favorite must be a boolean")
    if not _is_bool(record["solved"]):
        raise ContractError("solved must be a boolean")
    if not _is_bool(record["inReview"]):
        raise ContractError("inReview must be a boolean")
    if not _is_int(record["attemptCount"]) or record["attemptCount"] < 0:
        raise ContractError("attemptCount must be a non-negative integer")
    if not _is_int(record["createdAt"]) or not _is_int(record["updatedAt"]):
        raise ContractError("createdAt/updatedAt must be integer epoch milliseconds")
    tags = record["tags"]
    if not isinstance(tags, list) or not all(_is_str(t) for t in tags):
        raise ContractError("tags must be a list of strings")
    for field in OPTIONAL_FIELDS:
        if field in record and not _is_str(record[field]):
            raise ContractError("{} must be a string".format(field))


def _validate_dataset(dataset):
    if not isinstance(dataset, list):
        raise ContractError("dataset must be a JSON array of records")
    seen = set()
    for record in dataset:
        _validate_record(record)
        key = key_string(record)
        if key in seen:
            raise ContractError(
                "duplicate identity {!r}: (judge, externalId) pairs are unique"
                .format(key)
            )
        seen.add(key)


def _validate_query(query):
    if not isinstance(query, dict):
        raise ContractError("query must be a JSON object")
    if "sort" not in query:
        raise ContractError("query.sort is required")
    if query["sort"] not in SORTS:
        raise ContractError(
            "sort must be one of {}, got {!r}".format(list(SORTS), query["sort"])
        )
    status = query.get("status")
    if status is not None and status not in STATUSES:
        raise ContractError(
            "status must be one of {} or null, got {!r}".format(list(STATUSES), status)
        )
    judge = query.get("judge")
    if judge is not None and judge not in JUDGES:
        raise ContractError(
            "judge filter must be one of {} or null, got {!r}".format(list(JUDGES), judge)
        )
    if "favoriteOnly" in query and not _is_bool(query["favoriteOnly"]):
        raise ContractError("favoriteOnly must be a boolean")
    if query.get("tag") is not None and not _is_str(query["tag"]):
        raise ContractError("tag filter must be a string or null")
    for bound in ("difficultyMin", "difficultyMax"):
        if query.get(bound) is not None and not _is_int(query[bound]):
            raise ContractError("{} must be an integer or null".format(bound))
    if query.get("search") is not None and not _is_str(query["search"]):
        raise ContractError("search must be a string or null")


def _passes(record, query):
    search = (query.get("search") or "").strip().lower()
    if search:
        lowered_title = record["title"].lower()
        lowered_external = record["externalId"].lower()
        if search not in lowered_title and search not in lowered_external:
            return False
    if query.get("status") is not None and derive_status(record) != query["status"]:
        return False
    if query.get("judge") is not None and record["judge"] != query["judge"]:
        return False
    if query.get("favoriteOnly") and not record["favorite"]:
        return False
    tag = query.get("tag")
    if tag is not None and tag not in record["tags"]:
        return False
    difficulty_min = query.get("difficultyMin")
    difficulty_max = query.get("difficultyMax")
    if difficulty_min is not None or difficulty_max is not None:
        difficulty = record["difficulty"]
        if difficulty is None:
            return False
        if difficulty_min is not None and difficulty < difficulty_min:
            return False
        if difficulty_max is not None and difficulty > difficulty_max:
            return False
    return True


def _sort_key(query, record):
    """Sort key implementing the frozen ordering incl. the stable tie-break."""
    tiebreak = key_string(record)
    sort = query["sort"]
    if sort == "UPDATED":
        return (-record["updatedAt"], tiebreak)
    if sort == "DIFFICULTY":
        difficulty = record["difficulty"]
        if difficulty is None:
            return (1, 0, tiebreak)
        return (0, difficulty, tiebreak)
    if sort == "TITLE":
        return (record["title"].lower(), tiebreak)
    if sort == "ATTEMPTS":
        return (-record["attemptCount"], tiebreak)
    raise ContractError("unknown sort {!r}".format(sort))


def filter_and_sort(dataset, query):
    """Reference implementation: filter (AND-combined), then sort, then keys."""
    _validate_dataset(dataset)
    _validate_query(query)
    surviving = [record for record in dataset if _passes(record, query)]
    surviving.sort(key=lambda record: _sort_key(query, record))
    return [key_string(record) for record in surviving]


def _load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def _run_gate(cases_path, golden_dir):
    cases = _load_json(cases_path)
    if not isinstance(cases, dict) or "vectors" not in cases:
        raise ContractError("cases file must be an object with a 'vectors' list")
    golden = Path(golden_dir)
    failures = []
    checked = 0
    for name in cases["vectors"]:
        vector = _load_json(golden / name)
        checked += 1
        try:
            if not isinstance(vector, dict):
                raise ContractError("vector must be a JSON object")
            for field in ("dataset", "query", "expect"):
                if field not in vector:
                    raise ContractError("vector missing field {!r}".format(field))
            computed = filter_and_sort(vector["dataset"], vector["query"])
            expected = vector["expect"]
            if computed != expected:
                failures.append(
                    "{}: expected {}, got {}".format(name, expected, computed)
                )
        except ContractError as error:
            failures.append("{}: {}".format(name, error))
    print("checked {} vectors from {}".format(checked, cases_path))
    if failures:
        for failure in failures:
            print("FAIL {}".format(failure), file=sys.stderr)
        raise ContractError("{} of {} vectors failed".format(len(failures), checked))
    print("PASS")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--cases", metavar="CASES", help="gate mode: cases.json listing vectors"
    )
    group.add_argument(
        "--run", metavar="VECTOR", help="reference mode: print computed keys"
    )
    parser.add_argument(
        "golden_dir", nargs="?", help="directory vector paths resolve against (gate mode)"
    )
    args = parser.parse_args(argv)
    try:
        if args.cases is not None:
            if args.golden_dir is None:
                parser.error("gate mode needs the golden directory argument")
            _run_gate(args.cases, args.golden_dir)
        else:
            vector = _load_json(args.run)
            for field in ("dataset", "query"):
                if field not in vector:
                    raise ContractError("vector missing field {!r}".format(field))
            print(json.dumps(filter_and_sort(vector["dataset"], vector["query"])))
    except ContractError as error:
        print("CONTRACT ERROR: {}".format(error), file=sys.stderr)
        return 1
    except (OSError, json.JSONDecodeError) as error:
        print("INPUT ERROR: {}".format(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
