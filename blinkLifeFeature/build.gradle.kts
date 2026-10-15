import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.dynamic.feature)
}

// The Godot native engine, scene source and textures belong only to this
// on-demand feature. None are packaged in the base BLINK APK.
val stageBlinkLifeGodotAssets by tasks.registering(Sync::class) {
    from(rootProject.file("game3d")) {
        include("project.godot", "scenes/**", "scripts/**")
    }
    into(layout.buildDirectory.dir("generated/blinkLifeGodot/assets"))
}
tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.startsWith("lint") || it.name.contains("Lint")
}.configureEach { dependsOn(stageBlinkLifeGodotAssets) }

android {
    namespace = "com.example.game.feature"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/blinkLifeGodot/assets").get().asFile
    )
}

dependencies {
    implementation(project(":app"))
    implementation("com.google.android.play:feature-delivery:2.1.0")
    implementation("org.godotengine:godot:4.3.0.stable")
}
