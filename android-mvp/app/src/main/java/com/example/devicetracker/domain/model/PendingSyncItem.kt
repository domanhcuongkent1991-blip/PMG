package com.example.devicetracker.domain.model

data class PendingSyncItem(
    val id: String,
    val deviceCode: String,
    val typeLabel: String,
    val detail: String,
    val syncStatus: String,
    val updatedAt: Long
)
