import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.moment"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.moment"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) {
                f.inputStream().use { load(it) }
            }
        }
        val amapKey = (project.findProperty("amap.web.key") as String?)?.trim()?.takeIf { it.isNotEmpty() }
            ?: localProps.getProperty("amap.web.key")?.trim().orEmpty().takeIf { it.isNotEmpty() }
            ?: System.getenv("AMAP_WEB_KEY")?.trim().orEmpty()
        val escapedKey = amapKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "AMAP_WEB_JS_KEY", "\"$escapedKey\"")

        val amapSecurity = (project.findProperty("amap.security.jscode") as String?)?.trim()?.takeIf { it.isNotEmpty() }
            ?: localProps.getProperty("amap.security.jscode")?.trim().orEmpty().takeIf { it.isNotEmpty() }
            ?: System.getenv("AMAP_SECURITY_JS_CODE")?.trim().orEmpty()
        val escapedSecurity = amapSecurity.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "AMAP_SECURITY_JS_CODE", "\"$escapedSecurity\"")

        // 与 JS 地图 Key 分开：高德同一 Key 通常只能选一种「服务平台」，逆地理需单独申请「Web服务」类型 Key。
        val amapWebServiceKey =
            (project.findProperty("amap.web.service.key") as String?)?.trim()?.takeIf { it.isNotEmpty() }
                ?: localProps.getProperty("amap.web.service.key")?.trim().orEmpty().takeIf { it.isNotEmpty() }
                ?: System.getenv("AMAP_WEB_SERVICE_KEY")?.trim().orEmpty()
        val escapedServiceKey = amapWebServiceKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "AMAP_WEB_SERVICE_KEY", "\"$escapedServiceKey\"")

        // Web 服务若启用「数字签名」，填写控制台中与 Key 配套的私钥（签名用，不是 JS 的 securityJsCode）。
        val amapServiceSecret =
            (project.findProperty("amap.web.service.secret") as String?)?.trim()?.takeIf { it.isNotEmpty() }
                ?: localProps.getProperty("amap.web.service.secret")?.trim().orEmpty().takeIf { it.isNotEmpty() }
                ?: System.getenv("AMAP_WEB_SERVICE_SECRET")?.trim().orEmpty()
        val escapedServiceSecret = amapServiceSecret.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "AMAP_WEB_SERVICE_SECRET", "\"$escapedServiceSecret\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("com.google.dagger:hilt-android:2.56.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    ksp("androidx.room:room-compiler:2.8.4")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
