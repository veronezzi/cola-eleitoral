#!/usr/bin/env bash
# Valida a ficha da loja (formato do fastlane supply) contra os limites do Google Play.
#
# Uso: scripts/check-store-metadata.sh [--release] [DIRETORIO_DO_IDIOMA]
#   DIRETORIO_DO_IDIOMA  padrão: fastlane/metadata/android/pt-BR
#   --release            também exige a política de privacidade pronta e publicada. Os problemas da
#                        política, que sem --release são só avisos, viram erros, e a URL pública
#                        precisa responder (curl -fsSI) com o mesmo conteúdo de docs/privacidade/index.html.
# Sempre: docs/privacidade/index.html tem de estar atualizado em relação a docs/privacidade.md
# (scripts/render-privacy-page.py --check) e as duas versões têm de citar o e-mail de contato, a URL
# pública e o nome do desenvolvedor, sem [PREENCHER: ...] nem marcadores example.com. Esses valores vêm
# de COLA_ELEITORAL_CONTACT_EMAIL, COLA_ELEITORAL_PRIVACY_POLICY_URL e COLA_ELEITORAL_DEVELOPER_NAME ou,
# se vazias, de colaEleitoral.contactEmail, .privacyPolicyUrl e .developerName (gradle.properties).
#
# Limites conferidos (https://support.google.com/googleplay/android-developer/answer/9859152 e
# https://support.google.com/googleplay/android-developer/answer/9866151):
#   título <= 30, descrição curta <= 80, descrição completa <= 4.000, novidades <= 500 caracteres;
#   sem emoji; sem termos promocionais no título e na descrição curta; aviso de independência e
#   link oficial do TSE no começo da descrição completa (política de informações governamentais);
#   ícone 512x512 PNG 32 bits (RGBA) <= 1 MB; destaque 1024x500 PNG 24 bits (sem alfa) ou JPEG
#   <= 15 MB; capturas: 2 a 8 por tipo, lados de 320 a 3.840 px, lado maior <= 2x o menor.
# Sai com 1 se houver erro. Avisos (por exemplo, capturas ainda não feitas) não falham.
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release=0
dir=""
for arg in "$@"; do
  case "$arg" in
    --release) release=1 ;;
    -h | --help) sed -n '2,22p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) dir="$arg" ;;
  esac
done
dir="${dir:-$root/fastlane/metadata/android/pt-BR}"

errors=0
warnings=0
ok() { printf 'OK     %s\n' "$*"; }
err() { printf 'ERRO   %s\n' "$*"; errors=$((errors + 1)); }
warn() { printf 'AVISO  %s\n' "$*"; warnings=$((warnings + 1)); }

# Contagem de caracteres (não de bytes): exige um locale UTF-8.
utf8_locale=""
for candidate in C.UTF-8 C.utf8 en_US.UTF-8 en_US.utf8 pt_BR.UTF-8; do
  if [ "$(LC_ALL=$candidate bash -c 's="ção"; echo ${#s}' 2>/dev/null)" = 3 ]; then
    utf8_locale=$candidate
    break
  fi
done
if [ -z "$utf8_locale" ]; then
  echo "ERRO   nenhum locale UTF-8 disponível para contar caracteres" >&2
  exit 2
fi
export LC_ALL=$utf8_locale

have_perl=0
command -v perl > /dev/null 2>&1 && have_perl=1

# Conteúdo sem as quebras de linha finais (o Play Console não as conta).
read_text() { cat -- "$1"; }

