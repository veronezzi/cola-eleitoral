# Política de privacidade do app Cola Eleitoral

**Versão 1. Última atualização: 5 de outubro de 2026.**

Esta política explica quais dados o aplicativo Cola Eleitoral, para Android, trata, onde eles ficam e
quais são os seus direitos pela Lei Geral de Proteção de Dados Pessoais (LGPD, Lei 13.709/2018). O
mesmo texto aparece dentro do app, em Sobre > Política de privacidade, e no endereço
<https://veronezzi.github.io/cola-eleitoral/privacidade/>.

## Resumo

- **O desenvolvedor não coleta, não recebe e não compartilha dados pessoais.** O app não tem conta,
  servidor próprio, anúncios, analytics, rastreamento nem SDKs de terceiros.
- **Suas escolhas de candidatos ficam só no seu aparelho**, cifradas e fora do backup na nuvem.
  Ninguém além de você tem acesso a elas, nem o desenvolvedor.
- **Para mostrar as candidaturas, o app consulta diretamente os sistemas públicos do Tribunal
  Superior Eleitoral (TSE).** Como em qualquer acesso ao site do TSE, o TSE recebe o endereço IP da
  sua conexão e o que foi consultado: a UF ou o município, o cargo e a candidatura aberta. Suas
  escolhas nunca são enviadas.
- O app Cola Eleitoral é independente: **não tem vínculo** com o TSE, a Justiça Eleitoral, órgãos de
  governo, partidos ou candidatos.

## 1. Quem é o responsável

- **Desenvolvedor e responsável pelo app:** veronezzi, desenvolvedor independente, sem vínculo com
  partidos, candidatos ou governo.
- **Contato para privacidade e para exercer seus direitos:** veronezzi14@gmail.com

O desenvolvedor é controlador apenas dos dados que você mesmo enviar a ele, por exemplo ao escrever
para esse e-mail. Como agente de tratamento de pequeno porte, o desenvolvedor não indicou
encarregado; o canal de comunicação com titulares e com a Autoridade Nacional de Proteção de Dados
(ANPD) é esse e-mail (LGPD, art. 41; Resolução CD/ANPD nº 2/2022, art. 11).

## 2. Dados guardados no seu aparelho

Estes dados ficam só no armazenamento privado do app e não são enviados ao desenvolvedor nem a
ninguém.

- **Suas escolhas de candidatos (tela Minha cola):** servem para montar a cola na ordem da urna.
  Ficam num arquivo cifrado com AES-256-GCM, com a chave guardada no Android Keystore, que não pode
  ser exportada, e ficam fora do backup e da transferência entre aparelhos.
- **Local de votação (UF e, se for o caso, município):** serve para buscar as candidaturas do seu
  local. Fica nas preferências do app, fora do backup. O código do local vai ao TSE em cada
  consulta (seção 3).
- **Preferências** (aviso inicial aceito, lembrete, bloqueio do app, proteção de tela): fazem o app
  funcionar como você configurou. Ficam nas preferências do app, fora do backup.
- **Cópia dos dados públicos do TSE:** as listas de eleições, municípios, cargos e candidaturas,
  para o app funcionar sem internet. Fica no cache do app e é apagada automaticamente (seção 8).
- **Detalhes de candidaturas, vices, suplentes e fotos:** ficam só na memória do aparelho enquanto
  o app está aberto e nunca são gravados no armazenamento. Assim, os arquivos do app não mostram
  quais candidaturas você abriu.
- **Cola em PDF ou em imagem:** criada só quando você pede, para imprimir ou compartilhar. A imagem
  é um arquivo temporário, apagado na próxima abertura do app; o PDF vai para o destino que você
  escolher no diálogo de impressão.

Quando o sistema DivulgaCandContas recusa a consulta, o app usa os arquivos de dados abertos do TSE
(seção 3). Esses arquivos trazem dados pessoais dos candidatos que o app não usa, como CPF, título
de eleitor, e-mail e data de nascimento. O app lê o arquivo enquanto ele é baixado e guarda só o que
as listas mostram (número, nomes, partido, coligação ou federação, cargo e situação de cada
candidatura), além da identificação da versão do arquivo (ETag). O arquivo original nunca é gravado
no aparelho.

