package com.ojnexus.feature.settings

enum class OpenAppCredentialInputError {
    USER_REQUIRED,
    SECRET_REQUIRED,
}

internal fun validateOpenAppCredentialInput(
    user: String,
    secret: String,
): OpenAppCredentialInputError? = when {
    user.trim().isEmpty() -> OpenAppCredentialInputError.USER_REQUIRED
    secret.trim().isEmpty() -> OpenAppCredentialInputError.SECRET_REQUIRED
    else -> null
}
