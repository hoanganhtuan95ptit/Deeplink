plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.simple.deeplink.register"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    publishing {
        singleVariant("release")
    }
}

// Dùng afterEvaluate vì Android component (components.release) chỉ sẵn sàng
// sau khi toàn bộ cấu hình android {} được Gradle xử lý xong.
afterEvaluate {
    publishing {
        repositories {
            // Publish lên Maven Local (~/.m2) để test nội bộ trước khi release.
            mavenLocal()
        }
        publications {
            // Đặt tên publication là "release" → dùng AAR release variant.
            // Maven coordinates: com.github.hoanganhtuan95ptit:deeplink:1.0.0
            // (group + version kế thừa từ subprojects {} trong root build.gradle)
            create<MavenPublication>("release") {
                from(components.findByName("release"))
                artifactId = "deeplink"
            }
        }
    }
}

dependencies {
    // Chạy DeeplinkProcessor tại compile-time để sinh HandlerRegisterImpl
    ksp(project(":deeplink-processor"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.startup)          // App Startup: khởi tạo DeeplinkInitializer tự động
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // api thay vì implementation để consumer của thư viện này cũng thấy AutoRegister
    api(libs.auto.register)
    ksp(libs.auto.register.processor)            // Sinh Loader class để AutoRegister tự load các module

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
