package com.example.devicetracker.ui.sync

internal fun toFriendlySyncMessage(raw: String): String {
    val normalized = raw.trim()
    if (normalized.contains("HTTP 401", ignoreCase = true) ||
        normalized.contains("invalid authentication credentials", ignoreCase = true)
    ) {
        return "OAuth Google Sheets chưa hợp lệ hoặc refresh token không dùng được (401). " +
            "Vui lòng kiểm tra SHEETS_OAUTH_CLIENT_ID, SHEETS_OAUTH_CLIENT_SECRET và SHEETS_REFRESH_TOKEN."
    }
    if (normalized.contains("HTTP 403", ignoreCase = true)) {
        return "Tài khoản hiện tại chưa có quyền đọc/ghi Google Sheet (403). " +
            "Vui lòng kiểm tra quyền chia sẻ file."
    }
    if (normalized.contains("HTTP 404", ignoreCase = true)) {
        return "Không tìm thấy Spreadsheet hoặc sheetId đang cấu hình (404). " +
            "Vui lòng kiểm tra lại ID trong local.properties."
    }
    return normalized
}
