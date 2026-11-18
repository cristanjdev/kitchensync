import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val supabaseUrl = (localProperties.getProperty("SUPABASE_URL") ?: System.getenv("SUPABASE_URL") ?: "").trim()
// Debug-only one-tap sign-in password. Set DEMO_PASSWORD in local.properties (git-ignored); never commit it.
val demoPassword = (localProperties.getProperty("DEMO_PASSWORD") ?: System.getenv("KS360_DEMO_PASSWORD") ?: "")
    .trim().replace("\\", "\\\\").replace("\"", "\\\"")
val supabasePublishableKey = (localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: "").trim()

android {
    namespace = "com.kitchensync360.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kitchensync360.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        buildConfigField("long", "UNACKNOWLEDGED_TIMEOUT_SECONDS", "30L")
    }

    buildTypes {
        // Demo/developer tools (Settings dialog, role switcher, one-tap sign-in, Test Watch, order presets)
        // exist only in debug builds. Release builds are for staff: real sign-in, no demo password inside.
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "DEMO_TOOLS", "true")
            buildConfigField("String", "DEMO_PASSWORD", "\"$demoPassword\"")
        }
        release {
            buildConfigField("boolean", "DEMO_TOOLS", "false")
            buildConfigField("String", "DEMO_PASSWORD", "\"\"")
            // Sign release builds with debug signing config for direct presentation device installation
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xskip-metadata-version-check")
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
