import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            // Compile the same platform-neutral Blinkng contracts/logic as Android.
            kotlin.srcDir("../shared/src/main/kotlin")
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Desktop networking uses the same Supabase REST/Auth endpoints as Android.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20250517")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Cached network media for avatars, posts, marketplace and reel thumbnails.
    implementation("io.coil-kt.coil3:coil-compose:3.6.2")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.2")

    // Windows DPAPI wrapper used to protect persisted refresh/access tokens at rest.
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

val desktopPackageVersion = providers.environmentVariable("BLINK_DESKTOP_VERSION")
    .orElse(providers.gradleProperty("BLINK_DESKTOP_VERSION"))
    .orElse("1.0.0")

compose.desktop {
    application {
        mainClass = "com.blinkng.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Blinkng"
            packageVersion = desktopPackageVersion.get()
            description = "Blinkng social platform for Windows"
            vendor = "Blinkng"

            windows {
                dirChooser = true
                perUserInstall = true
                menuGroup = "Blinkng"
                shortcut = true
                menu = true
                upgradeUuid = "5F71C5F0-0D14-4FCE-9A92-8E29A3A5F7A1"
            }
        }
    }
}
