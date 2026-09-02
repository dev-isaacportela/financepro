plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.financepro"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.financepro"
        minSdk = 26          // java.time nativo, sem desugaring (arquitetura.md §1)
        // 36, não 37: compileSdk decide contra qual API se compila; targetSdk
        // liga comportamentos novos de runtime, e isso pede teste próprio.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric precisa do AndroidManifest mesclado e dos recursos
            // empacotados para levantar a Application nos testes de DAO (T-004).
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // REQ-DATA-003 — schema exportado e versionado no repositório.
    // REQ-DATA-001 proíbe fallbackToDestructiveMigration; o schema versionado
    // é o que torna cada Migration revisável em diff.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    // T-018 — bloqueio biométrico (REQ-SEC-003) e a preferência que o liga.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)

    // T-050 — o worker diário que busca o CDI (REQ-INV-005). A versão estava
    // declarada desde a T-001 e nunca aplicada: o worker de recorrências não
    // entrou (ADR-006), e este é o primeiro trabalho de fundo do app.
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
