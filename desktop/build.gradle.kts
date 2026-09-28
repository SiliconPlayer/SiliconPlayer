plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    alias(libs.plugins.kotlinCompose)
}

kotlin {
    jvmToolchain(17)
    sourceSets.getByName("main") {
        kotlin.srcDir("../shared/src/main/kotlin")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation("org.json:json:20240303")
    implementation(libs.smbj)
    implementation(libs.smbj.rpc)
    testImplementation(libs.junit)
}


compose.desktop {
    application {
        mainClass = "com.flopster101.siliconplayer.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage
            )
            packageName = "SiliconPlayer"
            packageVersion = "1.0.0"
            buildTypes {
                release {
                    proguard {
                        // Default 7.2.2 cannot read the Java 21 runtime (class 65).
                        version.set("7.4.2")
                        // Shrink only: optimization passes cost minutes on this tree.
                        optimize.set(false)
                        configurationFiles.from("siliconplayer-desktop.pro")
                    }
                }
            }
        }
    }
}

val nativeBuildDir = layout.buildDirectory.dir("native")

val configureDesktopNative by tasks.registering(Exec::class) {
    val srcDir = file("src/native")
    inputs.dir(srcDir)
    outputs.file(nativeBuildDir.get().file("Makefile"))

    workingDir = rootDir
    commandLine(
        "cmake",
        "-B", nativeBuildDir.get().asFile.absolutePath,
        "-S", srcDir.absolutePath
    )
}

val buildDesktopNative by tasks.registering(Exec::class) {
    dependsOn(configureDesktopNative)
    val nativeDir = nativeBuildDir.get().asFile
    inputs.dir(file("src/native"))
    inputs.dir(file("../app/src/main/cpp"))
    inputs.dir(file("../external/silicon/silicon_vis"))
    outputs.file(nativeDir.resolve("libsiliconplayer_desktop.so"))

    workingDir = rootDir
    commandLine(
        "cmake",
        "--build", nativeDir.absolutePath,
        "-j"
    )
}

fun configureNativePaths(task: JavaForkOptions) {
    val nativeDir = nativeBuildDir.get().asFile
    val prebuiltLibDir = file("prebuilt/x86_64/lib")
    val combinedPath = "${nativeDir.absolutePath}:${prebuiltLibDir.absolutePath}"
    task.systemProperty("java.library.path", combinedPath)
    val currentLd = System.getenv("LD_LIBRARY_PATH") ?: ""
    task.environment(
        "LD_LIBRARY_PATH",
        if (currentLd.isNotEmpty()) "$combinedPath:$currentLd" else combinedPath
    )
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(buildDesktopNative)
    configureNativePaths(this)
}

tasks.withType<Test>().configureEach {
    dependsOn(buildDesktopNative)
    configureNativePaths(this)
}

