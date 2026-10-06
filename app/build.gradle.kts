plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Вывод git-команды; null — нет git или репозитория (сборка из архива исходников). */
fun git(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
}.getOrNull()?.ifEmpty { null }

android {
    namespace = "com.github.vorobeyyyyyy.geelynavbar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.github.vorobeyyyyyy.geelynavbar"
        minSdk = 28
        targetSdk = 28
        // Версия из git, одинаково локально и в CI: versionCode — число коммитов, versionName — последний тег vX.Y.Z
        // (на коммитах после тега — «X.Y.Z-N-gхеш»). Без git — 1 и «dev»
        versionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionName = git("describe", "--tags", "--match", "v[0-9]*", "--always")?.removePrefix("v") ?: "dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Ключ релизов: в CI из секретов, локально из ~/.android/geelynavbar-release.env
        System.getenv("RELEASE_KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = "geelynavbar"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Dex модуля грузится в процесс com.android.systemui — сжимаем; точка входа в proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Без release-ключа локальная сборка подписывается debug-ключом
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // targetSdk 28 — сознательно, под Android 9 на ГУ
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    // org.json в android.jar — заглушки; для JVM-тестов разбора конфига нужна настоящая реализация
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
