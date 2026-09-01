# Release identity plan / 发布版本身份计划

## Step 1 — Design / 设计

- [x] Record the mismatch between the published tag and Android metadata.
- [x] Define `v0.3.34` / `versionCode=34` as the next release identity.

## Step 2 — Implementation / 实现

- [ ] Set Android `versionCode` and `versionName`.
- [ ] Update bilingual README and roadmap status while preserving old entries.
- [ ] Add bilingual `docs/releases/v0.3.34.md`.

## Step 3 — Verification / 验证

- [ ] Run `git diff --check` and the full Gradle gate.
- [ ] Install the APK and verify package version metadata on `emulator-5554`.

## Step 4 — Publish / 发布

- [ ] Commit with a bilingual message.
- [ ] Push the branch and annotated tag `v0.3.34`.
- [ ] Create the GitHub Release with the APK and verify the remote digest.
