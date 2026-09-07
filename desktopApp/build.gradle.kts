import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "com.blinkng.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Blinkng"
            packageVersion = "1.0.0"

            windows {
                dirChooser = true
                perUserInstall = true
                menuGroup = "Blinkng"
                upgradeUuid = "5F71C5F0-0D14-4FCE-9A92-8E29A3A5F7A1"
            }
        }
    }
}
