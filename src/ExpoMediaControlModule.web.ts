import { registerWebModule, NativeModule } from 'expo';

import { ExpoMediaControlModuleEvents } from './ExpoMediaControl.types';

class ExpoMediaControlModule extends NativeModule<ExpoMediaControlModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(ExpoMediaControlModule, 'ExpoMediaControlModule');
