# Kitchen AI Mobile

Native Expo/React Native client for the Kitchen AI API.

## Local iPhone testing

1. Mac and iPhone must be on the same Wi-Fi.
2. Set `EXPO_PUBLIC_API_URL` in `.env` to the Mac LAN IP; do not use `localhost`.
3. Run `npm install`, then `npx expo start` and scan the QR code with Expo Go.

For a standalone build, configure Apple signing and run `eas build --platform ios`.
