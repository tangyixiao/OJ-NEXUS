#!/usr/bin/env bash
# Executes the WorkspaceRecoveryTests suite on a real installed iOS 16+ simulator.
# Discovery, the xcodebuild log and the xcresult are written to the evidence directory
# given as the only argument. This is CI tooling only; it never claims interactive
# app acceptance and never touches user directories.
set -euo pipefail

EVIDENCE_DIR="${1:?usage: test-ios-recovery.sh <evidence-dir>}"
mkdir -p "$EVIDENCE_DIR"
cd "$(dirname "$0")/.."

xcodebuild -version
swift --version

xcodebuild -list -json > "$EVIDENCE_DIR/scheme-discovery.json"
xcrun simctl list devices available -j > "$EVIDENCE_DIR/device-discovery.json"

python3 scripts/validate_ios_recovery.py discover \
  --schemes "$EVIDENCE_DIR/scheme-discovery.json" \
  --devices "$EVIDENCE_DIR/device-discovery.json" \
  --evidence "$EVIDENCE_DIR"

SCHEME="$(cat "$EVIDENCE_DIR/picked-scheme.txt")"
DESTINATION="$(cat "$EVIDENCE_DIR/picked-destination.txt")"
echo "[ios-recovery] scheme=$SCHEME destination=$DESTINATION"

set +e
xcodebuild test \
  -scheme "$SCHEME" \
  -destination "$DESTINATION" \
  -only-testing:OJNexusCoreTests/WorkspaceRecoveryTests \
  -resultBundlePath "$EVIDENCE_DIR/ios-recovery.xcresult" \
  CODE_SIGNING_ALLOWED=NO \
  > "$EVIDENCE_DIR/xcodebuild-test.log" 2>&1
TEST_STATUS=$?
set -e
echo "[ios-recovery] xcodebuild test exit=$TEST_STATUS"
tail -40 "$EVIDENCE_DIR/xcodebuild-test.log" || true

python3 scripts/validate_ios_recovery.py validate \
  --xcresult "$EVIDENCE_DIR/ios-recovery.xcresult" \
  --evidence "$EVIDENCE_DIR"

exit "$TEST_STATUS"
