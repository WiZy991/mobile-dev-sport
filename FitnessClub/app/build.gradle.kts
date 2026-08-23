import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "com.fitnessclub.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.worldcashfit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "1.2.4"

        multiDexEnabled = true
        multiDexKeepProguard = file("multidex-config.pro")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "API_BASE_URL", "\"https://worldcashfit.ru/api/v1/\"")
        buildConfigField("String", "SITE_URL", "\"https://worldcashfit.ru\"")
        buildConfigField(
            "String",
            "SBER_REDIRECT_URI",
            "\"https://worldcashfit.ru/api/v1/auth/sber/callback\"",
        )
        // Fallback для merge; реальные значения задаются в productFlavors.
        manifestPlaceholders["deepLinkScheme"] = "dobrozal"
    }

    flavorDimensions += "brand"
    productFlavors {
        create("dobrozal") {
            dimension = "brand"
            isDefault = true
            applicationId = "ru.worldcashfit.app"
            // Уникальная схема: старые APK Академии всё ещё ловят worldfitness:// и крадут callback.
            manifestPlaceholders["deepLinkScheme"] = "dobrozal"
            buildConfigField("String", "DEEP_LINK_SCHEME", "\"dobrozal\"")
            buildConfigField("String", "APP_AUTH_BRIDGE_URI", "\"dobrozal://auth/callback\"")
            buildConfigField("String", "APP_PAYMENT_BRIDGE_URI", "\"dobrozal://payment/callback\"")
            buildConfigField("String", "BRAND_NAME", "\"Доброзал\"")
            // Пусто = DEFAULT_ORGANIZATION_SLUG на сервере (организация Доброзал).
            buildConfigField("String", "ORGANIZATION_SLUG", "\"\"")
            buildConfigField("String", "CLUB_SITE_URL", "\"https://dobrozal.ru\"")
            buildConfigField(
                "String",
                "PLAY_STORE_URL",
                "\"https://play.google.com/store/apps/details?id=ru.worldcashfit.app\"",
            )
            buildConfigField(
                "String",
                "RUSTORE_CATALOG_URL",
                "\"https://www.rustore.ru/catalog/app/ru.worldcashfit.app\"",
            )
        }
        create("academyWrestling") {
            dimension = "brand"
            applicationId = "ru.academywrestling.app"
            manifestPlaceholders["deepLinkScheme"] = "academywrestling"
            buildConfigField("String", "DEEP_LINK_SCHEME", "\"academywrestling\"")
            buildConfigField("String", "APP_AUTH_BRIDGE_URI", "\"academywrestling://auth/callback\"")
            buildConfigField("String", "APP_PAYMENT_BRIDGE_URI", "\"academywrestling://payment/callback\"")
            buildConfigField("String", "BRAND_NAME", "\"Академия Борьбы\"")
            // Slug организации из CRM (платформа → организации).
            buildConfigField("String", "ORGANIZATION_SLUG", "\"akademiy-borbi\"")
            buildConfigField("String", "CLUB_SITE_URL", "\"https://worldcashfit.ru\"")
            buildConfigField(
                "String",
                "PLAY_STORE_URL",
                "\"https://play.google.com/store/apps/details?id=ru.academywrestling.app\"",
            )
            buildConfigField(
                "String",
                "RUSTORE_CATALOG_URL",
                "\"https://www.rustore.ru/catalog/app/ru.academywrestling.app\"",
            )
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile")!!)
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Firebase Cloud Messaging (push-уведомления)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Biometric login (отпечаток для расшифровки сохранённого refresh-токена)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Custom Tabs (Сбер ID и будущая оплата в браузере)
    implementation("androidx.browser:browser:1.8.0")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Native map (WebView+OSM tiles часто пустые в эмуляторе/WebView)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    
    // ZXing for QR codes
    implementation("com.google.zxing:core:3.5.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
