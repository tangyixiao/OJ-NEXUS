# Knowledge and Mastery

Phase 6 introduces an explicit knowledge boundary. A problem is related to one or more
`KnowledgeArea` values through `problem_knowledge`; judge tags remain source metadata and are
not silently treated as mastery labels.

## Evidence

`KnowledgeDao` aggregates, per area, distinct attempted and solved problems, attempts, and
failure-log entries. `KnowledgeRepository` fills the complete static tree, including areas with
no evidence, and passes those aggregates to the pure `MasteryEngine`.

## Mastery policy

The score is deterministic and inspectable:

```text
coverage (solved / attempted) × 70
+ first-try efficiency (solved / attempts) × 30
− failure-log entries × 5
```

The result is clamped to 0–100. The UI renders reason codes such as `NO EVIDENCE`, `LOW AC RATE`,
and `FAILURE LOG`; it does not generate advice or hide the evidence behind a single color.

The Training screen currently exposes this tree and its reason-bearing scores. Relation editing
is available in problem detail and updates the tree reactively.

## Candidate priority

`TrainingPlanner` is the pure ranking boundary for the training-plan surface. It assigns
points only from explicit local signals: unsolved state (35), review due (25), failure history
(up to 20), fitting difficulty (15), and coverage value (5). It returns reason codes together
with the clamped 0–100 priority, and the Training screen now renders the top local candidates
with those reasons. An optional target difficulty can be supplied to activate the difficulty-fit
signal; the current UI leaves it unset rather than inventing a target.
