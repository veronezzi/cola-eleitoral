# Publicação no Google Play, do zero à produção

Guia escrito em 4 de outubro de 2026 (dia do 1º turno). Regras do Google Play mudam: confira os links
oficiais antes de cada etapa. Documentos relacionados: [`ARCHITECTURE.md`](ARCHITECTURE.md) (seções 5
a 7), [`data-safety.md`](data-safety.md) (respostas do formulário Segurança dos dados) e a política
de privacidade ([`privacidade.md`](privacidade.md) e [`privacidade/index.html`](privacidade/index.html)).

## 0. O que já está pronto e o que falta

| Item | Situação |
|---|---|
| Ficha da loja em pt-BR (`fastlane/metadata/android/pt-BR/`): título, descrições curta e completa, novidades da versão 1 | Pronto. Validar com `scripts/check-store-metadata.sh` |
| Ícone 512 px e imagem de destaque 1024 x 500 (`.../images/`), gerados do ícone adaptativo por `scripts/render-store-graphics.py` | Pronto |
| Política de privacidade (Markdown e página HTML para o GitHub Pages) | Texto pronto; **faltam nome e e-mail** (`[PREENCHER: ...]`) |
| Trava de release: o build de release falha enquanto a URL da política ou o e-mail forem `example.com` | Pronto (`app/build.gradle.kts`, tarefa `checkReleasePublishingProperties`) |
| Workflow de release (`.github/workflows/release.yml`): lint, testes, AAB assinado, artefatos, envio opcional ao Play | Pronto; faltam os segredos |
| Decisões Q2, Q3 e Q5 (seção 1) | **Você decide** |
| Validação num aparelho no Brasil (seção 3) | **Obrigatória antes de publicar** |
| Capturas de tela (seção 16) | Depois, com o app final |
| Conta no Play Console, chave de upload, segredos no GitHub | Seções 4 a 6 |

## 1. Decisões que só você pode tomar

### Q2. Conta do Play Console: pessoal ou de organização

| | Conta pessoal | Conta de organização |
|---|---|---|
| Custo | US$ 25, uma vez só (as duas) | US$ 25 |
| Exige | Ser maior de 18 anos; documento e cartão no seu nome; verificar e-mail e telefone; **provar acesso a um aparelho Android** pelo app Play Console | Número **D-U-N-S** da empresa (gratuito na Dun & Bradstreet; pode levar semanas se a empresa ainda não tiver), site da organização, e-mail e telefone verificados |
| Aparece na loja | Nome legal, país e e-mail de contato | Nome legal, endereço, e-mail e telefone |
| Teste antes da produção | Contas criadas depois de 13/11/2023: **teste fechado com pelo menos 12 testadores inscritos por 14 dias seguidos** antes de pedir acesso à produção | Não tem essa exigência |

Fontes: <https://support.google.com/googleplay/android-developer/answer/13628312>,
<https://support.google.com/googleplay/android-developer/answer/6112435>,
<https://support.google.com/googleplay/android-developer/answer/14151465>.

Recomendação: se você já tem CNPJ (inclusive MEI) com D-U-N-S, a conta de organização evita os 14
dias de teste fechado. Sem isso, conta pessoal e o cronograma da seção 2. A política de deturpação do
Google pede transparência extra sobre quem desenvolve apps com tema político: o nome mostrado na loja
precisa ser o seu nome real ou o da sua organização.

### Q3. Política de privacidade, e-mail e responsável

Você precisa de: (a) a URL pública da política, (b) um e-mail de contato e (c) o nome do responsável.
Passos na seção 7.

### Q5. Nome do app e `applicationId`

