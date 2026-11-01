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

    // Java 17 ကို အသုံးပြုရန် သတ်မှတ်ချက်များ
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    
    // အသံဖြတ်ရန် (FFmpeg Audio Kit)
    implementation("com.arthenica:ffmpeg-kit-audio:5.1")
    
    // Groq API သို့ လှမ်းချိတ်ရန် (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // နောက်ကွယ်မှ အလုပ်လုပ်ရန် (Coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
