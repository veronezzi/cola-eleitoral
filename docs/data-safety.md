# Formulário "Segurança dos dados" (Data safety) do Google Play

Respostas exatas para Play Console > Política > Conteúdo do app > Segurança dos dados, com a
justificativa de cada uma. Decisão do projeto (Q4 do `ARCHITECTURE.md`): **"nenhum dado coletado nem
compartilhado"**. No fim há a alternativa conservadora, para o caso de você preferir ou de a revisão
do Google pedir.

Os rótulos estão em português, com o texto da interface em inglês entre parênteses, porque a tradução
do Play Console muda com o tempo. O formulário é obrigatório mesmo para apps que não coletam nada, e
a política de privacidade (`docs/privacidade/`) precisa estar publicada e informada em
Conteúdo do app > Política de privacidade.

## Definições do Google que sustentam a resposta

Fonte: <https://support.google.com/googleplay/android-developer/answer/10787469>

- **Coleta** (collection): "transmitir dados do app para fora do aparelho do usuário".
- **Compartilhamento** (sharing): "transferir dados do usuário coletados pelo app para terceiros".
- **Não precisa ser declarado**: dado acessado pelo app que é "processado apenas localmente no
  aparelho do usuário e não é enviado para fora dele".
- **Exceções de compartilhamento**: provedor de serviço, obrigação legal, **ação iniciada pelo
  usuário** em que ele espera razoavelmente que o dado seja enviado, e dado anonimizado.
- **Localização aproximada**: "localização física do usuário ou do aparelho numa área de 3 km² ou
  mais, como a cidade em que o usuário está".

## Respostas recomendadas

### Etapa 1. Coleta e segurança de dados (Data collection and security)

| Pergunta | Resposta |
|---|---|
| O app coleta ou compartilha algum dos tipos de dados do usuário obrigatórios? (Does your app collect or share any of the required user data types?) | **Não** |

Com "Não", o formulário pula as etapas de tipos de dados e de uso. Se o Play Console mostrar estas
perguntas, as respostas são:

| Pergunta (se aparecer) | Resposta |
|---|---|
| Quais métodos de criação de conta o app aceita? (account creation) | **O app não permite criar conta** |
| Revisão de segurança independente (independent security review, MASA) | **Não** (deixar desmarcado) |

### Etapa 2. Visualização da ficha (Store listing preview)

O resultado esperado na página do app é "Nenhum dado compartilhado com terceiros" e "Nenhum dado
coletado". Confira e clique em Enviar (Submit).

## Justificativa, fluxo por fluxo

| Fluxo de dados | Por que não é coleta nem compartilhamento declarável |
|---|---|
| Escolhas de candidatos, local de votação e preferências | Ficam só no aparelho (arquivo cifrado e preferências privadas, fora do backup). Processamento local, que o Google exclui da declaração. |
| Consulta ao TSE: código da UF ou do município, cargo e identificador da candidatura no caminho da URL | É um pedido de informação pública feito pelo usuário diretamente à fonte oficial, como abrir uma página no navegador. O desenvolvedor não recebe nada: não há servidor do projeto. O código enviado é o do local de votação escolhido pelo usuário (pode ser qualquer UF ou município), não a localização física do aparelho, e o app não tem permissão de localização. O TSE não é provedor de serviço do desenvolvedor; mesmo numa leitura ampla, o envio ao TSE se encaixaria na exceção de ação iniciada pelo usuário, que espera que a consulta vá ao TSE (o app diz isso no aviso inicial, em Sobre e na política). |
| Endereço IP visto pelo TSE e pela CDN que ele usa | Consequência de qualquer conexão à internet; o app não lê, não guarda e não envia o IP como dado. |
| Cabeçalho `User-Agent` (versão do app e do Android) | Não identifica a pessoa nem o aparelho; não é um dos tipos de dados do formulário. |
| Fotos de candidatos baixadas do TSE | Dado público recebido, não enviado. |
| Imagem ou PDF da cola compartilhados | Criados e enviados pelo próprio usuário, pelo compartilhamento ou pela impressão do Android, para o destino que ele escolhe. O app não transmite nada por conta própria. |
| Estatísticas e relatórios de falhas do Google Play (Android vitals) | Coletados pelo Android e pelo Google Play conforme a configuração do usuário, não pelo código do app. O app não tem SDK de analytics nem de falhas. |
| Lembrete do dia da eleição | Agendado localmente (WorkManager); não há servidor de notificações. |

**Condição para manter esta resposta:** nenhuma versão pode adicionar SDK com rede (analytics,
falhas, anúncios, Firebase), servidor próprio, login ou envio de escolhas. Qualquer mudança assim
exige atualizar este formulário e a política de privacidade antes de publicar a versão.

**Risco residual:** a definição de coleta do Google é ampla ("transmitir dados para fora do
aparelho"). Uma leitura estrita pode considerar o código da UF ou do município enviado ao TSE como
"localização aproximada" coletada. Se a revisão do Google questionar a resposta, use a alternativa
abaixo e atualize a seção 3 da política, que já descreve esse envio.

## Alternativa conservadora

Declara só o local de votação enviado ao TSE como localização aproximada coletada.

### Etapa 1. Coleta e segurança de dados

| Pergunta | Resposta |
|---|---|
| O app coleta ou compartilha algum dos tipos de dados do usuário obrigatórios? | **Sim** |
| Todos os dados do usuário coletados pelo app são criptografados em trânsito? (Is all of the user data collected by your app encrypted in transit?) | **Sim** (só HTTPS, `cleartextTrafficPermitted="false"`) |
| Quais métodos de criação de conta o app aceita? | **O app não permite criar conta** |
| Você oferece uma forma de os usuários pedirem a exclusão dos dados? (Do you provide a way for users to request that their data is deleted?) | **Não**: o dado vai ao TSE, que o guarda pelos próprios prazos; o desenvolvedor não o recebe e não pode apagá-lo. ("Apagar meus dados" apaga o local guardado no aparelho, mas isso não é dado coletado.) |

### Etapa 2. Tipos de dados

Marque só **Localização > Localização aproximada** (Location > Approximate location).

### Etapa 3. Uso e tratamento de Localização aproximada

| Pergunta | Resposta |
|---|---|
| Os dados são coletados, compartilhados ou ambos? | **Coletados** (não compartilhados: o envio ao TSE é ação iniciada pelo usuário) |
| Os dados são processados de forma temporária (efêmera)? | **Não** (o desenvolvedor não controla o que o TSE guarda) |
| A coleta é obrigatória ou opcional? | **Obrigatória** (sem um local não há o que consultar) |
| Por que os dados são coletados? | **Funcionalidade do app** (App functionality), e mais nada |

Resultado esperado na ficha: "Nenhum dado compartilhado com terceiros"; "Este app pode coletar
estes tipos de dados: Localização"; "Os dados são criptografados em trânsito"; "Não é possível
solicitar a exclusão dos dados".

Leitura ainda mais estrita, se a revisão pedir: o identificador da candidatura aberta também vai ao
TSE na URL do detalhe e da foto; quem quiser declarar isso marca também **Atividade no app >
Interações no app** (App activity > App interactions) com as mesmas respostas da tabela acima.
