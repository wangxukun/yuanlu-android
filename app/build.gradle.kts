plugins {
  alias(libs.plugins.hilt)
  alias(libs.plugins.kapt)
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  // Retrofit & OkHttp
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  kapt(libs.room.compiler)

  // Coil
  implementation(libs.coil.compose)
  implementation(libs.coil.network)

  // Navigation (Compose)
  implementation(libs.androidx.navigation.compose)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Serialization
  implementation(libs.kotlinx.serialization.json)
}

android {
    namespace = "com.wxkzd.yuanlu"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.wxkzd.yuanlu"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  // Retrofit & OkHttp
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  kapt(libs.room.compiler)

  // Coil
  implementation(libs.coil.compose)
  implementation(libs.coil.network)

  // Navigation (Compose)
  implementation(libs.androidx.navigation.compose)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Serialization
  implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(17)

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  // Retrofit & OkHttp
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  kapt(libs.room.compiler)

  // Coil
  implementation(libs.coil.compose)
  implementation(libs.coil.network)

  // Navigation (Compose)
  implementation(libs.androidx.navigation.compose)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Serialization
  implementation(libs.kotlinx.serialization.json)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  // Retrofit & OkHttp
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  kapt(libs.room.compiler)

  // Coil
  implementation(libs.coil.compose)
  implementation(libs.coil.network)

  // Navigation (Compose)
  implementation(libs.androidx.navigation.compose)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Serialization
  implementation(libs.kotlinx.serialization.json)
}
