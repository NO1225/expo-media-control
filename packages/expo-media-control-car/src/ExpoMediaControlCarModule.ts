import { NativeModule, requireNativeModule } from "expo";

import type {
  CarPlayRequest,
  NativeCarLibrary,
  NativeCarMediaItem,
} from "./CarLibrary.types";

export interface LoadChildrenEvent {
  requestId: string;
  parentId: string;
}

export interface SearchEvent {
  requestId: string;
  query: string;
}

type ExpoMediaControlCarEvents = {
  onLoadChildren(event: LoadChildrenEvent): void;
  onSearch(event: SearchEvent): void;
  onPlayRequest(event: CarPlayRequest): void;
};

declare class ExpoMediaControlCarNativeModule extends NativeModule<ExpoMediaControlCarEvents> {
  setLibrary(library: NativeCarLibrary): Promise<void>;
  setHandlers(handlers: {
    childrenLoader: boolean;
    search: boolean;
  }): Promise<void>;
  resolveChildren(
    requestId: string,
    items: NativeCarMediaItem[] | null,
  ): Promise<void>;
  resolveSearch(
    requestId: string,
    items: NativeCarMediaItem[] | null,
  ): Promise<void>;
  notifyChildrenChanged(parentId: string): Promise<void>;
  /** Re-sends a play request that arrived before any listener was registered */
  flushPendingPlayRequest(): Promise<void>;
}

export default requireNativeModule<ExpoMediaControlCarNativeModule>(
  "ExpoMediaControlCar",
);
