# 📱 Expo Media Control

A comprehensive, modern media control module for Expo and React Native applications. Provides seamless integration with system media controls including Control Center (iOS), lock screen controls, notification controls, and remote control events handling with full TypeScript support.

## ✨ Features

- 🎵 **Complete Media Session Management** - Full control over media playback state and metadata
- 🔒 **Lock Screen Integration** - Native lock screen controls with artwork support
- 📱 **Control Center Support** - iOS Control Center and Android notification controls
- 🎨 **Artwork Display** - Support for local and remote artwork/album covers
- ⏯️ **Rich Playback Controls** - Play, pause, stop, next, previous, seek, skip, and rating controls
- 🔊 **Audio Focus Management** - Proper audio focus handling for Android
- 📢 **Background Audio Support** - Continue playback when app is backgrounded
- 🔔 **Audio Interruption Handling** - Graceful handling of calls, notifications, and other interruptions
- 📳 **Volume Control Integration** - Monitor and respond to system volume changes
- 🎯 **Event-Driven Architecture** - React to user interactions with system controls
- 🛠️ **Full TypeScript Support** - Complete type definitions and IntelliSense support
- 🔧 **Highly Configurable** - Extensive customization options for both iOS and Android

## 📦 Installation

```bash
npm install expo-media-control
# or
yarn add expo-media-control
```

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
          "skipInterval": 15,
          "notificationChannel": {
            "name": "Music Playback",
            "description": "Controls for music playback",
            "importance": "low"
          }
        }
      ]
    ]
  }
}
```

## 🚀 Quick Start

```typescript
import React, { useEffect, useState } from 'react';
import {
  MediaControl,
  PlaybackState,
  Command,
  MediaControlEvent,
} from 'expo-media-control';

export default function App() {
  const [isEnabled, setIsEnabled] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);

  useEffect(() => {
    // Set up event listeners
    const removeListener = MediaControl.addListener((event: MediaControlEvent) => {
      switch (event.command) {
        case Command.PLAY:
          handlePlay();
          break;
        case Command.PAUSE:
          handlePause();
          break;
        case Command.NEXT_TRACK:
          handleNext();
          break;
        // Handle other commands...
      }
    });

    return removeListener; // Cleanup on unmount
  }, []);

  const enableControls = async () => {
    try {
      await MediaControl.enableMediaControls({
        capabilities: [
          Command.PLAY,
          Command.PAUSE,
          Command.NEXT_TRACK,
          Command.PREVIOUS_TRACK,
        ],
        notification: {
          icon: 'ic_music_note',
          color: '#2196F3',
        },
      });

      // Set initial metadata
      await MediaControl.updateMetadata({
        title: 'Song Title',
        artist: 'Artist Name',
        album: 'Album Name',
        duration: 240,
        artwork: {
          uri: 'https://example.com/artwork.jpg',
          width: 300,
          height: 300,
        },
      });

      setIsEnabled(true);
    } catch (error) {
      console.error('Failed to enable media controls:', error);
    }
  };

  const handlePlay = async () => {
    setIsPlaying(true);
    await MediaControl.updatePlaybackState(PlaybackState.PLAYING);
  };

  const handlePause = async () => {
    setIsPlaying(false);
    await MediaControl.updatePlaybackState(PlaybackState.PAUSED);
  };

  // Your UI components...
}
```

## 📖 API Reference

### Core Methods

#### `enableMediaControls(options?: MediaControlOptions): Promise<void>`

Enables media controls with specified configuration.

```typescript
await MediaControl.enableMediaControls({
  capabilities: [
    Command.PLAY,
    Command.PAUSE,
    Command.STOP,
    Command.NEXT_TRACK,
    Command.PREVIOUS_TRACK,
    Command.SKIP_FORWARD,
    Command.SKIP_BACKWARD,
    Command.SEEK,
    Command.SET_RATING,
  ],
  notification: {
    icon: 'ic_music_note',
    largeIcon: { uri: 'https://example.com/large-icon.png' },
    color: '#2196F3',
    showWhenClosed: true,
    skipInterval: 15,
  },
  ios: {
    skipInterval: 15,
    showPlaybackPosition: true,
  },
  android: {
    requestAudioFocus: true,
    stopForegroundGracePeriod: 5000,
  },
});
```

#### `disableMediaControls(): Promise<void>`

Disables media controls and cleans up all resources.

```typescript
await MediaControl.disableMediaControls();
```

#### `updateMetadata(metadata: MediaMetadata): Promise<void>`

Updates the media metadata displayed in system controls.

```typescript
await MediaControl.updateMetadata({
  title: 'Bohemian Rhapsody',
  artist: 'Queen',
  album: 'A Night at the Opera',
  duration: 355,
  elapsedTime: 45,
  genre: 'Rock',
  trackNumber: 11,
  albumTrackCount: 12,
  date: '1975',
  artwork: {
    uri: 'https://example.com/album-art.jpg',
    width: 512,
    height: 512,
  },
  rating: {
    type: RatingType.FIVE_STARS,
    value: 4.5,
    maxValue: 5,
  },
  color: '#2196F3',
  colorized: true,
});
```

#### `updatePlaybackState(state: PlaybackState, position?: number): Promise<void>`

Updates the current playback state and optionally the playback position.

```typescript
// Start playing
await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 45);

