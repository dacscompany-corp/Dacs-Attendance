plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.dacs.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dacs.attendance"

        // minSdk 24 on purpose: construction workers are on cheap, older
        // phones. java.time is provided on 24-25 by core library desugaring
        // below, so the date handling can stay modern.
        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Supabase project the existing web app already talks to.
        //
        // The anon key is PUBLIC by design -- js/supabase-config.js ships the
        // exact same string to every browser. It grants nothing on its own:
        // every attendance table is behind RLS and every write goes through a
        // security-definer RPC. Do NOT ever put the service_role key here.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"https://hqbgduyonlbbsvjuapre.supabase.co\""
        )
        // The Edge Function that signs a worker in. It runs with the
        // service_role key server-side, which is what lets the APK stay
        // free of any web view: captcha is enforced on client callers and
        // bypassed for service-role ones, so the native app posts here
        // instead of talking to the auth endpoint directly.
        buildConfigField(
            "String",
            "SIGN_IN_FUNCTION_URL",
            "\"https://hqbgduyonlbbsvjuapre.supabase.co/functions/v1/attendance-signin\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhxYmdkdXlvbmxiYnN2anVhcHJlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA2Mjc3NDEsImV4cCI6MjA5NjIwMzc0MX0.sDKSroNIm-1Lip6ueq2lN1VTsvO1g4mT7Rf_WZ5AxWo\""
        )

        // NOTE: no locale filtering here on purpose. The app is Filipino
        // facing but NOT localized in the Android sense — every label shows
        // English AND Tagalog together, from the single default strings.xml.
        // Do not add values-fil/; see BilingualText in the UI layer.
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room schemas are committed. A queue that loses rows on upgrade
    // loses attendance, so migrations here are not optional and they
    // need the schema history to be written against.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // CameraX. The photo is taken IN the app -- there is no gallery
    // picker anywhere, deliberately (see AndroidManifest): a photo that
    // must be taken now, at the site, is the cheapest anti-spoofing
    // measure available.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Decodes the captured file honouring its EXIF rotation. Doing it by
    // hand gets a sideways selfie on half the phones in the field.
    implementation(libs.coil.compose)
    // Coil 3 ships NO network fetcher in coil-compose: an http(s) model
    // silently loads nothing without one. Local File models worked, so
    // this stayed invisible until History asked for a signed URL.
    // OkHttp rather than the ktor fetcher because ktor-client-okhttp is
    // already the engine here -- this adds a fetcher, not a second stack.
    implementation(libs.coil.network.okhttp)
    implementation(libs.play.services.location)
    // The home-screen widget: today's status, and a way into the flow.
    implementation(libs.androidx.glance.appwidget)
    // CameraX writes rotation to EXIF rather than rotating pixels.
    implementation(libs.androidx.exifinterface)

    // Room is the app's memory when there is no signal: the submission
    // queue, plus mirrors of the record and project list so the dashboard
    // and the picker still render.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager survives process death and reboots, which a coroutine
    // scoped to a ViewModel does not. A worker who taps SUBMIT and puts
    // the phone in their pocket must still have the record uploaded.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    implementation(libs.hilt.viewmodel.compose)
    ksp(libs.hilt.compiler)

    // Supabase. auth + postgrest are what B2 needs; storage is declared now
    // because B3's photo upload uses the same client and adding a module
    // later would change the resolved ktor version underneath it.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.multiplatform.settings)
    implementation(libs.androidx.security.crypto)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
