package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.HgtCheckRepository
import javax.inject.Inject

class UpsertHgtCheckUseCase @Inject constructor(
    private val repository: HgtCheckRepository
) {
    suspend operator fun invoke(
        id: String?,
        maThietBi: String,
        chuKyNgay: Int,
        lanGanNhat: String,
        ghiChu: String
    ) {
        repository.upsertCheck(
            id = id,
            maThietBi = maThietBi,
            chuKyNgay = chuKyNgay,
            lanGanNhat = lanGanNhat,
            ghiChu = ghiChu
        )
    }
}