// Pause playback
await MediaControl.updatePlaybackState(PlaybackState.PAUSED, 45);

// Stop playback
await MediaControl.updatePlaybackState(PlaybackState.STOPPED, 0);

// Buffering
await MediaControl.updatePlaybackState(PlaybackState.BUFFERING);
```

#### `resetControls(): Promise<void>`

Resets all media control information to the default state.

```typescript
await MediaControl.resetControls();
```

### State Query Methods

#### `isEnabled(): Promise<boolean>`

Checks if media controls are currently enabled.

```typescript
const enabled = await MediaControl.isEnabled();
```

#### `getCurrentMetadata(): Promise<MediaMetadata | null>`

Gets the current media metadata.

```typescript
const metadata = await MediaControl.getCurrentMetadata();
```

#### `getCurrentState(): Promise<PlaybackState>`

Gets the current playback state.

```typescript
const state = await MediaControl.getCurrentState();
```

### Event Handling

#### `addListener(listener: MediaControlEventListener): () => void`

Adds a listener for media control events. Returns a function to remove the listener.

```typescript
const removeListener = MediaControl.addListener((event) => {
  console.log('Command:', event.command);
  console.log('Data:', event.data);
  console.log('Timestamp:', event.timestamp);
  
  switch (event.command) {
    case Command.PLAY:
      // Handle play
      break;
    case Command.PAUSE:
      // Handle pause
      break;
    case Command.SEEK:
      // Handle seek to position: event.data.position
      break;
    case Command.SET_RATING:
      // Handle rating: event.data.rating, event.data.type
      break;
  }
});

// Don't forget to remove the listener
removeListener();
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
    icon?: string;                   // Small icon resource name
    largeIcon?: MediaArtwork;        // Large icon (artwork)
    color?: string;                  // Background color
    showWhenClosed?: boolean;        // Show when app closed
    skipInterval?: number;           // Skip interval in seconds
  };
  ios?: {                           // iOS-specific config
    skipInterval?: number;           // Skip interval in seconds
    showPlaybackPosition?: boolean;  // Show position info
  };
  android?: {                       // Android-specific config
    requestAudioFocus?: boolean;     // Request audio focus
    stopForegroundGracePeriod?: number; // Grace period in ms
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
- **Audio Focus Management** - Proper audio focus handling for interruptions
- **Lock Screen Controls** - Media controls on Android lock screen
- **Bluetooth Integration** - Works with Bluetooth headphones and car systems
- **Android Auto Support** - Compatible with Android Auto

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
          "skipInterval": 15,
          "notificationChannel": {
            "name": "Music Player",
            "description": "Media playback controls",
            "importance": "low"
          },
          "notificationIcon": "ic_music_note"
        }
      ]
    ]
  }
}
```

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

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [Expo Modules API](https://docs.expo.dev/modules/overview/)
- Inspired by [react-native-track-player](https://github.com/doublesymmetry/react-native-track-player)
- iOS implementation uses [MediaPlayer framework](https://developer.apple.com/documentation/mediaplayer/)
- Android implementation uses [MediaSessionCompat](https://developer.android.com/guide/topics/media/mediasession)

## 📞 Support

- 📚 [Documentation](https://github.com/NO1225/expo-media-control#readme)
- 🐛 [Issues](https://github.com/NO1225/expo-media-control/issues)
- 💬 [Discussions](https://github.com/NO1225/expo-media-control/discussions)

---

Made with ❤️ by [Juma](https://github.com/NO1225)

# API documentation

- [Documentation for the latest stable release](https://docs.expo.dev/versions/latest/sdk/media-control/)
- [Documentation for the main branch](https://docs.expo.dev/versions/unversioned/sdk/media-control/)

# Installation in managed Expo projects

For [managed](https://docs.expo.dev/archive/managed-vs-bare/) Expo projects, please follow the installation instructions in the [API documentation for the latest stable release](#api-documentation). If you follow the link and there is no documentation available then this library is not yet usable within managed projects &mdash; it is likely to be included in an upcoming Expo SDK release.

# Installation in bare React Native projects

For bare React Native projects, you must ensure that you have [installed and configured the `expo` package](https://docs.expo.dev/bare/installing-expo-modules/) before continuing.

### Add the package to your npm dependencies

```
npm install expo-media-control
```

### Configure for Android




### Configure for iOS

Run `npx pod-install` after installing the npm package.

# Contributing

Contributions are very welcome! Please refer to guidelines described in the [contributing guide]( https://github.com/expo/expo#contributing).
