#!/usr/bin/env bash
# Confere o manifesto mesclado de release (ARCHITECTURE.md 5.5; revisão de segurança S3 e S11).
#
# Falha se o manifesto tiver alguma permissão fora da lista abaixo (inclusive as que bibliotecas
# acrescentam sozinhas, como FOREGROUND_SERVICE do WorkManager ou AD_ID) ou se voltarem componentes
# que o app remove de propósito (SystemForegroundService, EmojiCompatInitializer).
#
# Uso:
#   ./gradlew :app:processReleaseMainManifest
#   scripts/check-merged-manifest.sh [caminho/do/AndroidManifest.xml]
#
# Sem argumento, lê o manifesto que o processReleaseMainManifest gera. Precisa de python3.
# Saída: 0 = ok; 1 = permissão ou componente proibido; 2 = arquivo ausente ou ilegível.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest="${1:-$root/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml}"

if [[ ! -f "$manifest" ]]; then
  echo "Manifesto mesclado não encontrado: $manifest" >&2
  echo "Gere com: ./gradlew :app:processReleaseMainManifest" >&2
  exit 2
fi

python3 - "$manifest" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

# Permissões aceitas. A de assinatura do androidx.core leva o applicationId na frente.
ALLOWED = {
    "android.permission.INTERNET",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.USE_BIOMETRIC",
    "android.permission.USE_FINGERPRINT",
    "android.permission.WAKE_LOCK",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
}
SIGNATURE_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

# Componentes removidos no AndroidManifest.xml do app com tools:node="remove".
FORBIDDEN_COMPONENTS = {
    "androidx.work.impl.foreground.SystemForegroundService",
    "androidx.emoji2.text.EmojiCompatInitializer",
}

path = sys.argv[1]
try:
    root = ET.parse(path).getroot()
except (ET.ParseError, OSError) as error:
    print(f"Manifesto ilegível: {path}: {error}", file=sys.stderr)
    sys.exit(2)

package = root.get("package", "")
allowed = ALLOWED | ({package + SIGNATURE_SUFFIX} if package else set())

problems = []
for tag in ("uses-permission", "uses-permission-sdk-23", "permission"):
    for element in root.iter(tag):
        name = element.get(ANDROID + "name", "")
        if name in allowed:
            continue
        if tag == "permission":
            problems.append(f"permissão declarada fora da lista: {name}")
        else:
            problems.append(f"permissão fora da lista: {name} ({tag})")

for element in root.iter():
    name = element.get(ANDROID + "name", "")
    if name in FORBIDDEN_COMPONENTS:
        problems.append(f"componente que deveria ter sido removido: {name} (<{element.tag}>)")

if problems:
    print(f"Manifesto mesclado reprovado ({path}):", file=sys.stderr)
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    print("Remova com tools:node=\"remove\" em app/src/main/AndroidManifest.xml ou, se a permissão for", file=sys.stderr)
    print("mesmo necessária, atualize esta lista, a política de privacidade e docs/data-safety.md.", file=sys.stderr)
    sys.exit(1)

used = sorted({e.get(ANDROID + "name", "") for t in ("uses-permission", "uses-permission-sdk-23") for e in root.iter(t)})
print("Manifesto mesclado ok. Permissões:")
for name in used:
    print(f"  - {name}")
PY
