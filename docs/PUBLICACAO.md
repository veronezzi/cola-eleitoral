# Publicação no Google Play, do zero à produção

Guia atualizado em 5 de outubro de 2026, dia seguinte ao 1º turno. Regras do Google Play mudam:
confira os links oficiais antes de cada etapa. Documentos relacionados: [`ARCHITECTURE.md`](ARCHITECTURE.md)
(seções 5 a 7), [`data-safety.md`](data-safety.md) (respostas do formulário Segurança dos dados) e a
política de privacidade ([`privacidade.md`](privacidade.md), da qual é gerada
[`privacidade/index.html`](privacidade/index.html)).

## 0. O que já está pronto e o que falta

| Item | Situação |
|---|---|
| Nome **Cola Eleitoral** e pacote `com.veronezzi.colaeleitoral` | Decididos. O pacote fica permanente no primeiro upload |
| Ficha da loja em pt-BR (`fastlane/metadata/android/pt-BR/`): título, descrições curta e completa, novidades da versão 1 | Pronto. Validar com `scripts/check-store-metadata.sh` |
| Ícone 512 px e imagem de destaque 1024 x 500 (`.../images/`), gerados do ícone adaptativo por `scripts/render-store-graphics.py` | Pronto |
| Capturas de tela do celular (`.../images/phoneScreenshots/`, 2 a 8) | Confira se já existem e se estão neutras; senão, seção 16 |
| Política de privacidade: `docs/privacidade.md` (o app embute este texto no build) e a página `docs/privacidade/index.html`, gerada dele | Texto pronto e preenchido; **falta publicar** no repositório público (seção 7) |
| Dados de publicação em `gradle.properties`: `colaEleitoral.privacyPolicyUrl`, `colaEleitoral.contactEmail` e `colaEleitoral.developerName` | Preenchidos |
| Trava de release: o build de release falha se esses dados forem marcadores ou inválidos | Pronto (`app/build.gradle.kts`, tarefa `checkReleasePublishingProperties`) |
| Workflow de release (`.github/workflows/release.yml`): ficha e política, lint, testes, AAB assinado, artefatos, envio opcional ao Play | Pronto; faltam o repositório e os segredos (seção 6) |
| Conta pessoal no Play Console | **A criar** (seção 4) |
| Validação num aparelho no Brasil (seção 3) | **Obrigatória antes de publicar** |

## 1. Decisões tomadas

| Tema | Decisão |
|---|---|
| Conta do Play Console | **Pessoal e nova**, criada com o Gmail veronezzi14@gmail.com. Por ser pessoal e criada depois de 13/11/2023, exige teste fechado com 12 testadores por 14 dias seguidos antes da produção (seção 13) |
| Desenvolvedor e responsável pelos dados (controlador, na política) | **veronezzi**, sem vínculo com partidos, candidatos ou governo |
| E-mail de contato público | **veronezzi14@gmail.com**: no app (Sobre), na política e na ficha da loja |
| Política de privacidade | <https://veronezzi.github.io/cola-eleitoral/privacidade/>, publicada pelo GitHub Pages da pasta `docs/` do próprio repositório (público) `veronezzi/cola-eleitoral` |
| Código do app | Repositório **privado** `veronezzi/cola-eleitoral` |
| Segurança dos dados | "Nenhum dado coletado nem compartilhado" ([`data-safety.md`](data-safety.md)) |
| Nome do app | Cola Eleitoral (histórico no fim deste guia) |