check_text() { # arquivo limite rótulo
  local file="$1" limit="$2" label="$3" text count
  if [ ! -f "$file" ]; then
    err "$label: arquivo ausente (${file#"$root"/})"
    return 1
  fi
  text="$(read_text "$file")"
  count=${#text}
  if [ "$count" -eq 0 ]; then
    err "$label: vazio"
  elif [ "$count" -gt "$limit" ]; then
    err "$label: $count caracteres (limite $limit)"
  else
    ok "$label: $count/$limit caracteres"
  fi
  if [ "$have_perl" = 1 ] &&
    perl -CSD -ne 'exit 1 if /[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}\x{FE0F}\x{200D}]/' "$file"; then
    :
  elif [ "$have_perl" = 1 ]; then
    err "$label: contém emoji ou símbolo pictográfico"
  fi
  return 0
}

check_single_line() { # arquivo rótulo
  local lines
  lines=$(grep -c '' -- "$1" 2> /dev/null || echo 0)
  [ "$lines" -le 1 ] || err "$2: deve ter uma linha só (tem $lines)"
}

check_promotional() { # arquivo rótulo
  [ "$have_perl" = 1 ] || return 0
  local found
  found=$(perl -CSD -Mutf8 -ne 'while (/(?<!\w)(gr[aá]tis|gratuit[oa]s?|melhor(es)?|top|n[º°o]\.?\s?1|n[uú]mero\s+1|#\s?1|promo[cç][aã]o|desconto|oferta|baixe\s+j[aá]|instale\s+j[aá]|free|best|new)(?!\w)/gi) { print "$1\n" }' -- "$1" | sort -u | paste -sd, -)
  if [ -n "$found" ]; then
    err "$2: termo promocional ou de ranking não permitido: $found"
  fi
}

png_info() { # arquivo -> "largura altura profundidade tipo_de_cor" (vazio se não for PNG)
  local sig
  sig=$(od -An -tx1 -N8 -- "$1" 2> /dev/null | tr -d ' \n')
  [ "$sig" = "89504e470d0a1a0a" ] || return 1
  od -An -tu1 -j16 -N10 -- "$1" | awk '{
    printf "%d %d %d %d\n", ($1*16777216)+($2*65536)+($3*256)+$4, ($5*16777216)+($6*65536)+($7*256)+$8, $9, $10 }'
}

image_size() { # arquivo -> "largura altura" (PNG pelo cabeçalho; JPEG pelo comando file)
  local info
  if info=$(png_info "$1"); then
    echo "$info" | awk '{ print $1, $2 }'
    return 0
  fi
  if command -v file > /dev/null 2>&1; then
    file -b -- "$1" | grep -oE '[0-9]+ ?x ?[0-9]+' | tail -1 | tr -d ' ' | tr 'x' ' '
    return 0
  fi
  return 1
}

file_bytes() { wc -c < "$1" | tr -d ' '; }

echo "Ficha da loja em ${dir#"$root"/}"

# ---------------------------------------------------------------------------------------- textos
check_text "$dir/title.txt" 30 "title.txt" && check_single_line "$dir/title.txt" "title.txt" &&
  check_promotional "$dir/title.txt" "title.txt"
check_text "$dir/short_description.txt" 80 "short_description.txt" &&
  check_single_line "$dir/short_description.txt" "short_description.txt" &&
  check_promotional "$dir/short_description.txt" "short_description.txt"
if check_text "$dir/full_description.txt" 4000 "full_description.txt"; then
  # Política de informações governamentais: independência e fonte oficial logo no começo.
  head_text="$(read_text "$dir/full_description.txt")"
  head_text="${head_text:0:600}"
  lower="$(printf '%s' "$head_text" | tr '[:upper:]' '[:lower:]')"
  case "$lower" in
    *"sem vínculo"* | *"não tem vínculo"*) ok "full_description.txt: aviso de independência nos primeiros 600 caracteres" ;;
    *) err "full_description.txt: o aviso de independência (\"sem vínculo\") precisa estar nos primeiros 600 caracteres" ;;
  esac
  case "$head_text" in
    *"https://divulgacandcontas.tse.jus.br"*) ok "full_description.txt: link oficial do TSE nos primeiros 600 caracteres" ;;
    *) err "full_description.txt: o link https://divulgacandcontas.tse.jus.br precisa estar nos primeiros 600 caracteres" ;;
  esac
  if grep -q 'licença CC BY' -- "$dir/full_description.txt"; then
    ok "full_description.txt: atribuição CC BY dos dados do TSE"
  else
    err "full_description.txt: falta a atribuição \"Fonte: ... TSE ..., licença CC BY\""
  fi
fi

