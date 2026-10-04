plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Assinatura de release: vem só de variáveis de ambiente (segredos do CI), nada fica no repositório.
// Sem nenhuma delas, o release sai sem assinatura (serve para checar bundleRelease localmente).
// MEU_SANTINHO_KEYSTORE deve ser um caminho absoluto (caminhos relativos partem de app/).
val releaseSigningEnv: Map<String, String?> =
    listOf(
        "MEU_SANTINHO_KEYSTORE",
        "MEU_SANTINHO_KEYSTORE_PASSWORD",
        "MEU_SANTINHO_KEY_ALIAS",
        "MEU_SANTINHO_KEY_PASSWORD",
    ).associateWith { name -> providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() } }
val missingReleaseSigningEnv = releaseSigningEnv.filterValues { it == null }.keys
val hasReleaseSigning = missingReleaseSigningEnv.isEmpty()
if (!hasReleaseSigning && missingReleaseSigningEnv.size < releaseSigningEnv.size) {
    throw GradleException(
        // Sem acentos de propósito: a mensagem aparece em consoles com qualquer locale.
        "Assinatura de release incompleta, faltam: ${missingReleaseSigningEnv.joinToString()}.",
    )
}

// Dados de publicação (URL da política de privacidade, e-mail de contato) ficam em gradle.properties.
fun publishingProperty(name: String): String =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Propriedade de publicacao ausente: $name (ver gradle.properties).")

android {
    namespace = "com.veronezzi.meusantinho"
    // 37 porque as versões estáveis atuais de core, Compose, navigation e material3-adaptive exigem
    // minCompileSdk 37. O targetSdk segue o mínimo do Google Play para apps novos (API 36).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.veronezzi.meusantinho"
        minSdk = 26
        // Mínimo do Google Play para apps novos desde 31/08/2026. O aviso OldTargetApi do lint é
        // esperado; subir para 37 exige testar as mudanças de comportamento do Android 17.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Dados de publicação vêm de gradle.properties (ou -P na linha de comando). Os valores
        // padrão são marcadores e não podem ir para um build de release.
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${publishingProperty("meuSantinho.privacyPolicyUrl")}\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"${publishingProperty("meuSantinho.contactEmail")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningEnv.getValue("MEU_SANTINHO_KEYSTORE")!!)
                storePassword = releaseSigningEnv.getValue("MEU_SANTINHO_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("MEU_SANTINHO_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("MEU_SANTINHO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric precisa dos recursos e do manifest mesclados.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(17)
}

// O Robolectric só cria sandboxes do SDK 35+ (o targetSdk é 36) numa JVM 21. A compilação continua
// no toolchain 17; apenas a JVM que executa os testes locais usa o 21 (o CI instala os dois JDKs).
tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    maxHeapSize = "2g"
    // O sandbox do SDK 36 (ApplicationSharedMemory) mexe nos internos de FileDescriptor via
    // jdk.internal.access, que o java.base não exporta por padrão.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
}

room {
    // Esquemas exportados são versionados para testar migrações.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Compose (versões pelo BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Registra a ComponentActivity vazia usada pelos testes de UI Compose (Robolectric).
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Injeção de dependência
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Persistência local
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Rede e serialização
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    // Só em debug: o interceptor deve ser instalado a partir de src/debug, nunca em release.
    debugImplementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Imagens
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Lembrete do dia da eleição e bloqueio opcional do app
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    // Testes locais (JVM + Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Testes instrumentados
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