**O que fica público com uma conta pessoal.** Na página do app, o Google mostra o nome de
desenvolvedor (veronezzi), o seu **nome legal** e o país, tirados do perfil de pagamentos, e o
e-mail do desenvolvedor. O endereço completo só aparece se o app for monetizado, o que não é o caso.
O telefone e o e-mail de contato com o Google não ficam públicos
(<https://support.google.com/googleplay/android-developer/answer/13628312>). Isso também atende à
exigência de transparência sobre quem desenvolve apps de tema político (política de deturpação,
ARCHITECTURE.md, P5).

## 2. Cronograma realista

Hoje é 05/10/2026. O 2º turno é em **25/10/2026**, daqui a 20 dias. As próximas eleições são as
municipais de **01/10/2028** (1º turno) e **29/10/2028** (2º turno).

| Quando (aproximado) | O quê |
|---|---|
| 05 a 09/10/2026 | Criar a conta pessoal, pagar a taxa e concluir as verificações de identidade, e-mail, telefone e aparelho Android (de horas a alguns dias). Em paralelo: validação no Brasil (seção 3), repositórios, chave de upload e segredos (seções 5 a 7) |
| 08 a 12/10 | Criar o app e fazer o primeiro upload no teste interno (seção 9): até 100 testadores por e-mail, disponível em minutos |
| até 12/10 | Completar a ficha e o Conteúdo do app (seções 10 e 11) e enviar o teste fechado para revisão, que pode levar alguns dias |
| ~14/10 a ~28/10 | 14 dias seguidos com 12 ou mais testadores inscritos. **Os testadores usam o app no 2º turno, em 25/10** |
| ~28/10 a ~04/11 | Pedir acesso à produção. A análise costuma levar até 7 dias, às vezes mais |
| novembro/2026 | Produção, com lançamento gradual |
| até 31/08/2027 | Subir o `targetSdk` para o nível exigido em 2027 e testar as mudanças de comportamento |
| até o começo de 09/2028 | Quando o TSE publicar a eleição municipal de 2028 (o registro de candidaturas termina em 15/08/2028, Lei 9.504/97, art. 11), refazer a validação da seção 3 com um município e publicar uma atualização |

Conclusão: com conta pessoal nova, **produção antes do 2º turno de 2026 não é viável** (14 dias de
teste fechado mais a análise). A meta realista é o teste fechado a tempo de os testadores usarem o
app em 25/10, a produção em novembro e o app pronto para 2028.

Até 2028:
- **Nível de API:** todo ano, até 31 de agosto, apps novos e atualizações precisam mirar a API do ano
  anterior (<https://support.google.com/googleplay/android-developer/answer/11926878>). Planeje subir
  o `targetSdk` em 2027 e em 2028.
- Mantenha o app atualizado entre as eleições (dependências, política, Segurança dos dados).

## 3. Validação obrigatória num aparelho no Brasil

Antes de qualquer publicação (ARCHITECTURE.md, seção 7.3). Use um APK de debug instalado por adb
(`./gradlew installDebug`); a verificação de desenvolvedor não afeta instalações por adb. Faça em
**dados móveis e em Wi-Fi residencial**, anotando status HTTP, `Content-Type`, `Content-Encoding`,
`Cache-Control` e tamanho, **sem salvar corpos de resposta com dados pessoais**. Não faça teste de
carga nem repita chamadas em laço contra o TSE.

- [ ] 1. `eleicao/ordinarias` responde 200 com JSON (o ponto mais importante). Se der 403, anote e
      confira se o app passa para a fonte de dados abertos, **sem testar variações de cabeçalho**.
- [ ] 2. `eleicao/listar/municipios/20322002026/SP/cargos` e `.../BR/cargos`: formato e presença
      dos cargos 2, 4, 9 e 10.
- [ ] 3. `candidatura/listar/2026/SP/20322002026/6/candidatos` (cerca de 1.132 itens): tempo,
      tamanho e gzip.
- [ ] 4. Detalhe de um presidenciável (`BR`) e de um senador (UF): `vices`, textos de `ds_CARGO`
      ("1º Suplente"?) e `situacaoVice`.
- [ ] 5. Foto: tipo, tamanho e resposta para candidato com `fotoUrlPublicavel = false`.
- [ ] 6. Municípios com `20322002026` e com `2045202024`.
- [ ] 7. A partir de 05/10/2026: `descricaoTotalizacao = "2º turno"` em
      `listar/2026/BR/20322002026/1` e no cargo 3 de uma UF com 2º turno; `eleicao/ordinarias`
      continua com uma entrada só para 2026?
- [ ] 8. Com VPN ligada: 403 e a mensagem de bloqueio no app (ou a fonte de dados abertos).
- [ ] 9. Links "Ver no site do TSE" (`txLink` e o formato `{uf}/{uf}/...`) abrem no Chrome do Android.
- [ ] 10. Android 8.0 (API 26): gerar a chave AES-GCM no Keystore, salvar escolhas, fechar e reabrir
      o app, e as escolhas continuam legíveis.

Confira também no aparelho: a cola impressa (PDF) e a imagem compartilhada; o lembrete (Android 13+
pede a permissão de notificação só ao ligar); o bloqueio do app; TalkBack e fonte em 200%.

## 4. Conta pessoal no Play Console

1. Ative a verificação em duas etapas no Gmail veronezzi14@gmail.com e entre em
   <https://play.google.com/console/signup> com ele.
2. Escolha a conta **pessoal** (para você mesmo), aceite o Contrato de Distribuição do Desenvolvedor
   e pague a **taxa única de US$ 25** com cartão de crédito ou débito no seu nome.
3. Perfil de pagamentos: nome legal e endereço reais. O Google pode pedir um documento oficial com
   foto e um cartão no mesmo nome; a identidade precisa estar verificada antes de publicar.
4. Dados do desenvolvedor: nome de desenvolvedor **veronezzi** (o mesmo do app, em Sobre, e da
   política) e e-mail do desenvolvedor **veronezzi14@gmail.com** (público). O e-mail e o telefone de
   contato com o Google são verificados por código de uso único e não aparecem na loja.
5. Verificação do aparelho: instale o app **Google Play Console** num celular Android, entre com a
   mesma conta e conclua a verificação. Contas pessoais novas precisam dela antes de publicar.
6. **Verificação de desenvolvedor Android:** obrigatória no Brasil desde 30/09/2026 para instalações
   em aparelhos certificados. Apps do Google Play são registrados automaticamente na maioria dos
   casos; depois do primeiro upload, confira na página inicial do Play Console se
   `com.veronezzi.colaeleitoral` aparece registrado
   (<https://developer.android.com/developer-verification>,
   <https://support.google.com/googleplay/android-developer/answer/17134731>).

Fontes: <https://support.google.com/googleplay/android-developer/answer/6112435>,
<https://support.google.com/googleplay/android-developer/answer/13628312>,
<https://support.google.com/googleplay/android-developer/answer/14151465>.

## 5. Chave de upload e Play App Signing

O Google guarda a chave que assina o app para os usuários (Play App Signing, padrão em apps novos). Você
assina os uploads com uma **chave de upload** que fica com você. Gere uma vez, fora do repositório:

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=veronezzi, C=BR"
```

- O `keytool` pede a senha. Em PKCS12, a senha do keystore e a da chave são a mesma: use o mesmo
  valor em `COLA_ELEITORAL_KEYSTORE_PASSWORD` e `COLA_ELEITORAL_KEY_PASSWORD`.
- `-validity 10000` são cerca de 27 anos.
- Guarde `upload-keystore.jks` e a senha num gerenciador de senhas, com uma cópia de segurança. O
  `.gitignore` já ignora `*.jks`, `*.keystore` e `*.p12`; nunca faça commit da chave.

Comandos úteis:

```bash
# Impressões digitais (SHA-1 e SHA-256) do certificado de upload
keytool -list -v -keystore upload-keystore.jks -alias upload

# Certificado em PEM, pedido pelo Google se um dia você precisar redefinir a chave de upload
keytool -export -rfc -keystore upload-keystore.jks -alias upload -file upload_certificate.pem
```

Perdeu a chave de upload? Peça a redefinição em Play Console > Teste e lançamento > Configuração >
Integridade do app (App integrity) > Assinatura de apps, enviando o PEM de uma chave nova. A chave de
assinatura do app continua com o Google.

Assinar um build local (opcional), com variáveis de ambiente e caminho absoluto:

```bash
export COLA_ELEITORAL_KEYSTORE="$HOME/chaves/upload-keystore.jks"
export COLA_ELEITORAL_KEY_ALIAS=upload
read -rs COLA_ELEITORAL_KEYSTORE_PASSWORD && export COLA_ELEITORAL_KEYSTORE_PASSWORD
export COLA_ELEITORAL_KEY_PASSWORD="$COLA_ELEITORAL_KEYSTORE_PASSWORD"
./gradlew bundleRelease
```

Sem nenhuma dessas variáveis o AAB sai sem assinatura (serve só para conferir o build); com só algumas,
o build falha dizendo quais faltam.

## 6. Repositório do código e segredos do GitHub

O código fica no repositório **privado** `veronezzi/cola-eleitoral`. Para criá-lo a partir desta
pasta, já com o histórico do git:

```bash
gh repo create veronezzi/cola-eleitoral --private --source=. --remote=origin --push
```

O GitHub Actions de repositório privado no plano gratuito tem uma cota mensal de minutos; o CI roda a
cada push e o release só com tags, então ela costuma bastar. Os segredos ficam em Settings > Secrets
and variables > Actions. Com o `gh`, `gh secret set NOME` pede o valor sem mostrá-lo e sem deixá-lo
no histórico do shell.

| Nome | Tipo | Valor |
|---|---|---|
| `COLA_ELEITORAL_KEYSTORE_BASE64` | segredo | O keystore em base64: `base64 -w 0 upload-keystore.jks \| gh secret set COLA_ELEITORAL_KEYSTORE_BASE64` (no macOS: `base64 -i upload-keystore.jks \| gh secret set ...`) |
| `COLA_ELEITORAL_KEYSTORE_PASSWORD` | segredo | Senha do keystore (`gh secret set COLA_ELEITORAL_KEYSTORE_PASSWORD`) |
| `COLA_ELEITORAL_KEY_ALIAS` | segredo | `upload` |
| `COLA_ELEITORAL_KEY_PASSWORD` | segredo | A mesma senha (PKCS12) |
| `COLA_ELEITORAL_PRIVACY_POLICY_URL` | variável (opcional) | Substitui `colaEleitoral.privacyPolicyUrl` de `gradle.properties` |
| `COLA_ELEITORAL_CONTACT_EMAIL` | variável (opcional) | Substitui `colaEleitoral.contactEmail` |
| `COLA_ELEITORAL_DEVELOPER_NAME` | variável (opcional) | Substitui `colaEleitoral.developerName` |
| `PLAY_SERVICE_ACCOUNT_JSON` | segredo (opcional) | JSON da conta de serviço (seção 12). De preferência como segredo do ambiente `google-play` |
| `PLAY_UPLOAD_TRACK` | variável (opcional) | `internal` ou `alpha`: envia ao Play em cada tag. Vazia = não envia |
| `PLAY_RELEASE_STATUS` | variável (opcional) | `draft` (padrão: a versão fica como rascunho no Play Console) ou `completed` |

Os valores de `gradle.properties` já são os reais; as três variáveis opcionais só servem para
substituí-los sem commit. O workflow nunca imprime segredos: só os nomes dos que faltam. A chave é
decodificada em `$RUNNER_TEMP` com permissão 600 e apagada no fim do job.

## 7. Política de privacidade publicada: GitHub Pages do repositório do app

O texto tem uma fonte só, `docs/privacidade.md`: o app embute esse arquivo no build (Sobre >
Política de privacidade) e `scripts/render-privacy-page.py` gera dele `docs/privacidade/index.html`,
uma página autocontida (CSS embutido, sem scripts nem recursos externos). O repositório
`veronezzi/cola-eleitoral` é público, então o GitHub Pages publica a pasta `docs/` dele mesmo e a
página fica em <https://veronezzi.github.io/cola-eleitoral/privacidade/>. O arquivo vazio
`docs/.nojekyll` faz o Pages servir os arquivos como estão. Se o repositório virar privado, o Pages
gratuito para de funcionar: aí a página precisa ir para um repositório público só dela, e a URL muda
em `gradle.properties` e em `docs/privacidade.md`.

1. Gere a página e confira; depois faça commit e push do `.md` junto com o `index.html`:
   ```bash
   python3 scripts/render-privacy-page.py
   scripts/check-store-metadata.sh
   ```
2. Ligue o Pages, uma vez só: no repositório, Settings > Pages > Build and deployment > Source:
   **Deploy from a branch**; Branch: **main**, pasta **/docs** > Save. Pelo `gh`:
   ```bash
   gh api -X POST repos/veronezzi/cola-eleitoral/pages \
     -f "source[branch]=main" -f "source[path]=/docs"
   ```
3. Espere a publicação (aba Actions, "pages build and deployment", alguns minutos) e confira:
   ```bash
   curl -fsSI https://veronezzi.github.io/cola-eleitoral/privacidade/   # HTTP/2 200
   scripts/check-store-metadata.sh --release   # também compara a página publicada com o arquivo
   ```
   Abra o endereço no celular. A URL tem de ser pública, sem login e sem bloqueio geográfico, e não
   pode ser PDF. O HTTPS do `github.io` já vem ligado.
4. Informe a mesma URL no Play Console (Conteúdo do app > Política de privacidade, seção 11).
5. A cada mudança na política: edite `docs/privacidade.md`, rode o script, faça commit e push. O Pages
   republica sozinho e o próximo build do app embute o texto novo.

## 8. Versionamento e workflow de release

- `versionCode` (inteiro em `app/build.gradle.kts`): sobe 1 a cada upload; o Play recusa um número
  repetido. `versionName`: `MAIOR.MENOR.CORREÇÃO` (por exemplo, `1.0.1` para uma correção no 2º turno).
- Cada versão precisa de `fastlane/metadata/android/pt-BR/changelogs/<versionCode>.txt` (até 500
  caracteres); o workflow recusa a versão sem ele.
- A tag é `v` + `versionName` (por exemplo, `v1.0.0`); o workflow confere.

Para lançar:

```bash
# 1. Atualize versionCode e versionName, crie o changelog e faça commit no main (o CI tem de passar).
# 2. Crie e envie a tag:
git tag -a v1.0.0 -m "Cola Eleitoral 1.0.0"
git push origin v1.0.0
# 3. Baixe os artefatos (app-release.aab e mapping.txt) quando o workflow terminar:
gh run list --workflow release.yml --limit 1
gh run download <id-da-execucao>
```

O workflow `Release` roda: (1) `scripts/check-store-metadata.sh --release` (ficha da loja, política
preenchida, HTML igual ao Markdown e página publicada igual ao arquivo), lint e testes unitários (JDK
21 para os testes e 17 para o build, como o CI); (2) confere tag, versão e changelog, decodifica a
chave, roda `bundleRelease` assinado, confere a assinatura e guarda AAB e `mapping.txt` por 90 dias;
(3) opcionalmente envia ao Play (seção 12). Também dá para disparar à mão em Actions > Release > Run
workflow, escolhendo a trilha `internal` ou `alpha` ou `nenhum`. Enquanto a política não estiver
publicada (seção 7), o passo (1) falha.

O `mapping.txt` do R8 também vai dentro do AAB (`BUNDLE-METADATA`), e o Play Console o usa para
desofuscar falhas automaticamente; o artefato serve de cópia.

## 9. Criar o app e fazer o primeiro upload

O primeiro AAB precisa ser enviado à mão: a API do Play não envia para um pacote que ainda não existe.

1. Play Console > Criar app: nome **Cola Eleitoral**, idioma padrão **Português (Brasil) – pt-BR**,
   tipo **App**, **Gratuito** (um app gratuito não pode virar pago depois). Aceite as declarações.
2. Teste e lançamento > Teste interno > Criar nova versão. Na primeira vez, confirme o Play App
   Signing com a chave gerada pelo Google (padrão).
3. Envie o `app-release.aab` do workflow. O primeiro upload registra sua chave de upload e torna o
   pacote `com.veronezzi.colaeleitoral` definitivo.
4. Nome da versão: `1.0.0 (1)`. Notas: o conteúdo de `changelogs/1.txt`.
5. Testadores: crie uma lista de e-mails, salve e compartilhe o link de participação.

## 10. Ficha da loja e configurações

**Presença na loja > Ficha principal da loja**: copie de `fastlane/metadata/android/pt-BR/`:

| Campo | Arquivo | Limite |
|---|---|---|
| Nome do app | `title.txt` | 30 |
| Descrição curta | `short_description.txt` | 80 |
| Descrição completa | `full_description.txt` (as primeiras linhas trazem o aviso de independência, o nome do desenvolvedor e os links oficiais do TSE, exigidos pela política de informações governamentais) | 4.000 |
| Ícone do app | `images/icon.png` (512 x 512, PNG 32 bits) | 1 MB |
| Gráfico de destaque | `images/featureGraphic.png` (1024 x 500, PNG 24 bits) | 15 MB |
| Capturas de tela do telefone | `images/phoneScreenshots/` (seção 16) | 2 a 8 |

Antes de enviar para revisão, confira se cada afirmação da descrição vale para a versão publicada.

**Configurações da loja**: categoria **Ferramentas** (não use "Notícias e revistas"); e-mail de
contato veronezzi14@gmail.com (o mesmo de `colaEleitoral.contactEmail` e da política); site
opcional (pode ficar vazio).

**Países**: Brasil. Eleitores no exterior votam só para Presidente e o TSE recusa consultas de fora do
Brasil; incluir outros países faz sentido só se a fonte de dados abertos funcionar para eles.

## 11. Conteúdo do app (Política > Conteúdo do app)

| Seção | Resposta |
|---|---|
| Política de privacidade | <https://veronezzi.github.io/cola-eleitoral/privacidade/> (seção 7) |
| Anúncios | **Não**, o app não contém anúncios |
| Acesso ao app | **Todos os recursos estão disponíveis sem restrições** (não há login) |
| Classificação do conteúdo | Questionário abaixo |
| Público-alvo e conteúdo | Faixa etária: **18 anos ou mais**. "A ficha pode atrair crianças sem querer?": **Não**. **Não** ative "Restringir o acesso de menores": eleitores de 16 e 17 anos também podem usar o app. (Marcar também 16–17 é uma alternativa válida e não aciona a política Famílias, que vale para menores de 13.) |
| Segurança dos dados | Ver [`data-safety.md`](data-safety.md): "nenhum dado coletado nem compartilhado" |
| Apps governamentais | **Não**, o app não é desenvolvido por um governo nem em nome de um |
| Recursos financeiros | **O app não oferece recursos financeiros** |
| Saúde | **O app não tem recursos de saúde** |
| Apps de notícias | **Não** é app de notícias |
| COVID-19 (rastreamento de contato e status) | **Não** |
| ID de publicidade | **Não** usa (o manifesto mesclado não tem `AD_ID`) |
| Declaração de permissões | Nenhuma permissão sensível (SMS, chamadas, localização, arquivos) |

O manifesto mesclado de release só tem as permissões listadas na seção 5 da política: `INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC`, `USE_FINGERPRINT`,
`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` e a permissão interna
`com.veronezzi.colaeleitoral.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. `FOREGROUND_SERVICE` e o
serviço em primeiro plano do WorkManager são removidos no `AndroidManifest.xml`, e
`scripts/check-merged-manifest.sh` (no CI) falha se aparecer outra permissão. Se o Play Console pedir
a declaração de serviço em primeiro plano, algo mudou numa dependência: corrija o manifesto em vez
de declarar.

**Questionário de classificação (IARC)**: e-mail de contato; categoria **utilitário, produtividade,
comunicação ou outros** (na interface em inglês, "Utility, Productivity, Communication, or Other"; não
escolha jogo nem notícias). Respostas:

| Tema | Resposta | Por quê |
|---|---|---|
| Violência, sangue, medo | Não | Só dados de candidaturas |
| Conteúdo sexual, nudez | Não | |
| Linguagem ofensiva | Não | Textos do app e dados oficiais do TSE |
| Drogas, álcool, tabaco | Não | |
| Jogos de azar ou simulação | Não | |
| Humor grosseiro | Não | |
| Interação entre usuários (chat, conteúdo gerado por usuários) | Não | Não há contas, chat nem publicação dentro do app; o compartilhamento da imagem usa o compartilhamento do Android |
| Compartilha a localização do usuário com outros usuários | Não | |
| Compras digitais | Não | |
| Navegador ou buscador da web | Não | Links abrem no navegador do aparelho |

Resultado esperado: Livre (ClassInd, no Brasil) e equivalentes nas outras autoridades.

## 12. Envio automático ao Play (opcional, desligado por padrão)

Só depois do primeiro upload manual (seção 9).

1. No Google Cloud, crie um projeto, ative a **Google Play Android Developer API** e crie uma conta de
   serviço sem papéis. Crie uma chave JSON (se a organização do Google Cloud bloquear chaves, use
   Workload Identity Federation; a ação aceita `serviceAccountJson` com um arquivo de credencial).
2. No Play Console > Usuários e permissões, convide o e-mail da conta de serviço só para este app,
   com permissão de **lançar em faixas de teste**. Não dê permissão de produção.
3. Guarde o JSON como segredo `PLAY_SERVICE_ACCOUNT_JSON`, de preferência no ambiente `google-play`
   (Settings > Environments), onde dá para exigir aprovação manual antes de cada envio.
4. Para enviar: dispare o workflow à mão escolhendo `internal` ou `alpha` (trilha padrão do teste
   fechado), ou defina a variável `PLAY_UPLOAD_TRACK` para enviar a cada tag. Com
   `PLAY_RELEASE_STATUS` vazio, a versão fica como **rascunho**: você revisa e lança no Play Console.
   Apps que ainda não foram publicados só aceitam rascunho.
5. As notas da versão vão de `changelogs/<versionCode>.txt` como `whatsnew-pt-BR`. A ação
   `r0adkll/upload-google-play` está fixada no commit da v1.1.5; ao atualizar, fixe o novo commit.

## 13. Teste fechado (conta pessoal: 12 testadores por 14 dias)

1. Teste e lançamento > Teste fechado > a faixa padrão (Alpha) > Gerenciar faixa. Países: Brasil.
2. Testadores: lista de e-mails ou um Grupo do Google. Convide **15 a 20 pessoas** para ter folga:
   contam só testadores que **continuam inscritos por 14 dias seguidos**; quem sai antes não conta.
3. Envie a versão (promova a do teste interno ou faça upload) e mande para revisão.
4. Peça que os testadores aceitem pelo link de participação, instalem pelo Play e usem o app no
   2º turno; deixe um canal de feedback (o e-mail de contato serve).
5. Depois dos 14 dias, peça acesso à produção no Painel. O formulário pergunta sobre o teste (como os
   testadores foram recrutados, o que mudou com o feedback), sobre o app e sobre a prontidão para
   produção. A análise costuma levar até 7 dias.

## 14. Relatório de pré-lançamento

Cada upload em faixa de teste roda o app em aparelhos do Google (Teste e lançamento > Teste >
Relatório de pré-lançamento). Esperado e aceitável:

- esses aparelhos ficam em data centers fora do Brasil: o TSE responde 403, e o app deve mostrar a
  mensagem de bloqueio ou a fonte de dados abertos, **sem travar**;
- as telas com escolhas aparecem pretas nas capturas por causa do `FLAG_SECURE`.

Corrija antes de seguir: falhas (crashes), ANRs, avisos de acessibilidade (rótulos, contraste, alvos
de toque menores que 48 dp) e de segurança. Não há login, então não é preciso informar credenciais.

## 15. Produção

1. Teste e lançamento > Produção > Criar nova versão (ou promova a do teste fechado). Países: Brasil.
2. Lançamento gradual (por exemplo, 20% e depois 100%) para acompanhar falhas em Android vitals.
3. Antes de enviar, passe pela lista da seção 17.
4. Evite mudar textos e imagens da ficha perto do dia da eleição e nunca mostre propaganda de
   candidato (Lei 9.504/97, art. 39, § 5º).

## 16. Capturas de tela

As capturas podem já estar em `fastlane/metadata/android/pt-BR/images/phoneScreenshots/`, geradas
pelo projeto com dados de exemplo. Nesse caso, confira com `scripts/check-store-metadata.sh` e revise
a neutralidade (item 4). Para fazer à mão, com o app final:

1. Instale o build de debug num emulador ou aparelho com Android recente e idioma pt-BR. Em telas com
   escolhas, desligue antes "Proteger telas com escolhas" nas configurações do app; o `FLAG_SECURE`
   deixa a captura preta. Religue depois.
2. Limpe a barra de status com o modo demonstração:
   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   adb exec-out screencap -p > fastlane/metadata/android/pt-BR/images/phoneScreenshots/1.png
   adb shell am broadcast -a com.android.systemui.demo -e command exit
   ```
3. Requisitos: 2 a 8 capturas, PNG ou JPEG, lados de 320 a 3.840 px, lado maior até 2 vezes o menor
   (um celular 1080 x 2400 passa do limite: use um emulador 1080 x 1920 ou recorte). Para tablets,
   `sevenInchScreenshots/` e `tenInchScreenshots/`. Rode `scripts/check-store-metadata.sh`.
4. **Neutralidade:** nenhuma captura pode destacar um candidato real. Prefira a cédula com caixas
   vazias, listas em ordem de número com vários candidatos, filtros, a cola sem nomes e a tela Sobre.
   Se precisar mostrar um detalhe, use uma eleição já encerrada. Sem textos promocionais nas imagens.
5. Sugestão de sequência: aviso inicial de independência; escolha do local; tela inicial com os
   cargos; lista de candidatos; Minha cola; cola pronta para imprimir; tela Sobre ou privacidade.

## 17. Lista final antes de publicar

- [ ] Nome "Cola Eleitoral" pesquisado no Google Play e no INPI (<https://busca.inpi.gov.br/>). O
      nome exibido pode mudar depois; o pacote `com.veronezzi.colaeleitoral`, não.
- [ ] Validação num aparelho no Brasil (seção 3) feita em dados móveis e em Wi-Fi.
- [ ] Conta pessoal verificada (identidade e aparelho), com nome de desenvolvedor **veronezzi**.
- [ ] Política publicada: `curl -fsSI https://veronezzi.github.io/cola-eleitoral/privacidade/`
      responde 200 e a página é igual a `docs/privacidade/index.html` (seção 7).
- [ ] `scripts/check-store-metadata.sh --release` sem erros: ficha, política preenchida, HTML gerado
      do Markdown atual e página publicada igual.
- [ ] O AAB foi gerado depois da última mudança em `docs/privacidade.md`: o texto do app (Sobre >
      Política de privacidade) é o mesmo da página publicada.
- [ ] Mesmo e-mail (veronezzi14@gmail.com) em `gradle.properties`, na política, na ficha e no
      perfil de desenvolvedor; mesmo nome (veronezzi) no Sobre, na política e no Play Console.
- [ ] Manifesto mesclado só com as permissões da seção 11 (`scripts/check-merged-manifest.sh` no CI).
- [ ] Ficha completa: título, descrições, ícone, destaque e 2 a 8 capturas neutras; categoria
      Ferramentas; e-mail de contato.
- [ ] Conteúdo do app sem pendências (seção 11), inclusive Segurança dos dados ("nenhum dado
      coletado nem compartilhado") e Apps governamentais ("não").
- [ ] Teste fechado com 12 ou mais testadores por 14 dias seguidos e acesso à produção aprovado.
- [ ] `changelogs/<versionCode>.txt`, tag `v<versionName>`, AAB assinado pelo workflow e
      `mapping.txt` guardado.
- [ ] Lançamento gradual e acompanhamento no Android vitals.

## Histórico do nome

O nome de trabalho era "Meu Santinho". Antes de qualquer upload ele virou **Cola Eleitoral**, com o
pacote `com.veronezzi.colaeleitoral` no lugar de `com.veronezzi.meusantinho`, por dois motivos: já
existe um projeto sem relação com este, o "Meu Santinho 2026"
(<https://github.com/Meu-Santinho/meu-santinho>), e "santinho" é o nome popular do material de
campanha dos candidatos, o que podia confundir um app neutro com propaganda. No app, a tela das
escolhas se chama "Minha cola"; "santinho" só aparece quando o assunto é o material de campanha.
