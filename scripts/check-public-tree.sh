#!/usr/bin/env bash
# Read-only scan for files that must not enter the public snapshot or a release archive.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
fail=0
while IFS= read -r path; do
  case "$path" in
    *.jks|*.keystore|keystore.properties|local.properties|.env|*/.env|*.p12|*.pfx)
      echo "FAIL: secret-like file is tracked: $path" >&2; fail=1 ;;
    .local/*|dist/*|synthNotes/out/*|tools/*/raw/*|tools/*/candidates*/*)
      echo "FAIL: local/generated tree is tracked: $path" >&2; fail=1 ;;
  esac
done < <(git ls-files)
if grep -RInE 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|storePassword[[:space:]]*=[[:space:]]*"[^"$]' \
  app core scripts docs --exclude-dir=build --exclude-dir=.gradle 2>/dev/null \
  | grep -v 'storePassword = "android"'; then
  echo 'FAIL: possible private key or literal release signing password in tracked sources' >&2
  fail=1
fi
if [[ "$fail" != 0 ]]; then exit 1; fi
echo 'PASS: no keystore, private key, local-only tree or literal signing password is tracked'
