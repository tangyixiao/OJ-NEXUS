# Plan — Judge Health Dashboard (Phase 96)

1. `feature/settings/JudgeHealth.kt` — pure snapshot + projection + latency formatter. DONE.
2. `ConnectorCenter.kt` — `ConnectorCenterRow(row, health)` renders LAST RUN + LATENCY lines;
   `ConnectorCenterSection` gains healthByJudge + luoguQuota params; new private
   `LuoguQuotaRow` with explicit PROBE action wired to `checkOpenAppQuota`. DONE.
3. `SettingsScreen.kt` — pass `deriveJudgeHealth` per connection + openAppState quota fields. DONE.
4. Strings `settings_health_*` (7 keys) EN + zh-rCN. DONE.
5. Tests `JudgeHealthTest` (5 tests). DONE.
6. Gates `test assembleDebug`, versionCode 94/0.3.94, ROADMAP prepend, plan ✅ mark, commit feat:.

## Result

SHIPPED (v0.3.94). `JudgeHealth.kt` pure projection (snapshot + latency formatter + status
label), `ConnectorCenter.kt` renders LAST RUN/LATENCY per judge and a manual Luogu PROBE row
(`LuoguQuotaRow` reusing `checkOpenAppQuota` — the only quota trigger, no background probing),
`SettingsScreen.kt` wires `deriveJudgeHealth` + openAppState quota fields. Strings
`settings_health_*` 7 keys EN + zh. Tests: `JudgeHealthTest` 5 (null when nothing finished,
latest-finished wins over running rows, error-type pairing, negative-latency clamp, honest
latency units). Gates: `test assembleDebug` BUILD SUCCESSFUL 1m29s; 159 suites / 723 tests /
0 failures. versionCode 94 / 0.3.94; ROADMAP "JUDGE HEALTH DASHBOARD" prepended;
docs/LONG_RANGE_PLAN.md PHASE 96 ✅.
