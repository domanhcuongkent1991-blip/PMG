package com.example.devicetracker.data.sheet

import com.example.devicetracker.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: Move this config to DataStore or a dedicated configuration screen.
 * MVP keeps it static to enforce schema rules before implementing OAuth + API calls.
 */
@Singleton
class SheetConfig @Inject constructor() {
    val spreadsheetId: String = BuildConfig.SHEETS_SPREADSHEET_ID.trim()
    val accessToken: String = BuildConfig.SHEETS_ACCESS_TOKEN.trim()
    val oauthClientId: String = BuildConfig.SHEETS_OAUTH_CLIENT_ID.trim()
    val oauthClientSecret: String = BuildConfig.SHEETS_OAUTH_CLIENT_SECRET.trim()
    val refreshToken: String = BuildConfig.SHEETS_REFRESH_TOKEN.trim()
    val dmbtReadOnlySheetIds: List<Int>
        get() = parseReadOnlySheetIds(
            rawValue = BuildConfig.SHEETS_DMBT_READONLY_SHEET_IDS,
            primarySheetId = sheetId(SheetRole.DMBT_LOG)
        )
    val dmbtSheetBindings: List<DmbtSheetBinding>
        get() = parseDmbtSheetBindings(
            rawSheetIds = BuildConfig.SHEETS_DMBT_SHEET_IDS,
            defaultCreateSheetId = parseSheetId(BuildConfig.SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID),
            legacyPrimarySheetId = sheetId(SheetRole.DMBT_LOG),
            legacyReadOnlySheetIds = BuildConfig.SHEETS_DMBT_READONLY_SHEET_IDS
        )
    val yearlyDmbtSheetBindings: List<DmbtSheetBinding>
        get() = splitDmbtSheetBindings(
            bindings = dmbtSheetBindings,
            monthlySheetIds = MONTHLY_DMBT_SHEET_IDS
        ).yearly
    val monthlyDmbtSheetBindings: List<DmbtSheetBinding>
        get() = splitDmbtSheetBindings(
            bindings = dmbtSheetBindings,
            monthlySheetIds = MONTHLY_DMBT_SHEET_IDS
        ).monthly
    val dmbtDefaultCreateSheetId: Int?
        get() = dmbtSheetBindings.firstOrNull { it.isDefaultCreateTarget }?.sheetId

    fun dmbtSheetIdForDiscoveryDate(discoveryDate: String): Int? {
        return resolveDmbtSheetIdForDiscoveryDate(
            discoveryDate = discoveryDate,
            yearlyBindings = yearlyDmbtSheetBindings
        )
    }

    val canRefreshAccessToken: Boolean
        get() = authMode == SheetsAuthMode.REFRESH_TOKEN

    val authMode: SheetsAuthMode
        get() = resolveSheetsAuthMode(
            accessToken = accessToken,
            oauthClientId = oauthClientId,
            refreshToken = refreshToken
        )

