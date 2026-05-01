# Fluxer iOS (SwiftUI) Port

This folder contains a SwiftUI iOS app scaffold that ports the Android app's core flows:

- token login
- guild list + search
- channel list (guild and DM mode)
- message list + send + basic reply metadata

## Project setup

1. Open Xcode and create a new **App** project named `FluxerIOS`.
2. Replace the generated files with the Swift files in `FluxerIOS/`.
3. Set minimum iOS target to 16+.
4. Add `NSAppTransportSecurity` exceptions only if your environment requires them.

## Notes

- Network API base is `https://api.fluxer.app/v1`.
- Authentication token is persisted in `UserDefaults`.
- Websocket gateway parity is intentionally left as a follow-up; this port includes the REST UX parity first.
