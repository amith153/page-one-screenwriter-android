# Security

## Reporting a problem
Please report security issues privately with the "Report a vulnerability" button on the repository's Security tab, not in a public issue.

## What is and is not in this repository
- No API keys, server addresses, signing keys or personal data are stored here, and none are built into the APK on the Releases page.
- Live co-writing, desktop sync, Google Drive and film stills need keys that only the person building the app has. Copy `collab.properties.example` to `collab.properties` (git-ignored) and fill in your own. The downloadable APK runs without them, with those features switched off.
- `keystore.properties` and `*.jks` signing files are git-ignored. Never commit them.

## If you run your own sync server
Firebase API keys identify a project; they do not protect it. The Firestore rules do. Use the rules in `docs/HISTORY.md`, turn on App Check, and restrict the API key to your app's package and signing key.
