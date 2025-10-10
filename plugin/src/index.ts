import {
  withInfoPlist,
  withAndroidManifest,
  withDangerousMod,
  AndroidConfig,
  ConfigPlugin,
} from 'expo/config-plugins';
import * as path from 'path';
import * as fs from 'fs';

/**
 * Configuration options for the Expo Media Control plugin
 * 
 * Note: This plugin config is only for build-time configuration.
 * Runtime media control options should be passed to enableMediaControls().
 */
interface MediaControlOptions {
  /** Enable background audio modes (iOS) */
  enableBackgroundAudio?: boolean;
  /** Audio session category for iOS */
  audioSessionCategory?: string;
  /** 
   * Custom notification icon (Android)
   * 
   * IMPORTANT: Notification icon specification for Android:
   * 
   * The notification icon MUST be monochrome (white/transparent only).
   * 
   * Option 1: Use a custom icon (Recommended):
   * ----------------------------------------
   * 1. Create a monochrome PNG (white silhouette on transparent background)
   * 2. Place in your project: "./assets/notification-icon.png"
   * 3. Reference in app.json:
   *    ```json
   *    {
   *      "plugins": [
   *        ["expo-media-control", {
   *          "notificationIcon": "./assets/notification-icon.png"
   *        }]
   *      ]
   *    }
   *    ```
   * 4. The plugin will automatically copy it to Android drawable during prebuild
   * 
   * Option 2: Use the default icon:
   * -------------------------------
   * - Don't specify notificationIcon
   * - A default music note icon will be created automatically
   * 
   * Option 3: Reference existing Android resource:
   * ----------------------------------------------
   * - For bare workflow only
   * - Reference by name: "ic_notification"
   * - Icon must already exist in android/app/src/main/res/drawable/
   * 
   * Icon Requirements:
   * - MUST be monochrome (white on transparent only)
   * - PNG format recommended (24x24dp to 96x96dp)
   * - Colored icons will render as white squares
   * - Simple, recognizable design
   * 
   * If not specified or file not found, the module will:
   * - Create a default music note icon automatically
   * - Or use standard Android icon names (ic_notification, etc.)
   * - Or fall back to system default media icon
   */
  notificationIcon?: string;
}

/**
 * iOS Configuration
 * Adds required background modes and audio session configuration
 */
const withIOSMediaControl: ConfigPlugin<MediaControlOptions> = (config, options = {}) => {
  config = withInfoPlist(config, (config) => {
    const infoPlist = config.modResults;

    // Add background modes for audio playback
    if (options.enableBackgroundAudio !== false) {
      if (!infoPlist.UIBackgroundModes) {
        infoPlist.UIBackgroundModes = [];
      }
      
      const backgroundModes = infoPlist.UIBackgroundModes as string[];
      
      // Add audio background mode if not present
      if (!backgroundModes.includes('audio')) {
        backgroundModes.push('audio');
      }
    }

    // Add audio session category configuration
    const audioSessionCategory = options.audioSessionCategory || 'playback';
    infoPlist['AVAudioSessionCategory'] = audioSessionCategory;

    // Add required audio session options
    infoPlist['AVAudioSessionCategoryOptions'] = [
      'AVAudioSessionCategoryOptionAllowBluetooth',
      'AVAudioSessionCategoryOptionAllowBluetoothA2DP'
    ];

    return config;
  });

  return config;
};

/**
 * Android Configuration  
 * Adds required permissions and service configuration
 */
