# 📱 Expo Media Control

A comprehensive, production-ready media control module for Expo and React Native applications. Provides seamless integration with system media controls including Control Center (iOS), lock screen controls, Android notifications, and remote control events with full TypeScript support.

[![npm version](https://img.shields.io/npm/v/expo-media-control.svg)](https://www.npmjs.com/package/expo-media-control)
[![Platform - Android and iOS](https://img.shields.io/badge/platform-Android%20%7C%20iOS-blue.svg)](https://github.com/NO1225/expo-media-control)
[![MIT license](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/NO1225/expo-media-control/blob/main/LICENSE)

> **⚠️ UNDER ACTIVE DEVELOPMENT**  
> This module is currently under rapid development. While we strive to maintain stability, breaking changes may occur between versions. Please:
> - Pin your version in `package.json`
> - Check the [CHANGELOG](./CHANGELOG.md) before updating
> - Report issues on [GitHub](https://github.com/NO1225/expo-media-control/issues)
> - Join discussions for upcoming features and changes

## ✨ Features

- 🎵 **Complete Media Session Management** - Full control over media playback state and metadata
- 🔒 **Lock Screen Integration** - Native lock screen controls with artwork support
- 📱 **Control Center & Notification Controls** - iOS Control Center and Android notification controls
- 🎨 **Rich Artwork Display** - Support for local and remote artwork/album covers
- ⏯️ **Comprehensive Playback Controls** - Play, pause, stop, next, previous, seek, skip, and rating
- ⚡ **Variable Playback Rate Support** - Accurate progress tracking at any playback speed (0.5x, 1.5x, 2x, etc.)
- 📢 **Background Audio Support** - Continue playback when app is backgrounded
- 🎯 **Event-Driven Architecture** - React to user interactions with system controls
- 🛠️ **Full TypeScript Support** - Complete type definitions and IntelliSense support
- 🔧 **Highly Configurable** - Extensive customization options for both platforms

## 📦 Installation

```bash
npm install expo-media-control
# or
yarn add expo-media-control
# or
pnpm install expo-media-control
```

> **💡 Tip**: Pin your version in `package.json` during active development to avoid unexpected breaking changes. For example: `"expo-media-control": "~0.1.0"`

### Configuration

Add the plugin to your `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": [
      [
        "expo-media-control",
        {
          "enableBackgroundAudio": true,
          "audioSessionCategory": "playback",
          "notificationIcon": "./assets/notification-icon.png"
        }
      ]
    ]
  }
}
```

**Configuration Options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enableBackgroundAudio` | `boolean` | `true` | Enable background audio modes (iOS) |
| `audioSessionCategory` | `string` | `"playback"` | Audio session category for iOS |
| `notificationIcon` | `string` | `undefined` | Path to custom notification icon for Android (e.g., `"./assets/notification-icon.png"`) - see [Custom Icon Guide](./CUSTOM_NOTIFICATION_ICON.md) |

**Note:** The plugin configuration is for **build-time** setup only. Runtime configuration (like `skipInterval` and which buttons are shown) should be passed to `enableMediaControls()`. See [API Reference](#api-reference) below.

**💡 Custom Notification Icon:** Android requires monochrome (white on transparent) icons for notifications. See our [detailed guide](./CUSTOM_NOTIFICATION_ICON.md) on creating and using custom icons.

## 🏃‍♂️ Quick Start

```typescript
import React, { useEffect, useState } from 'react';
import { View, Button, Text } from 'react-native';
import {
  MediaControl,
  PlaybackState,
  Command,
  MediaControlEvent,
} from 'expo-media-control';

export default function MusicPlayer() {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTrack, setCurrentTrack] = useState({
    title: 'Bohemian Rhapsody',
    artist: 'Queen',
    album: 'A Night at the Opera',
    artwork: {
      uri: 'https://example.com/album-art.jpg'
    },
    duration: 355 // 5:55 in seconds
  });

  useEffect(() => {
    // Initialize media controls
    const initializeControls = async () => {
      try {
        await MediaControl.enableMediaControls({
          capabilities: [
            Command.PLAY,
            Command.PAUSE,
            Command.STOP,
            Command.NEXT_TRACK,
            Command.PREVIOUS_TRACK,
            Command.SKIP_FORWARD,
            Command.SKIP_BACKWARD,
          ],
          compactCapabilities: [
            Command.PREVIOUS_TRACK,
            Command.PLAY,
            Command.NEXT_TRACK,
          ],
        });

        // Set initial metadata
        await MediaControl.updateMetadata(currentTrack);
        await MediaControl.updatePlaybackState(PlaybackState.STOPPED);

      } catch (error) {
        console.error('Failed to initialize media controls:', error);
      }
    };

    initializeControls();

    // Listen for media control events
    const removeListener = MediaControl.addListener((event: MediaControlEvent) => {
      console.log('Media control event:', event.command);
      
      switch (event.command) {
        case Command.PLAY:
          handlePlay();
          break;
        case Command.PAUSE:
          handlePause();
          break;
        case Command.STOP:
          handleStop();
          break;
        case Command.NEXT_TRACK:
          handleNext();
          break;
        case Command.PREVIOUS_TRACK:
          handlePrevious();
          break;
        case Command.SKIP_FORWARD:
          handleSkipForward(event.data?.interval || 15);
          break;
        case Command.SKIP_BACKWARD:
          handleSkipBackward(event.data?.interval || 15);
          break;
      }
    });

    // Cleanup on unmount
    return () => {
      removeListener();
      MediaControl.disableMediaControls();
    };
  }, []);

  const handlePlay = async () => {
    setIsPlaying(true);
    await MediaControl.updatePlaybackState(PlaybackState.PLAYING);
  };

  const handlePause = async () => {
    setIsPlaying(false);
    await MediaControl.updatePlaybackState(PlaybackState.PAUSED);
  };

  const handleStop = async () => {
    setIsPlaying(false);
    await MediaControl.updatePlaybackState(PlaybackState.STOPPED);
  };

  const handleNext = async () => {
    // Switch to next track
    setCurrentTrack({
      title: 'We Will Rock You',
      artist: 'Queen',
      album: 'News of the World',
      artwork: { uri: 'https://example.com/album-art-2.jpg' },
      duration: 122
    });
  };

  const handlePrevious = async () => {
    // Switch to previous track
    // Implementation here
  };

  const handleSkipForward = async (interval: number) => {
    // Skip forward by interval seconds
    console.log(`Skipping forward ${interval} seconds`);
  };

  const handleSkipBackward = async (interval: number) => {
    // Skip backward by interval seconds
    console.log(`Skipping backward ${interval} seconds`);
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', padding: 20 }}>
      <Text style={{ fontSize: 20, fontWeight: 'bold' }}>{currentTrack.title}</Text>
      <Text style={{ fontSize: 16, marginBottom: 20 }}>{currentTrack.artist}</Text>
      
      <Button 
        title={isPlaying ? 'Pause' : 'Play'} 
        onPress={isPlaying ? handlePause : handlePlay} 
      />
      <Button title="Stop" onPress={handleStop} />
      <Button title="Next" onPress={handleNext} />
    </View>
  );
}
```

## 📚 API Reference

### Core Methods

#### `enableMediaControls(options?: MediaControlOptions): Promise<void>`

Enables media controls with specified configuration.

```typescript
interface MediaControlOptions {
  capabilities?: Command[];          // Controls which commands are enabled on both platforms
  compactCapabilities?: Command[];   // Android: commands next to play/pause, in display order (max 3)
  ios?: {
    skipInterval?: number;      // Skip interval in seconds (default: 15)
  };
  android?: {
    skipInterval?: number;      // Skip interval in seconds (default: 15)
  };
}

await MediaControl.enableMediaControls({
  capabilities: [
    Command.PLAY,
    Command.PAUSE,
    Command.NEXT_TRACK,
    Command.PREVIOUS_TRACK,
    Command.SKIP_FORWARD,
    Command.SKIP_BACKWARD,
    Command.SEEK,
  ],
  compactCapabilities: [
    Command.PREVIOUS_TRACK,
    Command.PLAY,         // play/pause are treated as a single toggle button
    Command.NEXT_TRACK,
  ],
  ios: {
    skipInterval: 15,
  },
  android: {
    skipInterval: 15,
  },
});
```

#### `updateMetadata(metadata: MediaMetadata): Promise<void>`

Updates the media metadata displayed in system controls.

```typescript
interface MediaMetadata {
  title?: string;
  artist?: string;
  album?: string;
  artwork?: MediaArtwork;
  duration?: number;       // seconds
  elapsedTime?: number;    // seconds - same as passing a position to updatePlaybackState()
  genre?: string;
  trackNumber?: number;
  albumTrackCount?: number;
  date?: string;           // Android uses the year only
  rating?: MediaRating;
  isLiveStream?: boolean;  // hides progress / seeking
}

await MediaControl.updateMetadata({
  title: 'Bohemian Rhapsody',
  artist: 'Queen',
  album: 'A Night at the Opera',
  duration: 355,
  artwork: {
    uri: 'https://example.com/album-art.jpg'
  },
  genre: 'Rock',
  trackNumber: 11,
  albumTrackCount: 12,
});
```

#### `updatePlaybackState(state: PlaybackState, position?: number, playbackRate?: number): Promise<void>`

Updates the current playback state, position, and playback rate.

```typescript
enum PlaybackState {
  NONE = 0,
  STOPPED = 1,
  PLAYING = 2,
  PAUSED = 3,
  BUFFERING = 4,
  ERROR = 5,
}

// Start playing at 45 seconds at normal speed
await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 45);

// Playing at 1.5x speed - system controls will show accurate progress
await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 45, 1.5);

// Playing at 2x speed
await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 30, 2.0);

// Pause playback
await MediaControl.updatePlaybackState(PlaybackState.PAUSED);

// Show buffering
await MediaControl.updatePlaybackState(PlaybackState.BUFFERING);
```

**Playback Rate Parameter:**
- Optional third parameter that specifies the playback speed (1.0 = normal speed)
- When provided, enables the system to calculate accurate progress between updates
- Particularly useful when playing audio at different speeds (0.5x, 1.5x, 2x, etc.)
- If omitted, defaults to 1.0 when playing, 0.0 when paused/stopped/buffering
- Range: 0.0 to 10.0 (validated by the module)

#### Other Core Methods

- `disableMediaControls(): Promise<void>` - Disable and cleanup controls
- `resetControls(): Promise<void>` - Reset to default state
- `isEnabled(): Promise<boolean>` - Check if controls are enabled
- `getCurrentMetadata(): Promise<MediaMetadata | null>` - Get current metadata
- `getCurrentState(): Promise<PlaybackState>` - Get current state

### Event Handling

#### Media Control Events

```typescript
const removeListener = MediaControl.addListener((event: MediaControlEvent) => {
  console.log('Command:', event.command);
  console.log('Data:', event.data);
  console.log('Timestamp:', event.timestamp);
  
  switch (event.command) {
    case Command.PLAY:
      // Start playback
      break;
    case Command.SEEK:
      // Seek to position: event.data.position
      break;
    case Command.SET_RATING:
      // Set rating: event.data.rating, event.data.type
      break;
  }
});

// Don't forget to remove the listener
removeListener();
```

### Available Commands

```typescript
enum Command {
  PLAY = 'play',
  PAUSE = 'pause',
  STOP = 'stop',
  NEXT_TRACK = 'nextTrack',
  PREVIOUS_TRACK = 'previousTrack',
  SKIP_FORWARD = 'skipForward',
  SKIP_BACKWARD = 'skipBackward',
  SEEK = 'seek',
  SET_RATING = 'setRating',
}
```

#### `removeAllListeners(): Promise<void>`

Removes all event listeners.

```typescript
await MediaControl.removeAllListeners();
```

## ⚡ Variable Playback Rate

The module supports variable playback rates, enabling accurate progress display in system media controls when playing audio at different speeds.

### How It Works

When you pass a playback rate to `updatePlaybackState()`, the native platform (iOS and Android) uses this information to:
- **Calculate progress automatically** between your updates
- **Display accurate scrubber position** in Control Center / Lock Screen / Notifications
- **Reduce the need for frequent updates** from JavaScript

### Example Usage

```typescript
import { MediaControl, PlaybackState } from 'expo-media-control';

// Set playback rate when it changes
const setPlaybackSpeed = async (speed: number, currentPosition: number) => {
  // Update your audio player's speed
  await audioPlayer.setPlaybackRate(speed);

  // Update media controls with the new rate
  await MediaControl.updatePlaybackState(
    PlaybackState.PLAYING,
    currentPosition,
    speed  // Pass the playback rate
  );
};

// Examples
await setPlaybackSpeed(0.5, 30);  // Half speed at 30 seconds
await setPlaybackSpeed(1.0, 45);  // Normal speed at 45 seconds
await setPlaybackSpeed(1.5, 60);  // 1.5x speed at 60 seconds
await setPlaybackSpeed(2.0, 90);  // Double speed at 90 seconds
```

### Optimal Integration Pattern (Recommended)

**Important:** Native platforms (iOS and Android) automatically animate progress based on the playback rate you provide. Calling `updatePlaybackState()` too frequently will **interrupt** this smooth native animation, especially on Android.

**Best Practice:** Only update MediaControl when state **actually changes**:

```typescript
// ✅ GOOD: Only update on state changes
class PlayerManager {
  private isPlaying = false;
  private rate = 1.0;
  private isBuffering = false;

  // Update when play/pause state changes
  setIsPlaying(playing: boolean) {
    if (this.isPlaying !== playing) {
      MediaControl.updatePlaybackState(
        playing ? PlaybackState.PLAYING : PlaybackState.PAUSED,
        this.getCurrentTime(),
        playing ? this.rate : 0.0
      );
      this.isPlaying = playing;
    }
  }

  // Update when playback rate changes
  setRate(rate: number) {
    this.rate = rate;
    if (this.isPlaying) {
      MediaControl.updatePlaybackState(
        PlaybackState.PLAYING,
        this.getCurrentTime(),
        rate
      );
    }
  }

  // Update when buffering state changes
  setBuffering(buffering: boolean) {
    if (this.isBuffering !== buffering) {
      MediaControl.updatePlaybackState(
        buffering ? PlaybackState.BUFFERING : PlaybackState.PLAYING,
        this.getCurrentTime(),
        buffering ? 0.0 : this.rate
      );
      this.isBuffering = buffering;
    }
  }

  // Update when user seeks
  seekTo(position: number) {
    MediaControl.updatePlaybackState(
      this.isPlaying ? PlaybackState.PLAYING : PlaybackState.PAUSED,
      position,
      this.isPlaying ? this.rate : 0.0
    );
  }

  // ❌ DON'T: Update in periodic progress callback
  onProgressUpdate(position: number) {
    // Just update your UI, NOT MediaControl
    this.updateUI(position);

    // Native platform is already animating progress smoothly!
    // No need to call MediaControl.updatePlaybackState() here
  }
}
```

### Benefits

- **Smooth Native Animation**: System controls animate progress smoothly without interruption
  - iOS Control Center and Lock Screen use native rendering
  - The Android media notification extrapolates progress from the playback speed
  - **Critical for Android**: Frequent updates interrupt the native animation
- **Accurate Progress Display**: System controls show the correct progress at any playback speed
- **Better Performance**: Eliminates unnecessary JavaScript ↔ Native bridge calls (500ms → only on state changes)
- **Native Behavior**: Platform media controls work exactly as users expect
- **Supports All Speeds**: Works with any playback rate from 0.0 to 10.0

### Platform Support

- **iOS**: Uses `MPNowPlayingInfoPropertyPlaybackRate` to inform Control Center and Lock Screen
- **Android**: The Media3 session extrapolates the position using the playback speed

## 🎨 Artwork Support

The module supports various artwork sources:

### Remote URLs
```typescript
{
  artwork: {
    uri: 'https://example.com/album-art.jpg',
    width: 300,
    height: 300
  }
}
```

### Local Files
```typescript
{
  artwork: {
    uri: 'file:///path/to/image.jpg'
  }
}
```

### App Bundle Resources (iOS)
```typescript
{
  artwork: {
    uri: 'album-art' // Image in app bundle
  }
}
```

### Android Resources
```typescript
{
  artwork: {
    uri: 'ic_album_art' // Drawable resource
  }
}
```

## ⚠️ Important Notes

### Artwork Fallback

If an artwork URI points to a non-existent file, the metadata (title, artist, etc.) will still update correctly on the notification — the artwork will simply be omitted. No error is thrown. This applies to both local file URIs and remote URLs that fail to load.

### Notification Button Appearance (Android 13+)

Starting with Android 13 (API 33), the system renders media notification buttons entirely on its own using Material Design styling. This means:
- **Button icons, shapes, and backgrounds** are controlled by the system and cannot be customized by apps
- **The gray/colored circular backgrounds** around buttons are a Material Design choice in Android's SystemUI
- This affects all media apps equally (Spotify, YouTube Music, etc.)

What you **can** control:
- **Which buttons appear** via `capabilities`, and which ones sit next to play/pause via `compactCapabilities`
- **Small notification icon** via the `notificationIcon` plugin option

### iOS Control Center Limitations

iOS Control Center and Lock Screen media controls are entirely system-rendered. You can:
- **Enable/disable commands** via `capabilities`
- **Configure skip intervals** via `ios.skipInterval`

You cannot customize button icons, colors, layout, or order on iOS.

### Background Audio (iOS)
For background audio to work on iOS, ensure your `app.json` includes:

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "UIBackgroundModes": ["audio"]
      }
    }
  }
}
```

This is automatically handled by the plugin when `enableBackgroundAudio` is true.

### Android Permissions
The following permissions are automatically added:
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - For the media playback service

### Network Security (Android 9+)
If using HTTP artwork URLs on Android 9+, add network security configuration to allow cleartext traffic.

## 🐛 Troubleshooting

### Common Issues

**Controls not showing up:**
- Ensure you've called `enableMediaControls()` before using other methods
- Check that your app has background audio permissions if needed
- Verify the plugin is properly configured in `app.json`

**Artwork not loading:**
- Check network connectivity for remote URLs
- Verify file paths for local files
- Check image format compatibility (JPG, PNG supported)
- If the artwork file doesn't exist, metadata will still update (just without artwork)

**Events not firing:**
- Ensure event listeners are properly registered
- Check that the commands are enabled in capabilities
- Verify iOS audio session is properly configured

**Android notification not showing:**
- Check notification permissions on Android 13+
- Verify notification channel configuration
- Ensure foreground service permissions

### Native logs

- **iOS**: Xcode console or device logs
- **Android**: `adb logcat` or Android Studio logs

### Using with expo-audio

Don't call expo-audio's `player.setActiveForLockScreen()` together with this module. Both create a system media session and notification, and the OS routes lock screen, Bluetooth and car buttons to only one of them - the usual symptom is that some buttons "do nothing". Use this module for the system controls and expo-audio only for playback.

On Android, expo-audio also creates a plain media session of its own for every player, even without `setActiveForLockScreen()`. Android sends media keys to the session that became active last, so this module re-activates its session when you confirm `PlaybackState.PLAYING` after a remote play. Always report `PLAYING` once your player has really started, and next/previous keep arriving as events.

On Android, expo-audio's docs mention background playback stopping after ~3 minutes without `setActiveForLockScreen()`. That limit comes from the app not running a foreground service; this module's media service runs in the foreground while you report `PlaybackState.PLAYING`, which serves the same purpose. Start playback (and report `PLAYING`) while the app is in the foreground - Android doesn't allow apps to start a foreground service from the background.

### Reset Controls

If you encounter issues, try resetting:

```typescript
await MediaControl.disableMediaControls();
await MediaControl.resetControls();
await MediaControl.enableMediaControls(options);
```

## 🔧 Migration Guide

### From react-native-music-control

This module is designed as a modern replacement with improved TypeScript support:

```typescript
// Old way (react-native-music-control)
MusicControl.enableControl('play', true);
MusicControl.enableControl('pause', true);

// New way (expo-media-control)
await MediaControl.enableMediaControls({
  capabilities: [Command.PLAY, Command.PAUSE]
});
```

## 🔧 Type Definitions

### PlaybackState

```typescript
enum PlaybackState {
  NONE = 0,
  STOPPED = 1,
  PLAYING = 2,
  PAUSED = 3,
  BUFFERING = 4,
  ERROR = 5,
}
```

### Command

```typescript
enum Command {
  PLAY = 'play',
  PAUSE = 'pause',
  STOP = 'stop',
  NEXT_TRACK = 'nextTrack',
  PREVIOUS_TRACK = 'previousTrack',
  SKIP_FORWARD = 'skipForward',
  SKIP_BACKWARD = 'skipBackward',
  SEEK = 'seek',
  SET_RATING = 'setRating',
}
```

### RatingType

```typescript
enum RatingType {
  HEART = 'heart',
  THUMBS_UP_DOWN = 'thumbsUpDown',
  THREE_STARS = 'threeStars',
  FOUR_STARS = 'fourStars',
  FIVE_STARS = 'fiveStars',
  PERCENTAGE = 'percentage',
}
```

### MediaArtwork

```typescript
interface MediaArtwork {
  uri: string;           // Local file path or HTTP URL
  width?: number;        // Width in pixels
  height?: number;       // Height in pixels
}
```

### MediaMetadata

```typescript
interface MediaMetadata {
  title?: string;                    // Track title
  artist?: string;                   // Artist name
  album?: string;                    // Album name
  artwork?: MediaArtwork;            // Album artwork
  duration?: number;                 // Track duration in seconds
  elapsedTime?: number;             // Current position in seconds (same as updatePlaybackState position)
  genre?: string;                   // Music genre
  trackNumber?: number;             // Track number in album
  albumTrackCount?: number;         // Total tracks in album
  date?: string;                    // Release date (Android uses the year only)
  rating?: MediaRating;             // Track rating
  isLiveStream?: boolean;           // Flags track as a live stream (hides progress)
}
```

### MediaControlOptions

```typescript
interface MediaControlOptions {
  capabilities?: Command[];          // Which commands to enable (omit for all)
  compactCapabilities?: Command[];   // Android: commands next to play/pause, in display order (max 3)
  ios?: {                           // iOS-specific config
    skipInterval?: number;           // Skip interval in seconds (default: 15)
  };
  android?: {                       // Android-specific config
    skipInterval?: number;           // Skip interval in seconds (default: 15)
  };
}
```

**Capabilities Behavior:**
- If `capabilities` is omitted, all commands are enabled (backward compatible)
- On **Android**, only the specified commands are available to the notification, lock screen, Bluetooth and other controllers
- On **iOS**, only the specified commands are registered with the Control Center
- `play` and `pause` are treated as a single toggle button in Android notifications

**compactCapabilities Behavior (Android):**
- Android shows a fixed layout: a *back* slot, play/pause, a *forward* slot, plus extra buttons in the expanded notification.
- Write `compactCapabilities` in display order: the command before `PLAY` takes the back slot and the command after it takes the forward slot, e.g. `[SKIP_BACKWARD, PLAY, SKIP_FORWARD]`.
- If omitted, `previousTrack`/`nextTrack` take the slots when enabled, otherwise `skipBackward`/`skipForward`.
- Other enabled commands (`skipBackward`, `skipForward`, `previousTrack`, `nextTrack`, `stop`) are shown as extra buttons where the system has room.
- Maximum 3 items. iOS ignores this option (the Control Center layout is system-controlled).
- Bluetooth next/previous keys are mapped to `skipForward`/`skipBackward` when `nextTrack`/`previousTrack` are not enabled.

**Changing capabilities at runtime:**
Call `enableMediaControls()` again with the new options (for example, switching between skip buttons for a single episode and next/previous for a queue). The new capabilities are applied on both platforms without re-registering event listeners.

## 🎨 Platform-Specific Features

### iOS Features

- **Control Center Integration** - Native iOS Control Center controls
- **Lock Screen Controls** - Rich lock screen media controls with artwork
- **Background Audio** - Continues playback when app is backgrounded
- **MPNowPlayingInfoCenter** - Full integration with iOS media system
- **Remote Command Center** - Handles all iOS remote control events
- **AirPlay Support** - Works with AirPlay and Bluetooth devices

> **CarPlay / Android Auto:** Now Playing information appears wherever the OS shows it for any audio app, but this module does not provide a CarPlay or Android Auto app (browsable library, templates). That needs platform entitlements and a content tree, and is planned as a separate package.

### Android Features

- **Media3 Session** - Built on AndroidX Media3 (`MediaSessionService`), the current Android media API
- **Notification Controls** - Rich media notifications with custom actions
- **Lock Screen Controls** - Media controls on Android lock screen
- **Bluetooth Integration** - Works with Bluetooth headphones and car systems

> **Note:** Audio focus management should be handled by your media player (e.g., expo-audio, react-native-video), not by this control module. This module only provides the UI controls.

## 🔧 Configuration Options

### Plugin Configuration

Configure the plugin in your `app.json`:

```json
{
  "expo": {
    "plugins": [
      [
        "expo-media-control",
        {
          "enableBackgroundAudio": true,
          "audioSessionCategory": "playback",
          "notificationIcon": "./assets/notification-icon.png"
        }
      ]
    ]
  }
}
```

**Plugin Configuration Notes:**
- **Build-time only**: Plugin config is processed during build, not at runtime
- **notificationIcon**: 
  - **Managed workflow**: Use `"./assets/notification-icon.png"` - plugin copies it during prebuild
  - **Bare workflow**: Reference existing drawable resource by name (e.g., `"ic_notification"`)
  - **Requirements**: Must be monochrome (white on transparent) - see [Custom Icon Guide](./CUSTOM_NOTIFICATION_ICON.md)
  - **Default**: If not specified, a music note icon is created automatically
- **skipInterval**: Configure via `enableMediaControls()` options instead

## 📱 Platform Requirements

### Expo
- Expo SDK 54 or newer (developed and tested against SDK 57)
- Development build or bare workflow (not Expo Go)

### iOS
- iOS 15.1 or higher

### Android
- Android API level 24 (Android 7.0) or higher

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guide](https://github.com/NO1225/expo-media-control/blob/main/CONTRIBUTING.md) for details.

## 📋 Changelog

See [CHANGELOG.md](https://github.com/NO1225/expo-media-control/blob/main/CHANGELOG.md) for detailed release notes.

## 📄 License

MIT License - see [LICENSE](https://github.com/NO1225/expo-media-control/blob/main/LICENSE) file for details.

## 🙏 Acknowledgments

- React Native community for the foundation
- Expo team for the excellent module system
- Original react-native-music-control contributors for inspiration

---

Made with ❤️ by [Juma](https://github.com/NO1225)
