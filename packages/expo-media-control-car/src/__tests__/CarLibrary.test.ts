type Listener = (event: any) => void;

jest.mock("expo", () => {
  const listeners: Record<string, Listener[]> = {};
  const nativeModule = {
    listeners,
    setLibrary: jest.fn(async () => {}),
    setHandlers: jest.fn(async () => {}),
    resolveChildren: jest.fn(async () => {}),
    resolveSearch: jest.fn(async () => {}),
    notifyChildrenChanged: jest.fn(async () => {}),
    flushPendingPlayRequest: jest.fn(async () => {}),
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
import { CarLibrary, CarLibraryValidationError } from "../index";

const mockNative = requireNativeModule("ExpoMediaControlCar") as any;

function emit(eventName: string, event: unknown) {
  (mockNative.listeners[eventName] ?? []).forEach((listener: Listener) =>
    listener(event),
  );
}

function listenerCount(eventName: string): number {
  return (mockNative.listeners[eventName] ?? []).length;
}

async function flushPromises() {
  for (let i = 0; i < 5; i++) {
    await Promise.resolve();
  }
}

beforeEach(async () => {
  await CarLibrary.setChildrenLoader(null);
  await CarLibrary.setSearchHandler(null);
  jest.clearAllMocks();
  jest.spyOn(console, "warn").mockImplementation(() => {});
});

afterEach(() => {
  jest.restoreAllMocks();
});

describe("setLibrary", () => {
  it("converts the library to the native format with defaults", async () => {
    await CarLibrary.setLibrary({
      style: "grid",
      tabs: [
        {
          id: "recent",
          title: "Recent",
          children: [
            {
              id: "ep-42",
              title: "Episode 42",
              subtitle: "My Podcast",
              artwork: { uri: "https://example.com/42.jpg" },
              duration: 120,
            },
          ],
        },
        { id: "shows", title: "Shows", browsable: true, style: "list" },
        {
          id: "radio",
          title: "Radio",
          artwork: "https://example.com/radio.png",
          explicit: true,
        },
      ],
    });

    expect(mockNative.setLibrary).toHaveBeenCalledWith({
      style: "grid",
      tabs: [
        {
          id: "recent",
          title: "Recent",
          browsable: true,
          playable: false,
          children: [
            {
              id: "ep-42",
              title: "Episode 42",
              subtitle: "My Podcast",
              artworkUri: "https://example.com/42.jpg",
              browsable: false,
              playable: true,
              duration: 120,
            },
          ],
        },
        {
          id: "shows",
          title: "Shows",
          browsable: true,
          playable: false,
          style: "list",
        },
        {
          id: "radio",
          title: "Radio",
          browsable: false,
          playable: true,
          artworkUri: "https://example.com/radio.png",
          explicit: true,
        },
      ],
    });
  });

  it("keeps items that are both browsable and playable", async () => {
    await CarLibrary.setLibrary({
      tabs: [{ id: "album", title: "Album", playable: true, children: [] }],
    });
    expect(mockNative.setLibrary.mock.calls[0][0].tabs[0]).toMatchObject({
      browsable: true,
      playable: true,
      children: [],
    });
  });

  it.each([
    ["a missing tabs array", {}],
    ["an item without id", { tabs: [{ title: "No id" }] }],
    ["an item without title", { tabs: [{ id: "a" }] }],
    [
      "duplicate ids",
      {
        tabs: [
          { id: "a", title: "A" },
          { id: "a", title: "B" },
        ],
      },
    ],
    [
      "nested duplicate ids",
      { tabs: [{ id: "a", title: "A", children: [{ id: "a", title: "B" }] }] },
    ],
    [
      "children on a non-browsable item",
      { tabs: [{ id: "a", title: "A", browsable: false, children: [] }] },
    ],
    [
      "an item that is neither browsable nor playable",
      { tabs: [{ id: "a", title: "A", playable: false }] },
    ],
    ["an invalid style", { tabs: [{ id: "a", title: "A", style: "cards" }] }],
    ["a negative duration", { tabs: [{ id: "a", title: "A", duration: -1 }] }],
    [
      "invalid artwork",
      { tabs: [{ id: "a", title: "A", artwork: { url: "x" } }] },
    ],
  ])("rejects %s", async (_, library) => {
    await expect(CarLibrary.setLibrary(library as any)).rejects.toBeInstanceOf(
      CarLibraryValidationError,
    );
    expect(mockNative.setLibrary).not.toHaveBeenCalled();
  });
});

describe("children loader", () => {
  it("tells the native side whether a loader is set and subscribes once", async () => {
    await CarLibrary.setChildrenLoader(async () => []);
    await CarLibrary.setChildrenLoader(async () => []);
    expect(mockNative.setHandlers).toHaveBeenLastCalledWith({
      childrenLoader: true,
      search: false,
    });
    expect(listenerCount("onLoadChildren")).toBe(1);

    await CarLibrary.setChildrenLoader(null);
    expect(mockNative.setHandlers).toHaveBeenLastCalledWith({
      childrenLoader: false,
      search: false,
    });
    expect(listenerCount("onLoadChildren")).toBe(0);
  });

  it("answers children requests with the loader's items", async () => {
    const loader = jest.fn(async (parentId: string) => [
      { id: `${parentId}-1`, title: "One" },
    ]);
    await CarLibrary.setChildrenLoader(loader);

    emit("onLoadChildren", { requestId: "r1", parentId: "shows" });
    await flushPromises();

    expect(loader).toHaveBeenCalledWith("shows");
    expect(mockNative.resolveChildren).toHaveBeenCalledWith("r1", [
      {
        id: "shows-1",
        title: "One",
        browsable: false,
        playable: true,
      },
    ]);
  });

  it("answers with null when the loader throws or returns invalid items", async () => {
    await CarLibrary.setChildrenLoader(async () => {
      throw new Error("offline");
    });
    emit("onLoadChildren", { requestId: "r1", parentId: "shows" });
    await flushPromises();
    expect(mockNative.resolveChildren).toHaveBeenLastCalledWith("r1", null);

    await CarLibrary.setChildrenLoader(async () => [{ id: "", title: "Bad" }]);
    emit("onLoadChildren", { requestId: "r2", parentId: "shows" });
    await flushPromises();
    expect(mockNative.resolveChildren).toHaveBeenLastCalledWith("r2", null);
  });
});

describe("search handler", () => {
  it("answers search requests", async () => {
    const handler = jest.fn(() => [{ id: "match", title: "Match" }]);
    await CarLibrary.setSearchHandler(handler);
    expect(mockNative.setHandlers).toHaveBeenLastCalledWith({
      childrenLoader: false,
      search: true,
    });

    emit("onSearch", { requestId: "s1", query: "jazz" });
    await flushPromises();

    expect(handler).toHaveBeenCalledWith("jazz");
    expect(mockNative.resolveSearch).toHaveBeenCalledWith("s1", [
      {
        id: "match",
        title: "Match",
        browsable: false,
        playable: true,
      },
    ]);
  });

  it("removes the subscription when the handler is cleared", async () => {
    await CarLibrary.setSearchHandler(async () => []);
    expect(listenerCount("onSearch")).toBe(1);
    await CarLibrary.setSearchHandler(null);
    expect(listenerCount("onSearch")).toBe(0);
  });
});

describe("play requests", () => {
  it("delivers play requests and flushes a pending one", () => {
    const listener = jest.fn();
    const subscription = CarLibrary.addPlayRequestListener(listener);
    expect(mockNative.flushPendingPlayRequest).toHaveBeenCalledTimes(1);

    emit("onPlayRequest", { itemId: "ep-42", playWhenReady: true });
    expect(listener).toHaveBeenCalledWith({
      itemId: "ep-42",
      playWhenReady: true,
    });

    subscription.remove();
    emit("onPlayRequest", { query: "jazz", playWhenReady: true });
    expect(listener).toHaveBeenCalledTimes(1);
  });
});

describe("notifyChildrenChanged", () => {
  it("validates the parent id", async () => {
    await expect(CarLibrary.notifyChildrenChanged("")).rejects.toBeInstanceOf(
      CarLibraryValidationError,
    );
    await CarLibrary.notifyChildrenChanged("shows");
    expect(mockNative.notifyChildrenChanged).toHaveBeenCalledWith("shows");
  });
});
