# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### 🐛 Fixed
- **Android 13+: skip forward/backward buttons missing** ([#20](https://github.com/NO1225/expo-media-control/issues/20), [#23](https://github.com/NO1225/expo-media-control/issues/23)) - `skipForward`/`skipBackward` are now exposed as MediaSession custom actions, which is what the Android 13+ system media player renders
- **Reconfiguring capabilities had no effect** ([#23](https://github.com/NO1225/expo-media-control/issues/23)) - calling `enableMediaControls()` again now applies the new capabilities/skip interval on Android (service was already bound, so the new options were dropped) and iOS (the call returned early)
- **Duplicate events after re-enabling** - JS no longer registers another native listener on every `enableMediaControls()` call
- **Duplicate rating events on iOS** - like/dislike targets were re-added on every metadata update
- **Stale track info** - a slow artwork download for a previous track could overwrite the current track's metadata (Android and iOS); text metadata is now published immediately and artwork is merged in when loaded
- **iOS metadata race** - `updatePlaybackState()` right after `updateMetadata()` could restore the previous track's info
- **iOS playback state** - `MPNowPlayingInfoCenter.playbackState` is now kept in sync ([#21](https://github.com/NO1225/expo-media-control/pull/21), thanks @kockar96)
- Volume change listener subscribed to the wrong native event name
- Android: large artwork is downsampled before use, HTTP connections are closed, stop button uses a stop icon, duplicate `OnDestroy` removed
- Config plugin no longer adds duplicate Android permissions

### ⬆️ Updated
- Expo SDK 57 / React Native 0.86 / TypeScript 6 for development and the example app
- CI uses Node 22
- Added unit tests for the JS layer
- Removed verbose debug logging from the JS layer

### 🔧 Changed
- **Configuration Cleanup** - Removed redundant configuration options for clearer API
  - Removed `skipInterval` from plugin config (build-time) - now only configurable at runtime via `enableMediaControls()`
  - Removed unused `notificationChannel` from plugin config
  - Removed `android.requestAudioFocus` option - audio focus should be managed by media player, not control UI

### 🗑️ Removed
- **Dead Code Elimination** - Removed ~330 lines of unused/unimplemented code for better maintainability
  - Removed audio focus management code (~150 lines) - this is a control UI module, not a media player
  - Removed unused helper methods: `getPlaybackActions()`, `getSkipInterval()`, artwork loading methods (~130 lines)
  - Removed unused imports: `Bitmap`, `BitmapFactory`, `URL`, `PlaybackStateCompat` (partial)
  - Removed `addAudioInterruptionListener()` API (~50 lines) - was declared but never implemented, misleading to users
  - Removed `AudioInterruption` interface and related types
  
### 📚 Documentation
- Clarified that audio focus and interruption handling should be done by media player (expo-audio, react-native-video, etc.)
- Removed misleading API documentation for unimplemented `addAudioInterruptionListener()`
- Updated README to distinguish between build-time (plugin) and runtime (enableMediaControls) configuration
- Improved documentation for configuration options and their purposes
- Removed "Audio Interruption Handling" from feature list

### 🎯 Architecture
- **Clearer Separation of Concerns** - Module now focuses solely on media control UI, not playback logic
- **Reduced from 828 to 687 lines** in main Android module (17% reduction)
- **More maintainable** - Less code to maintain, clearer responsibilities

## [1.0.0] - 2024-12-20

### 🎉 Initial Release

This is the first stable release of `expo-media-control`, a comprehensive media control module for Expo and React Native applications.

### ✨ Added

#### Core Features
- **Complete Media Session Management** - Full control over media playback state and metadata
- **Cross-Platform Support** - Native iOS and Android implementations with unified TypeScript API
- **Lock Screen Integration** - Native lock screen controls with artwork support
- **System Integration** - iOS Control Center and Android notification controls
- **Rich Artwork Display** - Support for local and remote artwork/album covers with automatic scaling
- **Comprehensive Playback Controls** - Play, pause, stop, next, previous, seek, skip, and rating controls

#### Platform-Specific Features

**iOS Implementation:**
- Control Center integration with full metadata display
- Lock screen controls with artwork
- CarPlay support (automatic)
- Apple Watch support (automatic)
- Background audio with proper audio session management
- MPNowPlayingInfoCenter integration
- MPRemoteCommandCenter for all remote controls
- Audio interruption handling for calls and notifications

**Android Implementation:**
- Media notification with custom actions and artwork
- Lock screen controls with MediaSessionCompat
- Android Auto support (automatic)
- Hardware media button support
- MediaBrowserCompat for enhanced system integration
- Background service support for continuous playback

#### Advanced Features
- **Event-Driven Architecture** - React to user interactions with system controls
- **Audio Interruption Handling** - Graceful handling of calls, notifications, and other interruptions
- **Volume Control Integration** - Monitor and respond to system volume changes
- **Background Audio Support** - Continue playback when app is backgrounded
- **Full TypeScript Support** - Complete type definitions and IntelliSense support
- **Highly Configurable** - Extensive customization options for both platforms

#### API Features
- **Async/Await API** - Modern promise-based API throughout
- **Type Safety** - Comprehensive TypeScript definitions for all methods and events
- **Event Listeners** - Robust event system for media control commands, audio interruptions, and volume changes
- **State Management** - Query current state, metadata, and control status
- **Error Handling** - Comprehensive error handling with descriptive error messages

#### Configuration
- **Expo Plugin** - Automatic configuration through app.json/app.config.js
- **iOS Background Modes** - Automatic UIBackgroundModes configuration
- **Android Permissions** - Automatic permission management
- **Notification Channels** - Configurable Android notification channels
- **Audio Session Categories** - Configurable iOS audio session handling

### 🔧 Technical Implementation

#### Architecture
- **Modern Kotlin** implementation for Android with coroutines and best practices
- **Swift** implementation for iOS with async/await and proper memory management
- **Expo Modules API** for seamless React Native integration
- **Thread-Safe Operations** - All native operations properly handled across threads
- **Memory Management** - Proper cleanup and resource management on both platforms

#### Dependencies
- Android: MediaSessionCompat, NotificationCompat, AudioManager
- iOS: MediaPlayer framework, AVFoundation for audio session
- JavaScript: Expo Modules Core for native bridge

### 📱 Supported Platforms

- **iOS**: 15.1+ (iPhone, iPad, Apple Watch, CarPlay)
- **Android**: API 24+ (Android 7.0+, Android Auto)
- **React Native**: 0.64+
- **Expo**: SDK 47+

### 🎯 Use Cases

Perfect for:
- Music streaming applications
- Podcast players
- Audio book applications
- Radio streaming apps
- Meditation and wellness apps
- Educational audio content
- Any app requiring system-level media controls

### 📚 Documentation

- Comprehensive README with examples and best practices
- Full TypeScript API documentation
- Platform-specific implementation notes
- Troubleshooting guide for common issues
- Migration guide from other media control libraries

### 🔒 Security & Permissions

#### Automatically Handled Permissions:
- iOS: Background audio modes, remote control events
- Android: FOREGROUND_SERVICE, WAKE_LOCK, ACCESS_NETWORK_STATE

#### Privacy Considerations:
- No data collection or analytics
- Artwork loading respects network security policies
- Audio session management follows platform best practices

### 🚀 Performance

- Minimal battery impact during background playback
- Efficient artwork loading and caching
- Optimized for smooth UI updates
- Low memory footprint
- Fast startup and shutdown

### 🧪 Quality Assurance

- Comprehensive error handling throughout
- Memory leak testing and prevention
- Cross-device compatibility testing
- Performance profiling and optimization
- Thread safety validation

### 📦 Distribution

- Available on npm registry
- Full source code available on GitHub
- MIT license for commercial and open source use
- Semantic versioning for predictable updates

---

## Release Notes

### Version 1.0.0 Notes

This initial release represents a complete rewrite and modernization of media control functionality for React Native applications. Built from the ground up with:

- **Modern Architecture**: Using latest platform APIs and best practices
- **Type Safety**: Complete TypeScript coverage for better developer experience  
- **Cross-Platform**: Unified API with platform-specific optimizations
- **Production Ready**: Comprehensive testing and error handling
- **Extensible**: Plugin architecture for future enhancements

The module has been tested across various device configurations and use cases to ensure reliability and performance in production applications.

### Migration from Other Libraries

For developers migrating from `react-native-music-control` or similar libraries, this version provides:

- **Enhanced API**: Modern async/await pattern instead of callbacks
- **Better Type Safety**: Full TypeScript definitions
- **Improved Reliability**: Better error handling and resource management  
- **More Features**: Additional platform integrations and controls
- **Better Documentation**: Comprehensive guides and examples

### Known Limitations

- Web platform not yet supported (planned for future release)
- Some advanced CarPlay features require additional configuration
- Android Auto advanced features may need app-specific setup

### Support

- GitHub Issues for bug reports and feature requests
- Community Discord for discussions and support
- Comprehensive documentation and examples
- Migration assistance for existing implementations

---

**Full Changelog**: https://github.com/NO1225/expo-media-control/compare/...v1.0.0