#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
[[ "$(uname -m)" == x86_64 ]] || { echo 'This bootstrap targets Linux x86_64.' >&2; exit 2; }
mkdir -p "$EAR_ROOT/.local"
python3 "$EAR_ROOT/scripts/download-toolchain.py"
source "$EAR_ROOT/scripts/env.sh"
# Interactive license review is required by sdkmanager. No blanket yes pipe.
sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
    'platform-tools' 'platforms;android-36' \
    'build-tools;36.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1' \
    'emulator' 'system-images;android-29;default;x86_64' 'system-images;android-36;google_apis;x86_64'
sdkmanager --sdk_root="$ANDROID_HOME" --list_installed > "$EAR_ROOT/.local/android-packages.txt"
python3 - <<'PYTHON'
import os
from pathlib import Path
sdk = os.environ['ANDROID_HOME'].replace('\\', '\\\\').replace(':', '\\:')
(Path(os.environ['EAR_ROOT']) / 'local.properties').write_text(f'sdk.dir={sdk}\n')
PYTHON
printf '%s\n' 'Android packages installed. Next: source scripts/env.sh; scripts/bootstrap-gradle.sh'
