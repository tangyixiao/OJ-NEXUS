# Installable release plan / 可安装 Release 计划

## Step 1 — Design / 设计

- [x] Confirm that `assembleRelease` currently yields only an unsigned APK.
- [x] Define a local, explicitly labelled debug-key-signed Release artifact.

## Step 2 — Version and documentation / 版本与文档

- [x] Set `versionName=0.3.36` and `versionCode=36`.
- [x] Update bilingual README/Roadmap and add `docs/releases/v0.3.36.md`.

## Step 3 — Build and runtime / 构建与运行

- [x] Run the full gate plus `assembleRelease`.
- [x] Sign the Release APK without tracking the keystore.
- [x] Install it and verify package metadata, launch, and absent Demo controls.

## Step 4 — Publish / 发布

- [ ] Add `SHA256SUMS-v0.3.36.txt`, commit, push branch/tag `v0.3.36`.
- [ ] Create the GitHub Release and verify the asset digest remotely.
