plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.haithem44.offlinetube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.haithem44.offlinetube"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // المكتبة الأساسية لعرض القوائم والشبكات (RecyclerView)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // مكتبة أساسية لأندرويد
    implementation("androidx.core:core-ktx:1.12.0")
}