- **Conflito de nome:** já existe o projeto web "Meu Santinho 2026"
  (<https://github.com/Meu-Santinho/meu-santinho>), e "santinho" é o nome popular do material de
  campanha. Antes do primeiro upload, pesquise o nome no Google Play e no INPI
  (<https://busca.inpi.gov.br/>) e decida se mantém "Meu Santinho" ou troca.
- **`applicationId` é permanente:** depois do primeiro upload, `com.veronezzi.meusantinho` não pode
  mais ser trocado nem reaproveitado, nem se o app for apagado. O nome exibido pode mudar depois; o
  pacote, não. Se for trocar, troque antes de gerar o primeiro AAB.
- Se trocar o nome, atualize: `app_name` em `res/values/strings.xml`, `title.txt` e as descrições, a
  política (Markdown e HTML), o README e rode `python3 scripts/render-store-graphics.py` para refazer
  a imagem de destaque (ela usa o `app_name`).

## 2. Cronograma realista

Hoje é 04/10/2026. O 2º turno é em **25/10/2026**, daqui a 21 dias. As próximas eleições são as
municipais de **01/10/2028** (1º turno) e **29/10/2028** (2º turno).

| Data aproximada | Conta pessoal nova | Conta de organização |
|---|---|---|
| 04 a 07/10 | Criar a conta, verificar identidade e o aparelho Android (de horas a dias). Validação no Brasil (seção 3) | Pedir o D-U-N-S, se ainda não tiver (até 30 dias) |
| 07 a 10/10 | Primeiro upload no teste interno (seção 9): até 100 testadores por e-mail, disponível em minutos | Igual, assim que a conta estiver verificada |
| até 10/10 | Enviar o teste fechado para revisão (pode levar alguns dias) | Pode ir direto para produção (revisão de até 7 dias ou mais) |
| ~10/10 a ~24/10 | 14 dias com 12 ou mais testadores inscritos. **Os testadores usam o app no 2º turno** | Produção antes de 25/10 só se D-U-N-S, verificação e revisão andarem rápido |
| ~25/10 a ~03/11 | Pedir acesso à produção; a análise costuma levar até 7 dias | |
| novembro/2026 | Produção | |

Conclusão: com conta pessoal nova, **produção antes do 2º turno é improvável**. A meta realista é
teste interno e teste fechado a tempo de 25/10 e produção em novembro, pronta para 2028.

Até 2028:
- **Nível de API:** todo ano, até 31 de agosto, apps novos e atualizações precisam mirar a API do ano
  anterior (<https://support.google.com/googleplay/android-developer/answer/11926878>). Planeje subir
  o `targetSdk` em 2027 e em 2028 e testar as mudanças de comportamento.
- **Dados de 2028:** o registro de candidaturas termina em 15/08/2028 (Lei 9.504/97, art. 11). Quando
  o TSE publicar a eleição municipal de 2028, refaça a validação da seção 3 com um município e
  publique uma atualização até o começo de setembro.
- Mantenha o app atualizado entre as eleições (dependências, política, Segurança dos dados).

## 3. Validação obrigatória num aparelho no Brasil

Antes de qualquer publicação (ARCHITECTURE.md, seção 7.3). Use um APK de debug instalado por adb
(`./gradlew installDebug`); a verificação de desenvolvedor não afeta instalações por adb. Faça em
**dados móveis e em Wi-Fi residencial**, anotando status HTTP, `Content-Type`, `Content-Encoding`,
`Cache-Control` e tamanho, **sem salvar corpos de resposta com dados pessoais**. Não faça teste de
carga nem repita chamadas em laço contra o TSE.

- [ ] 1. `eleicao/ordinarias` responde 200 com JSON (o ponto mais importante). Se der 403, anote e
      siga a decisão Q1 (fonte de dados abertos), **sem testar variações de cabeçalho**.
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
- [ ] 8. Com VPN ligada: 403 e a mensagem de bloqueio no app.
- [ ] 9. Links "Ver no site do TSE" (`txLink` e o formato `{uf}/{uf}/...`) abrem no Chrome do Android.
- [ ] 10. Android 8.0 (API 26): gerar a chave AES-GCM no Keystore, salvar escolhas, fechar e reabrir
      o app, e as escolhas continuam legíveis.

Confira também no aparelho: a cola impressa (PDF) e a imagem compartilhada; o lembrete (Android 13+
pede a permissão de notificação só ao ligar); o bloqueio do app; TalkBack e fonte em 200%.

## 4. Conta no Play Console

1. Acesse <https://play.google.com/console/signup> com a conta Google que será a dona do app (use uma
   conta própria do projeto, com verificação em duas etapas).
2. Escolha o tipo de conta (Q2), aceite o Contrato de Distribuição e pague a taxa.
3. Complete a verificação de identidade, de e-mail e de telefone. Conta pessoal: instale o app Play
   Console num aparelho Android e conclua a verificação do aparelho.
4. **Verificação de desenvolvedor Android:** obrigatória no Brasil desde 30/09/2026 para instalações
   em aparelhos certificados. Apps do Google Play são registrados automaticamente na maioria dos
   casos; depois do primeiro upload, confira na página inicial do Play Console se
   `com.veronezzi.meusantinho` aparece registrado
   (<https://developer.android.com/developer-verification>,
   <https://support.google.com/googleplay/android-developer/answer/17134731>).

## 5. Chave de upload e Play App Signing

O Google guarda a chave que assina o app para os usuários (Play App Signing, padrão em apps novos). Você
assina os uploads com uma **chave de upload** que fica com você. Gere uma vez, fora do repositório:

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Nome do responsavel, C=BR"
```

- O `keytool` pede a senha. Em PKCS12, a senha do keystore e a da chave são a mesma: use o mesmo
  valor em `MEU_SANTINHO_KEYSTORE_PASSWORD` e `MEU_SANTINHO_KEY_PASSWORD`.
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
export MEU_SANTINHO_KEYSTORE="$HOME/chaves/upload-keystore.jks"
export MEU_SANTINHO_KEY_ALIAS=upload
read -rs MEU_SANTINHO_KEYSTORE_PASSWORD && export MEU_SANTINHO_KEYSTORE_PASSWORD
export MEU_SANTINHO_KEY_PASSWORD="$MEU_SANTINHO_KEYSTORE_PASSWORD"
./gradlew bundleRelease
```

Sem nenhuma dessas variáveis o AAB sai sem assinatura (serve só para conferir o build); com só algumas,
o build falha dizendo quais faltam.

## 6. Segredos e variáveis do GitHub

Em Settings > Secrets and variables > Actions. Com o `gh`, `gh secret set NOME` pede o valor sem
mostrá-lo e sem deixá-lo no histórico do shell.

| Nome | Tipo | Valor |
|---|---|---|
| `MEU_SANTINHO_KEYSTORE_BASE64` | segredo | O keystore em base64: `base64 -w 0 upload-keystore.jks \| gh secret set MEU_SANTINHO_KEYSTORE_BASE64` (no macOS: `base64 -i upload-keystore.jks \| gh secret set ...`) |
| `MEU_SANTINHO_KEYSTORE_PASSWORD` | segredo | Senha do keystore (`gh secret set MEU_SANTINHO_KEYSTORE_PASSWORD`) |
| `MEU_SANTINHO_KEY_ALIAS` | segredo | `upload` |
| `MEU_SANTINHO_KEY_PASSWORD` | segredo | A mesma senha (PKCS12) |
| `MEU_SANTINHO_PRIVACY_POLICY_URL` | variável (opcional) | URL pública da política; substitui a de `gradle.properties` |
| `MEU_SANTINHO_CONTACT_EMAIL` | variável (opcional) | E-mail de contato; substitui o de `gradle.properties` |
| `PLAY_SERVICE_ACCOUNT_JSON` | segredo (opcional) | JSON da conta de serviço (seção 12). De preferência como segredo do ambiente `google-play` |
| `PLAY_UPLOAD_TRACK` | variável (opcional) | `internal` ou `alpha`: envia ao Play em cada tag. Vazia = não envia |
| `PLAY_RELEASE_STATUS` | variável (opcional) | `draft` (padrão: a versão fica como rascunho no Play Console) ou `completed` |

O workflow nunca imprime segredos: só os nomes dos que faltam. A chave é decodificada em
`$RUNNER_TEMP` com permissão 600 e apagada no fim do job.

## 7. Política de privacidade publicada (Q3)

1. Preencha os campos `[PREENCHER: ...]` em `docs/privacidade.md` e `docs/privacidade/index.html`
   (nome do responsável, e-mail, endereço da página) e apague o aviso "Antes de publicar". O texto do
   app (Sobre > Política de privacidade) tem de dizer o mesmo.
2. Publique pelo GitHub Pages: Settings > Pages > Deploy from a branch > `main`, pasta `/docs`. O
   `docs/_config.yml` publica só a política, em `https://<usuario>.github.io/<repositorio>/privacidade/`.
   A URL precisa ser pública, sem login, sem bloqueio geográfico e não pode ser PDF. O GitHub Pages de
   repositório privado exige plano pago; nesse caso, use um repositório público só para a página ou
   outra hospedagem estática.
3. Ponha os valores reais em `gradle.properties` (não são segredos; aparecem no app):
   ```properties
   meuSantinho.privacyPolicyUrl=https://<usuario>.github.io/<repositorio>/privacidade/
   meuSantinho.contactEmail=<o mesmo e-mail da política e da ficha>
   ```
   ou defina as variáveis `MEU_SANTINHO_PRIVACY_POLICY_URL` e `MEU_SANTINHO_CONTACT_EMAIL` (seção 6).
4. Confira: `scripts/check-store-metadata.sh --release` não pode ter erros. O `bundleRelease` falha
   enquanto os valores forem `example.com` (também exige `https://` e um e-mail válido).

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
git tag -a v1.0.0 -m "Meu Santinho 1.0.0"
git push origin v1.0.0
# 3. Baixe os artefatos (app-release.aab e mapping.txt) quando o workflow terminar:
gh run list --workflow release.yml --limit 1
gh run download <id-da-execucao>
```

O workflow `Release` roda: (1) `scripts/check-store-metadata.sh --release`, lint e testes unitários
(JDK 21 para os testes e 17 para o build, como o CI); (2) confere tag, versão e changelog, decodifica a
chave, roda `bundleRelease` assinado, confere a assinatura e guarda AAB e `mapping.txt` por 90 dias;
(3) opcionalmente envia ao Play (seção 12). Também dá para disparar à mão em Actions > Release > Run
workflow, escolhendo a trilha `internal` ou `alpha` ou `nenhum`.

O `mapping.txt` do R8 também vai dentro do AAB (`BUNDLE-METADATA`), e o Play Console o usa para
desofuscar falhas automaticamente; o artefato serve de cópia.

## 9. Criar o app e fazer o primeiro upload

O primeiro AAB precisa ser enviado à mão: a API do Play não envia para um pacote que ainda não existe.

1. Play Console > Criar app: nome "Meu Santinho" (ou o escolhido em Q5), idioma padrão
   **Português (Brasil) – pt-BR**, tipo **App**, **Gratuito** (um app gratuito não pode virar pago
   depois). Aceite as declarações.
2. Teste e lançamento > Teste interno > Criar nova versão. Na primeira vez, confirme o Play App
   Signing com a chave gerada pelo Google (padrão).
3. Envie o `app-release.aab` do workflow. O primeiro upload registra sua chave de upload.
4. Nome da versão: `1.0.0 (1)`. Notas: o conteúdo de `changelogs/1.txt`.
5. Testadores: crie uma lista de e-mails, salve e compartilhe o link de participação.

## 10. Ficha da loja e configurações

**Presença na loja > Ficha principal da loja**: copie de `fastlane/metadata/android/pt-BR/`:

| Campo | Arquivo | Limite |
|---|---|---|
| Nome do app | `title.txt` | 30 |
| Descrição curta | `short_description.txt` | 80 |
| Descrição completa | `full_description.txt` (as primeiras linhas trazem o aviso de independência e os links oficiais do TSE, exigidos pela política de informações governamentais) | 4.000 |
| Ícone do app | `images/icon.png` (512 x 512, PNG 32 bits) | 1 MB |
| Gráfico de destaque | `images/featureGraphic.png` (1024 x 500, PNG 24 bits) | 15 MB |
| Capturas de tela do telefone | `images/phoneScreenshots/` (seção 16) | 2 a 8 |

Antes de enviar para revisão, confira se cada afirmação da descrição vale para a versão publicada.

**Configurações da loja**: categoria **Ferramentas** (não use "Notícias e revistas"); e-mail de
contato = o mesmo de `meuSantinho.contactEmail` e da política; site opcional (a página do projeto).

**Países**: Brasil. Eleitores no exterior votam só para Presidente e o TSE recusa consultas de fora do
Brasil; incluir outros países faz sentido só se a fonte de dados abertos funcionar para eles.

## 11. Conteúdo do app (Política > Conteúdo do app)

| Seção | Resposta |
|---|---|
| Política de privacidade | A URL publicada (seção 7) |
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

Se o Play Console pedir a declaração de **serviço em primeiro plano**, é porque o manifesto mesclado
tem `FOREGROUND_SERVICE` (vem do WorkManager). O app não usa serviço em primeiro plano: remova a
permissão com `tools:node="remove"` no `AndroidManifest.xml` (ARCHITECTURE.md, seção 5.5) em vez de
declarar.

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
3. Antes de enviar, a lista final: ficha completa com pelo menos 2 capturas; Conteúdo do app sem
   pendências; política publicada e igual à do app; e-mail de contato igual nos três lugares
   (`gradle.properties`, política, ficha); validação da seção 3 feita; `scripts/check-store-metadata.sh
   --release` sem erros.
4. Evite mudar textos e imagens da ficha perto do dia da eleição e nunca mostre propaganda de
   candidato (Lei 9.504/97, art. 39, § 5º).

## 16. Capturas de tela (para depois)

Fora do escopo desta etapa: precisam do app final. Como fazer:

1. Instale o build de debug num emulador ou aparelho com Android recente e idioma pt-BR. Em telas com
   escolhas, desligue antes "Proteger telas" nas configurações do app; o `FLAG_SECURE` deixa a captura
   preta. Religue depois.
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
4. **Neutralidade:** nenhuma captura pode destacar um candidato. Prefira a cédula com caixas vazias,
   listas em ordem de número com vários candidatos, filtros, a cola sem nomes e a tela Sobre. Se
   precisar mostrar um detalhe, use uma eleição já encerrada. Sem textos promocionais nas imagens.
5. Sugestão de sequência: aviso inicial de independência; escolha do local; cédula com os cargos;
   lista de candidatos; cola pronta para imprimir; tela de privacidade.
