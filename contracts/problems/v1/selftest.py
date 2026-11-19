#!/usr/bin/env python3
"""Self-test for the problem-contract checker.

Each check asserts that the reference implementation either (a) applies one of
the frozen semantics exactly, or (b) rejects a mutated/invalid input loudly.
Gate-level checks drive the REAL gate entry point (check._run_gate) against
temporary golden fixtures, so the comparison logic itself is under test.
Run: python3 selftest.py  (exit 0 = all checks pass).
"""

import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check  # noqa: E402
from check import ContractError, derive_status, filter_and_sort  # noqa: E402

PASSED = []


def record(name):
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


def run_gate_on(vector, extra_unlisted=None, filename=None):
    """Drive the REAL gate over a temp golden dir. Returns normally on PASS,
    raises ContractError on any gate failure. `vector` may be a single vector
    dict or a list of them. `filename` overrides the on-disk name (used to
    test the name/filename cross-check)."""
    vectors = vector if isinstance(vector, list) else [vector]
    with tempfile.TemporaryDirectory() as tmp:
        golden = Path(tmp)
        names = []
        for item in vectors:
            fname = filename or (item["name"] + ".json")
            (golden / fname).write_text(
                json.dumps(item, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            names.append(fname)
        if extra_unlisted is not None:
            (golden / extra_unlisted).write_text(
                json.dumps({"name": extra_unlisted[:-5], "dataset": [],
                            "query": {"sort": "TITLE"}, "expect": []}, ensure_ascii=False),
                encoding="utf-8",
            )
        cases = {"contract": "problems", "version": 1, "vectors": names}
        cases_path = golden / "cases.json"
        cases_path.write_text(json.dumps(cases), encoding="utf-8")
        check._run_gate(str(cases_path), str(golden))


def base_vector(**overrides):
    vec = {
        "name": "selftest-vector",
        "note": "selftest",
        "dataset": [base_record(externalId="1"), base_record(externalId="2")],
        "query": {"sort": "TITLE"},
        "expect": ["codeforces/1", "codeforces/2"],
    }
    vec.update(overrides)
    return vec


@record("01 duplicate identity rejected")
def _():
    dataset = [base_record(externalId="1"), base_record(externalId="1")]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("02 unknown judge rejected")
def _():
    dataset = [base_record(judge="topcoder")]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("03 float difficulty rejected")
def _():
    dataset = [base_record(difficulty=1.5)]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "TITLE"}))


@record("04 bool-as-int rejected at every integer site")
def _():
    for field in ("attemptCount", "createdAt", "updatedAt", "difficulty"):
        dataset = [base_record(**{field: True})]
        expect_contract_error(lambda d=dataset: filter_and_sort(d, {"sort": "TITLE"}))


@record("05 unknown sort rejected")
def _():
    dataset = [base_record()]
    expect_contract_error(lambda: filter_and_sort(dataset, {"sort": "RATING"}))


@record("06 gate detects a mutated expect (real gate path)")
def _():
    mutated = base_vector(expect=["codeforces/2", "codeforces/1"])
    expect_contract_error(lambda: run_gate_on(mutated))
    run_gate_on(base_vector())  # clean vector must pass the same path


@record("07 sharp-s: 'ss' does not match eszett (simple lowercase)")
def _():
    dataset = [base_record(externalId="1", title="Grosse Daten"), base_record(externalId="2", title="Untouched")]
    result = filter_and_sort(dataset, {"search": "ss", "sort": "TITLE"})
    if result != ["codeforces/1"]:
        raise AssertionError("case-fold leaked into search: {}".format(result))
    dataset_sharp_s = [
        base_record(externalId="1", title="Grosse Daten"),
        base_record(externalId="2", title="Gro" + chr(0xDF) + "e Daten"),
    ]
    result = filter_and_sort(dataset_sharp_s, {"search": "ss", "sort": "TITLE"})
    if result != ["codeforces/1"]:
        raise AssertionError("case-fold leaked into search: {}".format(result))


@record("08 astral code-point order (not UTF-16)")
def _():
    dataset = [
        base_record(externalId="1", title="Ａlpha"),
        base_record(externalId="2", title="𝐀lpha"),
    ]
    result = filter_and_sort(dataset, {"sort": "TITLE"})
    if result != ["codeforces/1", "codeforces/2"]:
        raise AssertionError("UTF-16-naive order leaked: {}".format(result))


@record("09 null difficulty sorts after difficulty 0")
def _():
    dataset = [
        base_record(externalId="1", difficulty=None),
        base_record(externalId="2", difficulty=0),
    ]
    result = filter_and_sort(dataset, {"sort": "DIFFICULTY"})
    if result != ["codeforces/2", "codeforces/1"]:
        raise AssertionError("null difficulty coerced or misordered: {}".format(result))


@record("10 REVIEW overrides solved in derivation")
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


