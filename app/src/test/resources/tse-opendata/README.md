# Fixtures reais dos dados abertos do TSE (candidaturas 2026)

Usadas pelos testes da fonte de dados alternativa (`TseOpenDataSource`), que o app usa quando a API
DivulgaCandContas recusa a consulta (`AppError.Blocked`). Nenhum valor foi inventado: as linhas
foram copiadas dos arquivos oficiais e só tiveram colunas apagadas.

## Origem

| Arquivo de origem | Baixado em | `Last-Modified` | `ETag` | Tamanho | SHA-256 |
|---|---|---|---|---|---|
| https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_cand/consulta_cand_2026.zip | 2026-10-04T05:24Z | Sat, 03 Oct 2026 22:36:18 GMT | `"30f0c0-65cf74703f687"` | 3.207.360 bytes | `7456fcc61911059ddd65d9b189089993ca6cb90ef4fad88614a3f50c668503df` |
| https://cdn.tse.jus.br/estatistica/sead/odsele/consulta_cand_complementar/consulta_cand_complementar_2026.zip | 2026-10-04T05:24Z | Sat, 03 Oct 2026 22:36:13 GMT | `"13d7d9-65cf746bf8356"` | 1.300.441 bytes | `80346b44ea087db182b052a941c38ad5838cb33a1f3ce1686198b425a8f2b58b` |

Os CSVs internos foram gerados pelo TSE em 03/10/2026 às 19:31:31 (`DT_GERACAO`, `HH_GERACAO`).
Licença: Creative Commons Atribuição (catálogo
https://dadosabertos.tse.jus.br/api/3/action/package_show?id=candidatos-2026). Fonte: Tribunal
Superior Eleitoral (TSE), dados públicos, licença CC BY.

## Conteúdo

| Arquivo | Entrada do ZIP | Linhas |
|---|---|---|
| `consulta_cand_2026_AL.csv` | `consulta_cand_2026_AL.csv` | 26 |
| `consulta_cand_2026_BR.csv` | `consulta_cand_2026_BR.csv` | 8 |
| `consulta_cand_complementar_2026_AL.csv` | `consulta_cand_complementar_2026_AL.csv` | 26 |
| `consulta_cand_complementar_2026_BR.csv` | `consulta_cand_complementar_2026_BR.csv` | 8 |

Linhas escolhidas por `SQ_CANDIDATO`, na ordem abaixo, para cobrir os casos que o parser precisa
tratar:

- **AL**: Governador 15 (coligação) e 80 (partido isolado) com os vices; Senador 151, 156 e 456
  (este sem suplentes na fixture), com todos os suplentes do 151 e do 156, inclusive os substituídos
  (`ST_SUBSTITUIDO = S`); Deputado Federal 1313 (federação), 2500 duas vezes (registro indeferido e
  novo registro deferido com o mesmo número), 7001 (renúncia, fora da urna) e 2727 (deferido com
  recurso); Deputado Estadual 13000, 27444 (indeferido com recurso) e 25125 (renúncia).
- **BR**: Presidente 13, 22 e 28 (duas candidaturas com o número 28: renúncia e indeferimento) e os
  quatro vices correspondentes, um deles substituído.

O formato é o original, byte a byte: Latin-1, `;` como separador, texto entre aspas, números sem
aspas, CRLF, sem BOM, cabeçalho completo (50 colunas no `consulta_cand`, 49 no complementar). O
`.gitattributes` desta pasta impede que o Git troque CRLF por LF.

## Redação (LGPD)

Antes de gravar qualquer arquivo, o script trocou por `""` o valor de **toda coluna que o app não
lê**, o que inclui todos os dados pessoais e sensíveis. O cabeçalho ficou completo, então o parser é
testado contra o layout real.

- `consulta_cand`: `NR_CPF_CANDIDATO`, `DS_EMAIL`, `SG_UF_NASCIMENTO`, `DT_NASCIMENTO`,
  `NR_TITULO_ELEITORAL_CANDIDATO`, `CD_GENERO`, `DS_GENERO`, `CD_GRAU_INSTRUCAO`, `DS_GRAU_INSTRUCAO`,
  `CD_ESTADO_CIVIL`, `DS_ESTADO_CIVIL`, `CD_COR_RACA`, `DS_COR_RACA`, `CD_OCUPACAO`, `DS_OCUPACAO`.
- `consulta_cand_complementar`: `CD_NACIONALIDADE`, `DS_NACIONALIDADE`, `CD_MUNICIPIO_NASCIMENTO`,
  `NM_MUNICIPIO_NASCIMENTO`, `NR_IDADE_DATA_POSSE`, `ST_QUILOMBOLA`, `CD_ETNIA_INDIGENA`,
  `DS_ETNIA_INDIGENA`, `VR_DESPESA_MAX_CAMPANHA`, `ST_REELEICAO`, `ST_DECLARAR_BENS`,
  `NR_PROTOCOLO_CANDIDATURA`, `NR_PROCESSO`, `ST_PREST_CONTAS`, `DT_ACEITE_CANDIDATURA`,
  `CD_SITUACAO_CASSACAO`, `DS_SITUACAO_CASSACAO`, `CD_SITUACAO_CASSACAO_MIDIA`,
  `DS_SITUACAO_CASSACAO_MIDIA`, `CD_SITUACAO_DIPLOMA`, `DS_SITUACAO_DIPLOMA`, `CD_GENERO_FEFC`,
  `DS_GENERO_FEFC`, `CD_COR_RACA_FEFC`, `DS_COR_RACA_FEFC`.

Ficaram só identificação da eleição e da UE, cargo, número, nomes (de urna, civil e social),
partido, federação, coligação e as colunas de situação e totalização. Os ZIPs originais ficaram
fora do repositório e foram apagados depois da extração.

## Como reproduzir

O script percorre cada linha do ZIP em memória, separa os campos sem perder as aspas originais,
mantém só as linhas da lista e, para cada coluna fora da lista de colunas mantidas, grava `""`. Em
seguida confere que nenhuma coluna pessoal ficou preenchida. As listas de colunas e de
`SQ_CANDIDATO` são as descritas acima.
