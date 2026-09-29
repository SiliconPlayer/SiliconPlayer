plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
}

import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

data class ProcessCommandResult(
    val exitCode: Int,
    val output: String
)

val androidBuildToolsVersion = "36.1.0"

fun runProcessAndCapture(
    command: List<String>,
    workingDir: File? = null
): ProcessCommandResult {
    val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
    if (workingDir != null) {
        processBuilder.directory(workingDir)
    }
    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    return ProcessCommandResult(exitCode = exitCode, output = output)
}

fun runProcessWithInheritedIo(
    command: List<String>,
    workingDir: File? = null
): Int {
    val processBuilder = ProcessBuilder(command)
    if (workingDir != null) {
        processBuilder.directory(workingDir)
    }
    val process = processBuilder
        .inheritIO()
        .start()
    return process.waitFor()
}

fun parseBooleanGradleProperty(value: String?): Boolean {
    return when (value?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }
}

fun gitShortSha(): String {
    return try {
        val result = runProcessAndCapture(
            command = listOf("git", "rev-parse", "--short", "HEAD"),
            workingDir = rootProject.projectDir
        )
        if (result.exitCode == 0) {
            result.output.trim().ifBlank { "nogit" }
        } else {
            "nogit"
        }
    } catch (_: Exception) {
        "nogit"
    }
}

val aboutToml = rootProject.file("tools/licenses.toml")
val aboutScript = rootProject.file("tools/generate-about.py")
val generatedAboutVersionDir = layout.buildDirectory.dir("generated/source/aboutVersions/main")
val generatedAboutMetaDir = layout.buildDirectory.dir("generated/about/main")

val generateAboutVersions by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Generate About versions and license texts from tools/licenses.toml."
    val disableTags = providers.gradleProperty("aboutVersionDisableTagsFor").orNull ?: ""
    inputs.file(aboutToml)
    inputs.file(aboutScript)
    inputs.property("aboutVersionDisableTagsFor", disableTags)
    inputs.property(
        "aboutSourceHeads",
        runProcessAndCapture(listOf("git", "submodule", "status"), rootProject.projectDir).output
    )
    outputs.dir(generatedAboutVersionDir)
    outputs.dir(generatedAboutMetaDir)
    commandLine(
        "python3",
        aboutScript.absolutePath,
        "--repo", rootProject.projectDir.absolutePath,
        "--toml", aboutToml.absolutePath,
        "--java-out", generatedAboutVersionDir.get().asFile.absolutePath,
        "--meta-out", generatedAboutMetaDir.get().asFile.absolutePath,
        "--disable-tags-for", disableTags
    )
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
    namespace = "com.flopster101.siliconplayer"
    compileSdk = 36
    buildToolsVersion = androidBuildToolsVersion
    ndkVersion = "29.0.14206865"

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.flopster101.siliconplayer"
        minSdk = 21
        targetSdk = 34
        versionCode = 1000
        versionName = "0.1.0"
        buildConfigField("String", "GIT_SHA", "\"${gitShortSha()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild {
                cmake {
                    // Apply aggressive native optimization for release-like builds.
                    cFlags += "-O3 -ffast-math"
                    cppFlags += "-O3 -ffast-math"
                }
            }
        }
        create("optimizedDebug") {
            initWith(getByName("release"))
            // Keep debug signing so it can replace/debug-install like normal debug builds.
            signingConfig = signingConfigs.getByName("debug")
            // Allow shell Perfetto to read app atrace sections without a debuggable build.
            isProfileable = true
            // Make it clear on-device which build is installed.
            versionNameSuffix = "-optdebug"
            matchingFallbacks += listOf("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            if (parseBooleanGradleProperty(providers.gradleProperty("enableX86").orNull)) {
                include("x86")
            }
            isUniversalApk = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        disable += "NewApi"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // UADE launches uadecore via exec(), so the binary must exist as a real file
            // under nativeLibraryDir instead of being mmap-loaded directly from APK.
            useLegacyPackaging = true
        }
    }
    sourceSets {
        getByName("main") {
            assets.directories.add("build/generated/uadeRuntimeAssets/main")
            assets.directories.add("build/generated/projectmPresetsAssets/main")
            jniLibs.directories.add("build/generated/prebuiltNativeLibs/main")
            java.directories.add("build/generated/source/aboutVersions/main")
            java.directories.add(rootProject.file("shared/src/main/kotlin").absolutePath)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    source(rootProject.file("shared/src/main/kotlin"))
}

tasks.named("preBuild").configure {
    dependsOn(generateAboutVersions)
}

configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.smbj)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.smbj.rpc) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.profileinstaller)
}

