import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoMediaControlViewProps } from './ExpoMediaControl.types';

const NativeView: React.ComponentType<ExpoMediaControlViewProps> =
  requireNativeView('ExpoMediaControl');

export default function ExpoMediaControlView(props: ExpoMediaControlViewProps) {
  return <NativeView {...props} />;
}
