// Adds the expo-media-control-car plugin unless EXPO_PUBLIC_MEDIA_CONTROL_CAR=0, so the example
// can be built with and without the car package (see CarSetup.ts).
const carEnabled = process.env.EXPO_PUBLIC_MEDIA_CONTROL_CAR !== '0';

module.exports = ({ config }) => ({
  ...config,
  plugins: [
    ...(config.plugins ?? []),
    [
      'expo-media-control-car',
      carEnabled
        ? {
            // The example has no CarPlay entitlement: CarPlay works in the iOS Simulator only.
            // Apps that were granted it set carPlayEntitlement: true.
            carPlayEntitlement: false,
            siri: false,
          }
        : { carPlay: false, androidAuto: false },
    ],
  ],
});
