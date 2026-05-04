package com.example.devicetracker.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration1To2_createsHgtTable_and_preservesExistingData() {
        context.deleteDatabase(dbName)
        createVersion1Database()

        val roomDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .build()

        val sqliteDb = roomDb.openHelper.writableDatabase

        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='hgt_checks'").use { cursor ->
            assertTrue("hgt_checks table must exist after migration", cursor.moveToFirst())
        }

        sqliteDb.query("SELECT maThietBi FROM device_logs WHERE recordId = 'log-1'").use { cursor ->
            assertTrue("existing device log must be preserved", cursor.moveToFirst())
            assertEquals("TB01", cursor.getString(0))
        }

        sqliteDb.query("SELECT sourceSheetId FROM device_logs WHERE recordId = 'log-1'").use { cursor ->
            assertTrue("sourceSheetId column must exist after migration", cursor.moveToFirst())
            assertTrue("legacy row sourceSheetId must be null", cursor.isNull(0))
        }

        sqliteDb.execSQL(
            """
            INSERT INTO hgt_checks (
                id, maThietBi, chuKyNgay, lanGanNhat, lanTiepTheo, updatedAt
            ) VALUES (
                'hgt-1', 'TB01', 30, '01/01/2026', '31/01/2026', 1700000000000
            )
            """.trimIndent()
        )

        sqliteDb.query("SELECT syncStatus FROM hgt_checks WHERE id = 'hgt-1'").use { cursor ->
            assertTrue("inserted hgt row must be readable", cursor.moveToFirst())
            assertEquals("SYNCED", cursor.getString(0))
        }

        roomDb.close()
    }

    private fun createVersion1Database() {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val legacyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacyDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS device_logs (
                recordId TEXT NOT NULL PRIMARY KEY,
                maThietBi TEXT NOT NULL,
                hangMuc TEXT NOT NULL,
                nguoiBaoCao TEXT NOT NULL,
                tinhTrangThietBi TEXT NOT NULL,
                ktvPhuTrach TEXT NOT NULL,
                ngayPhatHien TEXT NOT NULL,
                ngaySuaChua TEXT,
                ghiChu TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                syncStatus TEXT NOT NULL
            )
            """.trimIndent()
        )
        legacyDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recordId TEXT NOT NULL,
                operation TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0,
                lastError TEXT
            )
            """.trimIndent()
        )
        legacyDb.execSQL(
            """
            INSERT INTO device_logs (
                recordId, maThietBi, hangMuc, nguoiBaoCao, tinhTrangThietBi,
                ktvPhuTrach, ngayPhatHien, ngaySuaChua, ghiChu, updatedAt, syncStatus
            ) VALUES (
                'log-1', 'TB01', 'Camera', 'A', 'Loi',
                'KTV1', '01/01/2026', NULL, 'seed', 1700000000000, 'SYNCED'
            )
            """.trimIndent()
        )
        legacyDb.version = 1
        legacyDb.close()
    }
}