Opinião política é dado pessoal sensível (LGPD, art. 5º, II). Por isso as escolhas nunca aparecem
em notificações, registros (logs), backups ou na área de transferência, e nunca saem do aparelho, a
não ser que você mesmo imprima ou compartilhe a cola.

## 3. O que sai do aparelho: consultas ao TSE

O app só se conecta a dois servidores do TSE, no domínio `tse.jus.br`, e bloqueia qualquer outro
endereço. Não há servidor do desenvolvedor no caminho.

- **`divulgacandcontas.tse.jus.br`**, o sistema DivulgaCandContas: eleições, municípios, cargos,
  listas de candidaturas, detalhes e fotos.
- **`cdn.tse.jus.br`**, o Portal de Dados Abertos: usado só nas eleições gerais e só quando o
  DivulgaCandContas recusa a consulta. O app baixa o arquivo nacional de candidaturas da eleição,
  que é o mesmo para todo mundo.

As consultas acontecem quando você abre uma tela e, depois que você escolhe o local de votação, para
baixar com antecedência as listas da sua cédula (assim a cola funciona sem internet no dia da
eleição). Em cada consulta, o TSE e os provedores de infraestrutura que ele usa (por exemplo, a rede
de distribuição de conteúdo do site) recebem:

- o **endereço IP** da sua conexão, como em qualquer acesso à internet;
- **o que foi consultado**. No DivulgaCandContas: a eleição, a UF ou o município e o cargo de cada
  lista e, quando você abre uma candidatura ou o app mostra a foto dela, o identificador dessa
  candidatura. Assim, o TSE pode saber quais candidaturas foram consultadas a partir do seu IP, como
  aconteceria no site oficial. Nos dados abertos: só o arquivo da eleição e, se o app já tiver uma
  cópia, a versão dela (ETag), para não baixar de novo o que não mudou;
- o cabeçalho **`User-Agent`**, com o nome e a versão do app, a versão do Android e o endereço desta
  política, por exemplo
  `ColaEleitoral/1.0.0 (Android 16; +https://veronezzi.github.io/cola-eleitoral/privacidade/)`. Ele
  não identifica você nem o aparelho.

O local enviado é o que você escolheu na tela de local de votação (pode ser qualquer UF ou
município), e não a localização do aparelho: o app não pede permissão de localização. O app **não
envia** suas escolhas, seu nome, seu título de eleitor nem identificadores do aparelho, como o ID de
publicidade.

