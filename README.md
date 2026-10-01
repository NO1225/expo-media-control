# expo-media-control

System media controls for Expo and React Native apps: lock screen, Control Center, the Android media notification, Bluetooth and headset buttons, and (with the car package) Android Auto and Apple CarPlay.

| Package | npm | What it does |
| --- | --- | --- |
| [`expo-media-control`](./packages/expo-media-control) | [![npm](https://img.shields.io/npm/v/expo-media-control.svg)](https://www.npmjs.com/package/expo-media-control) | Now Playing metadata, playback state and remote commands on iOS and Android |
| [`expo-media-control-car`](./packages/expo-media-control-car) | soon | Optional: a browsable library, search and voice requests in Android Auto and CarPlay |

Start with the [expo-media-control README](./packages/expo-media-control/README.md). Add the car package only if your app should appear in car head units.

## Repository layout

```
packages/expo-media-control       core package
packages/expo-media-control-car   Android Auto / CarPlay package
apps/example                      example app for both packages
```

## Development

```bash
npm install            # installs every workspace
npm run build          # builds the packages
npm run build:plugins  # builds the config plugins
npm test
npm run lint
npm run typecheck
```

See [CONTRIBUTING.md](./CONTRIBUTING.md) for details.

## License

MIT
