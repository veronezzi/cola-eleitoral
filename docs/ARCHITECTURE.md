# Meu Santinho: arquitetura e especificação

Versão 1, escrita em 2026-10-04 (dia do 1º turno das Eleições Gerais 2026) na etapa 1 (pesquisa e
especificação). Para API e domínio, este documento prevalece sobre o `BRIEF.md`.

**Como ler as marcações de evidência**

| Marca | Significado |
|---|---|
| **[VERIFICADO]** | Confirmado em fonte primária (lei, página oficial do Google/TSE, dado aberto do TSE baixado nesta pesquisa) ou em resposta real da API arquivada (fixture em `app/src/test/resources/tse/`). |
| **[VERIFICADO-ind]** | Confirmado de forma indireta: código ou documentação de terceiros que chamaram a API real em 2026, sem amostra bruta disponível. |
| **[ASSUMIDO]** | Inferência razoável. Precisa ser validada num aparelho no Brasil (roteiro na seção 7.3). |

**Limites desta pesquisa.** `divulgacandcontas.tse.jus.br` e `www.tse.jus.br` respondem HTTP 403
(Akamai) a este ambiente; não houve tentativa de contorno. O `web.archive.org` está bloqueado pela
política de rede do ambiente. Por isso as amostras reais vieram de repositórios públicos que
arquivaram respostas da API (com proveniência registrada em `app/src/test/resources/tse/README.md`).
Os dados abertos do TSE (`dadosabertos.tse.jus.br` e `cdn.tse.jus.br`) responderam normalmente e
foram lidos diretamente. A legislação foi lida na Câmara dos Deputados (Legin), porque o portal do
Planalto não respondeu.

---

## 1. Visão geral e decisões

### 1.1 Produto

App Android que ajuda o eleitor a montar a própria "cola eleitoral" com os dados públicos de
candidaturas do TSE: escolher a eleição e o local de votação, buscar e filtrar candidatos, salvar
uma escolha por voto e levar a cola em papel (PDF impresso ou imagem) no dia da eleição, mesmo sem
internet. Sem contas, sem servidor próprio, sem anúncios, sem analytics, sem SDK de crash.

Fora do escopo da v1 (ver 7.2): eleições suplementares, voto de legenda, branco ou nulo na cola,
prestação de contas, bens, propostas de governo, resultados e apuração.

### 1.2 Stack (decidida no BRIEF; versões do scaffold atual)

| Área | Escolha |
|---|---|
| Build | Kotlin 2.4.20, AGP 9.4.1, KSP 2.3.12, compileSdk 37, **targetSdk 36**, minSdk 26, JDK 17 (testes em JVM 21) |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 com cor dinâmica e tema claro/escuro, material3-adaptive (list-detail e navigation suite) |
| Navegação | Navigation Compose 2.10.2 com rotas type-safe (`@Serializable`) |
| Estado | Lifecycle 2.11 ViewModel + `StateFlow`, Coroutines 1.11 e Flow |
| DI | Hilt 2.60.1 (KSP), hilt-navigation-compose, hilt-work |
| Rede | Retrofit 3.0 + OkHttp 5.5 + kotlinx.serialization 1.11 |
| Cache público | Room 2.8.5 (KSP, esquemas exportados em `app/schemas`) |
| Preferências | DataStore Preferences 1.2.1 |
| Escolhas (sensível) | DataStore tipado (`datastore-core`, transitivo) com serializer cifrado AES-256-GCM, chave no AndroidKeyStore |
| Imagens | Coil 3.6.3 (`coil-network-okhttp`) |
| Lembrete | WorkManager 2.12 |
| Bloqueio opcional | androidx.biometric 1.1.0 |
| Testes | JUnit 4, kotlinx-coroutines-test, Turbine, MockK, OkHttp MockWebServer3, Robolectric 4.17, Compose UI test |

Módulo único `:app`, pacote `com.veronezzi.colaeleitoral`, código em `app/src/main/kotlin`:
`core/` (network, common), `data/` (`remote/api`, `remote/dto`, `local/db`, `local/secure`, `mapper`,
`repository`), `domain/` (`model`, `repository`), `ui/` (`theme`, `navigation`, `screens/<feature>`,
`components`), `di/`, `work/`.

### 1.3 Princípios

1. **Fonte única e citada.** Todo dado de candidatura vem do TSE e aparece como foi publicado, com a
   data da última atualização e o link da página oficial do candidato.
2. **Offline-first.** A UI lê do cache (Room) e atualiza em segundo plano. A cola nunca depende de rede.
3. **Privacidade por padrão.** Escolhas revelam opinião política (dado sensível, LGPD art. 5º, II):
   ficam só no aparelho, cifradas, fora de backup, de logs e de notificações.
4. **Neutralidade.** Ordem padrão por número; nenhum ranking, destaque, recomendação, patrocínio,
   "mais acessados" ou agregação de escolhas. Todo candidato listado pelo TSE aparece.
5. **Acessível e em pt-BR.** Todo texto visível em `values/strings.xml` (pt-BR); identificadores e
   KDoc em inglês.

### 1.4 Decisões de arquitetura

| # | Decisão | Motivo |
|---|---|---|
| D1 | O app chama o TSE direto do aparelho; não há backend | Nenhum servidor nosso vê consultas ou escolhas; custo zero; BRIEF |
| D2 | Dado público em Room; escolhas fora do Room, num arquivo cifrado em `noBackupFilesDir` | Separar público de sensível; o cache pode ser apagado e migrado de forma destrutiva sem risco |
| D3 | Regras da urna (ordem, dígitos, votos por cargo) numa tabela de domínio baseada na lei (`OfficeRules`); a API só fornece a lista de cargos e as contagens | A cédula renderiza mesmo sem API |
| D4 | 2º turno = mesma eleição (mesmo id) + candidatos com `descricaoTotalizacao` "2º turno" | É como o TSE representa (2.4) |
| D5 | Cliente HTTP honesto: User-Agent do app, sem imitar navegador, sem contornar o Akamai | BRIEF; risco jurídico e de bloqueio; ver 2.6 |
| D6 | Fotos só sob demanda (detalhe e listas de cargos majoritários), com cache em disco do Coil | Menos carga no TSE e menos dados móveis |
| D7 | Nenhum SDK de terceiros com rede (analytics, crash, anúncios) | LGPD; Data safety sem coleta |
| D8 | Cada escolha guarda um snapshot completo do candidato | A cola funciona offline mesmo com o cache apagado |

### 1.5 Achados que mudam o plano

1. **Bloqueio anti-bot do TSE também no Brasil.** Relatos de setembro/2026 indicam que o Akamai do
   TSE recusa (403) clientes HTTP de linha de comando e até Chromium headless, inclusive em IP
   residencial brasileiro; outros relatos mostram clientes simples recebendo 200 a partir de IPs de
   provedores brasileiros (2.6). Um cliente OkHttp num celular brasileiro **pode** ser bloqueado.
   É o risco nº 1: validar num aparelho antes de investir na UI (7.3) e manter um plano B (2.10).
2. **IDs de eleição não cabem em `Int`.** A eleição de 2026 é `20322002026` [VERIFICADO]. `Long` em
   DTO, Room, rotas e domínio.
3. **Prazo do Google Play.** Conta pessoal criada depois de 13/11/2023 exige teste fechado com 12
   testadores por 14 dias seguidos antes de pedir produção [VERIFICADO]. Produção antes do 2º turno
   (25/10/2026) é improvável; o teste fechado pode servir os testadores no 2º turno (7.2).
4. **Senado 2026 com dois votos** em todas as UFs; repetir o mesmo candidato anula o 2º voto. A cola
   precisa de duas linhas para Senador e deve recusar o mesmo candidato duas vezes.
5. **Códigos de município são texto** com zero à esquerda (`"01120"`) [VERIFICADO].
6. **A API expõe dados pessoais.** A listagem traz `tituloEleitor` e o detalhe traz CPF completo,
   data de nascimento e e-mails [VERIFICADO]. Os DTOs não declaram esses campos e nenhum corpo de
   resposta é logado, nem em debug.

---

## 2. API do TSE (DivulgaCandContas)

### 2.1 Visão geral

