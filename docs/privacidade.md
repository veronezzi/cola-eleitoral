# Política de privacidade do Meu Santinho

**Versão 1. Última atualização: 4 de outubro de 2026.**

Esta política explica quais dados o aplicativo Meu Santinho (Android) trata, onde eles ficam e
quais são os seus direitos pela Lei Geral de Proteção de Dados Pessoais (LGPD, Lei 13.709/2018).
A versão publicada fica em <https://[PREENCHER: endereço público desta página]> e o mesmo texto
aparece dentro do app, em Sobre > Política de privacidade.

> **Antes de publicar**, troque todos os campos `[PREENCHER: ...]`. O e-mail tem de ser o mesmo da
> propriedade `colaEleitoral.contactEmail` (gradle.properties) e do contato da ficha no Google Play.
> Apague este aviso depois. Veja `docs/PUBLICACAO.md`.

## Resumo

- **O desenvolvedor não coleta, não recebe e não compartilha dados pessoais.** O app não tem conta,
  servidor próprio, anúncios, analytics, rastreamento nem SDKs de terceiros.
- **Suas escolhas de candidatos ficam só no seu aparelho**, cifradas e fora do backup na nuvem.
  Ninguém além de você tem acesso a elas, nem o desenvolvedor.
- **Para mostrar as candidaturas, o app consulta diretamente os sistemas públicos do Tribunal
  Superior Eleitoral (TSE).** Como em qualquer acesso ao site do TSE, o TSE e os provedores de
  infraestrutura que ele usa recebem o endereço IP do aparelho e o que foi consultado (por exemplo,
  a UF ou o município e o cargo). Suas escolhas nunca são enviadas.
- O Meu Santinho é independente: **não tem vínculo** com o TSE, a Justiça Eleitoral, órgãos de
  governo, partidos ou candidatos.

## 1. Quem é o responsável

| | |
|---|---|
| Responsável pelo app (desenvolvedor) | [PREENCHER: nome completo da pessoa ou razão social] |
| Contato para privacidade e para exercer seus direitos | [PREENCHER: e-mail de contato] |

O desenvolvedor é o controlador apenas dos dados que você mesmo enviar a ele, por exemplo ao
escrever para o e-mail acima. Como agente de tratamento de pequeno porte, o desenvolvedor não
indicou encarregado; o canal de comunicação com titulares e com a Autoridade Nacional de Proteção de
Dados (ANPD) é esse e-mail (LGPD, art. 41; Resolução CD/ANPD nº 2/2022, art. 11).

## 2. Dados tratados no seu aparelho

Estes dados são criados por você, ficam só no armazenamento privado do app e não são enviados ao
desenvolvedor nem a ninguém.

| Dado | Para quê | Onde fica e como é protegido |
|---|---|---|
| Suas escolhas de candidatos (o "santinho") | Montar a cola na ordem da urna | Arquivo cifrado com AES-256-GCM; a chave fica no Android Keystore e não pode ser exportada. Fora do backup e da transferência entre aparelhos. |
| Local de votação (UF e, se for o caso, município) | Buscar as candidaturas do seu local | Preferências do app, fora do backup. O código do local vai ao TSE em cada consulta (seção 3). |
| Preferências (aviso inicial aceito, lembrete, bloqueio do app, proteção de tela) | Funcionamento do app | Preferências do app, fora do backup. |
| Cópia dos dados públicos do TSE (candidaturas e fotos) | Uso sem internet | Cache do app. Pode ser apagado a qualquer momento (seção 8). |
| Cola em PDF ou em imagem | Imprimir ou compartilhar | Criada só quando você pede. A imagem é um arquivo temporário apagado na próxima abertura do app; o PDF vai para o destino que você escolher no diálogo de impressão. |

Opinião política é dado pessoal sensível (LGPD, art. 5º, II). Por isso as escolhas nunca aparecem
em notificações, registros (logs), backups ou na área de transferência, e nunca saem do aparelho,
a não ser que você mesmo imprima ou compartilhe a cola.

