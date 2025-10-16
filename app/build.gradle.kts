import java.util.Properties
val isIzzyOrFdroid = false


plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
//    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rk.taskmanager"
    compileSdk = 36
    buildFeatures.buildConfig = true

    dependenciesInfo {
        includeInApk = isIzzyOrFdroid.not()
        includeInBundle = isIzzyOrFdroid.not()
    }

    signingConfigs {
        create("release") {
            val isGITHUB_ACTION = System.getenv("GITHUB_ACTIONS") == "true"

            val propertiesFilePath = if (isGITHUB_ACTION) {
                "/tmp/signing.properties"
            } else {
                "/home/rohit/Android/xed-signing/signing.properties"
            }

            val propertiesFile = File(propertiesFilePath)
            if (propertiesFile.exists()) {
                val properties = Properties()
                properties.load(propertiesFile.inputStream())
                keyAlias = properties["keyAlias"] as String?
                keyPassword = properties["keyPassword"] as String?
                storeFile = if (isGITHUB_ACTION) {
                    File("/tmp/xed.keystore")
                } else {
                    (properties["storeFile"] as String?)?.let { File(it) }
                }

                storePassword = properties["storePassword"] as String?
            } else {
                println("Signing properties file not found at $propertiesFilePath")
            }
        }
    }


    buildTypes {
        release{
            isMinifyEnabled = isIzzyOrFdroid.not()
            isCrunchPngs = isIzzyOrFdroid.not()
            isShrinkResources = isIzzyOrFdroid.not()

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug{
            versionNameSuffix = "-DEBUG"
        }
    }

    defaultConfig {
        applicationId = "com.rk.taskmanager"
        minSdk = 26
        targetSdk = 36

        //versioning
        versionCode = 20
        versionName = "1.2.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.10"
    }
}

tasks.whenTaskAdded {
    if (name.contains("ArtProfile") && isIzzyOrFdroid) {
        println("Skipped Task $name")
        enabled = false
    }
}

dependencies {
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.material3)
    implementation(libs.navigation.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.vico.compose.m3)
    implementation(project(":components"))
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.google.material)
    implementation(project(":taskmanagerd"))
    implementation(libs.androidx.javascriptengine)

    //its time to make some money 💵💵💵💵💵💵💵💵
    implementation(libs.play.services.ads)
}
