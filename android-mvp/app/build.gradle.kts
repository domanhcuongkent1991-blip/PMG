import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name, defaultValue)

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val sheetsRefreshToken: String = localProperty("SHEETS_REFRESH_TOKEN")
val debugSheetsAccessToken: String = localProperty("SHEETS_ACCESS_TOKEN")
    .takeIf { sheetsRefreshToken.isBlank() }
    .orEmpty()

val codexBuildId: String? = providers.gradleProperty("codexBuildId").orNull
if (!codexBuildId.isNullOrBlank()) {
    layout.buildDirectory.set(file("$rootDir/.codex-build/$codexBuildId/app"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.devicetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.devicetracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "SHEETS_SPREADSHEET_ID",
            localProperty("SHEETS_SPREADSHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_DEVICE_MASTER_SHEET_ID",
            localProperty("SHEETS_DEVICE_MASTER_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_DMBT_LOG_SHEET_ID",
            localProperty("SHEETS_DMBT_LOG_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_DMBT_SHEET_IDS",
            localProperty("SHEETS_DMBT_SHEET_IDS").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID",
            localProperty("SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_REPAIR_LOG_SHEET_ID",
            localProperty("SHEETS_REPAIR_LOG_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_DMBT_READONLY_SHEET_IDS",
            localProperty("SHEETS_DMBT_READONLY_SHEET_IDS").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_LOOKUP_OPTIONS_SHEET_ID",
            localProperty("SHEETS_LOOKUP_OPTIONS_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_APP_CONFIG_SHEET_ID",
            localProperty("SHEETS_APP_CONFIG_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_HGT_CHECKS_SHEET_ID",
            localProperty("SHEETS_HGT_CHECKS_SHEET_ID").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_ACCESS_TOKEN",
            "".asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_OAUTH_CLIENT_ID",
            "".asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_OAUTH_CLIENT_SECRET",
            "".asBuildConfigString()
        )
        buildConfigField(
            "String",
            "SHEETS_REFRESH_TOKEN",
            "".asBuildConfigString()
        )
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SHEETS_ACCESS_TOKEN",
                debugSheetsAccessToken.asBuildConfigString()
            )
            buildConfigField(
                "String",
                "SHEETS_OAUTH_CLIENT_ID",
                localProperty("SHEETS_OAUTH_CLIENT_ID").asBuildConfigString()
            )
            buildConfigField(
                "String",
                "SHEETS_OAUTH_CLIENT_SECRET",
                localProperty("SHEETS_OAUTH_CLIENT_SECRET").asBuildConfigString()
            )
            buildConfigField(
                "String",
                "SHEETS_REFRESH_TOKEN",
                sheetsRefreshToken.asBuildConfigString()
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.work:work-runtime-ktx:2.11.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
