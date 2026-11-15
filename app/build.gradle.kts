import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val signingFile = rootProject.file("signing.properties")
val localSigning = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }
val localConfig = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val mapKey = providers.gradleProperty("KAKAO_MAP_NATIVE_KEY").orNull
    ?: System.getenv("KAKAO_MAP_NATIVE_KEY") ?: localConfig.getProperty("KAKAO_MAP_NATIVE_KEY", "")
require(mapKey.isEmpty() || mapKey.matches(Regex("[A-Za-z0-9_-]+"))) { "KAKAO_MAP_NATIVE_KEY contains invalid characters" }

android {
    namespace = "ai.arrivalcare.app"
    compileSdk = 34
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "ai.arrivalcare.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"
        buildConfigField("String", "KAKAO_MAP_NATIVE_KEY", "\"\"")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    flavorDimensions += listOf("integration", "mapEngine")
    productFlavors {
        create("demo") { dimension = "integration"; applicationIdSuffix = ".demo"; buildConfigField("boolean", "PLEOS", "false") }
        create("pleos") { dimension = "integration"; applicationIdSuffix = ".pleos"; buildConfigField("boolean", "PLEOS", "true") }
        create("offline") { dimension = "mapEngine"; buildConfigField("boolean", "KAKAO_INCLUDED", "false") }
        create("kakao") {
            dimension = "mapEngine"
            buildConfigField("boolean", "KAKAO_INCLUDED", "true")
            buildConfigField("String", "KAKAO_MAP_NATIVE_KEY", "\"$mapKey\"")
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        }
    }
    if (signingFile.exists()) signingConfigs.create("contest") {
        storeFile = file(localSigning.getProperty("storeFile"))
        storePassword = localSigning.getProperty("storePassword")
        keyAlias = localSigning.getProperty("keyAlias")
        keyPassword = localSigning.getProperty("keyPassword")
    }
    buildTypes { release {
        isMinifyEnabled = false
        if (signingFile.exists()) signingConfig = signingConfigs.getByName("contest")
    } }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    "pleosImplementation"("ai.pleos.playground:Vehicle:2.3.2")
    "pleosImplementation"("ai.pleos.playground:NaviHelper:3.0.5")
    "kakaoImplementation"("com.kakao.maps.open:android:2.15.2")
}