changelogs=("$dir"/changelogs/*.txt)
if [ ! -e "${changelogs[0]}" ]; then
  err "changelogs/: nenhum arquivo <versionCode>.txt"
else
  for changelog in "${changelogs[@]}"; do
    name="changelogs/$(basename "$changelog")"
    [[ "$(basename "$changelog" .txt)" =~ ^[0-9]+$ ]] || err "$name: o nome deve ser o versionCode (ex.: 1.txt)"
    check_text "$changelog" 500 "$name"
  done
fi

# --------------------------------------------------------------------------------------- imagens
icon="$dir/images/icon.png"
if [ ! -f "$icon" ]; then
  err "images/icon.png: ausente"
elif ! info=$(png_info "$icon"); then
  err "images/icon.png: não é PNG"
else
  read -r w h depth color <<< "$info"
  bytes=$(file_bytes "$icon")
  if [ "$w" != 512 ] || [ "$h" != 512 ]; then
    err "images/icon.png: ${w}x${h} (exigido 512x512)"
  elif [ "$depth" != 8 ] || [ "$color" != 6 ]; then
    err "images/icon.png: precisa ser PNG de 32 bits (RGBA 8 bits por canal); tipo de cor $color, $depth bits"
  elif [ "$bytes" -gt 1048576 ]; then
    err "images/icon.png: $bytes bytes (limite 1 MB)"
  else
    ok "images/icon.png: 512x512 RGBA, $bytes bytes"
  fi
fi

feature=""
for candidate in "$dir/images/featureGraphic.png" "$dir/images/featureGraphic.jpg" "$dir/images/featureGraphic.jpeg"; do
  [ -f "$candidate" ] && feature="$candidate" && break
done
if [ -z "$feature" ]; then
  err "images/featureGraphic.(png|jpg): ausente"
else
  name="images/$(basename "$feature")"
  bytes=$(file_bytes "$feature")
  if info=$(png_info "$feature"); then
    read -r w h depth color <<< "$info"
    if [ "$color" != 2 ] || [ "$depth" != 8 ]; then
      err "$name: precisa ser PNG de 24 bits sem alfa (RGB); tipo de cor $color, $depth bits"
    fi
  else
    read -r w h <<< "$(image_size "$feature")"
  fi
  if [ "${w:-}" != 1024 ] || [ "${h:-}" != 500 ]; then
    err "$name: ${w:-?}x${h:-?} (exigido 1024x500)"
  elif [ "$bytes" -gt 15728640 ]; then
    err "$name: $bytes bytes (limite 15 MB)"
  else
    ok "$name: 1024x500, $bytes bytes"
  fi
fi

check_screenshots() { # subdiretório obrigatório(1/0)
  local sub="$1" required="$2" shots=() shot w h min max count
  shopt -s nullglob nocaseglob
  shots=("$dir/images/$sub"/*.png "$dir/images/$sub"/*.jpg "$dir/images/$sub"/*.jpeg)
  shopt -u nullglob nocaseglob
  count=${#shots[@]}
  if [ "$count" -eq 0 ]; then
    if [ "$required" = 1 ]; then
      warn "images/$sub/: nenhuma captura ainda (o Google Play exige pelo menos 2 antes de publicar; ver docs/PUBLICACAO.md)"
    fi
    return
  fi
  if [ "$count" -gt 8 ]; then
    err "images/$sub/: $count capturas (máximo 8)"
  elif [ "$required" = 1 ] && [ "$count" -lt 2 ]; then
    err "images/$sub/: $count captura (mínimo 2)"
  fi
  for shot in "${shots[@]}"; do
    read -r w h <<< "$(image_size "$shot")"
    if [ -z "${w:-}" ] || [ -z "${h:-}" ]; then
      err "images/$sub/$(basename "$shot"): não consegui ler as dimensões"
      continue
    fi
    min=$((w < h ? w : h))
    max=$((w > h ? w : h))
    if [ "$min" -lt 320 ] || [ "$max" -gt 3840 ] || [ "$max" -gt $((2 * min)) ]; then
      err "images/$sub/$(basename "$shot"): ${w}x${h} (lados de 320 a 3840 px, lado maior <= 2x o menor)"
    fi
  done
  ok "images/$sub/: $count captura(s) conferida(s)"
}
check_screenshots phoneScreenshots 1
check_screenshots sevenInchScreenshots 0
check_screenshots tenInchScreenshots 0

# ------------------------------------------------------------------------------------- política
property() { # nome -> valor em gradle.properties
  sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*(.*[^[:space:]])[[:space:]]*$/\\1/p" "$root/gradle.properties" | tail -1
}
email="${COLA_ELEITORAL_CONTACT_EMAIL:-$(property 'colaEleitoral\.contactEmail')}"
url="${COLA_ELEITORAL_PRIVACY_POLICY_URL:-$(property 'colaEleitoral\.privacyPolicyUrl')}"
developer="${COLA_ELEITORAL_DEVELOPER_NAME:-$(property 'colaEleitoral\.developerName')}"
policy_issue() { if [ "$release" = 1 ]; then err "$*"; else warn "$*"; fi; }
is_placeholder() { [ -z "$1" ] || [[ "$1" == *example.com* ]]; }

if is_placeholder "$email"; then
  policy_issue "e-mail de contato ainda é marcador (${email:-vazio}); defina colaEleitoral.contactEmail"
fi
if is_placeholder "$url"; then
  policy_issue "URL da política ainda é marcador (${url:-vazia}); defina colaEleitoral.privacyPolicyUrl"
elif [[ "$url" != https://* ]]; then
  policy_issue "URL da política precisa começar com https:// ($url)"
fi
if [ -z "$developer" ]; then
  policy_issue "nome do desenvolvedor vazio; defina colaEleitoral.developerName"
fi

policy_html="$root/docs/privacidade/index.html"
for policy in "$root/docs/privacidade.md" "$policy_html"; do
  name="${policy#"$root"/}"
  if [ ! -f "$policy" ]; then
    policy_issue "$name: ausente"
    continue
  fi
  problems=0
  pending=$(grep -c 'PREENCHER' -- "$policy" || true)
  if [ "$pending" -gt 0 ]; then
    policy_issue "$name: $pending linha(s) com [PREENCHER: ...]"
    problems=1
  fi
  # Rótulo|valor|propriedade: cada valor real tem de aparecer no texto da política.
  for expected in "e-mail de contato|$email|colaEleitoral.contactEmail" \
    "URL pública|$url|colaEleitoral.privacyPolicyUrl" \
    "nome do desenvolvedor|$developer|colaEleitoral.developerName"; do
    IFS='|' read -r label value prop <<< "$expected"
    if ! is_placeholder "$value" && ! grep -qF -- "$value" "$policy"; then
      policy_issue "$name: não cita o $label $value ($prop)"
      problems=1
    fi
  done
  [ "$problems" = 1 ] || ok "$name: preenchida, com e-mail, URL pública e nome do desenvolvedor"
done

# O HTML publicado é gerado do Markdown que o app embute: os dois precisam dizer o mesmo.
if ! command -v python3 > /dev/null 2>&1; then
  policy_issue "python3 ausente: não conferi se docs/privacidade/index.html está atualizado"
elif sync=$(python3 "$root/scripts/render-privacy-page.py" --check 2>&1); then
  ok "$sync"
else
  err "$sync"
fi

# Só com --release: a página tem de estar no ar, igual à versão do repositório.
if [ "$release" = 1 ] && ! is_placeholder "$url" && [[ "$url" == https://* ]]; then
  if ! command -v curl > /dev/null 2>&1; then
    err "curl ausente: não consegui conferir se a política está publicada em $url"
  elif status=$(curl -fsSI --max-time 20 -o /dev/null -w '%{http_code}' -- "$url" 2> /dev/null); then
    ok "política publicada: $url responde HTTP $status"
    published="$(mktemp)"
    if ! curl -fsS --max-time 20 -o "$published" -- "$url" 2> /dev/null; then
      err "política publicada: não consegui baixar $url para comparar"
    elif [ -f "$policy_html" ] && cmp -s -- "$published" "$policy_html"; then
      ok "política publicada: igual a docs/privacidade/index.html"
    else
      err "a página em $url difere de docs/privacidade/index.html: copie o arquivo para o repositório veronezzi/cola-eleitoral-privacidade (docs/PUBLICACAO.md, seção 7; o GitHub Pages leva alguns minutos para atualizar)"
    fi
    rm -f -- "$published"
  else
    if [ "${status:-000}" = 000 ]; then status="sem resposta"; else status="HTTP $status"; fi
    err "política fora do ar: curl -fsSI $url falhou ($status); publique-a antes do release (docs/PUBLICACAO.md, seção 7)"
  fi
fi

echo
if [ "$errors" -gt 0 ]; then
  echo "Resultado: $errors erro(s), $warnings aviso(s)."
  exit 1
fi
echo "Resultado: sem erros, $warnings aviso(s)."