    private val roleConfigs: Map<SheetRole, SheetRoleConfig> = mapOf(
        SheetRole.DEVICE_MASTER to SheetRoleConfig(
            sheetId = parseSheetId(BuildConfig.SHEETS_DEVICE_MASTER_SHEET_ID),
            requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.DEVICE_MASTER)
        ),
        SheetRole.DMBT_LOG to SheetRoleConfig(
            sheetId = parseSheetId(BuildConfig.SHEETS_DMBT_LOG_SHEET_ID),
            requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.DMBT_LOG)
        ),
        SheetRole.DMBT_REPAIR_LOG to SheetRoleConfig(
            sheetId = parseSheetId(BuildConfig.SHEETS_REPAIR_LOG_SHEET_ID),
            requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.DMBT_REPAIR_LOG)
        ),
        SheetRole.HGT_CHECKS to SheetRoleConfig(
            sheetId = parseSheetId(BuildConfig.SHEETS_HGT_CHECKS_SHEET_ID),
            requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.HGT_CHECKS)
        ),
        SheetRole.LOOKUP_OPTIONS to SheetRoleConfig(
            sheetId = parseSheetId(BuildConfig.SHEETS_LOOKUP_OPTIONS_SHEET_ID),
            requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.LOOKUP_OPTIONS)
        ),
        SheetRole.APP_CONFIG to SheetRoleConfig(
            sheetId = parseSheetId(BuildConfig.SHEETS_APP_CONFIG_SHEET_ID),
            requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.APP_CONFIG)
        )
    )

    fun sheetId(role: SheetRole): Int? = roleConfigs[role]?.sheetId

    fun requiredColumns(role: SheetRole): Set<String> =
        roleConfigs[role]?.requiredColumns ?: emptySet()

    fun missingSheetIdRoles(requiredRoles: Set<SheetRole> = SheetContract.requiredRolesForSync): List<SheetRole> {
        return requiredRoles
            .let { roles -> findMissingSheetIdRoles(roles, roleConfigs.mapValues { it.value.sheetId }) }
    }

    fun duplicateSheetIdRoles(roles: Set<SheetRole> = roleConfigs.keys): Map<Int, List<SheetRole>> {
        val roleSheetIds = roleConfigs
            .filterKeys { roles.contains(it) }
            .mapValues { it.value.sheetId }
        return findDuplicateSheetIdRoles(roleSheetIds)
    }

    data class SheetRoleConfig(
        val sheetId: Int?,
        val requiredColumns: Set<String>
    )

    data class DmbtSheetBinding(
        val sheetId: Int,
        val roleLabel: String = "DMBT",
        val mode: SheetSyncMode,
        val isDefaultCreateTarget: Boolean
    )

    data class DmbtSheetBindingBuckets(
        val yearly: List<DmbtSheetBinding>,
        val monthly: List<DmbtSheetBinding>
    )

    private fun parseSheetId(rawValue: String): Int? {
        return rawValue
            .trim()
            .toIntOrNull()
            ?.takeIf { it >= 0 }
    }

    companion object {
        private const val FIRST_YEARLY_DMBT_YEAR = 2022
        internal val MONTHLY_DMBT_SHEET_IDS: Set<Int> = setOf(1383308512)
        internal fun isMonthlyDmbtSheetId(sheetId: Int?): Boolean =
            sheetId != null && MONTHLY_DMBT_SHEET_IDS.contains(sheetId)

        internal fun resolveDmbtSheetIdForDiscoveryDate(
            discoveryDate: String,
            yearlyBindings: List<DmbtSheetBinding>
        ): Int? {
            val year = Regex("(\\d{4})").find(discoveryDate.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: return null
            val index = year - FIRST_YEARLY_DMBT_YEAR
            return yearlyBindings.getOrNull(index)?.sheetId
        }

        internal fun findMissingSheetIdRoles(
            requiredRoles: Set<SheetRole>,
            roleSheetIds: Map<SheetRole, Int?>
        ): List<SheetRole> {
            return requiredRoles
                .filter { roleSheetIds[it] == null }
                .sortedBy { it.name }
        }

        internal fun findDuplicateSheetIdRoles(
            roleSheetIds: Map<SheetRole, Int?>
        ): Map<Int, List<SheetRole>> {
            return roleSheetIds
                .mapNotNull { (role, sheetId) -> sheetId?.let { it to role } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .mapValues { (_, roles) -> roles.sortedBy { it.name } }
                .filterValues { it.size > 1 }
                .toSortedMap()
        }

        internal fun parseReadOnlySheetIds(rawValue: String, primarySheetId: Int?): List<Int> {
            return rawValue
                .split(',', ';', '\n', '\r', '\t', ' ')
                .mapNotNull { rawId ->
                    rawId.trim().toIntOrNull()?.takeIf { sheetId ->
                        sheetId >= 0 && sheetId != primarySheetId
                    }
                }
                .distinct()
        }

        internal fun parseDmbtSheetBindings(
            rawSheetIds: String,
            defaultCreateSheetId: Int?,
            legacyPrimarySheetId: Int?,
            legacyReadOnlySheetIds: String
        ): List<DmbtSheetBinding> {
            val configuredSheetIds = parseSheetIdList(rawSheetIds)
            val sheetIds = if (configuredSheetIds.isNotEmpty()) {
                configuredSheetIds
            } else {
                listOfNotNull(legacyPrimarySheetId) +
                    parseReadOnlySheetIds(
                        rawValue = legacyReadOnlySheetIds,
                        primarySheetId = legacyPrimarySheetId
                    )
            }.distinct()

            if (sheetIds.isEmpty()) return emptyList()

            val resolvedDefaultSheetId = defaultCreateSheetId
                ?.takeIf { it in sheetIds }
                ?: legacyPrimarySheetId?.takeIf { it in sheetIds }
                ?: sheetIds.first()

            return sheetIds.map { sheetId ->
                DmbtSheetBinding(
                    sheetId = sheetId,
                    mode = SheetSyncMode.TWO_WAY,
                    isDefaultCreateTarget = sheetId == resolvedDefaultSheetId
                )
            }
        }

        internal fun splitDmbtSheetBindings(
            bindings: List<DmbtSheetBinding>,
            monthlySheetIds: Set<Int>
        ): DmbtSheetBindingBuckets {
            val monthly = bindings.filter { monthlySheetIds.contains(it.sheetId) }
            val yearly = bindings.filterNot { monthlySheetIds.contains(it.sheetId) }
            return DmbtSheetBindingBuckets(
                yearly = yearly,
                monthly = monthly
            )
        }

        private fun parseSheetIdList(rawValue: String): List<Int> {
            return rawValue
                .split(',', ';', '\n', '\r', '\t', ' ')
                .mapNotNull { rawId -> rawId.trim().toIntOrNull()?.takeIf { it >= 0 } }
                .distinct()
        }
    }
}
