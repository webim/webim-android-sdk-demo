plugins {
    id("com.android.library")
    id("signing")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

val versionName = project.property("sdkVersionName").toString()

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        testOptions.targetSdk = 35
        namespace = "ru.webim.android.sdk"

        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
    }
    buildTypes {
    }

    lint {
        abortOnError = false
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    api("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
    implementation("net.zetetic:sqlcipher-android:4.7.2@aar")
    implementation("androidx.sqlite:sqlite:2.3.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.7.10")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}

val siteUrl = project.property("websiteUrl").toString()
val gitUrl = project.property("gitUrl").toString()

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("ru.webim.sdk", "webimclientsdkandroid", versionName)

    pom {
        name.set("Webim Mobile SDK for Android")
        description.set("Webim Mobile SDK enables Android developers to implement chats into their mobile apps for communications between users and agents.")
        url.set(siteUrl)
        packaging = "aar"

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("nkaberov")
                name.set("Nikita Kaberov")
                email.set("nkaberov@webim.ru")
            }
        }
        scm {
            connection.set(gitUrl)
            developerConnection.set(gitUrl)
            url.set(siteUrl)
        }
    }
}