package com.example.devicetracker.data.sheet

enum class SheetSyncMode {
    DISABLED,
    INVENTORY_ONLY,
    PULL_ONLY,
    TWO_WAY
}

data class SheetSyncPolicy(
    val role: SheetRole,
    val mode: SheetSyncMode,
    val primaryKeyColumns: Set<String>,
    val conflictPolicy: String
)

class SheetSyncRegistry private constructor(
    private val policies: Map<SheetRole, SheetSyncPolicy>
) {
    fun get(role: SheetRole): SheetSyncPolicy {
        return policies[role] ?: SheetSyncPolicy(
            role = role,
            mode = SheetSyncMode.DISABLED,
            primaryKeyColumns = emptySet(),
            conflictPolicy = "disabled"
        )
    }

    fun canRead(role: SheetRole): Boolean {
        return get(role).mode in setOf(
            SheetSyncMode.INVENTORY_ONLY,
            SheetSyncMode.PULL_ONLY,
            SheetSyncMode.TWO_WAY
        )
    }

    fun canWrite(role: SheetRole): Boolean {
        return get(role).mode == SheetSyncMode.TWO_WAY
    }

    companion object {
        fun default(): SheetSyncRegistry {
            return SheetSyncRegistry(
                mapOf(
                    SheetRole.DMBT_LOG to SheetSyncPolicy(
                        role = SheetRole.DMBT_LOG,
                        mode = SheetSyncMode.TWO_WAY,
                        primaryKeyColumns = setOf(DmbtLogColumns.RECORD_ID),
                        conflictPolicy = "local_first_then_remote_merge"
                    ),
                    SheetRole.DMBT_REPAIR_LOG to SheetSyncPolicy(
                        role = SheetRole.DMBT_REPAIR_LOG,
                        mode = SheetSyncMode.TWO_WAY,
                        primaryKeyColumns = setOf(DmbtRepairLogColumns.RECORD_ID),
                        conflictPolicy = "local_first_repair_date_update"
                    ),
                    SheetRole.HGT_CHECKS to SheetSyncPolicy(
                        role = SheetRole.HGT_CHECKS,
                        mode = SheetSyncMode.TWO_WAY,
                        primaryKeyColumns = setOf(HgtCheckColumns.MA_THIET_BI),
                        conflictPolicy = "device_code_upsert"
                    ),
                    SheetRole.DEVICE_MASTER to SheetSyncPolicy(
                        role = SheetRole.DEVICE_MASTER,
                        mode = SheetSyncMode.INVENTORY_ONLY,
                        primaryKeyColumns = setOf(DeviceMasterColumns.DEVICE_CODE),
                        conflictPolicy = "dry_run_before_pull_only"
                    ),
                    SheetRole.LOOKUP_OPTIONS to SheetSyncPolicy(
                        role = SheetRole.LOOKUP_OPTIONS,
                        mode = SheetSyncMode.INVENTORY_ONLY,
                        primaryKeyColumns = setOf(
                            LookupOptionColumns.OPTION_GROUP,
                            LookupOptionColumns.OPTION_KEY
                        ),
                        conflictPolicy = "fallback_to_builtin_defaults"
                    ),
                    SheetRole.APP_CONFIG to SheetSyncPolicy(
                        role = SheetRole.APP_CONFIG,
                        mode = SheetSyncMode.INVENTORY_ONLY,
                        primaryKeyColumns = setOf(AppConfigColumns.CONFIG_KEY),
                        conflictPolicy = "whitelist_keys_only"
                    )
                )
            )
        }
    }
}
