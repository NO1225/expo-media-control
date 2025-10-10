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
-  **Background Audio Support** - Continue playback when app is backgrounded
- 🔔 **Audio Interruption Handling** - Graceful handling of calls, notifications, and interruptions
- 📳 **Volume Control Integration** - Monitor and respond to system volume changes
- 🎯 **Event-Driven Architecture** - React to user interactions with system controls
- 🛠️ **Full TypeScript Support** - Complete type definitions and IntelliSense support
- 🔧 **Highly Configurable** - Extensive customization options for both platforms

## � Installation

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

**Note:** The plugin configuration is for **build-time** setup only. Runtime configuration (like `skipInterval`, notification appearance, etc.) should be passed to `enableMediaControls()`. See [API Reference](#api-reference) below.

**💡 Custom Notification Icon:** Android requires monochrome (white on transparent) icons for notifications. See our [detailed guide](./CUSTOM_NOTIFICATION_ICON.md) on creating and using custom icons.
```

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
          notification: {
            icon: 'ic_music_note',
            color: '#1976D2',
          },
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

## � API Reference

### Core Methods

#### `enableMediaControls(options?: MediaControlOptions): Promise<void>`

Enables media controls with specified configuration.

```typescript
interface MediaControlOptions {
  capabilities?: Command[];
  notification?: {
    icon?: string;              // Notification icon resource name (bare workflow only - use plugin config for managed workflow)
    largeIcon?: MediaArtwork;   // Large icon for rich notifications
    color?: string;             // Notification accent color (Android)
    showWhenClosed?: boolean;   // Keep notification when app closes
  };
  ios?: {
    skipInterval?: number;      // Skip interval in seconds (default: 15)
  };
  android?: {
    skipInterval?: number;      // Skip interval in seconds (default: 15)
  };
}

await MediaControl.enableMediaControls({
  capabilities: [Command.PLAY, Command.PAUSE, Command.NEXT_TRACK],
  notification: {
    // Note: For managed workflow, set icon in app.json plugin config instead
    // icon: 'ic_music_note',   // Bare workflow only: reference existing drawable resource
    color: '#1976D2',
  },
    color: '#1976D2',
  },
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
  duration?: number;
  elapsedTime?: number;
  genre?: string;
  trackNumber?: number;
  albumTrackCount?: number;
  date?: string;
  rating?: MediaRating;
  color?: string;
  colorized?: boolean;
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

#### `updatePlaybackState(state: PlaybackState, position?: number): Promise<void>`

Updates the current playback state and position.

```typescript
enum PlaybackState {
  NONE = 0,
  STOPPED = 1,
  PLAYING = 2,
  PAUSED = 3,
  BUFFERING = 4,
  ERROR = 5,
}

// Start playing at 45 seconds
await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 45);

// Pause playback
await MediaControl.updatePlaybackState(PlaybackState.PAUSED);

// Show buffering
await MediaControl.updatePlaybackState(PlaybackState.BUFFERING);
```

#### Other Core Methods

- `disableMediaControls(): Promise<void>` - Disable and cleanup controls
- `resetControls(): Promise<void>` - Reset to default state
- `isEnabled(): Promise<boolean>` - Check if controls are enabled
- `getCurrentMetadata(): Promise<MediaMetadata | null>` - Get current metadata
- `getCurrentState(): Promise<PlaybackState>` - Get current state

### Event Handling

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

#### Audio Interruption Events

```typescript
const removeInterruptionListener = MediaControl.addAudioInterruptionListener(
  (interruption: AudioInterruption) => {
    if (interruption.type === 'begin') {
      // Pause playback due to interruption
      if (interruption.shouldResume) {
        // Save state to resume later
      }
    } else if (interruption.type === 'end') {
      // Resume playback if appropriate
      if (interruption.shouldResume) {
        // Resume playback
      }
    }
  }
);
```

#### Volume Change Events

```typescript
const removeVolumeListener = MediaControl.addVolumeChangeListener(
  (change: VolumeChange) => {
    console.log('Volume:', change.volume);
    console.log('User initiated:', change.userInitiated);
  }
);
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
  VOLUME_UP = 'volumeUp',
  VOLUME_DOWN = 'volumeDown',
}
```

#### `addAudioInterruptionListener(listener: AudioInterruptionListener): () => void`

Adds a listener for audio interruption events (calls, notifications, etc.).

```typescript
const removeListener = MediaControl.addAudioInterruptionListener((interruption) => {
  if (interruption.type === 'begin') {
    // Audio interrupted - pause playback
    pausePlayback();
  } else if (interruption.type === 'end' && interruption.shouldResume) {
    // Interruption ended - can resume playback
    resumePlayback();
  }
});
```

#### `addVolumeChangeListener(listener: VolumeChangeListener): () => void`

Adds a listener for system volume changes.

```typescript
const removeListener = MediaControl.addVolumeChangeListener((change) => {
  console.log('Volume:', change.volume); // 0.0 to 1.0
  console.log('User initiated:', change.userInitiated);
});
```

#### `removeAllListeners(): Promise<void>`

Removes all event listeners.

```typescript
await MediaControl.removeAllListeners();
```

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

## 📱 Platform-Specific Features

### iOS Features
- Control Center integration
- Lock screen controls
- CarPlay support (automatic)
- Apple Watch support (automatic)
- Background audio with proper audio session management

### Android Features
- Media notification with custom actions
- Lock screen controls
- Android Auto support (automatic)
- Audio focus management
- Hardware button support

## ⚠️ Important Notes

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
- `FOREGROUND_SERVICE` - For background media control
- `WAKE_LOCK` - To prevent device sleep during playback
- `ACCESS_NETWORK_STATE` - For artwork loading

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

**Events not firing:**
- Ensure event listeners are properly registered
- Check that the commands are enabled in capabilities
- Verify iOS audio session is properly configured

**Android notification not showing:**
- Check notification permissions on Android 13+
- Verify notification channel configuration
- Ensure foreground service permissions

### Debug Mode

Enable debug logging:

```typescript
// This will show detailed logs in development
console.log('Media Control Debug Mode - Check native logs for detailed information');
```

Check native logs:
- **iOS**: Xcode console or device logs
- **Android**: `adb logcat` or Android Studio logs

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
  VOLUME_UP = 'volumeUp',
  VOLUME_DOWN = 'volumeDown',
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
  elapsedTime?: number;             // Current position in seconds
  genre?: string;                   // Music genre
  trackNumber?: number;             // Track number in album
  albumTrackCount?: number;         // Total tracks in album
  date?: string;                    // Release date
  rating?: MediaRating;             // Track rating
  color?: string;                   // Notification color (Android)
  colorized?: boolean;              // Use colorized notification (Android)
}
```

### MediaControlOptions

```typescript
interface MediaControlOptions {
  capabilities?: Command[];          // Enabled commands
  notification?: {                   // Android notification config
    icon?: string;                   // Small icon resource name (bare workflow)
    largeIcon?: MediaArtwork;        // Large icon (artwork)
    color?: string;                  // Background color
    showWhenClosed?: boolean;        // Show when app closed
  };
  ios?: {                           // iOS-specific config
    skipInterval?: number;           // Skip interval in seconds (default: 15)
  };
  android?: {                       // Android-specific config
    skipInterval?: number;           // Skip interval in seconds (default: 15)
  };
}
```

## 🎨 Platform-Specific Features

### iOS Features

- **Control Center Integration** - Native iOS Control Center controls
- **Lock Screen Controls** - Rich lock screen media controls with artwork
- **Background Audio** - Continues playback when app is backgrounded
- **MPNowPlayingInfoCenter** - Full integration with iOS media system
- **Remote Command Center** - Handles all iOS remote control events
- **AirPlay Support** - Works with AirPlay and Bluetooth devices

### Android Features

- **MediaSession Integration** - Native Android MediaSession support
- **Notification Controls** - Rich media notifications with custom actions
- **Lock Screen Controls** - Media controls on Android lock screen
- **Bluetooth Integration** - Works with Bluetooth headphones and car systems
- **Android Auto Support** - Compatible with Android Auto

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
- **Runtime icon**: Use `notification.icon` in `enableMediaControls()` only for bare workflow runtime changes

## 🐛 Troubleshooting

### Common Issues

#### Controls not appearing

1. Ensure you've called `enableMediaControls()` successfully
2. Check that you've set metadata with `updateMetadata()`
3. Verify the playback state is set correctly
4. On iOS, ensure background audio capability is enabled

#### Audio interruptions not working

1. Verify audio session is configured properly
2. Check that audio focus is requested on Android
3. Ensure interruption listeners are set up before enabling controls

#### Artwork not loading

1. Verify the artwork URI is accessible
2. Check network permissions for remote images
3. Ensure local file paths are correct
4. Try different image formats (JPEG, PNG are preferred)

#### Notification not showing on Android

1. Verify notification permissions
2. Check notification channel configuration
3. Ensure the app has notification access
4. Try different notification importance levels

### Debug Tips

1. **Enable logging** - Check console output for error messages
2. **Test on device** - Media controls require physical devices
3. **Check permissions** - Ensure all required permissions are granted
4. **Verify configuration** - Double-check plugin configuration in app.json
5. **Test incrementally** - Enable features one by one to isolate issues

## 📱 Platform Requirements

### iOS
- iOS 11.0 or higher
- Xcode 12 or higher
- Swift 5.0 or higher

### Android
- Android API level 21 (Android 5.0) or higher
- Kotlin support
- AndroidX libraries

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guide](https://github.com/NO1225/expo-media-control/blob/main/CONTRIBUTING.md) for details.

## � Changelog

See [CHANGELOG.md](https://github.com/NO1225/expo-media-control/blob/main/CHANGELOG.md) for detailed release notes.

## � License

MIT License - see [LICENSE](https://github.com/NO1225/expo-media-control/blob/main/LICENSE) file for details.

## 🙏 Acknowledgments

- React Native community for the foundation
- Expo team for the excellent module system
- Original react-native-music-control contributors for inspiration

---

Made with ❤️ by [Juma](https://github.com/NO1225)
