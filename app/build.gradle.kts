import java.util.Properties
val isIzzyOrFdroid = false


plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.baselineprofile)
}


android {
    namespace = "com.rk.taskmanager.app"
    // 37 resolves to platforms/android-37; the stable platform is android-37.0.
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    // The app module runs stripReleaseDebugSymbols over the packaged native libs, so it must use
    // the same NDK that built them (see taskmanagerd) or stripping differs between machines.
    ndkVersion = libs.versions.ndk.get()

    lint {
        disable += "MissingTranslation"
    }

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
        targetSdk = 37

        //versioning
        versionCode = 55
        versionName = "1.5.5"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

}

tasks.whenTaskAdded {
    if (isIzzyOrFdroid && name.contains("ArtProfile")) {
        println("Skipped Task $name")
        enabled = false
    }
}

dependencies {

    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    implementation(libs.androidx.room.ktx)


    if (findProject(":taskmanager_pro") != null) {
        implementation(project(":taskmanager_pro"))
    }
    implementation(project(":main"))
}
