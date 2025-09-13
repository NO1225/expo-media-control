import * as React from 'react';

import { ExpoMediaControlViewProps } from './ExpoMediaControl.types';

export default function ExpoMediaControlView(props: ExpoMediaControlViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
