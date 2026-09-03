import java.util.Properties

/**
 * A assinatura de release, quando existe.
 *
 * O keystore nunca entra no repositório (`.gitignore` barra `*.jks` e
 * `keystore.properties`). Localmente o arquivo é seu; no CI ele é escrito a
 * partir dos secrets, logo antes do build. Sem ele o `assembleRelease` sai
 * **sem assinar** em vez de falhar — um clone qualquer precisa conseguir
 * compilar o release para revisar o que o R8 faz com o código.
 */
val chaveDeRelease = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

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
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (chaveDeRelease.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(chaveDeRelease.getProperty("storeFile"))
                storePassword = chaveDeRelease.getProperty("storePassword")
                keyAlias = chaveDeRelease.getProperty("keyAlias")
                keyPassword = chaveDeRelease.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Nulo quando não há keystore: o APK sai não assinado, e o
            // `packageRelease` continua rodando.
            signingConfig = signingConfigs.findByName("release")
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

    // `MigrationTestHelper` procura o schema exportado nos assets, por
    // `<classe do banco>/<versão>.json`. Apontar o diretório aqui evita a cópia
    // que envelheceria: o teste lê o mesmo arquivo que o KSP escreve.
    //
    // Vai no `debug`, e não no `test`: o AGP não empacota assets de teste de
    // unidade, e o Robolectric serve os assets da variante. Como consequência
    // os JSONs entram no APK de debug — 50 KB que nunca chegam ao release.
    sourceSets {
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
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

    // REQ-DATA-001 — `MigrationTestHelper` cria o banco na versão antiga a
    // partir do schema exportado em `app/schemas/`, o que é o ponto: o teste
    // roda contra o DDL que está no repositório, não contra o de agora.
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
