package com.example.devicetracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hgt_checks (
                    id TEXT NOT NULL PRIMARY KEY,
                    maThietBi TEXT NOT NULL,
                    chuKyNgay INTEGER NOT NULL,
                    lanGanNhat TEXT NOT NULL,
                    lanTiepTheo TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    syncStatus TEXT NOT NULL DEFAULT 'SYNCED'
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE device_logs ADD COLUMN sourceSheetId INTEGER")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE hgt_checks ADD COLUMN ghiChu TEXT NOT NULL DEFAULT ''")
        }
    }
}
