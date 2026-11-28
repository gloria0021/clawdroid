plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "jp.miyavi.android.sumahoclaw"
    compileSdk = 37
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "jp.miyavi.android.sumahoclaw"
        minSdk = 33
        targetSdk = 37
        versionCode = 4
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 署名の鍵は ~/.gradle/gradle.properties の SUMAHOCLAW_* から読む（レポジトリには置かない）。
    // 無ければ Release は未署名になり、scripts/release.sh が始める前に止める
    val storeFilePath = providers.gradleProperty("SUMAHOCLAW_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) create("release") {
            storeFile = file(storeFilePath)
            storePassword = providers.gradleProperty("SUMAHOCLAW_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("SUMAHOCLAW_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("SUMAHOCLAW_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        // Debug 版は別のアプリとして入れる（Release 版と並べて入れられる）。
        // アプリ名とアイコン（上に「DEV」の帯）は src/debug/res で差し替える。最適化が無く遅いので、動きの重さは Release 版で見る
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

    // 文言は日本語（src/main/res/values）だけを書く。ほかの言語は scripts/translate-strings.py が src/l10n/res へ作り、
    // Debug・Release とも使う（既定の言語は英語。日本語は values-ja の写し）。文言を変えたら同じ変更で訳も作る（AGENTS.md 3）。
    // main の values（日本語）と l10n の values（英語）は同じ名前なので、main には足せない。ビルドの種類ごとの source set に足して上書きさせる。
    // アプリ単位の言語の一覧は l10n の res/xml/locales_config.xml（スクリプトが作る）。言語を足すときはスクリプトの LOCALES と下の一覧に足す
    buildTypes.configureEach { sourceSets.maybeCreate(name).res.srcDir("src/l10n/res") }
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

// オープンソースのライセンスの一覧（spec 003 FR-033・034）。ビルドのたびに依存関係から res/raw/aboutlibraries.json を作る。
// 許諾文はネットから取らない（GitHub の API を使わない）。フォント・アイコンなど依存関係に出ないものは assets/licenses/ から足す
aboutLibraries {
    collect {
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    export {
        excludeFields.addAll("developers", "funding", "scm", "organization")
    }
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
    implementation(libs.sora.editor)
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

// 日本語の写し（app/src/l10n/res/values-ja）は main の日本語から作る。訳（ほかの言語）は文言を変えたときにスクリプトで作るが、
// 日本語の写しはビルドのたびに作り直し、画面の日本語が古いまま残らないようにする（訳さないのでトークンは使わない）
val syncJapaneseCopy by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("python3", "scripts/translate-strings.py", "--ja-only")
    inputs.dir("src/main/res/values")
    outputs.file("src/l10n/res/values-ja/strings.xml")
}
tasks.named("preBuild") { dependsOn(syncJapaneseCopy) }
