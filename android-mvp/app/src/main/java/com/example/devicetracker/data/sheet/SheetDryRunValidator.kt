package com.example.devicetracker.data.sheet

enum class SheetDryRunStatus {
    OK,
    SCHEMA_ERROR
}

data class SheetDryRunResult(
    val role: SheetRole,
    val status: SheetDryRunStatus,
    val missingColumns: List<String>,
    val extraColumns: List<String>
) {
    val canEnablePullOnly: Boolean
        get() = status == SheetDryRunStatus.OK
}

object SheetDryRunValidator {
    fun validateHeader(
        role: SheetRole,
        headers: List<String>
    ): SheetDryRunResult {
        val normalizedHeaders = headers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val requiredColumns = SheetContract.requiredColumnsByRole[role].orEmpty()
        val missingColumns = requiredColumns
            .filterNot { normalizedHeaders.contains(it) }
            .sorted()
        val extraColumns = normalizedHeaders
            .filterNot { requiredColumns.contains(it) }
            .sorted()

        return SheetDryRunResult(
            role = role,
            status = if (missingColumns.isEmpty()) SheetDryRunStatus.OK else SheetDryRunStatus.SCHEMA_ERROR,
            missingColumns = missingColumns,
            extraColumns = extraColumns
        )
    }
}
