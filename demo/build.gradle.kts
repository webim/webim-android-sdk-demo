import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.huawei.agconnect")
}

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://developer.huawei.com/repo/") }
}

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.webim.demo"
        minSdk = 21
        targetSdk = 35
        namespace = "ru.webim.android.demo"
        versionCode = project.property("versionCode").toString().toInt()
        versionName = project.property("sdkVersionName").toString()
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationVariants.all {
                outputs.all {
                    val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    outputImpl.outputFileName = "${gitBranchName()} (${getCurrentDate()}).apk"
                }
            }
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.lifecycle:lifecycle-process:2.5.1")
        force("androidx.appcompat:appcompat:1.6.1")
    }
}

dependencies {
    implementation(project(":chatview"))

    implementation("com.astuetz:pagerslidingtabstrip:1.0.1")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.15.1@aar")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")

    implementation("com.google.firebase:firebase-messaging:23.1.0")
    implementation("com.google.firebase:firebase-crashlytics:18.3.7")
    implementation("com.google.firebase:firebase-analytics:21.2.2")

    implementation("com.huawei.hms:push:6.7.0.300")
    implementation("com.huawei.agconnect:agconnect-core:1.5.2.300")

    implementation("com.github.thijsk:TouchImageView:v1.3.1")

    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.preference:preference:1.2.0")

    implementation("io.coil-kt:coil:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")
    implementation("io.coil-kt:coil-base:2.4.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

fun gitBranchName(): String {
    val defaultName = "debug-build"
    val builder: ProcessBuilder = ProcessBuilder()
        .directory(File(System.getProperty("user.dir")))
        .command("git", "rev-parse", "--abbrev-ref", "HEAD")

    return try {
        val process: Process = builder.start()
        val bufferedReader = process.inputStream.reader()
        return bufferedReader.readText().trim()
    } catch (exception: Exception) {
        println(exception.printStackTrace())
        defaultName
    }
}

fun getCurrentDate(): String {
    val currentDate = Date()
    return SimpleDateFormat("dd.MM.yyyy").format(currentDate)
}
