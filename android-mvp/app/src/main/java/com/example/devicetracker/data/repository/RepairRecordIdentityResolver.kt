package com.example.devicetracker.data.repository

/**
 * Resolves repair-sheet record IDs to local DMBT record IDs.
 *
 * Repair rows can contain either a base ID ("TB001-001") or a namespaced
 * readonly DMBT ID ("readonly-dmbt-1607125070-TB001-001"). Local rows can also
 * use either form depending on which sheet they came from.
 *
 * Safety rules:
 * - Prefer exact match.
 * - Strip "readonly-dmbt-{numericSheetId}-" only when the namespace is valid.
 * - Return null for not-found or ambiguous base-ID matches to avoid writing to
 *   the wrong DMBT row.
 */
object RepairRecordIdentityResolver {

    private const val NAMESPACE_PREFIX = "readonly-dmbt-"

    /**
     * Strips only valid "readonly-dmbt-{numericSheetId}-{baseRecordId}" IDs.
     * Malformed namespaces are preserved unchanged.
     */
    fun stripDmbtNamespace(recordId: String): String {
        if (!recordId.startsWith(NAMESPACE_PREFIX)) {
            return recordId
        }

        val withoutPrefix = recordId.removePrefix(NAMESPACE_PREFIX)
        val firstDashIndex = withoutPrefix.indexOf('-')
        if (firstDashIndex < 0) {
            return recordId
        }

        val potentialSheetId = withoutPrefix.substring(0, firstDashIndex)
        if (!potentialSheetId.all { it.isDigit() } || potentialSheetId.isEmpty()) {
            return recordId
        }

        val afterSheetId = withoutPrefix.substring(firstDashIndex + 1)
        if (afterSheetId.isEmpty()) {
            return recordId
        }

        return afterSheetId
    }

    /**
     * Returns a single safe local ID match, or null when there is no match or
     * more than one possible match.
     */
    fun resolveRepairRecordId(
        repairRecordId: String,
        localRecordIds: List<String>
    ): String? {
        if (repairRecordId.isBlank() || localRecordIds.isEmpty()) {
            return null
        }

        if (repairRecordId in localRecordIds) {
            return repairRecordId
        }

        val repairBaseId = stripDmbtNamespace(repairRecordId)
        val matchingLocalIds = localRecordIds.filter { localId ->
            stripDmbtNamespace(localId) == repairBaseId
        }

        return when (matchingLocalIds.size) {
            0 -> null
            1 -> matchingLocalIds.first()
            else -> null
        }
    }
}
