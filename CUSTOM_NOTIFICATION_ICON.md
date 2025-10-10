# Custom Notification Icon Guide

## Overview

You can use your own custom notification icon for the media control notifications on Android. This guide shows you how.

## ✅ Requirements

Your custom icon MUST be:
- **Monochrome**: White silhouette on transparent background
- **PNG format**: Any resolution (recommended: 48x48 to 96x96 pixels)
- **Simple design**: Clear and recognizable at small sizes
- **No colors or gradients**: These will render as solid white shapes

## 🎨 Option 1: Use Your Own Icon (Recommended)

### Step 1: Create Your Icon

Create a monochrome PNG icon. Tools you can use:
- **Photoshop/GIMP**: Create white silhouette on transparent background
- **Online Tools**: [Android Asset Studio](http://romannurik.github.io/AndroidAssetStudio/icons-notification.html)
- **Figma/Sketch**: Export as PNG with transparency

### Step 2: Add to Your Project

Place your icon in the assets folder:
```
your-project/
├── assets/
│   ├── notification-icon.png  ← Your custom icon here
│   ├── icon.png
│   └── splash.png
```

### Step 3: Configure in app.json

```json
{
  "expo": {
    "plugins": [
      [
        "expo-media-control",
        {
          "notificationIcon": "./assets/notification-icon.png"
        }
      ]
    ]
  }
}
```

### Step 4: Prebuild

```bash
npx expo prebuild --clean
```

The plugin will automatically:
1. Find your icon at `./assets/notification-icon.png`
2. Copy it to `android/app/src/main/res/drawable/`
3. Configure Android to use it

### Step 5: Build and Run

```bash
npx expo run:android
```

Your custom icon will now appear in media notifications! 🎉

## 🎵 Option 2: Use Default Icon

If you don't specify a `notificationIcon`, the module automatically creates a default music note icon:

```json
{
  "expo": {
    "plugins": [
      [
        "expo-media-control",
        {
          // No notificationIcon specified
        }
      ]
    ]
  }
}
```

## 🔧 Option 3: Reference Existing Resource (Bare Workflow)

If you already have an Android drawable resource:

```json
{
  "expo": {
    "plugins": [
      [
        "expo-media-control",
        {
          "notificationIcon": "ic_notification"
        }
      ]
    ]
  }
}
```

Note: The icon must exist at `android/app/src/main/res/drawable/ic_notification.xml` (or `.png`)

## 📐 Creating the Perfect Notification Icon

### Size Recommendations

- **Minimum**: 24x24 pixels
- **Recommended**: 48x48 to 96x96 pixels
- **Maximum**: No limit, but larger files waste space

### Design Guidelines

1. **Use simple shapes**: Complex details won't be visible
2. **Keep it recognizable**: Should be clear what it represents
3. **Center the icon**: Leave some padding around edges
4. **Test on device**: Always verify how it looks in actual notifications

### Example Icons

Good for music apps:
- Music note (♪)
- Headphones
- Play button
- Sound wave
- Vinyl record

## 🛠️ Creating Icons with Different Tools

### Using Photoshop/GIMP

1. Create new 96x96px image with transparent background
2. Draw your icon in white (#FFFFFF)
3. Export as PNG-24 with transparency
4. Save as `notification-icon.png`

### Using Online Tools

**Android Asset Studio** (Recommended):
1. Go to http://romannurik.github.io/AndroidAssetStudio/icons-notification.html
2. Upload your icon or use clipart
3. It will automatically create the monochrome version
4. Download and place in your assets folder

### Using Figma

1. Create 96x96 frame
2. Draw icon in white
3. Export as PNG
4. Check "Transparent background"

## ❌ Common Mistakes

### ❌ Using Colored Icons

```
Bad: notification-icon.png with blue color
Result: Will appear as solid white square
```

### ❌ Wrong File Path

```json
{
  "notificationIcon": "notification-icon.png"  // ❌ Missing ./assets/
}
```

Correct:
```json
{
  "notificationIcon": "./assets/notification-icon.png"  // ✅
}
```

### ❌ Forgetting to Prebuild

After changing the icon, you must run:
```bash
npx expo prebuild --clean
```

## 🔍 Troubleshooting

### Icon appears as white circle/square

**Cause**: Your icon has colors or isn't monochrome.

**Solution**:
1. Convert icon to pure white on transparent background
2. Use online tool to create monochrome version
3. Rebuild with `npx expo prebuild --clean`

### Icon not updating

**Cause**: Old build cache.

**Solution**:
```bash
npx expo prebuild --clean
npx expo run:android --no-build-cache
```

### "Custom icon not found" error

**Cause**: File path is incorrect.

**Solution**:
1. Check the file exists at the specified path
2. Use relative path from project root: `./assets/...`
3. Check file name spelling

### Icon is too small/large

**Cause**: Icon file resolution is too small or too large.

**Solution**:
- Use 48x48 to 96x96 pixels
- Android will scale appropriately

## 📱 Testing Your Icon

1. Build and install the app
2. Play some media
3. Swipe down notification shade
4. Check your icon appears correctly
5. Test on different Android versions if possible

## 🎯 Best Practices

1. **Keep it simple**: Use basic shapes
2. **Use SVG source**: Convert to PNG for best quality
3. **Test on device**: Emulator might not show it correctly
4. **Version control**: Keep source files in your repo
5. **Document**: Note what the icon represents

## 📚 Resources

- [Android Notification Icon Guidelines](https://developer.android.com/training/notify-user/build-notification#notification-icon)
- [Material Design Icons](https://material.io/design/iconography/product-icons.html)
- [Android Asset Studio](http://romannurik.github.io/AndroidAssetStudio/)

## 💡 Example Project Structure

```
your-music-app/
├── app.json
├── assets/
│   ├── notification-icon.png      ← Your custom icon
│   ├── icon.png
│   └── splash.png
├── src/
│   └── ...
└── package.json
```

**app.json**:
```json
{
  "expo": {
    "name": "My Music App",
    "slug": "my-music-app",
    "plugins": [
      [
        "expo-media-control",
        {
          "enableBackgroundAudio": true,
          "notificationIcon": "./assets/notification-icon.png"
        }
      ]
    ]
  }
}
```

## ✨ Complete Example

1. **Create icon**: `assets/notification-icon.png` (monochrome, 96x96px)
2. **Configure**: Add to `app.json` plugins
3. **Prebuild**: `npx expo prebuild --clean`
4. **Run**: `npx expo run:android`
5. **Test**: Play media and check notification

That's it! Your custom notification icon will now appear in all media control notifications. 🎉