- Base: `https://divulgacandcontas.tse.jus.br/divulga/rest/v1/`. JSON, somente GET, sem
  autenticação, sem documentação oficial nem contrato: os caminhos são posicionais e podem mudar sem
  aviso [VERIFICADO: https://github.com/augusto-herrmann/divulgacandcontas-doc ;
  https://github.com/mpbarbosa/public_apis/blob/main/tse/README.md].
- Não valida parâmetros: cargo inexistente devolve 200 com `candidatos: []`, não 404
  [VERIFICADO-ind: mpbarbosa §7, 28/09/2026].
- Cabeçalhos observados: `Cache-Control: max-age=1700` nas rotas e cerca de 270 s nas fotos
  [VERIFICADO-ind: mpbarbosa §6].
- Três formatos de data: `yyyy-MM-dd` (eleição), `yyyy-MM-dd HH:mm` (candidato), `dd/MM/yyyy`
  (contas) [VERIFICADO nas fixtures; mpbarbosa §7].

### 2.2 Endpoints usados

Caminhos relativos à base. `{electionId}` é `Long`, `{year}` é o ano da eleição, `{ueCode}` é texto.

**E1. Eleições ordinárias.** `GET eleicao/ordinarias` → `List<EleicaoDto>` (13 itens em 18/09/2026,
de 2004 a 2026) [VERIFICADO: fixture `eleicoes-ordinarias.json`].

**E2. Municípios de uma UF.** `GET eleicao/buscar/{uf}/{electionId}/municipios` →
`{ "estado": UeDto, "municipios": [UeDto] }` [VERIFICADO: fixture `municipios.json`, obtida com
`eleicao/buscar/AC/2045202024/municipios`, script em
https://github.com/hermesalvesbr/checkin-eleitoral/blob/main/script/fetchMunicipiosPorUF.js].
Usar o `electionId` da eleição **municipal** mais recente de E1 (hoje 2045202024). DF devolve
`municipios: []` [VERIFICADO]. Com o id de uma eleição geral, o comportamento é desconhecido
[ASSUMIDO: não usar].

**E3. Cargos de uma unidade eleitoral.** `GET eleicao/listar/municipios/{electionId}/{ueCode}/cargos`
→ `{ "unidadeEleitoralDTO": UeDto, "cargos": [CargoDto] }`. `ueCode` é o código do município em
eleições municipais [VERIFICADO: exemplo `eleicao/listar/municipios/2030402020/35157/cargos` em
https://github.com/augusto-herrmann/divulgacandcontas-doc/blob/main/examples/divulgacandcontas.http e
mock com o mesmo formato em https://github.com/BrasilAPI/BrasilAPI/blob/main/tests/eleicoes/cargos-por-municipio.test.js]
e a sigla da UF ou `BR` em eleições gerais [VERIFICADO-ind: mpbarbosa §4.2]. Em 2026 a lista da UF
inclui vice e suplentes (códigos 4, 9 e 10) [VERIFICADO-ind:
https://github.com/raschmitt/auditoria-sites-candidatos-2026/blob/master/docs/METODOLOGIA.md]: o app
filtra pelos códigos votáveis (2.4).

**E4. Candidatos de um cargo.** `GET candidatura/listar/{year}/{ueCode}/{electionId}/{officeCode}/candidatos`
→ `{ "unidadeEleitoral": UeDto, "cargo": CargoDto, "candidatos": [CandidatoDto] }`. Presidente usa
`ueCode = BR`. Exemplo: `candidatura/listar/2026/SP/20322002026/3/candidatos` [VERIFICADO: fixture
`candidatos-listar.json` (2022, BR, cargo 1); uso em 2026 em
https://github.com/thiago-salvador/puxa-ficha/blob/main/tests/fixtures/freshness-closeout/source.json e
https://github.com/andrelsehnem/analise-eleicoes/blob/main/documentacao/02-fontes-dados-api.md]. Maior
lista de 2026: Deputado Estadual em SP, 1.431 candidaturas [VERIFICADO: dados abertos, 2.10]; a 2 a
3,5 KB por item (medido na fixture de 2022), a resposta deve passar de 3 MB sem compressão [ASSUMIDO]. Existe um filtro `?partido={n}` citado por
terceiros [ASSUMIDO: https://github.com/flaviojmendes/colinhaeleitoral/blob/main/README.md]; a v1 não usa.

**E5. Detalhe do candidato.** `GET candidatura/buscar/{year}/{ueCode}/{electionId}/candidato/{candidateId}`
→ `CandidatoDto` completo [VERIFICADO: fixtures `candidato-buscar.json` (2026, SP) e
`candidato-buscar-com-vice.json` (2024, município 81809); formato `{ueCode}` = sigla da UF ou `BR`
confirmado em https://github.com/trifenol/meuvotolgbt/blob/main/docs/campos_api.md]. Algumas
candidaturas respondem **200 com corpo vazio e sem Content-Type** (registro não publicado; repetir não
resolve) [VERIFICADO-ind: meuvotolgbt]. A listagem pode trazer um julgamento mais antigo que o detalhe
[VERIFICADO-ind: comentário em
https://github.com/thiago-salvador/puxa-ficha/blob/main/scripts/lib/data-freshness/divulgacand-current.ts]:
na tela de detalhe vale o status do detalhe. Para Presidente, `BR` funciona [VERIFICADO-ind:
https://github.com/thiago-salvador/puxa-ficha/blob/main/data/divulgacand-vices-20260916.json]; há um
relato de detalhe de presidenciável que só respondeu com a UF de domicílio [ASSUMIDO:
https://github.com/guiolindo/Elei-es/blob/master/scripts/importar_termux.py]: se `BR` der `NotFound`,
mostrar o erro e o link oficial, sem tentar outras UFs.

**E6. Foto.** `GET https://divulgacandcontas.tse.jus.br/divulga/rest/arquivo/img/{electionId}/{candidateId}/{ueCode}`
(fora de `/rest/v1`) [VERIFICADO: campo `fotoUrl` das duas fixtures de detalhe, por exemplo
`.../img/20322002026/250002539612/SP`]. A listagem traz `fotoUrl = null`: o app monta a URL. No
detalhe, usar `fotoUrl` e não exibir quando `fotoUrlPublicavel == false`. Vices e suplentes trazem
`urlFoto` e `urlFotoPublicavel` [VERIFICADO]. Tipo `image/png` [VERIFICADO-ind: mpbarbosa §4.6].

**E7. Página oficial do candidato (link "Ver no site do TSE").**
1. Preferir `eleicoesAnteriores[i].txLink` do detalhe quando `eleicoesAnteriores[i].id == candidateId`:
   é gerado pelo próprio TSE no formato
   `https://divulgacandcontas.tse.jus.br/divulga/#/candidato/{year}/{electionId}/{ueCode}/{candidateId}`
   [VERIFICADO: fixtures].
2. Senão, montar `https://divulgacandcontas.tse.jus.br/divulga/#/candidato/{uf}/{uf}/{electionId}/{candidateId}/{year}/{ueCode}`
   (Presidente: `BR/BR/{electionId}/{candidateId}/{year}/BR`). Formato aberto num navegador em
   01/10/2026 com a página do candidato renderizada [VERIFICADO-ind:
   https://github.com/thiago-salvador/puxa-ficha/blob/main/docs/operations/evidence/issue-646/portal-publicacao-20261001.json].
   Em eleição municipal o último segmento é o código do município [ASSUMIDO: formato de
   https://github.com/turicas/eleicoes-brasil/blob/develop/divulgacandcontas.py, que usa a região no 1º segmento].
3. Último recurso: `https://divulgacandcontas.tse.jus.br/divulga/`.

**Conhecidos e não usados na v1:** `eleicao/eleicao-atual?idEleicao={id}` (todas as UEs com cargos e
contagens em uma chamada; alternativa ao E3 em eleições gerais) [VERIFICADO para 2024:
https://github.com/hermesalvesbr/checkin-eleitoral/blob/main/public/eleicao_atual.json];
`eleicao/anos-eleitorais`; `eleicao/suplementares/{ano}/{uf}`; `prestador/consulta/...` (contas).
`eleicao/estatistica/...` ("candidatos mais acessados") é **proibido no app**: quebra a neutralidade.

### 2.3 DTOs e mapeamento para o domínio

Configuração única do parser (em `core/network`):

```kotlin
val TseJson = Json {
    ignoreUnknownKeys = true   // a API tem ~85 chaves por candidato e muda sem aviso
    coerceInputValues = true   // null em campo com default vira o default
    explicitNulls = false
    isLenient = true           // tolera número onde se espera texto e vice-versa
}
```

Regras: todo campo de DTO é anulável com default `null`; declarar só o que é mapeado; um item sem os
campos obrigatórios do domínio é descartado (não derruba a lista); nomes JSON exatos via
`@SerialName` quando não forem identificadores Kotlin válidos ou convencionais (`sq_CANDIDATO`).
Campos de tipo instável usam `JsonPrimitive?` e são convertidos no mapper.

**EleicaoDto** (E1)

| JSON | DTO | Domínio (`Election`) | Evidência |
|---|---|---|---|
| `id` | `Long?` | `id` (obrigatório) | VERIFICADO: `20322002026`, `2045202024`, `2`, `680` |
| `ano` | `Int?` | `year` (obrigatório) | VERIFICADO |
| `nomeEleicao` | `String?` | `name` (fallback `"Eleições {ano}"`) | VERIFICADO: "Eleição Geral Federal 2026" |
| `tipoEleicao` | `String?` | filtro: manter só `"O"` | VERIFICADO |
| `tipoAbrangencia` | `String?` | `"F"` → `GENERAL`, `"M"` → `MUNICIPAL`; outro valor descarta o item | VERIFICADO (o swagger não oficial grafa `tipoAbragencia`: errado) |
| `dataEleicao` | `String?` | `date` = `LocalDate.parse` ou `null` | VERIFICADO: `"2026-10-04"`; `null` em 2010, 2006, 2004 |
| `turno` | `JsonPrimitive?` | `round` = `Round.fromNumber` ou `null` | VERIFICADO: sempre `null`; tipo quando preenchido ASSUMIDO |

Observação: 2020 tem duas entradas (`2030402020` nacional em 15/11 e `2032002020` Amapá em 14/11)
[VERIFICADO]. Mostrar as duas no seletor.

**UeDto** (E2, E3, E4)

| JSON | DTO | Domínio (`ElectoralUnit`) | Evidência |
|---|---|---|---|
| `codigo` | `String?` | `code` (obrigatório): `"01120"`, `"AC"`, `"BR"` | VERIFICADO |
| `nome` | `String?` | `name`, exibido como veio (o TSE manda em maiúsculas) | VERIFICADO |
| `sigla` | `String?` | `uf` (nulo nos municípios: usar a UF da consulta) | VERIFICADO |
| `regiao` | `String?` | não usado | VERIFICADO: `NORTE`, `CENTROOESTE`, `BR` |
| `capital` | `Boolean?` | não usar (sempre `false`) | VERIFICADO |
| `id`, `cargos`, `diretorios`, `estado` | - | não declarar | VERIFICADO |

**CargoDto** (E3, e aninhado em E4/E5)

| JSON | DTO | Domínio (`Office`) | Evidência |
|---|---|---|---|
| `codigo` | `Int?` | `code`; descartar se `!OfficeRules.isVotable` | VERIFICADO |
| `nome` | `String?` | `name` (fallback `OfficeRules.canonicalName`) | VERIFICADO: "Deputado Federal", "Vice-prefeito" |
| `contagem` | `Int?` | `candidateCount` (vem `0` dentro de candidato) | VERIFICADO |
| `codSuperior`, `titular` | - | não usar: inconsistentes ("Vice-prefeito" vem com `codSuperior` 11 numa rota e 0 noutra) | VERIFICADO |

**CandidatoDto** (E4 listagem e E5 detalhe)

| JSON | DTO | Domínio | Evidência |
|---|---|---|---|
| `id` | `Long?` | `Candidate.id` (obrigatório) | VERIFICADO: `280001607829` |
| `numero` | `Int?` | `number` (obrigatório) | VERIFICADO |
| `nomeUrna` | `String?` | `ballotName` (obrigatório) | VERIFICADO |
| `nomeCompleto` | `String?` | `fullName` | VERIFICADO |
| `partido.sigla` | `String?` | `party.acronym` (obrigatório) | VERIFICADO |
| `partido.numero` | `Int?` | `party.number`; `0` na listagem → `Party.numberFromCandidateNumber` | VERIFICADO: 0 na listagem, 30/50 no detalhe |
| `partido.nome` | `String?` | `party.name` (só no detalhe) | VERIFICADO |
| `nomeColigacao` | `String?` | `coalition` | VERIFICADO: "PDT", "COLIGAÇÃO BRASIL DA ESPERANÇA", "FEDERAÇÃO PSOL REDE(50-PSOL/18-REDE)" |
| `descricaoSituacao` | `String?` | `status.registration`, literal; vazio → `""` e a UI mostra "Situação não informada pelo TSE" | VERIFICADO: Deferido, Indeferido, Cancelado, Aguardando julgamento |
| `descricaoTotalizacao` | `String?` | `status.totalization`, literal | VERIFICADO: "Concorrendo"; "2º turno" VERIFICADO-ind (2.4) |
| `descricaoSituacaoCandidato` | `String?` | `status.onBallot` (detalhe) | VERIFICADO: "Consta da urna", "Cadastrado" |
| `candidatoApto` | `Boolean?` | `status.isFit` | VERIFICADO no detalhe; na listagem de 2026 VERIFICADO-ind (mpbarbosa §4.3) |
| `cargo.codigo` | `Int?` | conferir com o cargo pedido; divergente → descartar item | VERIFICADO |
| `ufCandidatura` | `String?` | conferência da UE (`"SP"`, `"80012"`) | VERIFICADO |
| `fotoUrl` | `String?` | `photoUrl` (detalhe); na listagem montar E6 | VERIFICADO: `null` na listagem |
| `fotoUrlPublicavel` | `Boolean?` | `CandidateDetail.photoPublishable` (só confiável no detalhe) | VERIFICADO |
| `descricaoTipoDrap` | `String?` | `coalitionType` | VERIFICADO: "Federação", "Partido Isolado" |
| `composicaoColigacao` | `String?` | `coalitionComposition`; `"**"` → `null` | VERIFICADO |
| `dataUltimaAtualizacao` | `JsonPrimitive?` | `lastUpdate`: `LocalDateTime` de `"yyyy-MM-dd HH:mm"`; outro formato → `null` | VERIFICADO: `"2026-09-18 14:39"` (o swagger antigo dizia epoch) |
| `vices` | `List<ViceDto>?` | `runningMates` | VERIFICADO (2024) |
| `eleicoesAnteriores[].id`, `.txLink` | `String?` | `officialPageUrl` (E7) | VERIFICADO |
| `eleicao.id` | `Long?` | conferência | VERIFICADO |

**Nunca declarar** (dado pessoal, sensível ou sem uso): `cpf`, `tituloEleitor` (vem preenchido na
listagem de 2024 e 2026), `dataDeNascimento`, `emails`, `descricaoSexo`, `descricaoCorRaca`,
`descricaoEstadoCivil`, `grauInstrucao`, `ocupacao`, `infoComplementar` (identidade de gênero,
orientação sexual, etnia), `legenda`, `bens`, `totalDeBens`, `arquivos`, `sites`, `cnpjcampanha`,
`gastoCampanha*`, `numeroProcesso*`, `st_MOTIVO_*`, `motivos`.

**ViceDto** (`vices[]` no detalhe)

| JSON | DTO | Domínio (`RunningMate`) | Evidência |
|---|---|---|---|
| `sq_CANDIDATO` | `Long?` | `id` | VERIFICADO |
| `nm_URNA` | `String?` | `ballotName` (obrigatório) | VERIFICADO |
| `nm_CANDIDATO` | `String?` | `fullName` | VERIFICADO |
| `ds_CARGO` | `String?` | `role`, literal | VERIFICADO: "Vice-prefeito"; "1º Suplente"/"2º Suplente" ASSUMIDO |
| `sg_PARTIDO` | `String?` | `partyAcronym` | VERIFICADO |
| `urlFoto`, `urlFotoPublicavel` | `String?`, `Boolean?` | `photoUrl` (nulo se não publicável) | VERIFICADO |
| `situacaoCandidato` | `String?` | `status` | VERIFICADO (nulo no exemplo) |
| `situacaoVice` | `Int?` | `3` = vice substituída: não exibir como vigente | VERIFICADO-ind (2026, puxa-ficha `divulgacand-vices-20260916.json`); semântica dos demais códigos ASSUMIDA |
| `nr_CANDIDATO` | `String?` (texto!) | não usado | VERIFICADO |

### 2.4 Códigos e convenções

**Cargos** [VERIFICADO: códigos 1 a 10 e nomes no CSV `consulta_cand_2026` do TSE (2.10); 11 a 13 em
`eleicao_atual.json` de 2024; dígitos na Lei 9.504/97 art. 15 e Res. TSE 23.609/2019 art. 14; ordem na
Lei 9.504/97 art. 59, § 3º, e na orientação do TSE para 2026].

| Código | Cargo | Eleição | Vota? | Dígitos | Ordem na urna | Votos |
|---|---|---|---|---|---|---|
| 6 | Deputado Federal | geral | sim | 4 | 1 | 1 |
| 7 | Deputado Estadual | geral (UFs exceto DF) | sim | 5 | 2 | 1 |
| 8 | Deputado Distrital | geral (DF) | sim | 5 | 2 | 1 |
| 5 | Senador | geral | sim | 3 | 3 | 2 quando `ano % 8 == 2` (2026), senão 1 |
| 3 | Governador | geral | sim | 2 | 4 | 1 |
| 1 | Presidente | geral (UE `BR`) | sim | 2 | 5 | 1 |
| 13 | Vereador | municipal | sim | 5 | 1 | 1 |
| 11 | Prefeito | municipal | sim | 2 | 2 | 1 |
| 2, 4, 12 | Vice-presidente, Vice-governador, Vice-prefeito | - | não (eleito com o titular) | - | - | - |
| 9, 10 | 1º e 2º Suplente de Senador | - | não (eleito com o titular, mesmo número) | - | - | - |

**Senado em 2026:** 2 vagas em todas as 27 UFs [VERIFICADO: `consulta_vagas_2026`, gerado pelo TSE
em 03/10/2026 19:31, `QT_VAGA = 2` para Senador nas 27 UFs; CF art. 46, § 2º]. A urna mostra duas telas
consecutivas ("primeira vaga" e "segunda vaga"); confirmar o mesmo candidato nas duas anula o 2º voto
(Res. TSE 23.751/2026) [VERIFICADO-ind: CNN Brasil, 07/09/2026, atualizada em 03/10/2026,
https://www.cnnbrasil.com.br/eleicoes/eleicoes-2026-veja-a-ordem-de-votacao-na-urna-eletronica/].
Ordem completa em 2026: deputado federal (4), deputado estadual ou distrital (5), senador 1ª vaga (3),
senador 2ª vaga (3), governador (2), presidente (2) [mesma fonte; notícia do TSE de março/2026
https://www.tse.jus.br/comunicacao/noticias/2026/Marco/eleicoes-2026-conheca-a-ordem-de-votacao-na-urna-eletronica,
bloqueada para leitura direta aqui].

**Unidades eleitorais (UE):**
- Eleição geral: `BR` para Presidente; sigla da UF para os demais cargos; no DF o cargo 8 substitui
  o 7 [VERIFICADO: CSV 2026 tem 433 candidaturas a DEPUTADO DISTRITAL no DF e nenhuma a estadual].
- Eleitor no exterior vota só para Presidente (Código Eleitoral, art. 225) [VERIFICADO]: opção
  "Exterior" (`ZZ`) mostra só o cargo 1 em `BR`.
- Eleição municipal: código TSE do município, 5 dígitos, texto (`"71072"` São Paulo, `"35157"` Feira
  de Santana, `"01120"` Acrelândia) [VERIFICADO]. O DF não tem eleição municipal (`municipios: []` e
  nenhum cargo em 2024) [VERIFICADO]. O código é estável entre eleições [ASSUMIDO; São Paulo é `71072`
  em 2020 e no histórico de 2026, VERIFICADO na fixture].

**IDs de eleição:** `20322002026` (2026, F, 2026-10-04), `2045202024` (2024, M), `2040602022`,
`2030402020`, `2032002020` (AP), `2022802018`, `2` (2016), `680` (2014), `1699` (2012), `14417`, `14422`,
`14423`, `14431` [VERIFICADO]. Não confundir com o `CD_ELEICAO` dos dados abertos (6257 federal e 6259
estadual em 2026) [VERIFICADO: CSV e
https://github.com/turicas/eleicoes-brasil/blob/develop/divulgacandcontas.py].

**Número do partido:** os dois primeiros dígitos do número do candidato (Lei 9.504/97 art. 15;
Res. TSE 23.609/2019 art. 14; em federação, o número do partido de filiação) [VERIFICADO].

**Segundo turno:**
- E1 lista uma entrada por eleição, com `turno = null`, inclusive para eleições passadas que tiveram
  2º turno [VERIFICADO: fixture].
- Depois da totalização do 1º turno, os candidatos que vão ao 2º turno passam a ter
  `descricaoTotalizacao = "2º turno"` no mesmo `electionId` [VERIFICADO-ind para 2024:
  https://github.com/murillo-ferrari/divulgacand2024/blob/main/consulta_gastos/busca_candidatos.js;
  ASSUMIDO para 2026]. Comparar com `CandidateStatus.isSecondRoundText` (tolera "2º TURNO", "2 turno").
- Só Presidente, Governador e Prefeito (municípios com mais de 200 mil eleitores) têm 2º turno
  (CF arts. 28, 29 II e 77) [VERIFICADO]. Data: último domingo de outubro (CF art. 77) [VERIFICADO];
  25/10/2026 (Res. TSE 23.751/2026) [VERIFICADO-ind por busca].
- Os dados abertos têm `NR_TURNO` e `DS_SIT_TOT_TURNO` (hoje todos `1` e `#NULO`) [VERIFICADO].

### 2.5 Escolha da eleição atual e do turno

Implementado em `ElectionCalendar` (domínio, testável sem Android), com "hoje" em `America/Sao_Paulo`:

1. Considerar só eleições de E1 com `tipoEleicao = "O"` e abrangência válida.
2. **Eleição atual** = a de menor data entre as que ainda não terminaram (data do 2º turno, ou do 1º
   quando não há regra de 2º turno, maior ou igual a hoje); se nenhuma, a mais recente por data; se
   nenhuma tem data, a de maior ano. Hoje: Eleição Geral Federal 2026. Depois de 25/10/2026, continua
   2026 até o TSE publicar a de 2028.
3. **Turno:** `round` da API, se vier preenchido; senão `SECOND` entre o dia seguinte ao 1º turno e a
   data do 2º turno; `FIRST` nos demais casos.
4. Em `SECOND`, a cédula mostra só cargos com 2º turno cujos candidatos na UE do eleitor estejam
   marcados "2º turno". Sem nenhum, a Home diz "Não há 2º turno para o seu local" e oferece a cédula do
   1º turno (só leitura).
5. O usuário pode trocar de eleição no seletor (todas as de E1, mais recentes primeiro). Escolhas são
   guardadas por (eleição, turno), e o 1º turno nunca é apagado ao entrar no 2º.

### 2.6 Erros e bloqueio (Akamai)

O que se sabe sobre o bloqueio:

| Data | Observação | Fonte |
|---|---|---|
| 2026-09-05 a 09-24 | Coletor Node com User-Agent próprio (`PuxaFichaDataFreshness/1.0`) e `Referer` do site recebeu 200; 400 transitório num detalhe que dera 200 minutos antes | https://github.com/thiago-salvador/puxa-ficha/blob/main/scripts/lib/data-freshness/divulgacand-current.ts |
| 2026-09-15 | 403 para curl, Python `requests` e Chromium headless, mesmo com IP residencial brasileiro; só navegador com janela funcionou | https://github.com/raschmitt/auditoria-sites-candidatos-2026/blob/master/docs/METODOLOGIA.md |
| 2026-09-20 | 403 direto; ~1 em 40 respostas veio 200 com página HTML de desafio; detalhe às vezes 200 com corpo vazio | https://github.com/trifenol/meuvotolgbt/blob/main/docs/campos_api.md |
| 2026-09-28 | curl recebe 403 "Access Denied" com `Reference #` em todos os hosts `*.tse.jus.br`, inclusive de IP residencial (Telefônica, SP) | https://github.com/mpbarbosa/public_apis/blob/main/tse/README.md |
| 2026 (sem data) | 403 para IPs de datacenter (Vercel/AWS, GitHub Actions, VPN, Zscaler); IP de provedor ou dados móveis passa | https://github.com/flaviojmendes/colinhaeleitoral/blob/main/docs/architecture/c4-dynamic-tse-ponte.md |
| 2026-10-04 | Este ambiente (fora do Brasil): 403 em `divulgacandcontas`; os dados abertos (`cdn.tse.jus.br`) respondem 200 | esta pesquisa |

Conclusão: a regra do Akamai combina reputação de IP e impressão digital do cliente e mudou ao longo
de setembro. Se um app nativo (OkHttp/Conscrypt) em rede móvel brasileira recebe 200 é a principal
incógnita do projeto [ASSUMIDO]. Terceiros contornam com impersonação de TLS do Chrome, "aquecimento"
de sessão, cabeçalhos `sec-ch-ua` falsos ou ponte via página do TSE: **o app não fará nada disso**
(BRIEF; é burlar uma proteção técnica do TSE e pode levar ao bloqueio do app inteiro).

**Mapeamento de falhas** (feito em `data/remote`, nunca na UI):

| Situação | Como chega | `AppError` | Mensagem (pt-BR, em `strings.xml`) |
|---|---|---|---|
| Sem rede, DNS, timeout, conexão interrompida | `IOException` | `Network` | "Sem conexão. Mostrando os dados salvos neste aparelho." |
| Bloqueio: fora do Brasil, VPN, rede corporativa, anti-bot | HTTP 403 com HTML "Access Denied" | `Blocked(403)` | "O site do TSE recusou a consulta desta rede. Isso acontece fora do Brasil, com VPN ou por proteção do TSE. Tente outra rede (Wi-Fi ou dados móveis) ou abra o site oficial." + botão "Abrir site do TSE" |
| Desafio anti-bot | HTTP 200 com `Content-Type: text/html` | `Blocked(null)` | idem |
| Limite de consultas | HTTP 429 (+ `Retry-After`) | `Blocked(429)` | "O TSE limitou as consultas por agora. Tente de novo em alguns minutos." |
| Candidatura não publicada | HTTP 200, corpo vazio | `NotFound` | "O TSE não publicou os dados desta candidatura." |
| Recurso inexistente | HTTP 404 | `NotFound` | idem, genérico |
| Instabilidade | 5xx; 400 em `candidatura/*` | `Server(code)` após as novas tentativas | "O sistema do TSE está instável. Tente mais tarde." |
| JSON inesperado | `SerializationException` | `Parsing` | "O TSE mudou o formato dos dados. Atualize o app." |
| Outros | qualquer outra exceção | `Unknown` | "Algo deu errado." |

Implementação:
- `TseResponseGuardInterceptor` (OkHttp, ordem: depois do User-Agent): 403 e 429 → lança
  `TseBlockedException(code, retryAfter)`; 2xx com `text/html` → `TseBlockedException(null)`; 2xx
  com corpo vazio (`peekBody(1)` vazio) → `TseEmptyBodyException`. Ambas estendem `IOException` (o
  OkHttp exige) e são distinguidas das falhas de rede no data source.
- Repetição no data source, com `delay` de corrotina (cancelável, testável com tempo virtual): até
  3 tentativas para `Network`, 5xx, 408, 429 (respeitando `Retry-After` até 10 s) e 400 em
  `candidatura/*`; espera 1 s e 3 s mais jitter de 0 a 250 ms. `Blocked(403)` e HTML: uma única nova
  tentativa após 2 s.
- Falha de atualização nunca apaga cache: registra `lastError` e emite `CachedData(isStale = true)`.

### 2.7 Etiqueta de requisições

- OkHttp único (`@Singleton`), compartilhado por Retrofit e Coil: connect 15 s, read 30 s, call 60 s;
  `Dispatcher.maxRequestsPerHost = 4`; gzip automático do OkHttp.
- Cabeçalhos: `User-Agent: ColaEleitoral/{versionName} (Android {release}; +{URL da política de
  privacidade})` e `Accept: application/json`. Não enviar `Referer`, `Origin`, `sec-ch-ua*`,
  `Sec-Fetch-*`; não usar WebView para buscar JSON.
- Allowlist de host num interceptor: só `divulgacandcontas.tse.jus.br` (e `cdn.tse.jus.br` se o plano B
  for adotado). Qualquer outro host → falha imediata.
- Nada de varredura: lista por (UE, cargo) só quando a tela pede; detalhe só quando o usuário abre;
  nada de polling em segundo plano. "Atualizar" manual limitado a 1 vez por minuto por chave.
- Logs: nenhum corpo, em nenhum build. Em debug, `HttpLoggingInterceptor.Level.BASIC` (método,
  caminho, status) instalado a partir de `src/debug`.

### 2.8 Cache em Room e offline-first

Banco `public_cache.db`, só com dado público e reconstruível; por isso a migração pode ser destrutiva
(`fallbackToDestructiveMigration(dropAllTables = true)`), mantendo os esquemas exportados.

| Entidade | Chave | Colunas principais |
|---|---|---|
| `ElectionEntity` | `id` | `year`, `name`, `scope`, `round?`, `date?` (ISO) |
| `MunicipalityEntity` | `code` | `uf`, `name`, `sourceElectionId` |
| `OfficeEntity` | (`electionId`, `ueCode`, `code`) | `name`, `candidateCount?` |
| `CandidateEntity` | (`electionId`, `id`); índice (`electionId`, `ueCode`, `officeCode`) | `number`, `ballotName`, `fullName?`, `searchKey` (nome normalizado), `partyAcronym`, `partyNumber?`, `partyName?`, `coalition?`, `registrationStatus`, `totalizationStatus?`, `onBallotStatus?`, `isFit?`, `photoUrl?` |
| `CandidateDetailEntity` | (`electionId`, `candidateId`) | `coalitionType?`, `coalitionComposition?`, `officialPageUrl`, `photoPublishable?`, `lastUpdate?` |
| `RunningMateEntity` | (`electionId`, `candidateId`, `position`) | `id?`, `role`, `ballotName`, `fullName?`, `partyAcronym?`, `photoUrl?`, `status?` |
| `FetchStateEntity` | `key` | `fetchedAt?`, `lastAttemptAt?`, `lastError?` |

Chaves de `FetchStateEntity`: `elections`, `municipalities:{uf}`, `offices:{electionId}:{ueCode}`,
`candidates:{electionId}:{ueCode}:{officeCode}`, `detail:{electionId}:{candidateId}`.

TTL (relógio injetado, `java.time.Clock`):

| Dado | Normal | Semana de eleição (D-7 a D+1 de cada turno) |
|---|---|---|
| Eleições | 24 h | 6 h |
| Municípios | 30 dias | 30 dias |
| Cargos | 24 h | 6 h |
| Lista de candidatos | 6 h | 1 h |
| Detalhe | 6 h | 1 h |
| Fotos | sem TTL (LRU do Coil) | - |

Fluxo:
1. A tela coleta o `Flow<CachedData<...>>` do repositório: o cache aparece imediatamente.
2. Na abertura da tela (e ao voltar o app para o primeiro plano), o ViewModel chama `refresh*()`;
   se o cache está dentro do TTL, nada é baixado.
3. A atualização grava numa transação: apaga as linhas da chave, insere as novas e atualiza
   `FetchStateEntity`. A UI recebe a nova emissão sozinha.
4. Indicador: "Dados do TSE de 04/10, 14:32" sempre visível nas listas; com `isStale`, um aviso
   discreto "Podem estar desatualizados" e o motivo (`lastError`). Sem cache e com erro: tela de erro
   com "Tentar de novo".
5. No onboarding, depois de escolher o local, pré-carregar as listas da cédula do eleitor na eleição
   atual (no máximo 7 listas, em sequência), para a cola funcionar offline no dia.
6. Limpeza: na inicialização, apagar candidatos de eleições que não são a atual nem foram abertas há
   60 dias. "Limpar dados baixados" nas configurações apaga tudo (inclusive o cache do Coil).

Busca e filtros: a lista de um cargo tem no máximo ~1.500 linhas; o repositório lê tudo da chave e
aplica `CandidateFilter` em memória (`filteredBy`, em `Dispatchers.Default`). Room FTS não é necessário.

### 2.9 Imagens

- Coil 3 com o mesmo OkHttp (User-Agent, allowlist, guard). Cache em disco de 64 MB em
  `cacheDir/image_cache`; a estratégia padrão do Coil 3 ignora `Cache-Control` e sempre grava em disco,
  então fotos vistas uma vez funcionam offline [VERIFICADO: https://coil-kt.github.io/coil/network/].
- Listas: foto só para cargos majoritários (1, 3, 5, 11); nas listas proporcionais, avatar com as
  iniciais do nome de urna, igual para todos (neutralidade e carga no TSE). Detalhe: foto se
  `photoPublishable != false`. Cola: sem fotos.
- `contentDescription = "Foto de {nome de urna}"`; erro de carregamento → avatar de iniciais.

### 2.10 Plano B: dados abertos do TSE (decisão pendente)

Se a validação em aparelho (7.3) mostrar que o Akamai bloqueia o app, existe uma fonte oficial que
respondeu normalmente a este ambiente, fora do Brasil, em 2026-10-04:

- Catálogo CKAN: `https://dadosabertos.tse.jus.br/api/3/action/package_show?id=candidatos-2026`,
  licença "Creative Commons Atribuição" (`license_id: cc-by`), atualização "4 vezes ao dia"
  [VERIFICADO].
- `https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_cand/consulta_cand_2026.zip`: **3,2 MB**,
  um CSV por UF mais `BR` e `BRASIL`, `;` como separador, Latin-1, 50 colunas, 20.989 candidaturas
  (gerado em 03/10/2026 19:31) [VERIFICADO]. Colunas úteis: `SQ_CANDIDATO`, `CD_CARGO`, `SG_UE`,
  `NR_CANDIDATO`, `NM_URNA_CANDIDATO`, `NM_CANDIDATO`, `SG_PARTIDO`, `NR_PARTIDO`, `TP_AGREMIACAO`
  ("PARTIDO ISOLADO", "FEDERAÇÃO", "COLIGAÇÃO"), `NM_FEDERACAO`, `NM_COLIGACAO`, `NR_TURNO`,
  `DS_SIT_TOT_TURNO`. **Também traz `NR_CPF_CANDIDATO`, `NR_TITULO_ELEITORAL_CANDIDATO`, `DS_EMAIL` e
  `DT_NASCIMENTO`: descartar no parse.** `DS_SITUACAO_CANDIDATURA` vem `#NE` em 2026.
- `.../consulta_cand_complementar/consulta_cand_complementar_2026.zip`: **1,3 MB**, com
  `DS_SITUACAO_JULGAMENTO` (DEFERIDO, INDEFERIDO, RENÚNCIA, "INDEFERIDO EM PRAZO RECURSAL OU COM
  RECURSO") e `ST_CANDIDATO_INSERIDO_URNA` (SIM/NÃO) por `SQ_CANDIDATO` [VERIFICADO].
- Respostas com `access-control-allow-origin: *`, `accept-ranges: bytes`, `ETag` e `Last-Modified`
  [VERIFICADO]. Em 2024 o ZIP de candidatos tem 64 MB (municipal): exigiria baixar só a entrada da UF
  com `Range` + diretório central do ZIP, ou só em Wi-Fi [VERIFICADO tamanho; técnica ASSUMIDA].
- Limitações: sem fotos por candidato (há ZIP de fotos por UF, 15,6 MB em SP), sem link `txLink`,
  status em caixa alta e com texto diferente da API ("DEFERIDO" × "Deferido").

Desenho sugerido, se aprovado: interface `CandidateRemoteSource` com `DivulgaCandContasSource`
(primária) e `TseOpenDataSource` (só listas de eleições gerais, sem detalhe), escolhida
automaticamente quando a primária devolve `Blocked`. A UI indica "Fonte: dados abertos do TSE". O
contrato de domínio já comporta isso (os repositórios não expõem a origem).

---

## 3. Contrato de domínio

Código em `app/src/main/kotlin/com/veronezzi/colaeleitoral/domain/{model,repository}/*.kt`, pacote
`com.veronezzi.colaeleitoral.domain.model` e `...domain.repository`. Kotlin puro (`java.time`,
`java.text.Normalizer`, `kotlinx.coroutines.flow.Flow`), sem tipos do Android. **Copiar para
`app/src/main/kotlin/com/veronezzi/colaeleitoral/domain/` sem mudar assinaturas**; mudanças passam por
este documento. Verificado com o compilador Kotlin 2.4.20 (`-Werror`, classpath: stdlib +
coroutines 1.11.0): sem erros nem avisos.

| Arquivo | Conteúdo | Regras relevantes |
|---|---|---|
| `model/Election.kt` | `Round`, `ElectionScope`, `Election`, `ElectionCalendar` | `id: Long`; `secondRoundDate` derivado (último domingo de outubro); `currentElection` e `roundOn` (2.5); horário de Brasília, 8h às 17h; lembrete às 7h |
| `model/ElectoralUnit.kt` | `ElectoralUnit`, `FederativeUnits`, `VoterLocation` | `BR`, `ZZ` (exterior); `ballotUnitCodes(scope)` |
| `model/Office.kt` | `Office`, `OfficeRules` | tabela da 2.4; `maxPicks` (Senado 2 em `ano % 8 == 2`; 2º turno só 1, 3, 11); `defaultCodes` para a cédula sem API |
| `model/Party.kt` | `Party` | `numberFromCandidateNumber` |
| `model/Candidate.kt` | `CandidateStatus`, `Candidate`, `RunningMate`, `CandidateDetail` | status literal; `isSecondRoundText` |
| `model/BallotPick.kt` | `BallotSlotKey`, `BallotPick`, `SavePickResult` | snapshot completo; `DuplicateCandidate` |
| `model/CandidateFilter.kt` | `SortOrder`, `CandidateFilter`, `FilterOptions`, `normalizeForSearch`, `List<Candidate>.filteredBy` | padrão = todos, por número |
| `model/AppResult.kt` | `AppError` (Network, Blocked, NotFound, Server, Parsing, Unknown), `AppResult`, `map` | erros sem corpo de resposta |
| `model/CachedData.kt` | `CachedData<T>` | `fetchedAt`, `isStale`, `lastError` |
| `model/UserSettings.kt` | `UserSettings` | `secureScreens = true` por padrão |
| `repository/ElectionRepository.kt` | eleições, municípios, cargos da cédula, `clearCache` | leitura por Flow do cache + `refresh*(force)` |
| `repository/CandidateRepository.kt` | listas filtradas, opções de filtro, detalhe | idem |
| `repository/BallotRepository.kt` | `observeBallot`, `savePick`, `removePick`, `clearBallot`, `deleteAll` | cifrado; nunca logar |
| `repository/SettingsRepository.kt` | local, onboarding, lembrete, bloqueio, telas protegidas, `clear` | DataStore Preferences |
| `repository/ReminderScheduler.kt` | `schedule`, `cancel`, `cancelAll` | texto genérico, sem escolhas |

Sem classes de caso de uso: a lógica de domínio que não é acesso a dados está em funções puras
(`ElectionCalendar`, `OfficeRules`, `filteredBy`). Testes unitários dessas funções vêm primeiro.

---

## 4. Telas e navegação

### 4.1 Rotas type-safe

`ui/navigation/Routes.kt` (Navigation Compose 2.10, `kotlinx.serialization`). Só tipos primitivos nas
rotas; dados maiores vêm do repositório pelo ViewModel (`SavedStateHandle.toRoute<...>()`).

```kotlin
@Serializable data object OnboardingRoute                       // aviso de independência
@Serializable data class LocationRoute(val fromSettings: Boolean = false)
@Serializable data object HomeRoute                             // destino inicial após o onboarding
@Serializable data class CandidateListRoute(
    val electionId: Long, val year: Int, val ueCode: String,
    val officeCode: Int, val round: Int, val slot: Int = 1,
)
@Serializable data class CandidateDetailRoute(
    val electionId: Long, val year: Int, val ueCode: String,
    val officeCode: Int, val candidateId: Long, val round: Int, val slot: Int = 1,
)
@Serializable data class BallotRoute(val electionId: Long, val round: Int)      // "Meu santinho"
@Serializable data class ColaExportRoute(val electionId: Long, val round: Int)
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
@Serializable data object PrivacyPolicyRoute
@Serializable data object LicensesRoute
```

Grafo: `OnboardingRoute` → `LocationRoute` → `HomeRoute` (limpa a pilha). Navegação de primeiro
nível com `NavigationSuiteScaffold` (barra inferior no celular, trilho em telas largas): **Início**
(`HomeRoute`), **Meu santinho** (`BallotRoute` da eleição e turno atuais), **Sobre** (`AboutRoute`).
`CandidateListRoute` e `CandidateDetailRoute` usam `ListDetailPaneScaffold` em telas largas (lista e
detalhe lado a lado). Sem deep links (privacidade e superfície de ataque). O bloqueio do app não é
uma rota: é um portão sobre o `NavHost` (4.10).

### 4.2 Primeira execução

1. **Aviso** (`OnboardingRoute`), antes de qualquer acesso à rede. Texto proposto:
   > O Meu Santinho é um aplicativo independente. Não tem vínculo com o Tribunal Superior Eleitoral
   > (TSE), com a Justiça Eleitoral, com nenhum órgão de governo, partido ou candidato. As informações
   > de candidaturas são públicas e vêm do sistema DivulgaCandContas do TSE
   > (divulgacandcontas.tse.jus.br). Confira sempre no site oficial.
   >
   > Suas escolhas ficam só neste aparelho, cifradas. Não há conta, anúncio nem rastreamento.

   Botão "Entendi" grava `acceptedDisclaimerVersion`. Sem logotipos do TSE ou da Justiça Eleitoral e
   sem o Brasão da República em nenhuma tela ou asset.
2. **Local** (`LocationRoute`): UF (27 + "Exterior: vota só para Presidente"), depois município
   (busca na lista de E2). O município é obrigatório só quando a eleição atual é municipal; em 2026
   aparece como opcional ("necessário nas eleições municipais"). Lista de municípios com busca sem
   acento; o DF não pede município.
3. Pré-carregamento da cédula (2.8, item 5) e ida para a Home.

### 4.3 Home

- Cartão da eleição: nome (do TSE), data, turno ("1º turno" / "2º turno"), contagem ("Faltam 21 dias",
  "Hoje é dia de votar: 8h às 17h, horário de Brasília"), seletor de outras eleições.
- Lista dos cargos da cédula em ordem da urna (`observeBallotOffices`): nome do cargo, "4 dígitos",
  e para cada voto a escolha salva (número em caixas + nome de urna + partido) ou "Escolher".
  Senado 2026 aparece como "Senador: 1ª vaga" e "Senador: 2ª vaga". Toque abre `CandidateListRoute`.
- Botão "Ver meu santinho" e cartão fixo: "Na cabine, leve a cola em papel. Celular, câmera e
  relógio inteligente não entram, nem desligados (Lei 9.504/97, art. 91-A; Res. TSE 23.751/2026)."
- Indicador de atualização e erro conforme 2.8.

### 4.4 Lista de candidatos

- Barra: nome do cargo e UE; campo de busca "Nome, número ou partido"; filtros em chips: Partido
  (multisseleção), Situação (multisseleção com os textos literais presentes), "Só 2º turno" (no 2º
  turno, ligado por padrão); ordenação: Número (padrão), Nome, Partido.
- Item (todos com o mesmo peso visual): foto ou iniciais, número em destaque, nome de urna, sigla do
  partido, coligação/federação em uma linha, situação literal num chip neutro (sem verde/vermelho).
- "Mostrando 37 de 1.431 candidaturas"; "Dados do TSE de ..."; puxar para atualizar.
- Estados: carregando (esqueleto), vazio por filtro ("Nenhum candidato com esses filtros"), vazio do TSE
  ("O TSE não lista candidatos para este cargo"), erro sem cache (2.6), erro com cache (aviso).
- `LazyColumn` com `key = id`; filtro em `Dispatchers.Default`; busca com debounce de 250 ms.

### 4.5 Detalhe do candidato

- Foto (se publicável), nome de urna (título), nome completo, número em caixas com o total de
  dígitos, cargo e UE.
- Partido: sigla, nome e número. Coligação ou federação: tipo (`descricaoTipoDrap`) e nome.
- Situação do registro (literal), "Consta da urna" (literal) e totalização, quando houver.
- Vice ou suplentes: nome de urna, papel literal ("Vice-prefeito", "1º Suplente"), partido, foto.
- "Atualizado pelo TSE em 18/09/2026, 14:39" e o botão **"Ver no site do TSE"** (E7, abre no navegador).
- Botão principal **"Salvar no meu santinho"**. Se o voto já tem outro candidato: diálogo "Trocar
  FULANO por BELTRANO?". Senado com 2 votos: salva no `slot` da rota; se o candidato já está no outro
  voto, mensagem "Esse candidato já está no outro voto para Senador. Na urna, o segundo voto
  repetido é anulado." Já salvo: "Remover do meu santinho".
- Nenhum texto avaliativo. Situação diferente de deferida é mostrada como o TSE publica, sem alerta
  colorido; ao salvar um candidato não apto, aviso neutro: "Situação no TSE: Indeferido. Confira no
  site oficial antes de votar."

### 4.6 Meu santinho

- Cargos em ordem da urna, um bloco por voto: rótulo ("3. Senador: 1ª vaga"), caixas com os dígitos
  (`digitCount` caixas; vazias se não houver escolha), nome de urna e partido.
- Lê do `BallotRepository` (snapshot): funciona sem rede. Se o cache tiver status mais novo e
  diferente de `statusAtSave`, mostra "Situação atualizada no TSE: ..." (literal).
- Ações: "Imprimir ou salvar PDF", "Compartilhar imagem", "Limpar escolhas" (confirmação), troca de
  turno quando houver 2º turno. Texto: "Suas escolhas ficam só neste aparelho."
- Acessibilidade: cada bloco é um nó semântico "Senador, primeira vaga: 1 2 3, FULANO, PARTIDO".

### 4.7 Cola: PDF e imagem

Um `ColaRenderer` desenha num `Canvas` (mesmo código para PDF e bitmap; testável com Robolectric):
título "Minha cola: {eleição}, {1º/2º} turno, {data}"; uma linha por voto com rótulo, caixas grandes
dos dígitos (≥ 20 pt, preto no branco) e, se a opção "Mostrar nomes" estiver ligada (padrão), nome de
urna e partido; rodapé: "Na cabine não é permitido levar celular (Lei 9.504/97, art. 91-A). Confira o
nome e a foto na urna antes de confirmar." e "Feito com o app Meu Santinho, independente e sem
vínculo com o TSE. Fonte dos dados: TSE.". Sem QR code, sem marca d'água rastreável.

- **PDF:** `PrintManager.print()` com `PrintDocumentAdapter` próprio que escreve um
  `PrintedPdfDocument` A4 de uma página; o diálogo do sistema oferece "Salvar como PDF".
- **Imagem:** PNG 1080 × 1350 em `cacheDir/shared/`, compartilhado por `FileProvider`
  (`${applicationId}.fileprovider`, `exported=false`, `grantUriPermissions=true`, só o caminho
  `shared/`) com `FLAG_GRANT_READ_URI_PERMISSION`; arquivos apagados na próxima abertura do app.
- Antes do primeiro compartilhamento: "A imagem mostra em quem você pretende votar. Compartilhe só
  com quem você confia." (com "Não mostrar de novo").

### 4.8 Lembrete (opt-in)

- Desligado por padrão. Ao ligar (Configurações ou cartão na Home), explicar e pedir
  `POST_NOTIFICATIONS` no Android 13+ [VERIFICADO:
  https://developer.android.com/develop/ui/views/notifications/notification-permission].
- `WorkManagerReminderScheduler`: `OneTimeWorkRequest` único por (eleição, turno),
  `ExistingWorkPolicy.REPLACE`, atraso até 7h do dia (Brasília). 2º turno: agendado quando o app detecta
  candidatos "2º turno" no local do eleitor. Reagendar quando a data da eleição mudar no cache.
- Notificação (canal "Lembretes da eleição", `VISIBILITY_PUBLIC`): "Hoje é dia de votar (1º turno).
  Leve sua cola em papel: o celular não entra na cabine." Nada de nomes ou números. Toque abre o app
  (passando pelo bloqueio).
- Sem alarme exato (`SCHEDULE_EXACT_ALARM` não é necessário; precisão de minutos basta).

### 4.9 Sobre, privacidade e licenças

- **Sobre:** o aviso de independência (4.2), "Fonte dos dados: Tribunal Superior Eleitoral,
  DivulgaCandContas" com link, data da última atualização, como o app escolhe a eleição, versão,
  nome e contato do desenvolvedor (exigido pela política de deturpação, 6.1).
- **Política de privacidade:** texto completo no app e o mesmo texto numa URL pública (6.1). Conteúdo
  mínimo: quem é o desenvolvedor e como contatar; o app não coleta nem compartilha dados pessoais; as
  consultas vão direto ao TSE, que recebe o IP e a UE consultada como em qualquer visita ao site;
  escolhas ficam só no aparelho, cifradas, fora de backup; como apagar ("Apagar meus dados" ou
  desinstalar); sem anúncios, analytics ou rastreamento; dados de candidatos são públicos (LGPD art. 7º,
  § 3º) e exibidos como o TSE publica.
- **Licenças:** lista estática (`res/raw`) das bibliotecas de código aberto (Apache-2.0) e a atribuição
  dos dados do TSE (Creative Commons Atribuição). Sem plugin que puxe Play Services.

### 4.10 Bloqueio opcional do app

- Desligado por padrão. Ligar exige credencial do aparelho configurada e um teste do prompt.
- `BiometricPrompt` com `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` no Android 11+;
  `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` no 9 e 10; no 8.x,
  `KeyguardManager.createConfirmDeviceCredentialIntent`. O androidx.biometric exige
  `FragmentActivity`: **`MainActivity` deve estender `FragmentActivity`**, não só `ComponentActivity`.
- Bloqueia na abertura e ao voltar do segundo plano depois de 30 s (`ProcessLifecycleOwner`, já
  incluído no scaffold). Enquanto bloqueado, o conteúdo não é composto.
- A chave das escolhas não depende de autenticação (o bloqueio é só um portão de UI): se o usuário
  trocar a biometria, as escolhas não se perdem.

### 4.11 Telas grandes, acessibilidade e textos

- Edge-to-edge obrigatório e volta preditiva ativa no targetSdk 36 [VERIFICADO:
  https://developer.android.com/about/versions/16/behavior-changes-16]: usar `Scaffold`/insets e
  `BackHandler`/`PredictiveBackHandler`; o manifest já tem `enableOnBackInvokedCallback="true"`.
  Em telas com largura mínima ≥ 600 dp, orientação e redimensionamento não podem ser travados.
- Alvos de toque ≥ 48 dp; contraste AA; textos em `sp`; testar com fonte 200% e TalkBack; números
  lidos dígito a dígito; nenhuma informação só por cor.
- Todos os textos em `values/strings.xml` (pt-BR), com plurais (`plurals`) para contagens.

---

## 5. Segurança e privacidade (LGPD)

### 5.1 Classificação dos dados

| Dado | Classe | Onde fica | Proteção |
|---|---|---|---|
| Escolhas (`BallotPick`) | **Sensível**: revela opinião política (LGPD art. 5º, II; tratamento só nas hipóteses do art. 11) | `noBackupFilesDir/ballot.enc` | AES-256-GCM, chave no AndroidKeyStore; fora de backup; nunca em log, notificação, analytics ou rede |
| Local de votação (UF, município) | Pessoal, baixo risco | DataStore Preferences | Armazenamento privado do app; fora de backup; enviado ao TSE só como código de UE no caminho da consulta |
| Preferências (onboarding, lembrete, bloqueio) | Não pessoal | DataStore Preferences | Fora de backup |
| Cache de candidaturas (nomes, números, partidos, situação, fotos) | Dado pessoal **público** de candidatos (LGPD art. 7º, §§ 3º e 7º) | Room `public_cache.db`, cache do Coil | Só o necessário à finalidade (informar o eleitor); nada de CPF, título, nascimento, e-mail, raça, gênero, orientação, bens |
| Imagem/PDF da cola | Sensível, criado por ação do usuário | `cacheDir/shared/` (imagem) ou destino escolhido no diálogo de impressão | Aviso antes de compartilhar; arquivo temporário apagado na próxima abertura |

O desenvolvedor não recebe nenhum desses dados (não há servidor). A LGPD não se aplica ao tratamento
feito pela própria pessoa para fins particulares (art. 4º, I), mas o app trata as escolhas como dado
sensível por desenho.

### 5.2 Criptografia das escolhas

- `data/local/secure/BallotCipher`: chave AES-256 gerada no AndroidKeyStore (alias `ballot_key_v1`,
  `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`,
  `setRandomizedEncryptionRequired(true)`), não exportável, sem exigir autenticação do usuário
  (o lembrete e o bloqueio de UI ficam independentes). StrongBox não é usado (lento e com falhas em
  alguns fabricantes; o TEE basta para o risco).
- `data/local/secure/EncryptedBallotSerializer`: `Serializer<BallotStateDto>` do DataStore. Formato
  do arquivo: `[versão: 1 byte = 1][IV: 12 bytes][ciphertext + tag GCM de 128 bits]`, AAD
  `"com.veronezzi.colaeleitoral:ballot:v1"`, conteúdo = JSON (kotlinx.serialization) de todas as escolhas.
  IV novo a cada escrita (gerado pelo Keystore). `DataStoreFactory.create(serializer, corruptionHandler,
  produceFile = { context.noBackupFilesDir.resolve("ballot.enc") })`.
- Falha de decifragem (`AEADBadTagException`, `KeyPermanentlyInvalidatedException`,
  `UnrecoverableKeyException`, arquivo truncado) → `CorruptionException` → `ReplaceFileCorruptionHandler`
  troca por um estado vazio e grava um aviso único: "Não foi possível ler as escolhas salvas neste
  aparelho; elas foram apagadas por segurança."
- O androidx.security-crypto (`EncryptedFile`, `EncryptedSharedPreferences`) está descontinuado
  desde a 1.1.0 e não deve ser usado [VERIFICADO: https://developer.android.com/jetpack/androidx/releases/security].
- "Apagar meus dados" (`BallotRepository.deleteAll` + `SettingsRepository.clear` +
  `ElectionRepository.clearCache` + cache do Coil): apaga o arquivo e a chave do Keystore e reinicia o
  onboarding.

### 5.3 Backup e transferência

- Já no scaffold: `android:allowBackup="false"`, `dataExtractionRules` (Android 12+) e
  `fullBackupContent` (Android 11 ou menos) excluindo todos os domínios. Manter.
- `noBackupFilesDir` é excluído do Auto Backup pelo sistema (defesa extra). Chaves do Keystore nunca
  são copiadas: um backup restaurado seria ilegível, por isso a exclusão é obrigatória.

### 5.4 Rede

- `network_security_config.xml` do scaffold: só HTTPS e só CAs do sistema. Manter.
- Sem certificate pinning: o TSE usa a CDN do Akamai e troca certificados; um pino errado derrubaria
  o app no dia da eleição. A allowlist de host (2.7) limita o tráfego a `divulgacandcontas.tse.jus.br`.
- Links externos só para domínios do TSE, abertos com `ACTION_VIEW` no navegador (sem WebView no app).
- Nenhum outro destino de rede: sem Firebase, sem Play Services, sem fontes baixáveis.

### 5.5 R8, logs e dependências

- Release com `isMinifyEnabled = true` e `isShrinkResources = true` (já no scaffold). Regras de consumo
  de kotlinx.serialization, Retrofit, Room e Hilt bastam; não adicionar `-keep` amplo.
- Remover logs em release: `-assumenosideeffects class android.util.Log { public static int v(...);
  public static int d(...); public static int i(...); }`. Nenhum `Log` com dados de escolha em nenhum
  build; nenhum `toString()` de `BallotPick` em mensagens de erro.
- Conferir o manifesto mesclado: remover permissões que bibliotecas adicionem sem uso
  (`com.google.android.gms.permission.AD_ID`, `FOREGROUND_SERVICE` se aparecer) com
  `tools:node="remove"`. Esperado: `INTERNET`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC`, e as do WorkManager
  (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`).
- Só código Kotlin/Java: sem `.so` no APK, o que atende o requisito de páginas de 16 KB; conferir com
  `zipalign -c -P 16 -v 4` e o APK Analyzer (6.1).

### 5.6 Tela, notificações e área de transferência

- `secureScreens` (padrão ligado): `FLAG_SECURE` nas telas Meu santinho, cola e detalhe com escolha
  salva; no Android 13+ também `setRecentsScreenshotEnabled(false)`. A geração da imagem da cola não
  depende de captura de tela, então o compartilhamento continua funcionando.
- Notificações nunca citam candidatos ou números (4.8).
- Não copiar escolhas para a área de transferência.

### 5.7 Ameaças consideradas

| Ameaça | Mitigação |
|---|---|
| Alguém com o celular desbloqueado vê as escolhas | Bloqueio opcional; `FLAG_SECURE`; nada em notificações |
| Backup na nuvem ou cópia entre aparelhos vaza escolhas | Backup desligado e excluído; arquivo cifrado com chave não exportável |
| Extração de arquivos (root, perícia) | Cifra AES-GCM em repouso, além da criptografia de arquivos do Android |
| Interceptação de rede | HTTPS com CAs do sistema; nenhuma escolha trafega |
| Dado do TSE manipulado/inesperado | Parser tolerante; allowlist de host; textos exibidos como texto puro; URLs de foto montadas pelo app |
| Engenharia social pelo compartilhamento | Aviso antes do primeiro compartilhamento; opção "Mostrar nomes" desligável |
| Desinformação eleitoral pelo app | Só dados do TSE, literais, com data e link oficial; textos sobre regras citam a lei |

---

## 6. Checklist de conformidade

### 6.1 Google Play (app novo, outubro de 2026)

| # | Requisito | O que o app faz | Fonte |
|---|---|---|---|
| P1 | Apps novos e atualizações com target Android 16 (API 36) desde 31/08/2026 (extensão possível até 01/11/2026) | `targetSdk = 36` | https://support.google.com/googleplay/android-developer/answer/11926878 |
| P2 | Conta pessoal criada após 13/11/2023: teste fechado com ≥ 12 testadores inscritos por ≥ 14 dias seguidos antes de pedir produção; análise costuma levar até 7 dias | Planejar o teste fechado já (7.2, Q2) | https://support.google.com/googleplay/android-developer/answer/14151465 |
| P3 | Verificação de desenvolvedor Android: identidade verificada e pacote registrado; obrigatória no Brasil desde 30/09/2026; instalação por adb/Android Studio não é afetada | Verificar a conta e registrar `com.veronezzi.colaeleitoral` (ID definitivo após o 1º upload) | https://android-developers.googleblog.com/2026/06/android-developer-verification.html ; https://support.google.com/googleplay/android-developer/answer/17134731 |
| P4 | Informação de governo sem afiliação: fontes fáceis de ver na descrição e na página da loja; deixar claro que o app não representa governo nem entidade política; preencher a declaração "Apps governamentais" | Aviso + link do TSE na descrição da loja, na 1ª execução e em Sobre; declarar "não é app governamental" | https://support.google.com/googleplay/android-developer/answer/9514050 |
| P5 | Deturpação: em conteúdo político, transparência extra sobre quem é o desenvolvedor e suas afiliações; nome e contato corretos | Nome e contato do desenvolvedor em Sobre e na loja; declarar ausência de afiliação | https://support.google.com/googleplay/android-developer/answer/9888689 |
| P6 | Falsificação de identidade: proibido usar emblema nacional ou marca de governo sugerindo afiliação | Sem Brasão, sem logos do TSE/Justiça Eleitoral; ícone e nome próprios | https://support.google.com/googleplay/android-developer/answer/9888374 |
| P7 | Comportamento enganoso: proibido conteúdo comprovadamente falso que interfira na votação ou sobre resultados | Só dados do TSE, literais, com data e link; regras de votação citam a lei | https://support.google.com/googleplay/android-developer/answer/9888077 |
| P8 | Política de privacidade no campo do Play Console e dentro do app; URL pública, ativa, sem geobloqueio, não PDF; com contato, dados tratados, segurança, retenção e exclusão | Tela no app + página pública (Q3) | https://support.google.com/googleplay/android-developer/answer/10144311 |
| P9 | Formulário Data safety obrigatório mesmo sem coleta; dado tratado só no aparelho não é declarado | Declarar "nenhum dado coletado nem compartilhado" (Q4) | https://support.google.com/googleplay/android-developer/answer/10787469 |
| P10 | Seções de conteúdo do app: política de privacidade, anúncios, acesso (login), público-alvo, classificação, apps de notícias; declarações de saúde e de recursos financeiros para todo app | Anúncios: não; acesso: sem login; notícias: não; saúde: nenhum recurso; financeiro: nenhum | https://support.google.com/googleplay/android-developer/answer/9859455 ; https://support.google.com/googleplay/android-developer/answer/14738291 ; https://support.google.com/googleplay/android-developer/answer/13849271 |
| P11 | Público-alvo: incluir menores de 13 aplica a política Famílias; declarar anúncios antes | Público 16-17 e 18+ (voto facultativo aos 16); sem anúncios | https://support.google.com/googleplay/android-developer/answer/9867159 |
| P12 | Questionário de classificação indicativa (IARC) obrigatório | Responder: sem violência, sem conteúdo gerado por usuário, sem compras | https://support.google.com/googleplay/android-developer/answer/9859655 |
| P13 | Páginas de memória de 16 KB para apps com target Android 15+ (novos apps e atualizações desde 01/11/2025) | Só Kotlin/Java, sem `.so`; checar APK Analyzer e `zipalign -c -P 16 -v 4` | https://developer.android.com/guide/practices/page-sizes ; https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html |
| P14 | Qualidade técnica: crash percebido < 1,09%, ANR < 0,47%, wake locks parciais excessivos < 5%; a partir de fev/2027, otimização mínima de 25% (apps com > 10 MB de DEX) | R8 ligado; nada de wake lock próprio; WorkManager só para o lembrete | https://support.google.com/googleplay/android-developer/answer/17492799 |
| P15 | Textos da loja: nome ≤ 30, descrição curta ≤ 80, completa ≤ 4.000 caracteres; sem emoji, sem MAIÚSCULAS fora da marca, sem alegação de ranking/preço/promoção, sem depoimentos anônimos | Rascunho em 6.3 | https://support.google.com/googleplay/android-developer/answer/9859152 ; https://support.google.com/googleplay/android-developer/answer/9898842 |
| P16 | Gráficos: ícone 512 × 512 PNG 32 bits ≤ 1 MB; imagem de destaque 1024 × 500 JPEG ou PNG 24 bits sem alfa; ≥ 2 capturas, 320 a 3.840 px, lado maior ≤ 2× o menor (≥ 4 com ≥ 1080 px para destaque); sem "melhor", "#1", "top" | Capturas reais do app, sem chamadas promocionais | https://support.google.com/googleplay/android-developer/answer/9866151 |
| P17 | Notificações: permissão `POST_NOTIFICATIONS` em tempo de execução no Android 13+, pedida em contexto | Só ao ligar o lembrete | https://developer.android.com/develop/ui/views/notifications/notification-permission |
| P18 | Comportamento do target 36: edge-to-edge sem opção de saída, volta preditiva, orientação livre em telas ≥ 600 dp | 4.11 | https://developer.android.com/about/versions/16/behavior-changes-16 |
| P19 | ID de publicidade: só apps que usam declaram `AD_ID` | Sem `AD_ID` no manifesto mesclado | https://support.google.com/googleplay/android-developer/answer/6048248 |

### 6.2 Regras eleitorais e LGPD

| # | Regra | Efeito no app | Fonte |
|---|---|---|---|
| L1 | "Fica vedado portar aparelho de telefonia celular, máquinas fotográficas e filmadoras, dentro da cabina de votação" (Lei 9.504/97, art. 91-A, parágrafo único); a Res. TSE 23.751/2026, art. 137, estende a qualquer equipamento capaz de registrar o voto, mesmo desligado | A saída principal é a cola em papel (PDF/imagem para imprimir); o app avisa na Home, na cola e na notificação | https://www2.camara.leg.br/legin/fed/lei/1997/lei-9504-30-setembro-1997-365408-normaatualizada-pl.html ; https://news.folhadoprogresso.com.br/eleicoes-2026-oculos-inteligentes-e-outros-dispositivos-sao-proibidos-na-cabine/ ; texto oficial: https://www.tse.jus.br/legislacao/compilada/res/2026/resolucao-no-23-751-de-26-de-fevereiro-de-2026 |
| L2 | Levar cola de papel é permitido e incentivado pelo TSE, que oferece modelos para impressão | Cola em papel com o layout da urna | https://sbtnews.sbt.com.br/noticia/politica/nunes-marques-incentiva-uso-de-colinha-de-papel-em-votacao ; https://www.cnnbrasil.com.br/eleicoes/eleicoes-2026-veja-a-ordem-de-votacao-na-urna-eletronica/ ; https://www.tse.jus.br/comunicacao/noticias/2026/Setembro/glossario-eleitoral-explica-como-funciona-a-cola-eleitoral |
| L3 | Ordem dos painéis: geral = Deputado Federal, Deputado Estadual ou Distrital, Senador, Governador, Presidente; municipal = Vereador, Prefeito (Lei 9.504/97, art. 59, § 3º) | `OfficeRules.urnaOrder` | Lei 9.504/97 (link L1) |
| L4 | Dígitos: partido (2) para majoritários; Senado +1; Câmara dos Deputados +2; Assembleias, Câmara Legislativa e Câmaras Municipais +3 (Lei 9.504/97, art. 15; Res. TSE 23.609/2019, art. 14) | `OfficeRules.digitCount`; número do partido derivado | Lei 9.504/97 (link L1); https://bookdown.org/informartizar/normas_eleitorais/res23609.html ; https://www.tse.jus.br/legislacao/compilada/res/2019/resolucao-no-23-609-de-18-de-dezembro-de-2019 |
| L5 | Senado renovado alternadamente em 1/3 e 2/3 (CF art. 46, § 2º): 2 vagas por UF em 2026; voto repetido no mesmo candidato é nulo (Res. TSE 23.751/2026) | Dois votos para Senador; recusa duplicado | https://www2.camara.leg.br/legin/fed/consti/1988/constituicao-1988-5-outubro-1988-322142-normaatualizada-pl.html ; https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_vagas/consulta_vagas_2026.zip ; CNN (link L2) |
| L6 | 2º turno no último domingo de outubro para Presidente, Governador e Prefeito em municípios com mais de 200 mil eleitores (CF arts. 28, 29 II e 77); 25/10/2026 | `ElectionCalendar`, `OfficeRules.hasRunoff` | Constituição (link L5) |
| L7 | Votação das 8h às 17h, horário de Brasília, em todo o país (Res. TSE 23.751/2026) | Lembrete às 7h de Brasília; textos da Home | https://www.tse.jus.br/comunicacao/noticias/2026/Setembro/faltam-26-dias-votacao-comeca-e-termina-no-mesmo-horario-em-todo-o-pais |
| L8 | Enquetes proibidas no período de campanha (Lei 9.504/97, art. 33, § 5º); enquete = levantamento sem plano amostral que permita inferir a ordem dos candidatos (Res. TSE 23.600/2019, art. 23, § 1º, red. da Res. 23.727/2024) | Nenhuma agregação de escolhas, nenhum ranking, nenhum "mais acessados" | Lei 9.504/97 (link L1); https://www.tse.jus.br/legislacao/compilada/res/2024/resolucao-no-23-727-de-27-de-fevereiro-de-2024 |
| L9 | No dia da eleição é crime divulgar propaganda de partidos ou candidatos e publicar novos conteúdos ou impulsionar conteúdos de campanha na internet (Lei 9.504/97, art. 39, § 5º, III e IV) | O app não exibe propaganda, nem links de sites/redes de campanha (v1); notificações neutras | Lei 9.504/97 (link L1) |
| L10 | Voto de legenda é válido nas eleições proporcionais (Lei 9.504/97, art. 59, §§ 1º e 2º) | Fora da v1 (Q6) | Lei 9.504/97 (link L1) |
| L11 | Eleitor no exterior vota só para Presidente (Código Eleitoral, art. 225) | Opção "Exterior" | https://www2.camara.leg.br/legin/fed/lei/1960-1969/lei-4737-15-julho-1965-356297-normaatualizada-pl.html |
| L12 | Opinião política é dado pessoal sensível (LGPD art. 5º, II; art. 11); dado de acesso público deve respeitar finalidade, boa-fé e interesse público (art. 7º, § 3º) | Escolhas só no aparelho, cifradas; dados de candidatos mínimos e literais | https://www2.camara.leg.br/legin/fed/lei/2018/lei-13709-14-agosto-2018-787077-normaatualizada-pl.html |
| L13 | Dados abertos do TSE sob Creative Commons Atribuição | "Fonte dos dados: TSE" em Sobre, na cola e na loja | https://dadosabertos.tse.jus.br/api/3/action/package_show?id=candidatos-2026 |

### 6.3 Rascunho da página da loja (pt-BR)

- **Nome (28):** `Meu Santinho: cola eleitoral`
- **Descrição curta (69):** `Monte sua cola eleitoral com dados públicos do TSE. App independente.`
- **Descrição completa, 1º parágrafo (obrigatório pela P4):** "O Meu Santinho é um aplicativo
  independente, sem vínculo com o Tribunal Superior Eleitoral (TSE), a Justiça Eleitoral, órgãos de
  governo, partidos ou candidatos. Os dados de candidaturas vêm do sistema público DivulgaCandContas do
  TSE: https://divulgacandcontas.tse.jus.br/divulga/". Depois: o que o app faz, a lembrança de que o
  celular não entra na cabine, privacidade (sem conta, sem anúncios, escolhas só no aparelho).
- **Categoria:** Ferramentas (evitar "Notícias e revistas").

---

## 7. Riscos e questões em aberto

### 7.1 Riscos

| # | Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|---|
| R1 | Akamai bloqueia o OkHttp do app também em redes brasileiras | média | crítico | Validar primeiro (7.3); cache offline e pré-carregamento; mensagem `Blocked` com link para o site oficial; plano B de dados abertos (2.10, Q1); nunca impersonar navegador |
| R2 | O TSE muda caminhos ou campos sem aviso | média | alto | Parser tolerante, fixtures reais, descarte de itens inválidos sem derrubar a lista, cédula com cargos da lei (`defaultCodes`) |
| R3 | Instabilidade ou limite de taxa no dia da eleição | alta | médio | Cache-first; sem polling; limite de concorrência; backoff com `Retry-After` |
| R4 | Prazo do Play (teste fechado de 14 dias + análise) impede produção antes de 25/10/2026 | alta | médio | Teste fechado já (testadores usam no 2º turno); produção mirando 2028; ou conta de organização (Q2) |
| R5 | 2º turno detectado tarde: o TSE pode demorar a marcar "2º turno" | média | médio | Entre os turnos, se nenhum candidato estiver marcado, a lista do cargo permite "Mostrar todos" com aviso; atualizar ao abrir o app |
| R6 | Situação de registro muda (julgamentos até a véspera) e a cola fica velha | média | médio | TTL de 1 h na semana da eleição; a cédula compara com o status mais novo e mostra a mudança literal |
| R7 | Escolhas perdidas (chave invalidada, troca de aparelho, desinstalação) | baixa | médio | Explicar "ficam só neste aparelho"; o próprio usuário pode imprimir ou guardar a imagem |
| R8 | Vazamento por compartilhamento, captura ou notificação | baixa | alto | Aviso antes de compartilhar; `FLAG_SECURE`; notificação genérica |
| R9 | Nome: já existe o projeto web "Meu Santinho 2026" (https://github.com/Meu-Santinho/meu-santinho/blob/main/web/README.md); "santinho" é o nome popular do material de campanha | média | baixo | Decisão do usuário (Q5) antes do 1º upload |
| R10 | Formato do link oficial (E7) muda no SPA do TSE | baixa | baixo | Preferir `txLink` da própria API; fallback para a home |
| R11 | Listas grandes (1.431 itens) e respostas de ~3 MB em aparelhos fracos | média | baixo | Parse em `Dispatchers.IO`, inserção em lote, `LazyColumn` com chaves, filtro em memória fora da main |

### 7.2 Decisões para o usuário

- **Q1. Plano B de dados abertos (2.10).** Recomendação: deixar a interface `CandidateRemoteSource`
  pronta agora e implementar a fonte de dados abertos só se o teste 7.3 mostrar bloqueio. É a única
  alternativa legítima conhecida; a "ponte" por WebView/página do TSE e a impersonação de TLS estão
  descartadas.
- **Q2. Conta do Play e prazo.** Conta pessoal ou de organização? Criada antes ou depois de
  13/11/2023? Meta realista: teste fechado durante o 2º turno de 2026, produção para 2028.
- **Q3. Política de privacidade e contato.** URL pública (por exemplo GitHub Pages do repositório) e
  e-mail de contato do desenvolvedor para a loja e para a tela Sobre.
- **Q4. Data safety.** Recomendação: "nenhum dado coletado nem compartilhado" (escolhas só no aparelho;
  o código da UF/município vai ao TSE só para consultar dado público). Se preferir uma postura mais
  conservadora: declarar "Localização aproximada: coletada, não compartilhada, necessária para o
  funcionamento".
- **Q5. Nome do app** ("Meu Santinho" ou alternativa) e o `applicationId`, permanente após o 1º upload.
- **Q6. Voto de legenda, branco e nulo na cola.** A lei admite legenda em cargos proporcionais; sugestão
  para a v1.1: permitir só legenda (2 dígitos do partido), sem branco/nulo.
- **Q7. Eleições suplementares** (`eleicao/suplementares/{ano}/{uf}`): fora da v1?
- **Q8. Sites e redes do candidato** (`sites` da API): ficam fora da v1 por neutralidade e pela regra do
  dia da eleição (L9). Confirmar.

### 7.3 Validação obrigatória num aparelho no Brasil (antes da UI)

Fazer com um APK de debug instalado por adb (não afetado pela verificação de desenvolvedor), em
**dados móveis e em Wi-Fi residencial**, registrando status, `Content-Type`, `Content-Encoding`,
`Cache-Control` e tamanho, sem salvar corpos com dados pessoais:

1. E1 `eleicao/ordinarias`: 200 com JSON? É o ponto mais importante. Se der 403, registrar e acionar
   Q1, sem testar variações de cabeçalho (`Referer`, User-Agent de navegador) antes da decisão.
2. E3 `eleicao/listar/municipios/20322002026/SP/cargos` e `.../BR/cargos`: formato; presença dos
   cargos 2, 4, 9 e 10.
3. E4 `candidatura/listar/2026/SP/20322002026/6/candidatos` (1.132 itens): tempo, tamanho, gzip.
4. E5 de um presidenciável (`BR`) e de um senador (UF): `vices` com textos de `ds_CARGO`
   ("1º Suplente"?) e `situacaoVice`.
5. E6 foto: tipo, tamanho e resposta para candidato com `fotoUrlPublicavel = false`.
6. E2 com `20322002026` e com `2045202024`.
7. A partir de 05/10/2026: `descricaoTotalizacao = "2º turno"` em `listar/2026/BR/20322002026/1` e no
   cargo 3 de uma UF com 2º turno; E1 continua com uma entrada só?
8. Com VPN ligada: 403 → mensagem `Blocked`.
9. Links E7 (`txLink` e formato `{uf}/{uf}/...`) abrindo no Chrome do Android.
10. Android 8.0 (API 26): gerar a chave AES-GCM no Keystore, cifrar e decifrar.

Não fazer teste de carga nem repetir chamadas em laço contra o TSE.

---

## 8. Referências

API e amostras: https://github.com/augusto-herrmann/divulgacandcontas-doc ·
https://github.com/mpbarbosa/public_apis/blob/main/tse/README.md ·
https://github.com/trifenol/meuvotolgbt/blob/main/docs/campos_api.md ·
https://github.com/AdriellyTI/TP_1_Ciencia-de-Dados (eleicoes_ordinarias + proveniência) ·
https://github.com/hermesalvesbr/checkin-eleitoral (municípios e eleicao-atual 2024) ·
https://github.com/epbsantos/SW2-02-22-infografico (listagem 2022) ·
https://github.com/leovargasdev/o-meu-voto (detalhe 2024 com vice) ·
https://github.com/thiago-salvador/puxa-ficha (coletas de 2026) ·
https://github.com/turicas/eleicoes-brasil/blob/develop/divulgacandcontas.py ·
https://github.com/raschmitt/auditoria-sites-candidatos-2026/blob/master/docs/METODOLOGIA.md ·
https://github.com/flaviojmendes/colinhaeleitoral ·
https://github.com/murillo-ferrari/divulgacand2024/blob/main/consulta_gastos/busca_candidatos.js ·
https://github.com/meucandidato/tse-apidoc/blob/master/swagger.yaml

Dados abertos do TSE: https://dadosabertos.tse.jus.br/api/3/action/package_show?id=candidatos-2026 ·
https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_cand/consulta_cand_2026.zip ·
https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_cand_complementar/consulta_cand_complementar_2026.zip ·
https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_vagas/consulta_vagas_2026.zip

Legislação: Lei 9.504/97 https://www2.camara.leg.br/legin/fed/lei/1997/lei-9504-30-setembro-1997-365408-normaatualizada-pl.html ·
Constituição https://www2.camara.leg.br/legin/fed/consti/1988/constituicao-1988-5-outubro-1988-322142-normaatualizada-pl.html ·
Código Eleitoral https://www2.camara.leg.br/legin/fed/lei/1960-1969/lei-4737-15-julho-1965-356297-normaatualizada-pl.html ·
LGPD https://www2.camara.leg.br/legin/fed/lei/2018/lei-13709-14-agosto-2018-787077-normaatualizada-pl.html ·
Res. TSE 23.751/2026 https://www.tse.jus.br/legislacao/compilada/res/2026/resolucao-no-23-751-de-26-de-fevereiro-de-2026 ·
Res. TSE 23.609/2019 https://bookdown.org/informartizar/normas_eleitorais/res23609.html ·
Res. TSE 23.727/2024 https://www.tse.jus.br/legislacao/compilada/res/2024/resolucao-no-23-727-de-27-de-fevereiro-de-2024

Imprensa e TSE (2026): https://www.cnnbrasil.com.br/eleicoes/eleicoes-2026-veja-a-ordem-de-votacao-na-urna-eletronica/ ·
https://sbtnews.sbt.com.br/noticia/politica/nunes-marques-incentiva-uso-de-colinha-de-papel-em-votacao ·
https://news.folhadoprogresso.com.br/eleicoes-2026-oculos-inteligentes-e-outros-dispositivos-sao-proibidos-na-cabine/ ·
https://www.tse.jus.br/comunicacao/noticias/2026/Marco/eleicoes-2026-conheca-a-ordem-de-votacao-na-urna-eletronica ·
https://www.tse.jus.br/comunicacao/noticias/2026/Setembro/faltam-26-dias-votacao-comeca-e-termina-no-mesmo-horario-em-todo-o-pais

Google e Android: ver as URLs da seção 6.1; Coil https://coil-kt.github.io/coil/network/ ;
security-crypto https://developer.android.com/jetpack/androidx/releases/security
