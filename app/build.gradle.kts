plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.haithem44.offlinetube"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.haithem44.offlinetube"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // المكتبات الأساسية للأندرويد
    implementation("androidx.core:core-ktx:1.13.1")
    
    // مكتبة AppCompat (ضرورية جداً بعد تغيير الوراثة في MainActivity)
    implementation("androidx.appcompat:appcompat:1.7.0")
    
    // مكتبة Activity KTX (ضرورية لـ registerForActivityResult)
    implementation("androidx.activity:activity-ktx:1.9.0")
    
    // مكتبة Material Design (لتحسين الواجهات والـ Theme)
    implementation("com.google.android.material:material:1.12.0")
    
    // مكتبة ConstraintLayout (إذا كنت ستستخدمها مستقبلاً)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // مكتبات الاختبار (لا تؤثر على التطبيق النهائي)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
