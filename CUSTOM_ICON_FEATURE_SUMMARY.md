# Custom Notification Icon Feature - Summary

## What Changed

Added support for users to provide their own custom notification icons for Android media notifications.

## How It Works

### For Users

Users can now specify a custom notification icon in their `app.json`:

```json
{
  "expo": {
    "plugins": [
      [
        "expo-media-control",
        {
          "notificationIcon": "./assets/my-custom-icon.png"
        }
      ]
    ]
  }
}
```

### Behind the Scenes

1. **Plugin Enhanced** (`plugin/src/index.ts`):
   - Added `withDangerousMod` to handle file copying during prebuild
   - Detects if `notificationIcon` is a file path (contains `/` or `.`)
   - Automatically copies the icon from project assets to Android drawable
   - Falls back to default music note icon if file not found

2. **Build-Time Processing**:
   - During `npx expo prebuild`, the plugin:
     - Checks if user provided a custom icon path
     - Copies the file to `android/app/src/main/res/drawable/`
     - Names it appropriately (e.g., `notification_icon.png`)
     - Or creates default `ic_notification.xml` if none specified

3. **Runtime Behavior** (unchanged):
   - Android module looks for icons in this order:
     1. Custom icon from metadata
     2. Standard icon names (`ic_notification`, etc.)
     3. App launcher icon
     4. System default

## Icon Requirements

- **Format**: PNG (recommended 48x48 to 96x96 pixels)
- **Style**: MUST be monochrome (white on transparent background)
- **No colors**: Colored icons render as solid white shapes in notifications
- **Simple design**: Clear at small sizes

## Usage Examples

### Example 1: Custom PNG Icon

```json
{
  "plugins": [
    ["expo-media-control", {
      "notificationIcon": "./assets/notification-icon.png"
    }]
  ]
}
```

### Example 2: No Icon (Use Default)

```json
{
  "plugins": [
    ["expo-media-control", {
      // No notificationIcon - uses default music note
    }]
  ]
}
```

### Example 3: Bare Workflow Resource Reference

```json
{
  "plugins": [
    ["expo-media-control", {
      "notificationIcon": "ic_notification"  // Must exist in drawable/
    }]
  ]
}
```

## Files Modified

1. **plugin/src/index.ts**:
   - Enhanced `withAndroidMediaControl` with `withDangerousMod`
   - Added file copying logic
   - Added path detection
   - Improved documentation

2. **Documentation**:
   - Created `CUSTOM_NOTIFICATION_ICON.md` - Comprehensive guide
   - Updated `README.md` - Added link to custom icon guide
   - Created `ANDROID_NOTIFICATION_ICON.md` - Technical reference

## Testing

```bash
# 1. Add custom icon to project
cp my-icon.png assets/notification-icon.png

# 2. Update app.json
# Add notificationIcon: "./assets/notification-icon.png"

# 3. Prebuild
npx expo prebuild --clean

# 4. Verify icon was copied
ls android/app/src/main/res/drawable/notification_icon.png

# 5. Build and run
npx expo run:android

# 6. Play media and check notification
```

## Benefits

1. **User Customization**: Users can brand their notifications
2. **Automatic Processing**: No manual file copying needed
3. **Proper Fallbacks**: Default icon if custom one fails
4. **Clear Documentation**: Step-by-step guides for users
5. **Follows Expo Best Practices**: Uses config plugin properly

## Backward Compatibility

- ✅ Fully backward compatible
- ✅ Existing projects without `notificationIcon` get default icon
- ✅ No breaking changes
- ✅ Existing behavior preserved

## Future Enhancements

Potential improvements for future versions:
1. Automatic monochrome conversion (using image processing)
2. Multi-density PNG generation
3. Support for SVG input
4. Icon validation during prebuild
5. iOS notification icon customization