@record("11 tag filter is case-sensitive")
def _():
    dataset = [base_record(tags=["dp"])]
    if filter_and_sort(dataset, {"tag": "DP", "sort": "TITLE"}) != []:
        raise AssertionError("tag 'DP' must not match tag 'dp'")


@record("12 identity keeps case and space distinct")
def _():
    dataset = [base_record(externalId="1A"), base_record(externalId="1a")]
    if len(filter_and_sort(dataset, {"sort": "TITLE"})) != 2:
        raise AssertionError("case-folded identity would merge two problems")


@record("13 bounded range excludes null difficulty")
def _():
    dataset = [base_record(externalId="1", difficulty=None), base_record(externalId="2", difficulty=100)]
    result = filter_and_sort(
        dataset, {"difficultyMin": 0, "difficultyMax": 200, "sort": "DIFFICULTY"}
    )
    if result != ["codeforces/2"]:
        raise AssertionError("null difficulty leaked through bounds: {}".format(result))


@record("14 gate detects a missing expect entry (real gate path)")
def _():
    truncated = base_vector(expect=["codeforces/1"])
    expect_contract_error(lambda: run_gate_on(truncated))


@record("15 vector JSON round-trips through the gate loader")
def _():
    vector = {
        "name": "selftest-vector",
        "dataset": [base_record()],
        "query": {"sort": "UPDATED"},
        "expect": ["codeforces/1"],
    }
    raw = json.dumps(vector)
    loaded = json.loads(raw)
    if filter_and_sort(loaded["dataset"], loaded["query"]) != loaded["expect"]:
        raise AssertionError("round-trip changed semantics")


@record("16 NBSP query is not trimmed (ASCII-only trim set)")
def _():
    dataset = [base_record(externalId="1", title="Two Sum")]
    if filter_and_sort(dataset, {"search": chr(0xA0), "sort": "TITLE"}) != []:
        raise AssertionError("NBSP was stripped: non-ASCII whitespace must survive trim")
    if filter_and_sort(dataset, {"search": chr(0xA0) + "two", "sort": "TITLE"}) != []:
        raise AssertionError("leading NBSP must not be trimmed away")


@record("17 whitespace-only ASCII query matches all")
def _():
    dataset = [base_record(externalId="1"), base_record(externalId="2", title="Other")]
    result = filter_and_sort(dataset, {"search": " \t\n", "sort": "TITLE"})
    if len(result) != 2:
        raise AssertionError("whitespace-only query must match everything: {}".format(result))


@record("18 optional difficultySource: missing ok, non-string rejected")
def _():
    filter_and_sort([base_record()], {"sort": "TITLE"})  # missing: fine
    filter_and_sort([base_record(difficultySource="CODEFORCES")], {"sort": "TITLE"})
    expect_contract_error(
        lambda: filter_and_sort([base_record(difficultySource=3)], {"sort": "TITLE"})
    )


@record("19 unknown record fields and query keys rejected")
def _():
    expect_contract_error(
        lambda: filter_and_sort([base_record(diffiiculty=5)], {"sort": "TITLE"})
    )
    expect_contract_error(
        lambda: filter_and_sort([base_record()], {"sort": "TITLE", "favoritOnly": True})
    )


@record("20 gate detects unlisted golden files")
def _():
    expect_contract_error(
        lambda: run_gate_on(base_vector(), extra_unlisted="rogue-vector.json")
    )


@record("21 gate rejects lone surrogates (unportable on JVM/Qt/Swift)")
def _():
    with tempfile.TemporaryDirectory() as tmp:
        vector_path = Path(tmp) / "surrogate.json"
        vector_path.write_text(
            '{"name": "surrogate", "dataset": [{"externalId": "\\ud800"}], "query": {"sort": "TITLE"}, "expect": []}',
            encoding="utf-8",
        )
        expect_contract_error(lambda: check._load_json(str(vector_path)))


@record("22 gate detects vector name/filename mismatch")
def _():
    mismatched = base_vector()  # name stays "selftest-vector", file differs
    expect_contract_error(
        lambda: run_gate_on(mismatched, filename="other-name.json")
    )


@record("23 gate rejects wrong cases version")
def _():
    with tempfile.TemporaryDirectory() as tmp:
        golden = Path(tmp)
        vec = base_vector()
        (golden / "selftest-vector.json").write_text(json.dumps(vec), encoding="utf-8")
        cases = {"contract": "problems", "version": 2, "vectors": ["selftest-vector.json"]}
        cases_path = golden / "cases.json"
        cases_path.write_text(json.dumps(cases), encoding="utf-8")
        expect_contract_error(lambda: check._run_gate(str(cases_path), str(golden)))


print()
print("selftest: {}/23 checks passed".format(len(PASSED)))
if len(PASSED) != 23:
    sys.exit(1)
print("PASS")
