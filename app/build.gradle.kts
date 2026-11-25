import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.detekt)
}

val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use(::load)
    }
}
val releaseStoreFilePath: String? = keystoreProps.getProperty("storeFile") ?: System.getenv("NEXUS_KEYSTORE_PATH")
val releaseStorePassword: String? = keystoreProps.getProperty("storePassword") ?: System.getenv("NEXUS_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = keystoreProps.getProperty("keyAlias") ?: System.getenv("NEXUS_KEY_ALIAS")
val releaseKeyPassword: String? = keystoreProps.getProperty("keyPassword") ?: System.getenv("NEXUS_KEY_PASSWORD")

android {
    namespace = "io.github.patodesoxigenado.nexusfetch"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.patodesoxigenado.nexusfetch"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val ksFile = releaseStoreFilePath?.takeIf { it.isNotBlank() }?.let(::file)
        if (ksFile != null &&
            ksFile.exists() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = ksFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("ObsoleteLintCustomCheck", "GradleDependency")
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM & Material 3
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room SQLite
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore, Coroutines & Serialization
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Network & Images
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Media Engines (Media3 Transformer + youtubedl-android)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    // Unit Testing (JUnit 5 + MockK + Turbine + Coroutines Test)
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

fun sha256OfFile(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun escapeJsonString(raw: String): String =
    raw.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

tasks.register("stageDistributionApks") {
    group = "distribution"
    description = "Builds release and ABI-split debug APKs, stages them under root apk/, and writes apk/version.json."
    dependsOn("assembleDebug", "assembleRelease")

    val apkOutputsDir = layout.buildDirectory.dir("outputs/apk")
    val distRootDir = rootProject.layout.projectDirectory.dir("apk").asFile
    val verCode = android.defaultConfig.versionCode ?: 1
    val verName = android.defaultConfig.versionName ?: "1.0.0"

    doLast {
        val releaseSrcDir = apkOutputsDir.get().dir("release").asFile
        val debugSrcDir = apkOutputsDir.get().dir("debug").asFile

        val releaseApkSrc = listOf(
            releaseSrcDir.resolve("app-arm64-v8a-release.apk"),
            releaseSrcDir.resolve("app-arm64-v8a-release-unsigned.apk"),
        ).firstOrNull { it.exists() }
            ?: error("Release APK not found in ${releaseSrcDir.absolutePath}")

        val debugArm64Src = debugSrcDir.resolve("app-arm64-v8a-debug.apk")
        val debugArmV7Src = debugSrcDir.resolve("app-armeabi-v7a-debug.apk")
        check(debugArm64Src.exists()) { "Debug arm64-v8a APK not found: ${debugArm64Src.absolutePath}" }
        check(debugArmV7Src.exists()) { "Debug armeabi-v7a APK not found: ${debugArmV7Src.absolutePath}" }

        val debugDestDir = distRootDir.resolve("debug")
        distRootDir.mkdirs()
        debugDestDir.mkdirs()

        distRootDir.listFiles()?.filter { it.isFile && it.extension == "apk" }?.forEach { it.delete() }
        debugDestDir.listFiles()?.filter { it.isFile && it.extension == "apk" }?.forEach { it.delete() }

        val releaseDest = distRootDir.resolve("NexusFetch.apk")
        val debugArm64Dest = debugDestDir.resolve("NexusFetch_arm64-v8a-debug.apk")
        val debugArmV7Dest = debugDestDir.resolve("NexusFetch_armeabi-v7a-debug.apk")

        releaseApkSrc.copyTo(releaseDest, overwrite = true)
        debugArm64Src.copyTo(debugArm64Dest, overwrite = true)
        debugArmV7Src.copyTo(debugArmV7Dest, overwrite = true)

        val maxGitHubBytes = 100L * 1024L * 1024L
        listOf(releaseDest, debugArm64Dest, debugArmV7Dest).forEach { staged ->
            check(staged.length() in 1L until maxGitHubBytes) {
                "Staged APK ${staged.name} size (${staged.length()} bytes) exceeds GitHub 100 MB limit."
            }
        }

        val notes = "NexusFetch v$verName: Downloads YouTube (4K/MP3), Instagram (Posts, Reels e Carrosséis) e Twitter/X; " +
            "Share Sheet rápido, Multi-Download em fila, Compressor Discord/WhatsApp, Conversor em lote e Recorte Rápido."

        fun artifactJson(fileName: String, relPath: String, file: java.io.File, aCode: Int, aName: String): String =
            """
            |{
            |      "fileName": "${escapeJsonString(fileName)}",
            |      "path": "${escapeJsonString(relPath)}",
            |      "sha256": "${sha256OfFile(file)}",
            |      "sizeBytes": ${file.length()},
            |      "versionCode": $aCode,
            |      "versionName": "${escapeJsonString(aName)}"
            |    }
            """.trimMargin()

        val manifestJson = """
            |{
            |  "versionCode": $verCode,
            |  "versionName": "${escapeJsonString(verName)}",
            |  "updatedAt": "${Instant.now()}",
            |  "releaseNotes": "${escapeJsonString(notes)}",
            |  "release": ${artifactJson("NexusFetch.apk", "apk/NexusFetch.apk", releaseDest, verCode, verName)},
            |  "debug": {
            |    "arm64-v8a": ${artifactJson("NexusFetch_arm64-v8a-debug.apk", "apk/debug/NexusFetch_arm64-v8a-debug.apk", debugArm64Dest, verCode, "$verName-debug")},
            |    "armeabi-v7a": ${artifactJson("NexusFetch_armeabi-v7a-debug.apk", "apk/debug/NexusFetch_armeabi-v7a-debug.apk", debugArmV7Dest, verCode, "$verName-debug")}
            |  }
            |}
            |
        """.trimMargin()

        distRootDir.resolve("version.json").writeText(manifestJson, Charsets.UTF_8)
        logger.lifecycle("Staged distribution APKs in ${distRootDir.absolutePath}")
    }
}
