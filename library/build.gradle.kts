plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.otaliastudios.zoom"
    compileSdk = 31
    defaultConfig {
        minSdk = 16
    }
    buildTypes {
        get("release").consumerProguardFile("proguard-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api("androidx.annotation:annotation:1.3.0")
    api("com.otaliastudios.opengl:egloo:0.6.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}
