# Changelog

All notable changes to this package will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - Unreleased

First release. Requires `expo-media-control` 2.1 or later.

### ✨ Added
- `CarLibrary.setLibrary()`: a browsable library (tabs, folders, playable items) for Android Auto and CarPlay, saved natively so the car can browse it before JavaScript runs
- `CarLibrary.setChildrenLoader()`: folders loaded on demand, cached and refreshed in the background
- `CarLibrary.setSearchHandler()`: in-car search on Android Auto
- `CarLibrary.addPlayRequestListener()`: play requests for picked items and voice requests ("Hey Google / Siri, play X")
- `CarLibrary.notifyChildrenChanged()`
- **Android Auto**: media app declaration, a `MediaLibraryProvider` for expo-media-control's service, list/grid content styles, explicit badges, and a content provider for artwork
- **CarPlay**: CarPlay and phone scene delegates, tab and list templates, the system Now Playing template
- **Siri**: in-app `INPlayMediaIntent` handling (opt-in)
- Config plugin: CarPlay scenes, `carPlayEntitlement`, `siri`, `siriMediaCategories`, `androidAuto`
