# Notification Icon Fix Summary

## Problem
The notification icon was showing as a solid white circle/square because:
1. The config plugin was referencing a PNG file (`./assets/favicon.png`) that wasn't being copied to Android drawable resources
2. The Android code was looking for the icon in drawable resources but couldn't find it
3. The fallback was using the app launcher icon, which for colored icons renders as solid white in notifications

## Solution

### 1. Improved Android Icon Lookup (`MediaPlaybackService.kt`)
- Enhanced `getSmallIconResource()` to try multiple standard icon names
- Added better fallback strategy:
  1. Custom icon from plugin metadata
  2. Standard notification icon names (`ic_notification`, `notification_icon`, etc.)
  3. Mipmap launcher icon
  4. System default media icon

### 2. Updated Plugin Documentation (`plugin/src/index.ts`)
- Clarified that icon must be a drawable resource name (not a file path)
- Added comprehensive documentation about monochrome requirements
- Updated console log to provide clear setup instructions

### 3. Created Sample Icon
- Added `ic_notification.xml` in example app's drawable folder
- Vector drawable format (scales perfectly, uses less space)
- Simple music note icon suitable for media notifications

### 4. Added Comprehensive Guide
- Created `ANDROID_NOTIFICATION_ICON.md` with full setup instructions
- Included troubleshooting section
- Provided examples for both vector and PNG formats

## How to Use

### Quick Setup
1. Place a monochrome icon in `android/app/src/main/res/drawable/ic_notification.xml` (or .png)
2. Optional: Reference in app.json:
   ```json
   {
     "plugins": [
       ["expo-media-control", {
         "notificationIcon": "ic_notification"
       }]
     ]
   }
   ```
3. If not specified, the module will automatically look for standard icon names

### Icon Requirements
- **MUST be monochrome**: White silhouette on transparent background
- **No colors or gradients**: Will render as solid white shapes
- **Simple design**: Clear at small sizes

### Formats
- **Vector Drawable (Recommended)**: XML format in `drawable/` folder
- **PNG**: Monochrome images in density-specific folders

## Testing
1. Run `npx expo prebuild --clean` to apply changes
2. Run `npx expo run:android` to test on device
3. Play media and check the notification icon

## Files Modified
- `android/src/main/java/expo/modules/mediacontrol/MediaPlaybackService.kt`
- `plugin/src/index.ts`
- `example/app.json`
- Created: `example/android/app/src/main/res/drawable/ic_notification.xml`
- Created: `ANDROID_NOTIFICATION_ICON.md`
