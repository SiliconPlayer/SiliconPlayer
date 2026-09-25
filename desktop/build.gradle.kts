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
        }
    }
}
