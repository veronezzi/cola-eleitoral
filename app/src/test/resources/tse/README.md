# Fixtures reais da API DivulgaCandContas (TSE)

Todas as respostas abaixo são **reais**, publicadas pelo TSE e arquivadas por projetos
de terceiros no GitHub. Nenhum valor foi inventado. A API não pôde ser chamada a partir do
ambiente de desenvolvimento (HTTP 403 do Akamai para IPs fora do Brasil), então estas cópias
são a melhor amostra disponível. Coletadas em 2026-10-04.

| Arquivo | Endpoint de origem | Eleição | Fonte da cópia | Alterações |
|---|---|---|---|---|
| `eleicoes-ordinarias.json` | `GET /divulga/rest/v1/eleicao/ordinarias` (coletado em 2026-09-18T17:56Z, HTTP 200) | todas (2004–2026) | https://github.com/AdriellyTI/TP_1_Ciencia-de-Dados/blob/main/projeto/dados_brutos/tse/divulgacandcontas_eleicoes_ordinarias.json (proveniência em `projeto/dados_brutos/proveniencia/proveniencia_tse.csv`) | nenhuma (o coletor reindentou o JSON; o Git trocou CRLF por LF) |
| `municipios.json` | `GET /divulga/rest/v1/eleicao/buscar/AC/2045202024/municipios` | Municipais 2024 | https://github.com/hermesalvesbr/checkin-eleitoral/blob/main/public/municipios_AC.json (gerado por `script/fetchMunicipiosPorUF.js`) | nenhuma (reindentado com `JSON.stringify(data, null, 2)`) |
| `candidatos-listar.json` | `GET /divulga/rest/v1/candidatura/listar/2022/BR/2040602022/1/candidatos` | Geral 2022, Presidente | https://github.com/epbsantos/SW2-02-22-infografico/blob/main/candidatos.json (CC0) | nenhuma |
| `candidato-buscar.json` | `GET /divulga/rest/v1/candidatura/buscar/2026/SP/20322002026/candidato/250002539612` | Geral 2026, Deputado Federal | https://github.com/trifenol/meuvotolgbt/blob/main/tests/fixtures/candidato_250002539612.json (MIT) | identificadores pessoais já redigidos na origem; redigimos também atributos sensíveis (ver abaixo) |
| `candidato-buscar-com-vice.json` | `GET /divulga/rest/v1/candidatura/buscar/2024/81809/2045202024/candidato/240001918430` (caminho inferido do conteúdo: `id`, `ufCandidatura`, `eleicao.id`) | Municipais 2024, Prefeito (com vice) | https://github.com/leovargasdev/o-meu-voto/blob/master/src/data/mock-candidato.json | redação de dados pessoais (ver abaixo) |

## Redação (LGPD)

Valores trocados por `"<REDIGIDO>"` (a estrutura e os demais valores ficam como publicados):
`cpf`, `tituloEleitor`, `dataDeNascimento`, `emails` (vira `["<REDIGIDO>"]`), `descricaoCorRaca`,
`infoComplementar.{identidadeGenero, orientacaoSexual, quilombola, dsTipoEtniaIndigena, nmEncarregadoDados, txCanalComunicacao}`
e `legenda.{nmEncarregadoDados, txCanalComunicacao, telefones, emails}`, quando não nulos.

O app **não declara esses campos** nos DTOs: com `ignoreUnknownKeys = true` eles são descartados no
parse. Os testes podem usar estas fixtures para garantir isso.

## O que cada fixture cobre

- `eleicoes-ordinarias.json`: `id` de 11 dígitos (`20322002026`, não cabe em `Int`), `dataEleicao`
  nula (2010, 2006, 2004), `turno` sempre `null`, `tipoAbrangencia` `F`/`M`, duas eleições em 2020.
- `municipios.json`: `codigo` de município como **texto com zero à esquerda** (`"01120"`), `capital`
  sempre `false`, objeto `estado`.
- `candidatos-listar.json`: situações `Deferido`, `Indeferido` e `Cancelado`; dois candidatos com o
  mesmo número (14); `partido.numero = 0` e `fotoUrl = null` na listagem; `nomeColigacao` com nome de
  coligação ou só a sigla do partido.
- `candidato-buscar.json`: esquema de 2026 (`dataUltimaAtualizacao` como texto `"2026-09-18 14:39"`,
  `fotoUrl`, `fotoUrlPublicavel`, `descricaoTipoDrap = "Federação"`, `descricaoSituacaoCandidato =
  "Consta da urna"`, `candidatoApto`, `eleicoesAnteriores[].txLink`), `vices = null`.
- `candidato-buscar-com-vice.json`: `vices[]` preenchido (chaves `sq_CANDIDATO`, `nm_URNA`,
  `nr_CANDIDATO` como texto, `ds_CARGO`, `urlFoto`...), `partido.numero` real no detalhe,
  `descricaoSituacao = "Aguardando julgamento"`, `descricaoTipoDrap = "Partido Isolado"`.

Os dados de candidaturas são públicos (TSE; os dados abertos do TSE são publicados sob Creative
Commons Atribuição). Os repositórios citados apenas arquivaram as respostas.
