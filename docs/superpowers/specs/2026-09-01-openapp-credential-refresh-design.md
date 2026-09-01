# OpenApp credential refresh UX / OpenApp 凭据更换体验

## Context / 背景

Settings currently shows the OpenApp credential editor only when no credential is configured.
After a credential is stored, the user must clear it before entering a replacement. Blank or
whitespace-only input also reaches storage/API validation and becomes a generic save error.

## Goal / 目标

Make credential maintenance usable without exposing the stored secret:

1. Validate trimmed user and secret input before starting storage or quota verification.
2. Let a configured user open a blank replacement form directly.
3. Keep the existing Keystore-backed store, read-only quota verification, and rejection behavior.

## Design / 设计

Add a pure validator returning a small result enum:

```kotlin
internal enum class OpenAppCredentialInputError { USER_REQUIRED, SECRET_REQUIRED }

internal fun validateOpenAppCredentialInput(
    user: String,
    secret: String,
): OpenAppCredentialInputError?
```

The ViewModel trims both values, rejects the first missing field locally, and does not launch
a coroutine or write a credential for invalid input. A configured Settings panel gets a
`REPLACE OPENAPP CREDENTIAL` action. Clicking it opens the existing editor with blank fields;
the secret is never read back or displayed. A successful save returns to the configured state;
an invalid or unverifiable save keeps the replacement form available through the existing
state and error messages.

## State and UI / 状态与界面

The editor visibility is local Compose state (`editingCredential`) while configured status
remains ViewModel state. Clear still removes the credential. Save and quota verification keep
their current loading, authorization-rejection, and network-error semantics.

## Boundaries / 边界

No main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, background submission, or automatic POST retry is introduced.
The stored secret remains Keystore-backed and excluded from backup.

## Verification / 验证

TDD covers whitespace trimming, missing user, missing secret, and valid input. ViewModel
regression tests cover that invalid input does not write. A Release emulator check confirms
the configured panel exposes replacement without revealing the secret and that the app remains
online.
