#!/usr/bin/env bash
# One-command whole-project QA: host matrix, assets, native checks, strict screenshots, builds
# and lint, packaged-APK audit, the isolated review-phone suites and a written report.
#
# Every run writes its evidence to its own timestamped folder
# (.local/verification/qa-YYYY-MM-DD-HHMM/), so runs are comparable and nothing overwrites a
# previous run. By default the real 30-minute endurance stage is excluded (owner decision of
# 2026-10-15; check-full.sh --skip-endurance) and the recorded M11 endurance evidence is cited
# in the report instead. `--with-endurance` runs the real endurance in this pass and the
# report/device scan then require its PASS markers.
#
# The run never touches the regular app, never records screenshot baselines, never needs visual
# approval, and always ends with the open human rows. On a stage failure the original logs and
# the APKs are kept under a stable finding ID (findings/QA-Fnn/); add a regression in the owning
# subsystem and run the same command again.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
cd "$EAR_ROOT"
if [[ $# -gt 1 || ( $# == 1 && "$1" != "--with-endurance" ) ]]; then
    echo 'Usage: scripts/qa.sh [--with-endurance] (default skips the real 30-minute endurance' >&2
    echo '       and cites the recorded M11 evidence; the flag runs it in this pass)' >&2
    exit 2
fi
with_endurance=0
if [[ "${1:-}" == "--with-endurance" ]]; then
    with_endurance=1
fi

EVIDENCE=".local/verification/qa-$(date +%Y-%m-%d-%H%M)"
if [[ -e "$EVIDENCE" ]]; then
    suffix=2
    while [[ -e "$EVIDENCE-$suffix" ]]; do suffix=$((suffix + 1)); done
    EVIDENCE="$EVIDENCE-$suffix"
fi
mkdir -p "$EVIDENCE"
printf '{"epochSeconds": %s, "local": "%s"}\n' "$(date +%s)" "$(date -Iseconds)" \
    > "$EVIDENCE/run-started.json"

# Fresh, self-contained evidence directories; nothing lands in a previous run's folder.
export EAR_UI_CANDIDATES_DIR="$EVIDENCE/ui-candidates"
export EAR_NATIVE_EVIDENCE_DIR="$EVIDENCE/native"
export EAR_REVIEW_OUT="$EVIDENCE/device"

STAGES="$EVIDENCE/stages.tsv"
FINDINGS="$EVIDENCE/findings.md"
: > "$STAGES"
printf '# Findings\n' > "$FINDINGS"
finding_count=0
stage_failed=0
REVIEW_PACKAGE='com.earby.app.review'

report_stage() {
    printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "$STAGES"
    printf '==> %s: %s\n' "$1" "$2"
}

# Run one stage with its full output in its own log; a failure never aborts the run. The failed
# stage's log and the current APKs are copied under a stable finding ID and named in findings.md.
run_stage() {
    local name="$1"
    shift
    local command="$*"
    local log="$EVIDENCE/$name.log"
    printf '===== %s: %s\n' "$name" "$command" > "$log"
    printf 'RUN  %s\n' "$name"
    if "$@" >> "$log" 2>&1; then
        report_stage PASS "$name" "$command" "$log"
        return 0
    fi
    stage_failed=1
    finding_count=$((finding_count + 1))
    local id="$EVIDENCE/findings/$(printf 'QA-F%02d' "$finding_count")"
    local apk
    mkdir -p "$id"
    cp "$log" "$id/$name.log"
    : > "$id/apks.sha256"
    for apk in \
        app/build/outputs/apk/debug/app-debug.apk \
        app/build/outputs/apk/release/app-release-unsigned.apk \
        app/build/outputs/apk/review/app-review.apk \
        app/build/outputs/apk/androidTest/review/app-review-androidTest.apk \
        core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
        core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk; do
        if [[ -f "$apk" ]]; then
            sha256sum "$apk" >> "$id/apks.sha256"
            cp "$apk" "$id/$(basename "$apk")"
        fi
    done
    {
        printf '\n## QA-F%02d — %s\n\n' "$finding_count" "$name"
        printf -- '- Stage failed: `%s`\n' "$command"
        printf -- '- Log copy: `findings/QA-F%02d/%s.log` (full log: `%s`)\n' \
            "$finding_count" "$name" "$log"
        printf -- '- APK copies and hashes: `findings/QA-F%02d/`\n' "$finding_count"
        printf -- '- Owning subsystem: add the regression with the repaired consumer here.\n'
    } >> "$FINDINGS"
    report_stage FAIL "$name" "$command" "$log"
    return 0
}

# The serial is never printed; it is recorded only in the ignored device metadata.
SERIAL=''
resolve_serial() {
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        SERIAL="$ANDROID_SERIAL"
        return 0
    fi
    local -a serials
    mapfile -t serials < <(adb devices | awk '$2 == "device" {print $1}')
    if [[ ${#serials[@]} != 1 ]]; then
        return 1
    fi
    SERIAL="${serials[0]}"
}

record_environment() {
    # The same record lands in the stage log and in environment.txt, which the report embeds.
    scripts/check-env.sh --build > "$EVIDENCE/environment.txt" || {
        cat "$EVIDENCE/environment.txt"
        return 1
    }
    {
        printf 'date: %s\n' "$(date -Iseconds)"
        printf 'host: %s\n' "$(uname -srm)"
        printf 'java: %s\n' "$(java -version 2>&1 | head -1)"
        printf 'android home: %s\n' "$ANDROID_HOME"
        printf 'attached devices (serial hidden):\n'
        adb devices -l | tail -n +2 | sed -E 's/^[^ ]+/<serial>/' | sed '/^[[:space:]]*$/d'
        if resolve_serial; then
            printf 'selected device: one authorized phone (serial recorded in device/metadata.json)\n'
        else
            printf 'selected device: none; set ANDROID_SERIAL to a single authorized phone\n'
        fi
    } | tee "$EVIDENCE/environment.txt"
}

# The device clock captured before the run filters stale crash/ANR entries out of the evidence.
record_device_window() {
    resolve_serial || { echo 'No single authorized device; set ANDROID_SERIAL'; return 1; }
    local fields
    fields="$(adb -s "$SERIAL" shell "date '+%Y %s %z'" | tr -d '\r')"
    set -- $fields
    if [[ $# != 3 ]]; then
        echo "Could not read the device clock: $fields"
        return 1
    fi
    printf '{"year": %s, "epochSeconds": %s, "utcOffset": "%s"}\n' "$1" "$2" "$3" \
        > "$EVIDENCE/device-window.json"
    printf 'device window: year %s, epoch %s, offset %s\n' "$1" "$2" "$3"
}

assert_visual_report() {
    local report
    report="$(python3 scripts/screenshot-report.py --candidates "$EAR_UI_CANDIDATES_DIR")" \
        || return 1
    printf '%s\n' "$report"
    if grep -q '^PENDING_VISUAL_APPROVAL' <<< "$report"; then
        echo 'FAIL: a pending declared visual change remains; the owner gate is not closed' >&2
        return 1
    fi
    echo 'PASS: no pending or undeclared case in the light candidate set'
}

# The dark review set is a review artifact only: it never compares against or writes a baseline.
capture_dark_review() {
    ./gradlew --dependency-verification strict :app:recordRoborazziDebug \
        "-Pear.screenshots.dir=$EVIDENCE/dark-candidates" -Pear.ui.fresh=true \
        -Pear.screenshots.theme=dark
    python3 scripts/screenshot-contract.py "$EVIDENCE/dark-candidates"
}

apk_audit() {
    scripts/check-apk.sh \
        app/build/outputs/apk/debug/app-debug.apk \
        app/build/outputs/apk/release/app-release-unsigned.apk \
        app/build/outputs/apk/review/app-review.apk
}

scan_device() {
    local endurance_flag=()
    if [[ "$with_endurance" == 1 ]]; then
        endurance_flag=(--expect-endurance)
    fi
    python3 scripts/qa-device-scan.py --device-dir "$EAR_REVIEW_OUT" \
        --window "$EVIDENCE/device-window.json" "${endurance_flag[@]}" || return 1
    resolve_serial || { echo 'FAIL: no authorized phone to confirm terminal resources' >&2; return 1; }
    local pid services
    pid="$(adb -s "$SERIAL" shell pidof "$REVIEW_PACKAGE" 2>&1 | tr -d '\r')" || true
    if [[ "$pid" == *rror* ]]; then
        echo "FAIL: could not query the device: $pid" >&2
        return 1
    fi
    if [[ -n "$pid" ]]; then
        echo "FAIL: the review process is still running after the device stage (pid $pid)" >&2
        return 1
    fi
    services="$(adb -s "$SERIAL" shell dumpsys activity services "$REVIEW_PACKAGE" 2>&1 \
        | tr -d '\r')" || true
    if grep -q 'ServiceRecord{' <<< "$services"; then
        echo 'FAIL: a review service is still registered after the device stage' >&2
        grep 'ServiceRecord{' <<< "$services" >&2
        return 1
    fi
    echo 'PASS: no review process or service remains (terminal resources released)'
}

run_stage preflight record_environment
run_stage device-window record_device_window
if [[ "$with_endurance" == 1 ]]; then
    run_stage integration scripts/check-full.sh --automated-only --fresh
else
    run_stage integration scripts/check-full.sh --automated-only --fresh --skip-endurance
fi
run_stage assets-reviewed scripts/check-assets.sh --require-reviewed
run_stage visual-strict scripts/check-ui.sh
run_stage visual-report assert_visual_report
run_stage visual-dark capture_dark_review
run_stage apk-audit apk_audit
run_stage device-scan scan_device
run_stage report python3 scripts/qa-report.py --evidence "$EVIDENCE"

cat <<'TEXT'

Human rows still open: none. Owner acceptance is recorded in QA-REPORT.md:
  - Acoustic quality, actual accessories (including earbuds removal), TalkBack, G3/G4 and the
    interval walk/starter/Stopping chooser as reported on 2026-10-25.
TEXT
printf '\nEvidence: %s\nReport: %s/QA-REPORT.md\nFindings: %s\n' "$EVIDENCE" "$EVIDENCE" "$FINDINGS"
if [[ "$stage_failed" == 0 ]]; then
    echo 'QA: PASS'
else
    echo "QA: FAIL ($finding_count finding(s)); repair in the owning subsystem, add its regression, rerun scripts/qa.sh"
fi
exit "$stage_failed"
