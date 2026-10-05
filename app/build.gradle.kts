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
// COLA_ELEITORAL_KEYSTORE deve ser um caminho absoluto (caminhos relativos partem de app/).
val releaseSigningEnv: Map<String, String?> =
    listOf(
        "COLA_ELEITORAL_KEYSTORE",
        "COLA_ELEITORAL_KEYSTORE_PASSWORD",
        "COLA_ELEITORAL_KEY_ALIAS",
        "COLA_ELEITORAL_KEY_PASSWORD",
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

// Guarda de release: nenhuma tarefa da variante release (assembleRelease, bundleRelease, lintRelease...)
// roda enquanto a URL da política de privacidade, o e-mail de contato ou o nome do desenvolvedor
// forem marcadores, ou enquanto docs/privacidade.md (embutida no app) não citar esses mesmos
// valores. A checagem é feita na execução, a partir de pre*ReleaseBuild,
// então debug, lint, testDebugUnitTest e o CI não são afetados. Ver docs/PUBLICACAO.md.
val checkReleasePublishingProperties = tasks.register("checkReleasePublishingProperties") {
    group = "verification"
    description = "Falha se os dados de publicacao (colaEleitoral.*) forem marcadores ou nao baterem com a politica."
    val privacyPolicyUrl = providers.gradleProperty("colaEleitoral.privacyPolicyUrl").orElse("")
    val contactEmail = providers.gradleProperty("colaEleitoral.contactEmail").orElse("")
    val developerName = providers.gradleProperty("colaEleitoral.developerName").orElse("")
    // A politica embutida no app (embedPrivacyPolicy) tem de citar o mesmo responsavel e contato.
    val policy = rootProject.layout.projectDirectory.file("docs/privacidade.md")
    inputs.property("privacyPolicyUrl", privacyPolicyUrl)
    inputs.property("contactEmail", contactEmail)
    inputs.property("developerName", developerName)
    inputs.file(policy)
    doLast {
        val url = privacyPolicyUrl.get().trim()
        val email = contactEmail.get().trim()
        val name = developerName.get().trim()
        val policyText = policy.asFile.readText()
        // Sem acentos de propósito, como as outras mensagens deste arquivo.
        val problems = buildList {
            when {
                "example.com" in url.lowercase() -> add("colaEleitoral.privacyPolicyUrl ainda e o marcador: $url")
                !url.startsWith("https://") -> add("colaEleitoral.privacyPolicyUrl precisa comecar com https://: $url")
            }
            when {
                "example.com" in email.lowercase() -> add("colaEleitoral.contactEmail ainda e o marcador: $email")
                !Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""").matches(email) ->
                    add("colaEleitoral.contactEmail nao parece um e-mail: $email")
            }
            when {
                name.isEmpty() -> add("colaEleitoral.developerName esta vazio")
                Regex("""preencher|example|exemplo|[\[\]"\\]""", RegexOption.IGNORE_CASE).containsMatchIn(name) ->
                    add("colaEleitoral.developerName ainda e um marcador ou tem caracteres invalidos: $name")
            }
            if ("[PREENCHER" in policyText) add("docs/privacidade.md ainda tem campos [PREENCHER]")
            listOf(name, email, url).filter { it.isNotEmpty() && it !in policyText }.forEach {
                add("docs/privacidade.md (politica embutida no app) nao cita: $it")
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                problems.joinToString(
                    separator = "\n",
                    prefix = "Build de release bloqueado: dados de publicacao invalidos.\n",
                    postfix = "\nDefina os valores reais em gradle.properties ou na linha de comando, por exemplo:\n" +
                        "  ./gradlew bundleRelease -PcolaEleitoral.privacyPolicyUrl=https://... " +
                        "-PcolaEleitoral.contactEmail=... -PcolaEleitoral.developerName=...\n" +
                        "Veja docs/PUBLICACAO.md.",
                ) { "  - $it" },
            )
        }
    }
}
tasks.named { it.startsWith("pre") && it.endsWith("ReleaseBuild") }.configureEach {
    dependsOn(checkReleasePublishingProperties)
}

/**
 * Politica de privacidade dentro do app: docs/privacidade.md e a fonte unica (a pagina publica e
 * gerada do mesmo arquivo). Esta tarefa copia o texto, sem alterar, para res/raw/privacy_policy.md
 * num diretorio gerado; a tela PrivacyPolicyScreen o le e o renderiza.
 */
abstract class EmbedPrivacyPolicy : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policy: RegularFileProperty

    @get:OutputDirectory
    abstract val resDirectory: DirectoryProperty

    @TaskAction
    fun embed() {
        val raw = resDirectory.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        policy.get().asFile.copyTo(raw.resolve("privacy_policy.md"))
    }
}

val embedPrivacyPolicy = tasks.register<EmbedPrivacyPolicy>("embedPrivacyPolicy") {
    group = "build"
    description = "Copia docs/privacidade.md para os recursos do app (res/raw/privacy_policy.md)."
    policy = rootProject.layout.projectDirectory.file("docs/privacidade.md")
    resDirectory = layout.buildDirectory.dir("generated/privacyPolicy/res")
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(embedPrivacyPolicy, EmbedPrivacyPolicy::resDirectory)
    }
}

android {
    namespace = "com.veronezzi.colaeleitoral"
    // 37 porque as versões estáveis atuais de core, Compose, navigation e material3-adaptive exigem
    // minCompileSdk 37. O targetSdk segue o mínimo do Google Play para apps novos (API 36).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.veronezzi.colaeleitoral"
        minSdk = 26
        // Mínimo do Google Play para apps novos desde 31/08/2026. O aviso OldTargetApi do lint é
        // esperado; subir para 37 exige testar as mudanças de comportamento do Android 17.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Dados de publicação vêm de gradle.properties (ou -P na linha de comando). Os valores
        // padrão são marcadores e não podem ir para um build de release.
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${publishingProperty("colaEleitoral.privacyPolicyUrl")}\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"${publishingProperty("colaEleitoral.contactEmail")}\"")
        // "Desenvolvido por ..." em Sobre e na política do app (política de deturpação do Google Play).
        buildConfigField("String", "DEVELOPER_NAME", "\"${publishingProperty("colaEleitoral.developerName")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningEnv.getValue("COLA_ELEITORAL_KEYSTORE")!!)
                storePassword = releaseSigningEnv.getValue("COLA_ELEITORAL_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("COLA_ELEITORAL_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("COLA_ELEITORAL_KEY_PASSWORD")
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
    // Capturas de tela da loja (ui/screenshots/StoreScreenshots, dados fictícios): fora da rodada
    // normal. Para gerar de novo em fastlane/metadata/android/pt-BR/images/phoneScreenshots:
    //   ./gradlew :app:testDebugUnitTest --tests '*.StoreScreenshots' -PcolaEleitoral.storeScreenshots=true
    if (providers.gradleProperty("colaEleitoral.storeScreenshots").orNull == "true") {
        val screenshotsDir = rootProject.layout.projectDirectory.dir("fastlane/metadata/android/pt-BR/images/phoneScreenshots")
        systemProperty("colaEleitoral.screenshotsDir", screenshotsDir.asFile.absolutePath)
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    } else {
        filter.excludeTestsMatching("*.StoreScreenshots")
    }
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