fun resolveAndroidSdkDir(): File {
    val sdkEnv = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
    require(!sdkEnv.isNullOrBlank()) { "ANDROID_SDK_ROOT/ANDROID_HOME is not set" }
    val sdkDir = File(sdkEnv)
    require(sdkDir.isDirectory) { "Android SDK directory not found: $sdkDir" }
    return sdkDir
}

fun resolveBuildToolsDir(sdkDir: File): File {
    val configured = androidBuildToolsVersion
    val configuredDir = File(sdkDir, "build-tools/$configured")
    require(configuredDir.isDirectory) { "Configured build-tools directory not found: $configuredDir" }
    return configuredDir
}

fun zipalignSupports16k(zipalign: File): Boolean {
    try {
        val result = runProcessAndCapture(listOf(zipalign.absolutePath))
        return result.output.contains("-P")
    } catch (_: Exception) {
        return false
    }
}

fun register16kAlignTaskForVariant(variantName: String) {
    val taskSuffix = variantName.replaceFirstChar { c ->
        if (c.isLowerCase()) c.titlecase() else c.toString()
    }
    val assembleTaskName = "assemble$taskSuffix"
    val alignTaskName = "align${taskSuffix}Apk16k"

    val alignTask = tasks.register(alignTaskName) {
        group = "build"
        description = "Zipalign $variantName APK native libs to 16KB page boundaries and re-sign."

        doLast {
            val apkDir = layout.buildDirectory.dir("outputs/apk/$variantName").get().asFile
            if (!apkDir.exists()) {
                throw GradleException("APK directory not found at: ${apkDir.absolutePath}")
            }

            val apks = apkDir.listFiles { file ->
                file.isFile && file.name.startsWith("app-") && file.name.endsWith("-$variantName.apk")
            } ?: emptyArray()

            if (apks.isEmpty()) {
                throw GradleException("No APKs found in: ${apkDir.absolutePath}")
            }

            val sdkDir = resolveAndroidSdkDir()
            val buildToolsDir = resolveBuildToolsDir(sdkDir)
            val zipalign = File(buildToolsDir, "zipalign")
            val apksigner = File(buildToolsDir, "apksigner")
            require(zipalign.exists() && zipalign.canExecute()) {
                "zipalign not found/executable at ${zipalign.absolutePath}"
            }
            require(apksigner.exists() && apksigner.canExecute()) {
                "apksigner not found/executable at ${apksigner.absolutePath}"
            }
            require(zipalignSupports16k(zipalign)) {
                "Configured zipalign does not support '-P 16': ${zipalign.absolutePath}"
            }

            logger.lifecycle("Using zipalign: ${zipalign.absolutePath}")
            logger.lifecycle("Using apksigner: ${apksigner.absolutePath}")
            logger.lifecycle("Aligning ${apks.size} APK(s)")

            val debugKeystore = rootProject.file("debug.keystore")
            require(debugKeystore.exists()) { "Debug keystore not found at ${debugKeystore.absolutePath}" }

            apks.forEach { apk ->
                logger.lifecycle("Aligning: ${apk.name}")

                val alignedUnsigned = File(apk.parentFile, "${apk.name}-aligned-unsigned.apk")
                val alignedSigned = File(apk.parentFile, "${apk.name}-aligned-signed.apk")

                val zipalignExitCode = runProcessWithInheritedIo(
                    command = listOf(
                        zipalign.absolutePath,
                        "-f",
                        "-P", "16",
                        "-v", "4",
                        apk.absolutePath,
                        alignedUnsigned.absolutePath
                    )
                )
                if (zipalignExitCode != 0) {
                    throw GradleException("zipalign failed for ${apk.name} with exit code $zipalignExitCode")
                }

                val apksignerExitCode = runProcessWithInheritedIo(
                    command = listOf(
                        apksigner.absolutePath,
                        "sign",
                        "--ks", debugKeystore.absolutePath,
                        "--ks-key-alias", "androiddebugkey",
                        "--ks-pass", "pass:android",
                        "--key-pass", "pass:android",
                        "--out", alignedSigned.absolutePath,
                        alignedUnsigned.absolutePath
                    )
                )
                if (apksignerExitCode != 0) {
                    throw GradleException("apksigner failed for ${apk.name} with exit code $apksignerExitCode")
                }

                copy {
                    from(alignedSigned)
                    into(apk.parentFile)
                    rename { apk.name }
                }
                alignedUnsigned.delete()
                alignedSigned.delete()
            }
        }
    }

    tasks.configureEach {
        if (name == assembleTaskName) {
            finalizedBy(alignTask)
        }
    }
}

