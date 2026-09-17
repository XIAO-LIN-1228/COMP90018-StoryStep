plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.comp90018.storystep"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.comp90018.storystep"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room writes the schema of every version here. The JSON files are committed so that
        // MigrationTestHelper can build an old database and migrate it, which is the only way to
        // prove a migration preserves user-owned history.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    sourceSets {
        // MigrationTestHelper reads the exported schemas from the instrumented test assets.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.osmdroid)
    implementation(libs.glide)

    testImplementation(libs.junit)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.room.testing)
}
