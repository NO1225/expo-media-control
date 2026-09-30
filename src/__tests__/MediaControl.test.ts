type Listener = (event: unknown) => void;

jest.mock("expo", () => {
  const listeners: Record<string, Listener[]> = {};
  const nativeModule = {
    listeners,
    enableMediaControls: jest.fn(async () => {}),
    disableMediaControls: jest.fn(async () => {}),
    updateMetadata: jest.fn(async () => {}),
    updatePlaybackState: jest.fn(async () => {}),
    resetControls: jest.fn(async () => {}),
    isEnabled: jest.fn(async () => true),
    getCurrentMetadata: jest.fn(async () => null),
    getCurrentState: jest.fn(async () => 0),
    addListener: jest.fn((eventName: string, listener: Listener) => {
      (listeners[eventName] ??= []).push(listener);
      return {
        remove: () => {
          listeners[eventName] = (listeners[eventName] ?? []).filter(
            (l) => l !== listener,
          );
        },
      };
    }),
  };
  return {
    NativeModule: class {},
    requireNativeModule: () => nativeModule,
  };
});

// eslint-disable-next-line import/first
import { requireNativeModule } from "expo";

// eslint-disable-next-line import/first
import {
  Command,
  MediaControl,
  PlaybackState,
  ValidationError,
} from "../index";

const mockNativeModule = requireNativeModule("ExpoMediaControl") as any;

function emit(eventName: string, event: unknown) {
  (mockNativeModule.listeners[eventName] ?? []).forEach((listener: Listener) =>
    listener(event),
  );
}

describe("MediaControl", () => {
  afterEach(async () => {
    await MediaControl.disableMediaControls();
    await MediaControl.removeAllListeners();
    jest.clearAllMocks();
  });

  it("dispatches each remote command once even after re-enabling", async () => {
    const onEvent = jest.fn();
    MediaControl.addListener(onEvent);

    await MediaControl.enableMediaControls({
      capabilities: [Command.PLAY, Command.PAUSE, Command.SKIP_FORWARD],
    });
    await MediaControl.enableMediaControls({
      capabilities: [Command.PLAY, Command.PAUSE, Command.NEXT_TRACK],
    });

    emit("mediaControlEvent", { command: Command.PLAY, timestamp: 0 });

    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(mockNativeModule.enableMediaControls).toHaveBeenCalledTimes(2);
  });

  it("stops dispatching after disabling", async () => {
    const onEvent = jest.fn();
    MediaControl.addListener(onEvent);

    await MediaControl.enableMediaControls();
    await MediaControl.disableMediaControls();

    emit("mediaControlEvent", { command: Command.PLAY, timestamp: 0 });

    expect(onEvent).not.toHaveBeenCalled();
  });

  it("forwards volume change events", async () => {
    const onVolume = jest.fn();
    MediaControl.addVolumeChangeListener(onVolume);
    await MediaControl.enableMediaControls();

    emit("volumeChange", { volume: 0.5, userInitiated: true });

    expect(onVolume).toHaveBeenCalledWith({ volume: 0.5, userInitiated: true });
  });

  it("rejects more than 3 compact capabilities", async () => {
    await expect(
      MediaControl.enableMediaControls({
        compactCapabilities: [
          Command.PLAY,
          Command.PAUSE,
          Command.NEXT_TRACK,
          Command.PREVIOUS_TRACK,
        ],
      }),
    ).rejects.toBeInstanceOf(ValidationError);
    expect(mockNativeModule.enableMediaControls).not.toHaveBeenCalled();
  });

  it("strips undefined metadata fields before calling native", async () => {
    await MediaControl.updateMetadata({ title: "Song", artist: undefined });

    expect(mockNativeModule.updateMetadata).toHaveBeenCalledWith({
      title: "Song",
    });
  });

  it("validates playback state and rate", async () => {
    await expect(
      MediaControl.updatePlaybackState(99 as PlaybackState),
    ).rejects.toBeInstanceOf(ValidationError);
    await expect(
      MediaControl.updatePlaybackState(PlaybackState.PLAYING, 0, 11),
    ).rejects.toBeInstanceOf(ValidationError);
    await MediaControl.updatePlaybackState(PlaybackState.PLAYING, 10, 1.5);
    expect(mockNativeModule.updatePlaybackState).toHaveBeenCalledWith(
      PlaybackState.PLAYING,
      10,
      1.5,
    );
  });
});
