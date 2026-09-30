# 🚗 expo-media-control-car

Android Auto and Apple CarPlay support for [`expo-media-control`](https://github.com/NO1225/expo-media-control/tree/main/packages/expo-media-control#readme): show your app in the car with a browsable library, answer in-car searches and voice requests ("Hey Google / Siri, play X"), and get a play request in JavaScript when the driver picks something.

The core package keeps doing the media session: Now Playing metadata, playback state and remote commands. This package adds the library and the car UI on top of it, and never creates a second session. You keep playing audio with your own player (expo-audio, react-native-video, ...).

> **Status:** Android Auto and CarPlay are implemented; CarPlay has only been tested in the iOS Simulator so far. Please report issues from real head units.

## 📦 Installation

```bash
npx expo install expo-media-control expo-media-control-car
```

Requires `expo-media-control` 2.1 or later.

Add both config plugins to `app.json` and rebuild the native app (`npx expo prebuild --clean` or a new development build):

```json
{
  "expo": {
    "plugins": [
      ["expo-media-control", { "enableBackgroundAudio": true }],
      ["expo-media-control-car", { "carPlayEntitlement": false, "siri": false }]
    ]
  }
}
```

### Plugin options

| Option | Default | What it does |
| --- | --- | --- |
| `carPlay` | `true` | Adds the CarPlay scene (and a phone scene, see [CarPlay notes](#carplay-notes)) to `Info.plist` |
| `carPlayEntitlement` | `false` | Adds `com.apple.developer.carplay-audio`. Enable it only after Apple granted the entitlement to your app ID, or device builds fail to sign |
| `siri` | `false` | Handles "Hey Siri, play X" (`INPlayMediaIntent`) in the app. Adds the Siri entitlement and `INIntentsSupported` |
| `siriMediaCategories` | `["INMediaCategoryGeneral"]` | `INSupportedMediaCategories` for Siri, e.g. `INMediaCategoryPodcasts`, `INMediaCategoryMusic` |
| `androidAuto` | `true` | `false` removes the Android Auto declaration and the media library |

## 🏃 Quick start

```ts
import { MediaControl, PlaybackState } from 'expo-media-control';
import { CarLibrary } from 'expo-media-control-car';

// 1. Hand over the library. The native side keeps a copy, so the car can browse it
//    right after the phone connects.
await CarLibrary.setLibrary({
  tabs: [
    {
      id: 'recent',
      title: 'Recent',
      children: [
        {
          id: 'ep-42',
          title: 'Episode 42',
          subtitle: 'My Podcast',
          artwork: { uri: 'https://example.com/42.jpg' },
          duration: 1800,
        },
      ],
    },
    // No children: loaded with the children loader when the driver opens it
    { id: 'shows', title: 'Shows', browsable: true, style: 'grid' },
  ],
});

// 2. Load big folders on demand
await CarLibrary.setChildrenLoader(async (parentId) => fetchEpisodes(parentId));

// 3. In-car search (Android Auto)
await CarLibrary.setSearchHandler(async (query) => searchEpisodes(query));

// 4. The car wants to play something: start it with your player
const subscription = CarLibrary.addPlayRequestListener(({ itemId, query }) => {
  // itemId: the driver picked an item. query: a voice request without an exact item
  itemId ? playEpisode(itemId) : playBestMatch(query ?? '');
});

// 5. Report playback as usual; the car's Now Playing screen uses it
await MediaControl.updateMetadata({ title: 'Episode 42', artist: 'My Podcast', duration: 1800 });
await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 0);
```

Enable the core controls (`MediaControl.enableMediaControls()`) as usual. Play/pause, skip and seek from the car arrive as regular `expo-media-control` events.

## 📚 API

### `CarLibrary.setLibrary(library: CarLibraryContent): Promise<void>`

Sets the whole library. Call it again when the library changes; the car refreshes the open screens. Invalid input rejects with `CarLibraryValidationError` (duplicate ids, missing titles, ...).

```ts
interface CarLibraryContent {
  tabs: CarMediaItem[]; // top-level entries; browsable ones become tabs (max. 4)
  style?: 'list' | 'grid'; // how the top level is shown when it isn't tabs
}

interface CarMediaItem {
  id: string; // unique across the library
  title: string;
  subtitle?: string;
  artwork?: { uri: string } | string; // http(s)://, file:// or content://
  playable?: boolean; // default: true unless browsable
  browsable?: boolean; // default: true when children is set
  children?: CarMediaItem[]; // leave out to load with the children loader
  style?: 'list' | 'grid'; // how this folder's children are shown (Android Auto)
  duration?: number; // seconds
  explicit?: boolean;
}
```

Items can be both browsable and playable (an album): Android Auto shows both actions, CarPlay starts playback when it's picked.

### `CarLibrary.setChildrenLoader(loader | null): Promise<void>`

`(parentId: string) => CarMediaItem[] | Promise<CarMediaItem[]>`. Called for browsable items without `children`. Results are cached natively: the next time the folder is opened, the cached list shows right away and the loader refreshes it in the background. The car waits up to 8 seconds for an answer.

### `CarLibrary.setSearchHandler(handler | null): Promise<void>`

`(query: string) => CarMediaItem[] | Promise<CarMediaItem[]>`. Answers searches typed in Android Auto. CarPlay has no search screen for audio apps.

### `CarLibrary.addPlayRequestListener(listener): EventSubscription`

```ts
interface CarPlayRequest {
  itemId?: string; // the item the driver picked
  query?: string; // voice request ("" = "play something")
  playWhenReady: boolean; // false for "prepare" requests
  position?: number; // requested start position in seconds
}
```

A request that arrives before a listener exists (for example while the app is starting) is delivered to the first listener. Build your queue from the requested item; the car's next/previous buttons send the usual `expo-media-control` commands.

### `CarLibrary.notifyChildrenChanged(parentId: string): Promise<void>`

Drops the cached children of a loaded folder and makes the car reload it. For folders in the library, call `setLibrary()` instead.

## 🤖 Android Auto setup

The package declares your app as an Android Auto media app (`automotive_app_desc.xml` and the `com.google.android.gms.car.application` meta-data) and registers the library with `expo-media-control`'s `MediaLibraryService`.

1. **Test with the Desktop Head Unit (DHU).** Install it from the Android SDK Manager (SDK Tools → Android Auto Desktop Head Unit Emulator), enable developer mode and "Unknown sources" in the Android Auto app on the phone, start the head unit server and run `desktop-head-unit`. See [Test Android apps for cars](https://developer.android.com/training/cars/testing/dhu).
2. **Publish.** Opt in to Android Auto in the Play Console (Advanced settings → Form factors). Google reviews the app against the [car app quality guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality) (for example: playback must start from the car without touching the phone, and artwork must load).

Notes:
- **JavaScript not running.** When the app process was killed, Android Auto can still start the service and browse the saved library. Play requests are queued and delivered when JavaScript registers a listener, which needs the app to run. Keep the app alive while connected, or make sure your app starts playback when it's opened.
- **Artwork.** Android Auto only loads `content://` artwork, so the package serves `http(s)://` and `file://` artwork of library items through its own content provider (cached for 7 days).
- **Strings.** Override `expo_media_control_car_empty_library` and `expo_media_control_car_root_title` in your app's `strings.xml` to translate them.

## 🍎 CarPlay setup

1. **Request the entitlement.** Every app that ships CarPlay needs Apple's CarPlay audio entitlement for its app ID. Request it through the [CarPlay page](https://developer.apple.com/carplay/) ("Request a CarPlay entitlement"). After approval, enable it in your provisioning profile and set `carPlayEntitlement: true`.
2. **Test in the Simulator.** Build for the iOS Simulator and open **I/O → External Displays → CarPlay**. The entitlement isn't needed there.
3. **Test in a car** with a build that has the entitlement.

### CarPlay notes

- **Scene life cycle.** A CarPlay scene moves the app to the UIKit scene life cycle. The plugin registers a phone scene delegate from this package that shows the window your Expo app delegate creates and forwards links, user activities, quick actions and life-cycle events to the app delegate. React Native keeps starting at app launch, so JavaScript also runs when CarPlay alone launches the app. If your `Info.plist` already has a phone scene configuration, the plugin keeps it.
- **Cold-start deep links.** With the scene life cycle, iOS passes the link that launched the app to the scene instead of the launch options. The phone scene delegate forwards it, but `Linking.getInitialURL()` may not see it. Test your deep links after adding the package.
- **iPad.** The app shows one window; extra windows (multitasking) are closed.
- **Templates.** Browsable top-level entries become tabs (up to 4); otherwise the top level is a list. Folders are lists; `style: 'grid'` only applies to Android Auto. CarPlay limits the navigation depth to 5 screens.
- **Strings.** Translate `expo_media_control_car_empty_library`, `expo_media_control_car_empty_folder` and `expo_media_control_car_root_title` in your app's `Localizable.strings`.

### Siri

With `siri: true`, "Hey Siri, play X in *App*" arrives as a play request with `query` (and `itemId` when Siri resolved an item). Enable the Siri capability for your app ID. The package adds `application(_:handlerFor:)` to your app delegate at launch; if your app delegate already implements it, return `ExpoMediaControlCarSiriIntentHandler.shared` (from `import ExpoMediaControlCar`) for `INPlayMediaIntent` yourself.

## 🧪 Example app

The example app in [`apps/example`](https://github.com/NO1225/expo-media-control/tree/main/apps/example) uses this package by default ([`CarSetup.ts`](https://github.com/NO1225/expo-media-control/tree/main/apps/example/CarSetup.ts)). Build it without the car package with `npm run android:no-car` / `npm run ios:no-car` (after `npx expo prebuild --clean`). The example has no CarPlay entitlement and only demonstrates the setup.

## License

MIT
