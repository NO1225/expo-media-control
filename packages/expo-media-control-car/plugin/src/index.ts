import {
  AndroidConfig,
  ConfigPlugin,
  withAndroidManifest,
  withEntitlementsPlist,
  withInfoPlist,
} from "expo/config-plugins";

export interface MediaControlCarPluginOptions {
  /**
   * Add the CarPlay scene to the iOS app (default `true`). This moves the app to the scene life
   * cycle; the package provides the phone scene delegate as well.
   */
  carPlay?: boolean;
  /**
   * Add the `com.apple.developer.carplay-audio` entitlement (default `false`). Only enable it
   * once Apple has granted the entitlement for your app ID, otherwise signing fails on devices.
   * The iOS Simulator's CarPlay window works without it.
   */
  carPlayEntitlement?: boolean;
  /**
   * Handle "Hey Siri, play X" (`INPlayMediaIntent`) in the app (default `false`). Adds the Siri
   * entitlement, so the Siri capability must be enabled for your app ID.
   */
  siri?: boolean;
  /** Media categories for Siri requests (default `["INMediaCategoryGeneral"]`) */
  siriMediaCategories?: string[];
  /**
   * Declare the Android app as an Android Auto media app (default `true`). `false` also keeps
   * the library away from other media browsers, so the app behaves as without this package.
   */
  androidAuto?: boolean;
}

const PHONE_SCENE_DELEGATE = "ExpoMediaControlCarPhoneSceneDelegate";
const CARPLAY_SCENE_DELEGATE = "ExpoMediaControlCarPlaySceneDelegate";
const WINDOW_ROLE = "UIWindowSceneSessionRoleApplication";
const CARPLAY_ROLE = "CPTemplateApplicationSceneSessionRoleApplication";

type SceneConfiguration = Record<string, string>;
type SceneManifest = {
  UIApplicationSupportsMultipleScenes?: boolean;
  UISceneConfigurations?: Record<string, SceneConfiguration[]>;
};

/** Adds the CarPlay scene and, unless the app already has one, the phone scene */
export function addCarPlayScenes(
  manifest: SceneManifest | undefined,
): SceneManifest {
  const result: SceneManifest = { ...manifest };
  const configurations = { ...result.UISceneConfigurations };

  // CarPlay needs multiple scenes: the phone and the car screen are separate scenes
  result.UIApplicationSupportsMultipleScenes = true;

  if (!configurations[WINDOW_ROLE]?.length) {
    configurations[WINDOW_ROLE] = [
      {
        UISceneConfigurationName: "Phone",
        UISceneDelegateClassName: PHONE_SCENE_DELEGATE,
      },
    ];
  }

  const carPlayConfigurations = (configurations[CARPLAY_ROLE] ?? []).filter(
    (configuration) =>
      configuration.UISceneDelegateClassName !== CARPLAY_SCENE_DELEGATE,
  );
  configurations[CARPLAY_ROLE] = [
    ...carPlayConfigurations,
    {
      UISceneClassName: "CPTemplateApplicationScene",
      UISceneConfigurationName: "CarPlay",
      UISceneDelegateClassName: CARPLAY_SCENE_DELEGATE,
    },
  ];

  result.UISceneConfigurations = configurations;
  return result;
}

const withCarPlay: ConfigPlugin<MediaControlCarPluginOptions> = (
  config,
  options,
) => {
  const {
    carPlay = true,
    carPlayEntitlement = false,
    siri = false,
    siriMediaCategories = ["INMediaCategoryGeneral"],
  } = options;

  config = withInfoPlist(config, (config) => {
    const infoPlist = config.modResults;
    if (carPlay) {
      infoPlist.UIApplicationSceneManifest = addCarPlayScenes(
        infoPlist.UIApplicationSceneManifest as SceneManifest | undefined,
      ) as any;
    }
    if (siri) {
      const intents = new Set<string>(
        (infoPlist.INIntentsSupported as string[]) ?? [],
      );
      intents.add("INPlayMediaIntent");
      infoPlist.INIntentsSupported = [...intents];
      const categories = new Set<string>(
        (infoPlist.INSupportedMediaCategories as string[]) ?? [],
      );
      siriMediaCategories.forEach((category) => categories.add(category));
      infoPlist.INSupportedMediaCategories = [...categories];
    }
    return config;
  });

  if (carPlayEntitlement || siri) {
    config = withEntitlementsPlist(config, (config) => {
      if (carPlayEntitlement) {
        config.modResults["com.apple.developer.carplay-audio"] = true;
      }
      if (siri) {
        config.modResults["com.apple.developer.siri"] = true;
      }
      return config;
    });
  }

  return config;
};

const ANDROID_AUTO_META_DATA = [
  "com.google.android.gms.car.application",
  "expo.modules.mediacontrol.LIBRARY_PROVIDER",
];

/** Removes the package's Android Auto declaration and library provider from the merged manifest */
const withAndroidAutoOptOut: ConfigPlugin = (config) =>
  withAndroidManifest(config, (config) => {
    const manifest = config.modResults;
    manifest.manifest.$["xmlns:tools"] ??= "http://schemas.android.com/tools";
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);
    const metaData = (application["meta-data"] ??= []);
    for (const name of ANDROID_AUTO_META_DATA) {
      if (!metaData.some((item) => item.$["android:name"] === name)) {
        metaData.push({ $: { "android:name": name, "tools:node": "remove" } as any });
      }
    }
    return config;
  });

/**
 * Config plugin for expo-media-control-car.
 *
 * ```json
 * {
 *   "plugins": [
 *     "expo-media-control",
 *     ["expo-media-control-car", { "carPlayEntitlement": true, "siri": true }]
 *   ]
 * }
 * ```
 */
const withMediaControlCar: ConfigPlugin<MediaControlCarPluginOptions | void> = (
  config,
  options,
) => {
  const resolvedOptions = options ?? {};
  config = withCarPlay(config, resolvedOptions);
  if (resolvedOptions.androidAuto === false) {
    config = withAndroidAutoOptOut(config);
  }
  return config;
};

export default withMediaControlCar;
