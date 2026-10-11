import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  // kotlin.android is deliberately absent: AGP 9 provides built-in Kotlin, and applying it on top
  // errors on a duplicate `kotlin` extension (version pinned in the root buildscript classpath).
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
}

android {
  namespace = "fi.nikosavola.wristbeat"
  // 37 rather than 36 because androidx.appfunctions will not build against anything older. This is
  // the compile-time API level only: targetSdk below is what decides the app's runtime behaviour,
  // and it stays at the 36 this app is written against.
  compileSdk = 37

  defaultConfig {
    applicationId = "fi.nikosavola.wristbeat"
    // Wear OS 3. The Bluetooth LE peripheral APIs this app is built on (advertiser + GATT server)
    // are all present well before this; the floor is set by Wear Compose Material 3.
    minSdk = 30
    targetSdk = 36
    // A watch APK's version code has to be unique across the form factors one listing covers, so
    // Play's recommendation is to lead with the target SDK rather than start at 1 and find out
    // later. 36 | 000 | 01 | 00 is targetSdk 36, no product split, release 1, build 0, and it leaves
    // room to add a phone module without a migration that cannot be undone.
    versionCode = 36_000_01_00
    versionName = "0.1.0"
  }

  signingConfigs {
    // Present only where an upload key has been set up, so a clone with no key - CI, F-Droid, anyone
    // reading this - still builds, and produces the unsigned release APK F-Droid signs itself.
    val keyProperties = rootProject.file("key.properties")
    if (keyProperties.exists()) {
      create("upload") {
        val key = Properties()
        keyProperties.inputStream().use { key.load(it) }
        storeFile = rootProject.file(requireNotNull(key.getProperty("storeFile")))
        storePassword = key.getProperty("storePassword")
        keyAlias = key.getProperty("keyAlias")
        keyPassword = key.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("upload")?.let { signingConfig = it }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures { compose = true }

  testOptions {
    unitTests {
      // Robolectric needs the merged manifest and the resource table to lay out a Wear screen.
      isIncludeAndroidResources = true
    }
  }
}

kotlin { jvmToolchain(21) }

tasks.withType<Test>().configureEach {
  // The API 36 runtime that Robolectric loads reaches for a JDK-internal class by reflection to set
  // up its shared memory, and a modern JDK does not open that package to unnamed modules. One
  // package, which is the one it reaches for.
  jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)

  implementation(libs.wear.compose.material3)
  implementation(libs.wear.compose.foundation)
  implementation(libs.wear.ongoing)

  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.androidx.appfunctions)
  ksp(libs.androidx.appfunctions.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.compose.ui.test.junit4)
  // Supplies the ComponentActivity the compose test rule drives, in the debug manifest.
  debugImplementation(libs.compose.ui.test.manifest)
}