package com.ojnexus.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAppCredentialInputValidationTest {
    @Test
    fun `trims valid input`() {
        assertNull(validateOpenAppCredentialInput(" user ", " secret "))
    }

    @Test
    fun `requires a non-blank user`() {
        assertEquals(
            OpenAppCredentialInputError.USER_REQUIRED,
            validateOpenAppCredentialInput("   ", "secret"),
        )
    }

    @Test
    fun `requires a non-blank secret`() {
        assertEquals(
            OpenAppCredentialInputError.SECRET_REQUIRED,
            validateOpenAppCredentialInput("user", "\t"),
        )
    }
}
