// Reexport the native module. On web, it will be resolved to ExpoMediaControlModule.web.ts
// and on native platforms to ExpoMediaControlModule.ts
export { default } from './ExpoMediaControlModule';
export { default as ExpoMediaControlView } from './ExpoMediaControlView';
export * from  './ExpoMediaControl.types';
