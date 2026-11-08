# Data safety mapping

Use this mapping in Play Console for EarBy 1.0.0.

| Question | Answer |
| --- | --- |
| Does the app collect or share any required user data types? | No |
| Is all user data encrypted in transit? | Not applicable; no data is transmitted |
| Does the app provide a way to request data deletion? | Not applicable; no server account or collected data; uninstall removes app-private settings/presets |
| Does the app use advertising ID? | No |
| Does the app use analytics or crash reporting? | No |
| Does the app have account creation/login? | No |
| Does the app request location, contacts, microphone, camera, photos or files? | No |
| Does the app have INTERNET permission? | No |

## Local data

Settings, mode setups and five preset slots are stored in app-private DataStore/SQLite storage.
Android automatic backup is disabled (`allowBackup=false`). Preset export writes a user-chosen file
through the Storage Access Framework.

## Privacy policy

Public URL: `https://thomastzh.github.io/EarBy/privacy/`.

The policy text is in `docs/legal/PRIVACY_POLICY.md` and `site/privacy/index.html`. Contact:
thomas.t.zhang@outlook.com.
