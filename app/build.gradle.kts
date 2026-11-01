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
    
    // ဒေါင်းလုဒ် Error လုံးဝမတက်တော့မည့် အတည်ငြိမ်ဆုံး FFmpeg Library 
    implementation("com.arthenica:mobile-ffmpeg-full:4.4")
    
    // Groq API သို့ လှမ်းချိတ်ရန်
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // နောက်ကွယ်မှ အလုပ်လုပ်ရန်
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
