# Meu Santinho

App Android que ajuda o eleitor a montar a própria **cola eleitoral** com os dados públicos de
candidaturas do Tribunal Superior Eleitoral (TSE): escolher a eleição e o local de votação, buscar e
filtrar candidatos, guardar uma escolha por voto e levar a cola **em papel** no dia da eleição, mesmo
sem internet. O celular não pode entrar na cabine de votação (Lei 9.504/97, art. 91-A); a cola em
papel pode.

> **Aviso.** O Meu Santinho é um aplicativo independente, sem vínculo com o TSE, a Justiça Eleitoral,
> órgãos de governo, partidos ou candidatos. Os dados vêm do sistema público DivulgaCandContas do TSE
> (<https://divulgacandcontas.tse.jus.br/divulga/>) e, quando ele não responde, do Portal de Dados
> Abertos do TSE (<https://dadosabertos.tse.jus.br/>). Confira sempre no site oficial.

## Funcionalidades

- Eleições descobertas pela API do TSE (gerais e municipais, 1º e 2º turno), sem datas fixas no código.
- Todos os candidatos que o TSE lista para cada cargo do local de votação, em ordem de número, com a
  situação da candidatura exatamente como o TSE publica; busca por nome, número ou partido e filtros.
- Uma escolha por voto, na ordem da urna e com o número de dígitos de cada cargo. Em 2026, dois votos
  para o Senado, com aviso de candidato repetido (o 2º voto repetido é anulado na urna).
- Cola para imprimir ou salvar em PDF (sistema de impressão do Android) e para compartilhar como imagem.
- Uso offline: os dados do local ficam em cache e as escolhas não dependem de rede.
- Lembrete opcional no dia da eleição, com texto genérico, e bloqueio opcional do app por biometria
  ou pelo bloqueio de tela do aparelho.
- Acessibilidade: TalkBack (números lidos dígito por dígito), fonte do sistema até 200%, tema claro e
  escuro, alvos de toque de 48 dp, layout para tablets e dobráveis.
- Neutralidade: nenhum candidato é recomendado, destacado ou patrocinado; não há enquete, ranking nem
  "mais buscados".

## Privacidade

- Sem conta, servidor próprio, anúncios, analytics, SDK de falhas ou rastreamento.
- As escolhas revelam opinião política (dado sensível, LGPD art. 5º, II) e **ficam só no aparelho**:
  arquivo cifrado com AES-256-GCM, chave no Android Keystore, fora do backup e da transferência entre
  aparelhos, nunca em logs ou notificações.
- O app só se conecta a servidores do TSE; o TSE recebe o IP e o que foi consultado, como em qualquer
  acesso ao site oficial.
- Política completa: [`docs/privacidade.md`](docs/privacidade.md) (publicada a partir de
  [`docs/privacidade/index.html`](docs/privacidade/index.html)). Formulário Segurança dos dados do
  Google Play: [`docs/data-safety.md`](docs/data-safety.md).

## Fonte dos dados e atribuição

Fonte: Tribunal Superior Eleitoral (TSE), dados públicos, licença CC BY
(<https://dadosabertos.tse.jus.br/>). O app exibe os dados como o TSE publica, com a data da última
atualização e o link para a página oficial de cada candidatura, e não usa dados pessoais
desnecessários dos candidatos (CPF, título de eleitor, data de nascimento, e-mail). O app não usa
logotipos do TSE ou da Justiça Eleitoral nem o Brasão da República.

## Stack

Kotlin 2.4 · Jetpack Compose com Material 3 (cor dinâmica, claro e escuro) e material3-adaptive ·
Navigation Compose com rotas type-safe · ViewModel + StateFlow, Coroutines/Flow · Hilt (KSP) ·
Retrofit + OkHttp + kotlinx.serialization · Room (cache dos dados públicos) · DataStore (preferências
e escolhas cifradas) · Coil 3 · WorkManager · androidx.biometric. Testes: JUnit 4,
kotlinx-coroutines-test, Turbine, MockK, MockWebServer, Robolectric e Compose UI test.

AGP 9.4, Gradle 9.8, compileSdk 37, targetSdk 36, minSdk 26 (Android 8.0). Módulo único `:app`,
pacote `com.veronezzi.colaeleitoral`.

## Como compilar e testar

Requisitos: Android SDK com a plataforma 37 (aponte `sdk.dir` em `local.properties` ou defina
`ANDROID_HOME`), **JDK 17** para compilar e **JDK 21** para os testes locais (o Robolectric do SDK 36
exige 21). O Gradle encontra os dois JDKs instalados e baixa o que faltar (foojay).

```bash
./gradlew assembleDebug            # APK de debug em app/build/outputs/apk/debug/
./gradlew installDebug             # instala no aparelho ou emulador conectado
./gradlew testDebugUnitTest        # testes locais (JVM + Robolectric)
./gradlew lint                     # lint do Android
./gradlew lint testDebugUnitTest assembleDebug   # o mesmo que o CI roda
```

## Release

```bash
# AAB de release sem assinatura, para conferir o build (a URL e o e-mail precisam ser reais):
./gradlew bundleRelease \
  -PcolaEleitoral.privacyPolicyUrl=https://<usuario>.github.io/<repositorio>/privacidade/ \
  -PcolaEleitoral.contactEmail=<e-mail de contato>

# Assinado: defina COLA_ELEITORAL_KEYSTORE (caminho absoluto), COLA_ELEITORAL_KEYSTORE_PASSWORD,
# COLA_ELEITORAL_KEY_ALIAS e COLA_ELEITORAL_KEY_PASSWORD antes do comando acima.

scripts/check-store-metadata.sh            # limites e formato da ficha da loja
scripts/check-store-metadata.sh --release  # idem, e exige a política de privacidade preenchida
python3 scripts/render-store-graphics.py   # refaz icon.png e featureGraphic.png do ícone adaptativo
```

- **Trava de release:** qualquer tarefa da variante release (`assembleRelease`, `bundleRelease`...)
  falha enquanto `colaEleitoral.privacyPolicyUrl` ou `colaEleitoral.contactEmail` (em `gradle.properties`)
  contiverem `example.com`. Debug, lint e testes não são afetados.
- **Workflow `Release`** (`.github/workflows/release.yml`): roda com tags `v*` (por exemplo,
  `v1.0.0`) ou à mão; faz lint e testes, gera o AAB assinado com segredos do GitHub e guarda AAB e
  `mapping.txt` como artefatos. O envio ao Google Play (teste interno ou fechado) é opcional e fica
  desligado por padrão.
- Passo a passo completo, do zero à produção: [`docs/PUBLICACAO.md`](docs/PUBLICACAO.md).

## Estrutura do projeto

```
app/src/main/kotlin/com/veronezzi/colaeleitoral/
  core/          rede (OkHttp, interceptores, JSON) e utilitários comuns
  data/          remote/api, remote/dto, local/db (Room), local/secure (escolhas cifradas), mapper, repository
  domain/        model e repository: contrato do domínio, Kotlin puro
  ui/            theme, navigation, screens/<tela>, components
  di/            módulos Hilt
  work/          lembrete do dia da eleição (WorkManager)
app/src/test/kotlin/            testes locais; amostras reais da API em app/src/test/resources/tse/
app/schemas/                    esquemas exportados do Room
fastlane/metadata/android/pt-BR/   ficha da loja (textos, novidades, ícone, imagem de destaque)
scripts/                        validação da ficha e geração dos gráficos da loja
docs/                           arquitetura, publicação, política de privacidade, Segurança dos dados
.github/workflows/              ci.yml (lint, testes, APK debug) e release.yml
```

## Documentação

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): arquitetura, API do TSE, segurança, conformidade,
  riscos e decisões em aberto.
- [`docs/PUBLICACAO.md`](docs/PUBLICACAO.md): publicação no Google Play, passo a passo.
- [`docs/privacidade.md`](docs/privacidade.md): política de privacidade (LGPD).
- [`docs/data-safety.md`](docs/data-safety.md): respostas do formulário Segurança dos dados.

## Licenças

- Dados de candidaturas: TSE, licença Creative Commons Atribuição (CC BY).
- Bibliotecas de código aberto: listadas na tela Licenças do app.
- A imagem de destaque da loja usa a fonte Roboto (SIL Open Font License 1.1).
- Código do app: o autor ainda não definiu uma licença; até lá, todos os direitos são reservados.
