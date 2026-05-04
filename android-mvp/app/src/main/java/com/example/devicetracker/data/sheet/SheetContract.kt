package com.example.devicetracker.data.sheet

enum class SheetRole {
    DEVICE_MASTER,
    DMBT_LOG,
    DMBT_REPAIR_LOG,
    HGT_CHECKS,
    LOOKUP_OPTIONS,
    APP_CONFIG
}

object DmbtLogColumns {
    const val RECORD_ID = "record_id"
    const val MA_THIET_BI = "ma_thiet_bi"
    const val HANG_MUC = "hang_muc"
    const val NGUOI_BAO_CAO = "nguoi_bao_cao"
    const val TINH_TRANG_THIET_BI = "tinh_trang_thiet_bi"
    const val KTV_PHU_TRACH = "ktv_phu_trach"
    const val NGAY_PHAT_HIEN = "ngay_phat_hien"
    const val NGAY_SUA_CHUA = "ngay_sua_chua"
    const val GHI_CHU = "ghi_chu"
    const val UPDATED_AT = "updated_at"
}

object DmbtRepairLogColumns {
    const val RECORD_ID = "record_id"
    const val MA_THIET_BI = "ma_thiet_bi"
    const val NGAY_SUA_CHUA = "ngay_sua_chua"
    const val GHI_CHU = "ghi_chu"
    const val UPDATED_AT = "updated_at"
}

object HgtCheckColumns {
    const val RECORD_ID = "record_id"
    const val MA_THIET_BI = "ma_thiet_bi"
    const val CHU_KY_NGAY = "chu_ky_ngay"
    const val LAN_GAN_NHAT = "lan_gan_nhat"
    const val LAN_TIEP_THEO = "lan_tiep_theo"
    const val UPDATED_AT = "updated_at"
}

object DeviceMasterColumns {
    const val DEVICE_CODE = "device_code"
    const val DEVICE_NAME = "device_name"
    const val AREA = "area"
    const val LINE = "line"
    const val STATUS = "status"
    const val UPDATED_AT = "updated_at"
}

object LookupOptionColumns {
    const val OPTION_GROUP = "option_group"
    const val OPTION_KEY = "option_key"
    const val OPTION_LABEL = "option_label"
    const val SORT_ORDER = "sort_order"
    const val IS_ACTIVE = "is_active"
    const val UPDATED_AT = "updated_at"
}

object AppConfigColumns {
    const val CONFIG_KEY = "config_key"
    const val CONFIG_VALUE = "config_value"
    const val VALUE_TYPE = "value_type"
    const val UPDATED_AT = "updated_at"
}

object SheetContract {
    val requiredRolesForSync: Set<SheetRole> = setOf(SheetRole.DMBT_LOG)

    val requiredColumnsByRole: Map<SheetRole, Set<String>> = mapOf(
        SheetRole.DMBT_LOG to setOf(
            DmbtLogColumns.RECORD_ID,
            DmbtLogColumns.MA_THIET_BI,
            DmbtLogColumns.HANG_MUC,
            DmbtLogColumns.NGUOI_BAO_CAO,
            DmbtLogColumns.TINH_TRANG_THIET_BI,
            DmbtLogColumns.KTV_PHU_TRACH,
            DmbtLogColumns.NGAY_PHAT_HIEN,
            DmbtLogColumns.NGAY_SUA_CHUA,
            DmbtLogColumns.GHI_CHU,
            DmbtLogColumns.UPDATED_AT
        ),
        SheetRole.DMBT_REPAIR_LOG to setOf(
            DmbtRepairLogColumns.RECORD_ID,
            DmbtRepairLogColumns.MA_THIET_BI,
            DmbtRepairLogColumns.NGAY_SUA_CHUA,
            DmbtRepairLogColumns.GHI_CHU,
            DmbtRepairLogColumns.UPDATED_AT
        ),
        SheetRole.HGT_CHECKS to setOf(
            HgtCheckColumns.MA_THIET_BI,
            HgtCheckColumns.CHU_KY_NGAY,
            HgtCheckColumns.LAN_GAN_NHAT,
            HgtCheckColumns.LAN_TIEP_THEO
        ),
        SheetRole.DEVICE_MASTER to setOf(
            DeviceMasterColumns.DEVICE_CODE
        ),
        SheetRole.LOOKUP_OPTIONS to setOf(
            LookupOptionColumns.OPTION_GROUP,
            LookupOptionColumns.OPTION_KEY,
            LookupOptionColumns.OPTION_LABEL
        ),
        SheetRole.APP_CONFIG to setOf(
            AppConfigColumns.CONFIG_KEY,
            AppConfigColumns.CONFIG_VALUE,
            AppConfigColumns.VALUE_TYPE
        )
    )
}
