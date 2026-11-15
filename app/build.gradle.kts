import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val signingFile = rootProject.file("signing.properties")
val localSigning = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

android {
    namespace = "ai.arrivalcare.app"
    compileSdk = 34
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "ai.arrivalcare.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    flavorDimensions += "integration"
    productFlavors {
        create("demo") { dimension = "integration"; applicationIdSuffix = ".demo"; buildConfigField("boolean", "PLEOS", "false") }
        create("pleos") { dimension = "integration"; applicationIdSuffix = ".pleos"; buildConfigField("boolean", "PLEOS", "true") }
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
}