register16kAlignTaskForVariant("debug")
register16kAlignTaskForVariant("optimizedDebug")

val enableX86 = parseBooleanGradleProperty(providers.gradleProperty("enableX86").orNull)
val uadeRuntimeAssetAbis = buildList {
    add("arm64-v8a")
    add("armeabi-v7a")
    add("x86_64")
    if (enableX86) add("x86")
}
val syncUadeRuntimeAssets = tasks.register("syncUadeRuntimeAssets") {
    group = "build setup"
    description = "Sync shared/per-ABI UADE runtime files into generated assets."

    doLast {
        val destinationRoot = layout.buildDirectory
            .dir("generated/uadeRuntimeAssets/main/uade")
            .get()
            .asFile
        delete(destinationRoot)

        // UADE share assets (players/score/config) are architecture-independent.
        // Keep a single common copy and only keep uadecore split by ABI.
        val sourceCommonShareDir =
            uadeRuntimeAssetAbis
                .asSequence()
                .map { abi -> File(file("src/main/cpp/prebuilt/$abi"), "share/uade") }
                .firstOrNull { it.isDirectory }

        if (sourceCommonShareDir == null) {
            logger.lifecycle("UADE shared runtime assets missing (expected share/uade under any configured ABI)")
        } else {
            copy {
                from(sourceCommonShareDir)
                into(File(destinationRoot, "common"))
            }
        }

        uadeRuntimeAssetAbis.forEach { abi ->
            val sourceUadeCore = file("src/main/cpp/prebuilt/$abi/lib/uade/uadecore")
            if (!sourceUadeCore.isFile) {
                logger.lifecycle(
                    "UADE runtime core missing for $abi, skipping (${sourceUadeCore.absolutePath})"
                )
                return@forEach
            }
            copy {
                from(sourceUadeCore)
                into(File(destinationRoot, abi))
                rename { "uadecore" }
            }
        }
    }
}

val syncPrebuiltNativeLibs = tasks.register("syncPrebuiltNativeLibs") {
    group = "build setup"
    description = "Sync all ABI-specific prebuilt shared native libraries into generated jniLibs."

    doLast {
        val destinationRoot = layout.buildDirectory
            .dir("generated/prebuiltNativeLibs/main")
            .get()
            .asFile
        delete(destinationRoot)

        uadeRuntimeAssetAbis.forEach { abi ->
            val prebuiltDir = file("src/main/cpp/prebuilt/$abi")
            if (!prebuiltDir.isDirectory) {
                logger.lifecycle("Prebuilt directory missing for $abi, skipping (${prebuiltDir.absolutePath})")
                return@forEach
            }

            prebuiltDir.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.forEach { soFile ->
                copy {
                    from(soFile)
                    into(File(destinationRoot, abi))
                }
            }

            val sourceUadeCore = file("src/main/cpp/prebuilt/$abi/lib/uade/uadecore")
            if (sourceUadeCore.isFile) {
                copy {
                    from(sourceUadeCore)
                    into(File(destinationRoot, abi))
                    rename { "libuadecore_exec.so" }
                }
            }
        }
    }
}

val syncProjectMPresetAssets = tasks.register("syncProjectMPresetAssets") {
    group = "build setup"
    description = "Sync projectM MilkDrop preset files into generated assets."

    doLast {
        val destinationRoot = layout.buildDirectory
            .dir("generated/projectmPresetsAssets/main/projectm")
            .get()
            .asFile
        delete(destinationRoot)
        mkdir(destinationRoot)

        val sourcePresetDir = rootProject.file("external/projectm/presets/tests")
        if (!sourcePresetDir.isDirectory) {
            logger.lifecycle("projectM presets missing (expected external/projectm/presets/tests)")
        } else {
            copy {
                from(sourcePresetDir) {
                    include("*.milk")
                }
                into(destinationRoot)
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateAboutVersions)
    dependsOn(syncUadeRuntimeAssets)
    dependsOn(syncProjectMPresetAssets)
    dependsOn(syncPrebuiltNativeLibs)
}
