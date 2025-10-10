import {
  withInfoPlist,
  withAndroidManifest,
  AndroidConfig,
  ConfigPlugin,
} from 'expo/config-plugins';

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
   * Custom notification icon name (Android)
   * 
   * IMPORTANT: Android notification icons have specific requirements:
   * - Must be monochrome (white/transparent only)
   * - PNG icons with colors will appear as white circles
   * - In Expo managed workflow, place icon in your assets folder
   * - Reference by path relative to project root (e.g., "./assets/notification-icon.png")
   * 
   * For Expo managed workflow:
   * 1. Create a white/transparent version of your icon
   * 2. Save as PNG in your assets folder (e.g., "./assets/notification-icon.png")
   * 3. Reference the path in this configuration
   * 4. The plugin will handle copying it to the correct Android location during build
   * 
   * For bare workflow:
   * 1. Place the monochrome icon in android/app/src/main/res/drawable/
   * 2. Reference by filename without extension
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
        ?.split('.')[0]; // Remove extension
      
      AndroidConfig.Manifest.addMetaDataItemToMainApplication(
        mainApplication,
        'expo.modules.mediacontrol.NOTIFICATION_ICON',
        iconName || 'notification_icon'
      );
      
      // Log guidance for notification icon setup
      console.log(`
📱 NOTIFICATION ICON CONFIGURED:
Using "${options.notificationIcon}" as notification icon.

✅ Make sure your icon is:
- Monochrome (white/transparent only)
- Located at: ${options.notificationIcon}
- Will be processed as: ${iconName}

If you see a white circle, your icon needs to be monochrome!
      `);
    }

    return config;
  });

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
