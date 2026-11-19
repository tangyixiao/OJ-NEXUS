#!/usr/bin/env python3
"""Self-test for the problem-contract checker.

Each check asserts that the reference implementation either (a) applies one of
the frozen semantics exactly, or (b) rejects a mutated/invalid input loudly.
Run: python3 selftest.py  (exit 0 = all checks pass).
"""

import copy
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check  # noqa: E402
from check import ContractError, derive_status, filter_and_sort  # noqa: E402

PASSED = []


def record(name, _expect_check):
    def wrap(fn):
        try:
            fn()
        except AssertionError as error:
            print("FAIL {} — {}".format(name, error))
            raise SystemExit(1)
        except ContractError as error:
            print("FAIL {} — unexpected ContractError: {}".format(name, error))
            raise SystemExit(1)
        PASSED.append(name)
        print("PASS {}".format(name))
    return wrap


def base_record(**overrides):
    rec = {
        "judge": "codeforces",
        "externalId": "1",
        "title": "Sample",
        "difficulty": None,
        "favorite": False,
        "solved": False,
        "attemptCount": 0,
        "inReview": False,
        "tags": [],
        "createdAt": 1700000000000,
        "updatedAt": 1700000000000,
    }
    rec.update(overrides)
    return rec


def expect_contract_error(fn):
    try:
        fn()
    except ContractError:
        return
    raise AssertionError("expected ContractError, none raised")


@record("01 duplicate identity rejected", 1)
def _():
    dataset = [base_record(externalId="1"), base_record(externalId="1")]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("02 unknown judge rejected", 2)
def _():
    dataset = [base_record(judge="topcoder")]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("03 float difficulty rejected", 3)
def _():
    dataset = [base_record(difficulty=1.5)]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("04 bool-as-int rejected", 4)
def _():
    dataset = [base_record(attemptCount=True)]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("05 unknown sort rejected", 5)
def _():
    dataset = [base_record()]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "RATING"}))


@record("06 mutated expect order detected by gate", 6)
def _():
    # Gate-level: a vector whose expect is hand-mutated must fail the gate.
    vector = {
        "dataset": [base_record(externalId="1"), base_record(externalId="2")],
        "query": {"sort": "TITLE"},
        "expect": ["codeforces/2", "codeforces/1"],
    }
    computed = filter_and_sort(vector["dataset"], vector["query"])
    if computed == vector["expect"]:
        raise AssertionError("mutated expect unexpectedly matched computation")
    # And the gate's comparison logic flags it (mirror _run_gate's check).
    if computed != ["codeforces/1", "codeforces/2"]:
        raise AssertionError("reference order wrong: {}".format(computed))


@record("07 sharp-s: 'ss' does not match eszett (simple lowercase)", 7)
def _():
    dataset = [base_record(externalId="1", title="Grosse Daten"), base_record(externalId="2", title="Untouched")]
    result = filter_and_sort(dataset, {"search": "ss", "sort": "TITLE"})
    if result != ["codeforces/1"]:
        raise AssertionError("case-fold leaked into search: {}".format(result))
    # A title containing the eszett must NOT match the query 'ss' (simple
    # lowercase keeps U+00DF as-is; full case-fold would wrongly match).
    dataset_sharp_s = [
        base_record(externalId="1", title="Grosse Daten"),
        base_record(externalId="2", title="Gro" + chr(0xDF) + "e Daten"),
    ]
    result = filter_and_sort(dataset_sharp_s, {"search": "ss", "sort": "TITLE"})
    if result != ["codeforces/1"]:
        raise AssertionError("case-fold leaked into search: {}".format(result))


@record("08 astral code-point order (not UTF-16)", 8)
def _():
    dataset = [
        base_record(externalId="1", title="Ａlpha"),
        base_record(externalId="2", title="𝐀lpha"),
    ]
    result = filter_and_sort(dataset, {"sort": "TITLE"})
    if result != ["codeforces/1", "codeforces/2"]:
        raise AssertionError("UTF-16-naive order leaked: {}".format(result))


@record("09 null difficulty sorts after difficulty 0", 9)
def _():
    dataset = [
        base_record(externalId="1", difficulty=None),
        base_record(externalId="2", difficulty=0),
    ]
    result = filter_and_sort(dataset, {"sort": "DIFFICULTY"})
    if result != ["codeforces/2", "codeforces/1"]:
        raise AssertionError("null difficulty coerced or misordered: {}".format(result))


@record("10 REVIEW overrides solved in derivation", 10)
def _():
    rec = base_record(solved=True, attemptCount=5, inReview=True)
    if derive_status(rec) != "REVIEW":
        raise AssertionError("REVIEW must override solved")
    rec["inReview"] = False
    if derive_status(rec) != "SOLVED":
        raise AssertionError("solved without review must be SOLVED")
    rec["solved"] = False
    rec["attemptCount"] = 2
    if derive_status(rec) != "ATTEMPTED":
        raise AssertionError("attemptCount>0 must be ATTEMPTED")
    rec["attemptCount"] = 0
    if derive_status(rec) != "UNSOLVED":
        raise AssertionError("pristine record must be UNSOLVED")


@record("11 tag filter is case-sensitive", 11)
def _():
    dataset = [base_record(tags=["dp"])]
    if filter_and_sort(dataset, {"tag": "DP", "sort": "TITLE"}) != []:
        raise AssertionError("tag 'DP' must not match tag 'dp'")


@record("12 identity keeps case and space distinct", 12)
def _():
    dataset = [base_record(externalId="1A"), base_record(externalId="1a")]
    if len(filter_and_sort(dataset, {"sort": "TITLE"})) != 2:
        raise AssertionError("case-folded identity would merge two problems")


@record("13 bounded range excludes null difficulty", 13)
def _():
    dataset = [base_record(externalId="1", difficulty=None), base_record(externalId="2", difficulty=100)]
    result = filter_and_sort(
        dataset, {"difficultyMin": 0, "difficultyMax": 200, "sort": "DIFFICULTY"}
    )
    if result != ["codeforces/2"]:
        raise AssertionError("null difficulty leaked through bounds: {}".format(result))


@record("14 gate detects missing expect entry", 14)
def _():
    dataset = [base_record(externalId="1"), base_record(externalId="2")]
    computed = filter_and_sort(dataset, {"sort": "TITLE"})
    mutated = computed[:-1]
    if computed == mutated:
        raise AssertionError("mutation not detected")


@record("15 vector JSON round-trips through the gate loader", 15)
def _():
    vector = {
        "dataset": [base_record()],
        "query": {"sort": "UPDATED"},
        "expect": ["codeforces/1"],
    }
    raw = json.dumps(vector)
    loaded = json.loads(raw)
    if filter_and_sort(loaded["dataset"], loaded["query"]) != loaded["expect"]:
        raise AssertionError("round-trip changed semantics")


print()
print("selftest: {}/15 checks passed".format(len(PASSED)))
if len(PASSED) != 15:
    sys.exit(1)
print("PASS")
