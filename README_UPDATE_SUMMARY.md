# README Update Summary

## Changes Made

### 1. Added Development Status Warning
Added a prominent warning at the top of the README:
```markdown
> **⚠️ UNDER ACTIVE DEVELOPMENT**  
> This module is currently under rapid development. While we strive to maintain stability, breaking changes may occur between versions. Please:
> - Pin your version in `package.json`
> - Check the [CHANGELOG](./CHANGELOG.md) before updating
> - Report issues on [GitHub](https://github.com/NO1225/expo-media-control/issues)
> - Join discussions for upcoming features and changes
```

### 2. Updated Installation Section
Added tip about version pinning:
```markdown
> **💡 Tip**: Pin your version in `package.json` during active development to avoid unexpected breaking changes. For example: `"expo-media-control": "~0.1.0"`
```

### 3. Clarified Notification Icon Configuration

#### In API Reference Section:
- Updated `notification.icon` comment to clarify it's for bare workflow only
- Added note that managed workflow should use plugin config
- Made it clear this is an Android-only option

#### In Configuration Options Section:
Expanded and clarified the notification icon notes:
- **Managed workflow**: Use file path in plugin config (e.g., `"./assets/notification-icon.png"`)
- **Bare workflow**: Reference drawable resource by name
- **Requirements**: Must be monochrome (white on transparent)
- **Default behavior**: Automatic music note icon if not specified
- Links to detailed custom icon guide

### 4. Improved Clarity

All notification icon configuration is now clearly distinguished:
- **Build-time** (plugin config): For managed workflow
- **Runtime** (enableMediaControls): For bare workflow only
- Clear separation of concerns

## User Benefits

1. **Clear Expectations**: Users know this is under active development
2. **Version Safety**: Encouraged to pin versions to avoid breaking changes
3. **Better Documentation**: Clear distinction between build-time and runtime configuration
4. **Comprehensive Guide**: Links to detailed custom icon guide for advanced users

## Files Updated

- `/Users/jumaalsafartee/Desktop/Git/expo-media-control/README.md`

## Next Steps for Users

1. Read the warning about active development
2. Pin their version in package.json
3. Follow the custom icon guide if they want custom notification icons
4. Check changelog before updating versions
