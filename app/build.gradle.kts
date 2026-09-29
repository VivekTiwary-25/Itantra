plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val demoFlavors = listOf("demoVachana", "demoYash", "demoVivek")

android {
    namespace = "com.chmod777.itantra"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chmod777.itantra"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "DEMO_ACTOR", "\"NONE\"")
    }

    // `full` is the real iTantra app, unchanged. The three `demo*` flavors are
    // film-demo builds: one per actor, each installable side by side, sharing
    // the demo-only source set in src/demo (no real STT/TTS/Bluetooth in the
    // filmed path).
    flavorDimensions += "demoActor"
    productFlavors {
        create("full") {
            dimension = "demoActor"
        }
        for (flavor in demoFlavors) {
            val actor = flavor.removePrefix("demo").lowercase()
            create(flavor) {
                dimension = "demoActor"
                applicationIdSuffix = ".demo.$actor"
                versionNameSuffix = "-demo-$actor"
                buildConfigField("String", "DEMO_ACTOR", "\"${actor.uppercase()}\"")
                // Sideloaded film builds: the release variant (non-debuggable, so
                // Compose animates smoothly) is signed with the local debug key.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets {
        for (actor in demoFlavors) {
            getByName(actor) {
                kotlin.directories.add("src/demo/java")
                res.directories.add("src/demo/res")
                manifest.srcFile("src/demo/AndroidManifest.xml")
            }
            // Variant-level manifest: the only level allowed to strip the
            // debug build type's launcher/receivers from the demo builds.
            maybeCreate("${actor}Debug").apply {
                manifest.srcFile("src/demoDebug/AndroidManifest.xml")
            }
            maybeCreate("test${actor.replaceFirstChar { it.uppercase() }}").apply {
                kotlin.directories.add("src/demoTest/java")
            }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The demo builds never load the speech runtime or its models, so keep them
// out of the demo APKs (the shared main code still compiles against them).
androidComponents {
    onVariants { variant ->
        if (variant.productFlavors.any { it.second in demoFlavors }) {
            variant.packaging.jniLibs.excludes.addAll(
                "**/libonnxruntime.so",
                "**/libsherpa-onnx-*.so"
            )
            variant.androidResources.ignoreAssetsPatterns.add("!*")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(files("libs/sherpa-onnx-1.13.7.aar"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tink.android)
    implementation(libs.noise.java)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded) {
        // zxing core is pinned directly above.
        exclude(group = "com.google.zxing", module = "core")
    }
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}