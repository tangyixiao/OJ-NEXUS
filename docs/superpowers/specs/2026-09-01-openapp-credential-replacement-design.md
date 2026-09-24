# Transaction-safe OpenApp credential replacement / 事务安全的 OpenApp 凭据更换

## Context / 背景

The current first-time flow stores a candidate OpenApp credential and then verifies it with
the quota endpoint. That is acceptable when no credential exists, but it is unsafe for rotation:
an invalid replacement could displace a still-working credential.

## Goal / 目标

Allow a configured user to enter a replacement credential without ever displaying the stored
secret and without losing the old credential when the candidate is unauthorized, forbidden,
unreachable, or cannot be persisted.

## Design / 设计

Add a separate capability for candidate verification:

```kotlin
interface LuoguOpenCredentialVerifier {
    suspend fun verifyCredential(credential: OpenAppCredential): LuoguOpenQuotaSnapshot
}
```

`LuoguOpenPlatformClient` implements this capability by calling the read-only
`quotaAvailable` endpoint with the candidate Basic authorization header. Its existing
`LuoguOpenQuotaReader.fetchQuota()` continues to read the stored credential.

The settings ViewModel gets the verifier in addition to the existing store and reader:

1. Validate and trim input locally.
2. Verify the candidate through the verifier without touching the store.
3. Write the candidate only after successful verification.
4. On any verification or write failure, keep the old store value and show the existing typed
   error state.

The configured panel exposes `REPLACE OPENAPP CREDENTIAL`. The replacement editor starts with
blank fields, offers `CANCEL`, and never reads the old secret. A successful replacement returns
to configured state and shows the candidate quota; a failed replacement keeps the editor open
while the old credential remains active.

## Error and state behavior / 错误与状态

Unauthorized, forbidden, quota, not-found, network, and API outcomes reuse the existing
localized quota errors. The configured flag remains true during replacement failures. The
existing first-time `SAVE CREDENTIAL` path remains compatible and continues to store before
verification because there is no old value to protect; replacement uses the new candidate-first
verification path.

## Boundaries / 边界

Only the official OpenApp credential is supported. No main-site password, Cookie, Session,
CSRF state, cloud service, cross-device sync, local compiler, custom-input runner, background
submission, or automatic POST retry is introduced. Secrets are never logged, backed up, or
rendered back into the UI.

## Verification / 验证

TDD covers candidate verification success, authorization rejection, network failure, and the
write-after-verification ordering. ViewModel tests cover replacement failure preserving the old
store value and success committing the candidate. Release emulator checks cover the replacement
and cancel controls without exposing the existing secret.
