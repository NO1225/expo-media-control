import { NativeModule, requireNativeModule } from 'expo';

import { ExpoMediaControlModuleEvents } from './ExpoMediaControl.types';

declare class ExpoMediaControlModule extends NativeModule<ExpoMediaControlModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoMediaControlModule>('ExpoMediaControl');
