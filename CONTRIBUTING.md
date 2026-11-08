# Contributing

EarBy is an offline Android app. Contributions are welcome when they preserve the offline,
no-account and no-tracking contract.

## Before changing code

1. Read [README.md](README.md), [docs/development/STATUS.md](docs/development/STATUS.md) and
   [docs/development/AGENT_WORKFLOW.md](docs/development/AGENT_WORKFLOW.md).
2. Use the existing Gradle wrapper and pinned dependency versions.
3. Do not add INTERNET permission, analytics, ads, accounts or runtime TTS.
4. Do not commit signing keys, passwords, `.local`, `dist`, raw voice candidates or device serials.

## Checks

```bash
source scripts/env.sh
scripts/check-fast.sh
```

Device and release checks are documented in [docs/testing/TEST_STRATEGY.md](docs/testing/TEST_STRATEGY.md).
Run focused tests for your change and include the exact commands and results in the pull request.

## Licensing

By contributing code, you agree that it may be distributed under Apache-2.0. By contributing
original assets, you agree that they may be distributed under CC BY 4.0. Do not submit third-party
audio, images or model output without a documented license.