## 3. O que sai do aparelho: consultas ao TSE

O app só se conecta a servidores do TSE, no domínio `tse.jus.br`: o sistema DivulgaCandContas
(`divulgacandcontas.tse.jus.br`) e, quando ele não responde, os arquivos de dados abertos do TSE
(`cdn.tse.jus.br`). Não há servidor do desenvolvedor no caminho.

Em cada consulta, o TSE e os provedores de infraestrutura que ele usa (por exemplo, a rede de
distribuição de conteúdo do site) recebem:

- o **endereço IP** do aparelho, como em qualquer conexão à internet;
- **o que foi consultado**: a eleição, a UF ou o código do município, o cargo e, quando você abre
  uma candidatura ou a foto dela, o identificador dessa candidatura. Assim, o TSE pode saber quais
  candidaturas foram consultadas a partir do seu IP, como aconteceria no site oficial;
- a **versão do app e a versão do Android**, no cabeçalho `User-Agent`, que também traz o endereço
  desta política.

O app **não envia** suas escolhas, seu nome, seu título de eleitor, sua localização (o app não
pede permissão de localização) nem identificadores do aparelho (como o ID de publicidade).

O TSE trata esses dados de acesso como controlador dos próprios sistemas, conforme as regras da LGPD
para o poder público e a política de privacidade publicada no portal do TSE
(<https://www.tse.jus.br>). O desenvolvedor não tem acesso a eles.

## 4. O que o app não faz

- Não tem conta, login ou cadastro.
- Não exibe anúncios e não usa o ID de publicidade.
- Não usa analytics, SDK de relatório de falhas nem rastreamento de qualquer tipo.
- Não vende, não aluga e não compartilha dados.
- Não recomenda, não destaca e não ordena candidatos por popularidade, e não junta as escolhas de
  ninguém (não há enquete, ranking nem "mais buscados").
- Não abre páginas dentro do app: os links para o site do TSE abrem no seu navegador.

## 5. Permissões do Android

| Permissão | Por quê |
|---|---|
| Internet (`INTERNET`) e estado da rede (`ACCESS_NETWORK_STATE`) | Consultar o TSE e saber se há conexão. |
| Notificações (`POST_NOTIFICATIONS`, Android 13 ou mais novo) | Só se você ligar o lembrete do dia da eleição. O texto da notificação é genérico e nunca cita candidatos ou números. |
| Biometria (`USE_BIOMETRIC`, `USE_FINGERPRINT`) | Só se você ligar o bloqueio do app. Quem confere a digital, o rosto ou a senha é o Android; o app nunca recebe dados biométricos. |
| Inicialização e ativação (`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`) | Usadas pelo agendador do Android (WorkManager) para manter o lembrete agendado depois que o aparelho reinicia. |

## 6. Bases legais (LGPD)

| Tratamento | Base legal |
|---|---|
| Escolhas, local de votação e preferências guardados no seu aparelho | Tratamento feito por você, no seu aparelho, para fins particulares (LGPD, art. 4º, I). O desenvolvedor não tem acesso a esses dados. |
| Consultas ao TSE (IP e parâmetros da consulta) | Feitas a seu pedido, para obter informação pública. O tratamento dos dados de acesso é do TSE, como controlador dos próprios sistemas (seção 3). |
| Exibição dos dados públicos de candidaturas | Dados de acesso público publicados pelo TSE para informar os eleitores, usados para a mesma finalidade, sem alteração e sem dados pessoais desnecessários, como CPF, título de eleitor, data de nascimento ou e-mail dos candidatos (LGPD, art. 7º, §§ 3º e 7º). |
| Mensagens que você enviar ao desenvolvedor | Legítimo interesse, para responder (art. 7º, IX), e cumprimento de obrigação legal quando o pedido for sobre os seus direitos (art. 7º, II). |

## 7. Seus direitos

A LGPD (art. 18) garante: confirmação da existência de tratamento; acesso aos dados; correção de
dados incompletos, inexatos ou desatualizados; anonimização, bloqueio ou eliminação de dados
desnecessários, excessivos ou tratados em desconformidade com a lei; portabilidade; eliminação dos
dados tratados com consentimento; informação sobre com quem os dados foram compartilhados;
informação sobre a possibilidade de não dar consentimento e suas consequências; e revogação do
consentimento.

Como os dados do app ficam no seu aparelho, você exerce a maior parte desses direitos no próprio app:

- **ver e corrigir**: na tela Meu santinho, onde você troca ou remove cada escolha;
- **apagar**: em Configurações > Apagar meus dados (apaga as escolhas, a chave de cifra, o local, as
  preferências e o cache), ou desinstalando o app;
- **levar com você**: imprimindo a cola, salvando em PDF ou compartilhando a imagem.

Para os dados que você enviou ao desenvolvedor por e-mail, escreva para o contato da seção 1. A
resposta sai em até 15 dias (LGPD, art. 19, II). Para os dados de acesso guardados pelo TSE, use
os canais de privacidade do TSE. Você também pode reclamar à ANPD: <https://www.gov.br/anpd>.

## 8. Por quanto tempo os dados ficam guardados

| Dado | Prazo |
|---|---|
| Escolhas | Até você trocar, remover ou apagar, ou desinstalar o app. Se a chave do Android Keystore deixar de valer (por exemplo, numa restauração do sistema), as escolhas ficam ilegíveis e são descartadas. |
| Local e preferências | Até você mudar ou apagar, ou desinstalar o app. |
| Cache dos dados públicos | Atualizado de tempos em tempos. Dados de eleições antigas que você não abre há 60 dias são apagados automaticamente; Configurações > Limpar dados baixados apaga tudo. |
| Imagem da cola compartilhada | Apagada na próxima abertura do app. As cópias enviadas a outros apps ficam sob o controle desses apps. |
| Mensagens enviadas ao desenvolvedor | Pelo tempo necessário para responder e até 12 meses depois, salvo obrigação legal ou defesa de direitos. |
| Dados de acesso no TSE | Pelo prazo definido pelo TSE. |

## 9. Crianças e adolescentes

O app é feito para eleitores e não é direcionado a crianças. Ele não coleta dados de ninguém,
inclusive de crianças e adolescentes. O voto é facultativo para quem tem 16 ou 17 anos (Constituição,
art. 14, § 1º, II, "c"), e esses eleitores podem usar o app da mesma forma.

## 10. Segurança

- Escolhas cifradas com AES-256-GCM, com chave gerada e guardada no Android Keystore, que não pode
  ser exportada.
- Backup na nuvem e transferência entre aparelhos desligados para todos os dados do app.
- Conexões só por HTTPS, confiando apenas nas autoridades certificadoras do sistema, e só com
  servidores do TSE.
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
- **Impressão e compartilhamento**: ao imprimir, salvar em PDF ou compartilhar a cola, o conteúdo vai
  para o serviço de impressão ou o app que você escolher, sob as regras desse serviço.
- **Navegador**: os links para o site do TSE abrem no navegador do aparelho.

## 12. Mudanças nesta política

Se esta política mudar, a nova versão será publicada neste mesmo endereço, com nova data e número
de versão, e o texto dentro do app será atualizado na versão seguinte do app. Mudanças que alterem o
tratamento de dados (por exemplo, uma nova conexão de rede) serão descritas nas novidades da
atualização no Google Play antes de valerem.

## 13. Contato

Dúvidas, pedidos e reclamações: [PREENCHER: e-mail de contato].

Fonte dos dados de candidaturas: Tribunal Superior Eleitoral (TSE), dados públicos, licença CC BY.
