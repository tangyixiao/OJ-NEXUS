# Luogu result query plan / 洛谷结果查询计划

## Step 1 — Design / 设计

- [x] Compare the current Retrofit declaration with the Open Platform contract.
- [x] Define query-parameter polling as the only transport change.

## Step 2 — Test first / 先写测试

- [x] Change the MockWebServer contract assertion to `/judge/result?id=req-1`.
- [x] Run the focused test and capture the expected RED failure against the current path.

## Step 3 — Implementation and docs / 实现与文档

- [x] Change `@Path("id")` to `@Query("id")`.
- [x] Correct `docs/LUOGU_OPEN_PLATFORM.md` and update bilingual README/Roadmap/release notes.
- [x] Align the APK identity to `versionName=0.3.35` and `versionCode=35` for the new release.

## Step 4 — Verification and publish / 验证与发布

- [x] Run the focused test and the full Gradle gate.
- [x] Install and launch the APK on `emulator-5554`.
- [ ] Push branch/tag `v0.3.35`, create the GitHub Release, and verify the asset digest.
