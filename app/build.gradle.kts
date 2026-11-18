plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dy.sparkmodule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dy.sparkmodule"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 模块无需签名校验，调试包即可安装；release 如需打包可自行配置签名
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Xposed/LSPosed API：仅编译期（compileOnly），不打包进 APK，运行时由 LSPosed 提供
    compileOnly("de.robv.android.xposed:api:82")
}
