import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

val env = Properties()
val envFile = rootProject.file(".env")
if (envFile.exists()) {
    envFile.reader().use { env.load(it) }
}

android {
    namespace = "com.example.aqualume"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aqualume"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val geocodingKey = env.getProperty("GEOCODING_API_KEY") ?: ""
        buildConfigField("String", "GEOCODING_API_KEY", "\"$geocodingKey\"")
        val openWeatherKey = env.getProperty("OPENWEATHER_API_KEY") ?: ""
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildToolsVersion = "34.0.0"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    implementation("androidx.preference:preference:1.2.1")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")


    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.gson)
    implementation(libs.play)
    implementation(platform(libs.firebase.bom))

    implementation("com.mapbox.maps:android:11.13.3")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.mapbox.search:autofill:2.14.0")
    implementation("com.mapbox.search:discover:2.14.0")
    implementation("com.mapbox.search:place-autocomplete:2.14.0")
    implementation("com.mapbox.search:offline:2.14.0")
    implementation("com.mapbox.search:mapbox-search-android:2.14.0")
    implementation("com.mapbox.search:mapbox-search-android-ui:2.14.0")
}