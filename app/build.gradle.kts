plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "jp.miyavi.android.clawdroid"
    compileSdk = 37
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "jp.miyavi.android.clawdroid"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 署名の鍵は ~/.gradle/gradle.properties の CLAWDROID_* から読む（レポジトリには置かない）。
    // 無ければ Release は未署名になり、scripts/release.sh が始める前に止める
    val storeFilePath = providers.gradleProperty("CLAWDROID_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) create("release") {
            storeFile = file(storeFilePath)
            storePassword = providers.gradleProperty("CLAWDROID_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("CLAWDROID_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("CLAWDROID_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        // Debug 版は別のアプリとして入れる（Release 版と並べて入れられる）
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 文言はふだん日本語（src/main/res/values）だけを書く。ほかの言語は scripts/translate-strings.py が
    // リリースのときに src/release/res へ作り、Release だけが使う（既定の言語は英語）。言語を足すときは両方に足す。
    // アプリ単位の言語の一覧は自動生成しない（AGP は main の resources.properties しか読まず、Debug と Release で既定の言語を分けられない）。
    // main と release の res/xml/locales_config.xml でそれぞれ持つ
    androidResources {
        localeFilters += listOf("ja", "en", "zh-rCN", "zh-rTW", "ko", "es", "fr", "de", "pt-rBR")
    }

    // Termux の libtermux.so（4KB 境界）を、16KB 境界で作った同名のライブラリに置き換える（src/main/cpp/termux_jni.c）
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            pickFirsts += "lib/*/libtermux.so"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation3)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)
    implementation(libs.material.kolor)
    implementation(libs.markdown.renderer.m3)
    // ターミナルの表示（Termux、001 research R9 で承認）とコードの色分け（R10 で承認）
    implementation(libs.termux.terminal.view)
    implementation(libs.highlights)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