const withAndroidMediaControl: ConfigPlugin<MediaControlOptions> = (config, options = {}) => {
  config = withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;

    // Add required permissions
    const permissions = [
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK', // Required for Android 14+ (API 34+)
      'android.permission.WAKE_LOCK',
      'android.permission.ACCESS_NETWORK_STATE',
    ];

    permissions.forEach(permission => {
      AndroidConfig.Permissions.addPermission(androidManifest, permission);
    });

    // Get the main application
    const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(androidManifest);

    // Add custom notification icon if specified
    if (options.notificationIcon) {
      // Extract filename without path and extension for Android resource naming
      const iconName = options.notificationIcon
        .split('/').pop() // Remove path
        ?.split('.')[0] // Remove extension
        || options.notificationIcon; // Use original if processing fails
      
      AndroidConfig.Manifest.addMetaDataItemToMainApplication(
        mainApplication,
        'expo.modules.mediacontrol.NOTIFICATION_ICON',
        iconName
      );
      
      // Log guidance for notification icon setup
      console.log(`
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📱 NOTIFICATION ICON CONFIGURED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Icon name: "${iconName}"

⚠️  IMPORTANT: You must manually add the icon resource!

For Bare Workflow:
1. Create a monochrome icon (white on transparent)
2. Place in: android/app/src/main/res/drawable/${iconName}.png
3. Or use vector: android/app/src/main/res/drawable/${iconName}.xml

For Managed Workflow:
1. Create drawable resource using EAS Build or prebuild
2. Place in android/app/src/main/res/drawable/

✅ Icon requirements:
- MUST be monochrome (white/transparent only)
- Colored PNGs will render as solid white shapes
- Recommended: Use vector drawable XML

📚 See Android documentation for notification icon best practices

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      `);
    }

    return config;
  });

  // Add default notification icon via withDangerousMod
  config = withDangerousMod(config, [
    'android',
    async (config) => {
      console.log('🔧 [expo-media-control] Running withDangerousMod for Android...');
      
      const drawablePath = path.join(
        config.modRequest.platformProjectRoot,
        'app/src/main/res/drawable'
      );

      console.log('🔧 [expo-media-control] Drawable path:', drawablePath);

      // Ensure drawable directory exists
      if (!fs.existsSync(drawablePath)) {
        fs.mkdirSync(drawablePath, { recursive: true });
        console.log('🔧 [expo-media-control] Created drawable directory');
      }

      // Check if user provided a custom icon path
      if (options.notificationIcon) {
        const iconName = options.notificationIcon
          .split('/').pop()
          ?.split('.')[0] || 'ic_notification';
        
        // Check if it's a path to an actual file (contains / or .)
        if (options.notificationIcon.includes('/') || options.notificationIcon.includes('.')) {
          const sourcePath = path.join(
            config.modRequest.projectRoot,
            options.notificationIcon
          );
          
          if (fs.existsSync(sourcePath)) {
            const extension = path.extname(options.notificationIcon);
            const targetPath = path.join(drawablePath, `${iconName}${extension}`);
            
            // Copy the custom icon
            fs.copyFileSync(sourcePath, targetPath);
            console.log(`✅ [expo-media-control] Copied custom notification icon: ${iconName}${extension}`);
            return config;
          } else {
            console.warn(`⚠️  [expo-media-control] Custom icon not found at: ${sourcePath}`);
            console.warn('   Falling back to default icon...');
          }
        }
      }

      // Create default ic_notification.xml if it doesn't exist
      const iconPath = path.join(drawablePath, 'ic_notification.xml');
      console.log('🔧 [expo-media-control] Checking for icon at:', iconPath);
      
      if (!fs.existsSync(iconPath)) {
        const defaultIconXml = `<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@android:color/white">
    <!-- Default music note icon for media notifications -->
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"/>
</vector>`;
        
        fs.writeFileSync(iconPath, defaultIconXml, 'utf-8');
        console.log('✅ [expo-media-control] Created default notification icon: ic_notification.xml');
      } else {
        console.log('ℹ️  [expo-media-control] Notification icon already exists, skipping...');
      }

      return config;
    },
  ]);

  return config;
};

/**
 * Main plugin function
 * Combines iOS and Android configurations for comprehensive media control support
 */
const withExpoMediaControl: ConfigPlugin<MediaControlOptions> = (config, options = {}) => {
  // Apply iOS configuration
  config = withIOSMediaControl(config, options);
  
  // Apply Android configuration  
  config = withAndroidMediaControl(config, options);

  return config;
};

export default withExpoMediaControl;
