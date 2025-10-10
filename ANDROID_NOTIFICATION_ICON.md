# Android Notification Icon Setup Guide

## Overview

Android notification icons have specific requirements and must be properly configured for the expo-media-control module.

## Requirements

Android notification icons MUST be:
- **Monochrome**: White silhouette on transparent background
- **No gradients or colors**: Any colors will render as solid white
- **Simple shapes**: Clear and recognizable at small sizes

## Icon Formats

You can use either:
1. **Vector Drawable (Recommended)**: XML format, scales perfectly
2. **PNG**: Must be monochrome, multiple densities recommended

## Setup Instructions

### Option 1: Vector Drawable (Recommended)

1. Create an XML file in `android/app/src/main/res/drawable/`:

```xml
<!-- ic_notification.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@android:color/white">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"/>
</vector>
```

2. Reference in your app.json:

```json
{
  "plugins": [
    [
      "expo-media-control",
      {
        "notificationIcon": "ic_notification"
      }
    ]
  ]
}
```

### Option 2: PNG Images

1. Create monochrome PNG icons for different densities:
   - `drawable-mdpi/ic_notification.png` (24x24 px)
   - `drawable-hdpi/ic_notification.png` (36x36 px)
   - `drawable-xhdpi/ic_notification.png` (48x48 px)
   - `drawable-xxhdpi/ic_notification.png` (72x72 px)
   - `drawable-xxxhdpi/ic_notification.png` (96x96 px)

2. Reference in your app.json:

```json
{
  "plugins": [
    [
      "expo-media-control",
      {
        "notificationIcon": "ic_notification"
      }
    ]
  ]
}
```

## Fallback Behavior

If no custom icon is specified or found, the module will attempt to use:
1. Standard notification icon names (`ic_notification`, `notification_icon`, etc.)
2. App launcher icon (`ic_launcher`)
3. System default media icon

## Troubleshooting

### Icon appears as solid white circle/square

**Cause**: Your icon is not monochrome or contains colors/gradients.

**Solution**: 
- Use a pure white silhouette on transparent background
- Remove all colors and gradients
- Consider using a vector drawable instead

### Icon not showing up

**Cause**: Icon resource not found or incorrectly named.

**Solution**:
- Check the file is in `android/app/src/main/res/drawable/`
- Verify the filename matches the configuration (without extension)
- For PNG, ensure you have at least one density folder
- Check the app.json configuration matches the filename

### Icon is too complex or detailed

**Cause**: Android notification icons should be simple.

**Solution**:
- Simplify the icon design
- Use basic shapes and silhouettes
- Test at 24x24dp size to ensure clarity

## Creating Icons

### Using Android Studio

1. Right-click on `res` folder → New → Image Asset
2. Select "Notification Icons"
3. Choose your source image
4. Android Studio will generate all densities automatically

### Using Online Tools

- [Android Asset Studio](http://romannurik.github.io/AndroidAssetStudio/icons-notification.html)
- [CleanMock](https://cleanmock.com/android-notification-icon-generator/)

### Manual Creation

1. Create white silhouette on transparent background
2. Export as PNG at appropriate sizes
3. Place in density-specific drawable folders

## Examples

The example app includes a sample `ic_notification.xml` in:
```
example/android/app/src/main/res/drawable/ic_notification.xml
```

This is a simple music note icon suitable for media playback notifications.

## Best Practices

1. **Keep it simple**: Use basic shapes that are recognizable at small sizes
2. **Use vectors**: Vector drawables scale perfectly and use less space
3. **Test on device**: Always test on a real device to see how it looks
4. **Follow Material Design**: Check [Material Design guidelines](https://material.io/design/platform-guidance/android-notifications.html#anatomy-of-a-notification) for notification icons
5. **Match your app**: Use an icon that represents your app's media functionality

## Additional Resources

- [Android Notification Icon Guidelines](https://developer.android.com/training/notify-user/build-notification#notification-icon)
- [Material Design - Notification Icons](https://material.io/design/iconography/product-icons.html#design-principles)
- [Android Asset Studio](http://romannurik.github.io/AndroidAssetStudio/)
