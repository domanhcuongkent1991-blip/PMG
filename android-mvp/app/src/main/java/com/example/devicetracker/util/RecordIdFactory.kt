package com.example.devicetracker.util

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordIdFactory @Inject constructor() {
    fun newId(): String {
        return "DMBT-${UUID.randomUUID()}"
    }
}
