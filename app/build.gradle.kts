plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false
}

// google-services.json is intentionally optional in source control.
// When the Firebase config file is supplied locally, apply the Google Services plugin.
if (file("google-services.json").exists()) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

// Release versioning is deterministic in CI and still safe for local release builds.
// GitHub's signed-release workflow supplies VERSION_CODE/VERSION_NAME. When they are
// absent locally, the Git commit count is used so versionCode no longer stays at 1.
val gitCommitCount = providers.exec {
  workingDir(rootDir)
  commandLine("git", "rev-list", "--count", "HEAD")
  isIgnoreExitValue = true
}.standardOutput.asText.get().trim().toIntOrNull()

val resolvedVersionCode = (
  providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull()
    ?: gitCommitCount
    ?: 2
).coerceAtLeast(2)

require(resolvedVersionCode <= 2_100_000_000) {
  "VERSION_CODE must be <= 2100000000, got $resolvedVersionCode"
}

val resolvedVersionName = providers.environmentVariable("VERSION_NAME").orNull
  ?.trim()
  ?.takeIf { it.isNotEmpty() }
  ?: "1.0.$resolvedVersionCode"

val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
val releaseStorePassword = System.getenv("STORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val releaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

// Never silently build a production APK/AAB with a missing or accidental signing key.
if (releaseTaskRequested) {
  val missing = buildList {
    if (releaseKeystorePath.isNullOrBlank()) add("KEYSTORE_PATH")
    if (releaseStorePassword.isNullOrBlank()) add("STORE_PASSWORD")
    if (releaseKeyAlias.isNullOrBlank()) add("KEY_ALIAS")
    if (releaseKeyPassword.isNullOrBlank()) add("KEY_PASSWORD")
  }
  if (missing.isNotEmpty()) {
    throw GradleException(
      "Release signing is not configured. Missing: ${missing.joinToString()}. " +
        "Use the signed-release GitHub workflow or provide the permanent release key variables."
    )
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(37) }
  defaultConfig {
    applicationId = "com.aistudio.blink.appvtwo"
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName
    manifestPlaceholders["shareHost"] = "my-app.com"
    buildConfigField("String", "SHARE_BASE_URL", "\"https://my-app.com\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  signingConfigs {
    create("release") {
      if (!releaseKeystorePath.isNullOrBlank()) storeFile = file(releaseKeystorePath)
      storePassword = releaseStorePassword
      keyAlias = releaseKeyAlias
      keyPassword = releaseKeyPassword
    }
  }
  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Debug builds can live beside production and can never replace/corrupt a release install.
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }
  compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  buildFeatures { compose = true; buildConfig = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo { includeInApk = false; includeInBundle = true }
}

secrets { propertiesFileName = ".env"; defaultPropertiesFileName = ".env.example"; ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN") }

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
}

dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation("androidx.compose.ui:ui-text-google-fonts")
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.accompanist.permissions)
  implementation(libs.play.services.location)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