O TSE trata esses dados de acesso como controlador dos próprios sistemas, conforme as regras da LGPD
para o poder público e a política de privacidade publicada no portal do TSE
(<https://www.tse.jus.br>). O desenvolvedor não tem acesso a eles.

## 4. O que o app não faz

- Não tem conta, login ou cadastro.
- Não exibe anúncios e não usa o ID de publicidade.
- Não usa analytics, SDK de relatório de falhas nem rastreamento de qualquer tipo.
- Não se conecta a nenhum servidor além dos dois do TSE da seção 3: nem do desenvolvedor, nem do
  Google, nem de outras empresas.
- Não consulta o TSE em segundo plano. O lembrete do dia da eleição é preparado no próprio aparelho,
  sem internet.
- Não vende, não aluga e não compartilha dados.
- Não recomenda, não destaca e não ordena candidatos por popularidade, e não junta as escolhas de
  ninguém (não há enquete, ranking nem "mais buscados").
- Não abre páginas dentro do app: os links para o site do TSE abrem no seu navegador.

## 5. Permissões do Android

Só a permissão de notificações aparece num pedido do Android, e só quando você liga o lembrete. As
outras são concedidas na instalação e não dão acesso a dados pessoais.

- **Acesso à internet** (`INTERNET`): consultar o TSE.
- **Ver o estado da rede** (`ACCESS_NETWORK_STATE`): saber se há conexão e perceber quando você
  troca de rede, para não tentar consultas sem internet.
- **Notificações** (`POST_NOTIFICATIONS`, no Android 13 ou mais novo): só se você ligar o lembrete
  do dia da eleição. O texto da notificação é genérico e nunca cita candidatos ou números.
- **Biometria** (`USE_BIOMETRIC` e, no Android 8, `USE_FINGERPRINT`): só se você ligar o bloqueio
  do app. Quem confere a digital, o rosto ou a senha é o Android; o app nunca recebe dados
  biométricos.
- **Executar na inicialização** (`RECEIVE_BOOT_COMPLETED`): o agendador do Android (WorkManager)
  usa esta permissão para manter o lembrete agendado depois que o aparelho reinicia.
- **Manter o aparelho ativo** (`WAKE_LOCK`): o mesmo agendador a usa por alguns instantes, para o
  lembrete aparecer na hora certa mesmo com a tela desligada.
- **Permissão interna do próprio app**
  (`com.veronezzi.colaeleitoral.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`): criada pela biblioteca
  AndroidX e restrita ao próprio app. Impede que outros apps enviem mensagens às partes internas do
  app. Não dá acesso a nenhum dado nem a recursos do aparelho.

O app não pede acesso a localização, câmera, microfone, contatos, fotos e arquivos, telefone ou SMS.

## 6. Bases legais (LGPD)

- **Escolhas, local de votação e preferências guardados no seu aparelho:** tratamento feito por
  você, no seu aparelho, para fins exclusivamente particulares e não econômicos (LGPD, art. 4º, I).
  O desenvolvedor não tem acesso a esses dados.
- **Consultas ao TSE (IP e parâmetros da consulta):** feitas a seu pedido, para obter informação
  pública. O tratamento dos dados de acesso é do TSE, como controlador dos próprios sistemas
  (seção 3).
- **Exibição dos dados públicos de candidaturas:** dados de acesso público publicados pelo TSE para
  informar os eleitores, usados para a mesma finalidade, sem alteração e sem dados pessoais
  desnecessários dos candidatos, como CPF, título de eleitor, data de nascimento ou e-mail (LGPD,
  art. 7º, §§ 3º e 7º).
- **Mensagens que você enviar ao desenvolvedor:** legítimo interesse, para responder (art. 7º, IX),
  e cumprimento de obrigação legal quando o pedido for sobre os seus direitos (art. 7º, II).

## 7. Seus direitos

A LGPD (art. 18) garante: confirmação da existência de tratamento; acesso aos dados; correção de
dados incompletos, inexatos ou desatualizados; anonimização, bloqueio ou eliminação de dados
desnecessários, excessivos ou tratados em desconformidade com a lei; portabilidade; eliminação dos
dados tratados com consentimento; informação sobre com quem os dados foram compartilhados;
informação sobre a possibilidade de não dar consentimento e suas consequências; e revogação do
consentimento.

Como os dados do app ficam no seu aparelho, você exerce a maior parte desses direitos no próprio app:

- **ver e corrigir**: na tela Minha cola, onde você troca ou remove cada escolha;
- **apagar tudo**: em Configurações > Apagar meus dados, que apaga as escolhas e a chave de cifra, o
  local, as preferências, o lembrete agendado e os dados baixados, ou desinstalando o app;
- **apagar só os dados públicos baixados**: em Configurações > Limpar dados baixados, que mantém
  suas escolhas;
- **levar com você**: imprimindo a cola, salvando em PDF ou compartilhando a imagem.

Para os dados que você enviou ao desenvolvedor por e-mail, escreva para o contato da seção 1. A
resposta sai em até 15 dias (LGPD, art. 19, II). Para os dados de acesso guardados pelo TSE, use os
canais de privacidade do TSE. Você também pode reclamar à ANPD: <https://www.gov.br/anpd>.

## 8. Por quanto tempo os dados ficam guardados

- **Escolhas:** até você trocar, remover ou apagar, ou desinstalar o app. Se a chave do Android
  Keystore deixar de valer de vez (por exemplo, depois de uma restauração do sistema), as escolhas
  ficam ilegíveis e são descartadas, com um aviso. Uma falha passageira não apaga nada: o app avisa
  e tenta ler de novo.
- **Local e preferências:** até você mudar ou apagar, ou desinstalar o app.
- **Cópia dos dados públicos do TSE:** atualizada de tempos em tempos enquanto você usa o app. Os
  dados de eleições que você não abre há mais de 60 dias são apagados automaticamente quando o app
  é aberto. Configurações > Limpar dados baixados apaga tudo na hora, e o Android também pode apagar
  esse cache quando falta espaço.
- **Detalhes, vices, suplentes e fotos:** só na memória, enquanto o app está aberto.
- **Imagem da cola compartilhada:** apagada na próxima abertura do app. As cópias enviadas a outros
  apps ficam sob o controle desses apps.
- **Mensagens enviadas ao desenvolvedor:** pelo tempo necessário para responder e até 12 meses
  depois, salvo obrigação legal ou defesa de direitos.
- **Dados de acesso no TSE:** pelo prazo definido pelo TSE.

## 9. Crianças e adolescentes

O app é feito para eleitores e não é direcionado a crianças. Ele não coleta dados de ninguém,
inclusive de crianças e adolescentes. O voto é facultativo para quem tem 16 ou 17 anos (Constituição,
art. 14, § 1º, II, "c"), e esses eleitores podem usar o app da mesma forma.

## 10. Segurança

- Escolhas cifradas com AES-256-GCM, com chave gerada e guardada no Android Keystore, que não pode
  ser exportada.
- Detalhes de candidaturas e fotos só na memória, para que os arquivos do app não revelem quais
  candidaturas você abriu.
- Backup na nuvem e transferência entre aparelhos desligados para todos os dados do app.
- Conexões só por HTTPS, confiando apenas nas autoridades certificadoras do sistema, e só com os
  dois servidores do TSE da seção 3.
- Nenhum registro (log) com escolhas ou com respostas do TSE.
- Telas com escolhas protegidas contra captura de tela e contra a prévia na lista de apps recentes
  (opção ligada por padrão).
- Bloqueio opcional do app com biometria ou com o bloqueio de tela do aparelho.

Nenhuma medida elimina todos os riscos. Quem tiver acesso ao seu aparelho desbloqueado pode ver as
escolhas: use o bloqueio do app e compartilhe a imagem da cola só com quem você confia. A cola em
papel também mostra seus votos; guarde-a com cuidado.

## 11. Serviços de terceiros que você pode usar junto com o app

- **Google Play**: a instalação e as atualizações passam pelo Google Play, que trata dados conforme
  a política de privacidade do Google. Se você permitir o envio de diagnósticos ao Google, o
  desenvolvedor pode ver no Play Console estatísticas agregadas e relatórios de falhas que não
  identificam você e não contêm suas escolhas.
- **Impressão e compartilhamento**: ao imprimir, salvar em PDF ou compartilhar a cola, o conteúdo
  vai para o serviço de impressão ou o app que você escolher, sob as regras desse serviço.
- **Navegador**: os links para o site do TSE abrem no navegador do aparelho, sob as regras do site e
  do navegador.

## 12. Mudanças nesta política

Se esta política mudar, a nova versão será publicada neste mesmo endereço, com nova data e número
de versão, e o texto dentro do app será atualizado na versão seguinte do app. Mudanças que alterem o
tratamento de dados (por exemplo, uma nova conexão de rede) serão descritas nas novidades da
atualização no Google Play antes de valerem.

## 13. Contato

Dúvidas, pedidos e reclamações: veronezzi14@gmail.com (desenvolvedor veronezzi).

Fonte dos dados de candidaturas: Tribunal Superior Eleitoral (TSE), dados públicos, licença CC BY.
