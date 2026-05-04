package com.example.devicetracker.data.sheet

enum class SheetsAuthMode {
    REFRESH_TOKEN,
    ACCESS_TOKEN,
    MISSING
}

internal fun resolveSheetsAuthMode(
    accessToken: String,
    oauthClientId: String,
    refreshToken: String
): SheetsAuthMode {
    return when {
        oauthClientId.isNotBlank() && refreshToken.isNotBlank() -> SheetsAuthMode.REFRESH_TOKEN
        accessToken.isNotBlank() -> SheetsAuthMode.ACCESS_TOKEN
        else -> SheetsAuthMode.MISSING
    }
}
