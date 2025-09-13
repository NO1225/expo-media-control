import { NativeModule, requireNativeModule } from 'expo';


declare class ExpoMediaControlModule extends NativeModule {
  getApiKey(): string;

}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoMediaControlModule>('ExpoMediaControl');
