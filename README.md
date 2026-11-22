# SmartTrade Android

Android control app for PannuLabs's SmartTrade System engine. It does what the Windows control panel does:
dashboard with open positions (close one / close all, pause new trades), scanner signals, activity log,
trade history and all settings (connection, trading, scanner, Binance API keys).

## Connect
On the server running the engine, create a code for the phone:

    sudo smarttrade-admin pair my-phone --host YOUR_SERVER_PUBLIC_IP

Paste the `PLST1-...` code into Settings > Connection. The connection is TLS, accepted only when the server's
certificate matches the fingerprint inside the code (checked before the token is sent). The code is stored
encrypted with the Android Keystore. Revoke a phone with `smarttrade-admin revoke my-phone`.

## Build
- GitHub Actions builds the APK on every push (Actions tab > latest run > artifact `SmartTrade-apk`).
- Or open this folder in Android Studio and use Build > Build APK(s).

The release build is signed with the debug key so it installs directly; use your own signing key before
publishing to the Play Store.

Test fixtures in `app/src/test/resources` were recorded from the engine's test suite with fake data and a fake
token; they contain no real keys or account data.
