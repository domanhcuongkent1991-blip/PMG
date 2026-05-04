package com.example.devicetracker.data.sheet

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetsAuthModeTest {

    @Test
    fun resolveSheetsAuthMode_prefers_refresh_token_when_available() {
        val mode = resolveSheetsAuthMode(
            accessToken = "short-lived-access-token",
            oauthClientId = "client-id",
            refreshToken = "refresh-token"
        )

        assertEquals(SheetsAuthMode.REFRESH_TOKEN, mode)
    }

    @Test
    fun resolveSheetsAuthMode_uses_access_token_only_as_fallback() {
        val mode = resolveSheetsAuthMode(
            accessToken = "short-lived-access-token",
            oauthClientId = "",
            refreshToken = ""
        )

        assertEquals(SheetsAuthMode.ACCESS_TOKEN, mode)
    }

    @Test
    fun resolveSheetsAuthMode_reports_missing_when_no_usable_credentials_exist() {
        val mode = resolveSheetsAuthMode(
            accessToken = "",
            oauthClientId = "",
            refreshToken = ""
        )

        assertEquals(SheetsAuthMode.MISSING, mode)
    }
}
