package com.example.devicetracker.ui.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFriendlyMessageTest {

    @Test
    fun toFriendlySyncMessage_for_401_guides_refresh_token_configuration() {
        val message = toFriendlySyncMessage(
            "Google Sheets token da het han hoac khong hop le (HTTP 401)."
        )

        assertTrue(message.contains("refresh token", ignoreCase = true))
        assertTrue(message.contains("OAuth", ignoreCase = true))
        assertFalse(message.contains("SHEETS_ACCESS_TOKEN", ignoreCase = true))
    }
}
