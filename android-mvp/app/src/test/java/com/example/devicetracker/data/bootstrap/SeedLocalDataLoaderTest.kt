package com.example.devicetracker.data.bootstrap

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class SeedLocalDataLoaderTest {

    @Test
    fun bundledSnapshotSeed_isDisabledByDefault_forFreshInstallRemoteFirstFlow() {
        assertFalse(SeedLocalDataLoader.isBundledSnapshotSeedEnabled())
    }

    @Test
    fun bundledSnapshotAssets_areEmptyForRemoteFirstFlow() {
        assertEquals("[]", File("src/main/assets/seed_device_logs.json").readText().trim())
        assertEquals("[]", File("src/main/assets/seed_hgt_checks.json").readText().trim())
    }
}
