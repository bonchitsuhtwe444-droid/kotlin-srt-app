plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.myname.srtapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myname.srtapp"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Background လုပ်ငန်းစဉ်များအတွက် (Coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Gemini API ချိတ်ဆက်ရန်အတွက် (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // ❌ FFmpegKit ကို လုံးဝ ဖယ်ရှားလိုက်ပါပြီ
}
