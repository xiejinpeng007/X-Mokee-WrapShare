import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "org.mokee.warpshare"
        minSdk = 21
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = File(rootProject.projectDir,"keystore/xmokee-warpshare")
            storePassword = "xmokeewrapshare"
            keyAlias = "xmokeewrapshare"
            keyPassword = "xmokeewrapshare"
        }
    }
    buildTypes {
        release {
            multiDexEnabled = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
        }
    }
    splits {
        // Configures multiple APKs based on ABI.
        abi {

            // Enables building multiple APKs per ABI.
            isEnable = true

            // By default all ABIs are included, so use reset() and include to specify that you only
            // want APKs for x86 and x86_64.

            // Resets the list of ABIs for Gradle to create APKs for to none.
//            reset()
            // Specifies that you don't want to also generate a universal APK that includes all ABIs.
            isUniversalApk = true
        }
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    namespace = "org.mokee.warpshare"

    applicationVariants.configureEach {
        val date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
        val apkName = "WarpShare_${versionName}%s_${date}.apk"
        outputs.all {
            val output = this as? BaseVariantOutputImpl
            val abi2 = (this as? BaseVariantOutputImpl)?.getFilter("ABI")?.let{
                "_$it"
            }?:""
            output?.outputFileName = String.format(apkName,abi2)
        }

    }
}

dependencies {
    implementation("androidx.preference:preference-ktx:1.2.1")

    //Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    //okhttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    //Gson
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.Tgo1014:JP2ForAndroid:1.0.4")
    implementation("com.koushikdutta.async:androidasync:3.1.0")
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("com.googlecode.plist:dd-plist:1.27")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.jmdns:jmdns:3.5.8")
    implementation("com.microsoft.connecteddevices:connecteddevices-sdk:1.6.1"){
        exclude(group = "com.android.support")
    }
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}