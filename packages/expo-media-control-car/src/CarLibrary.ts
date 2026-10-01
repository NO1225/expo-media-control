import type { EventSubscription } from "expo-modules-core";

import type {
  CarChildrenLoader,
  CarLibraryContent,
  CarListStyle,
  CarMediaItem,
  CarPlayRequestListener,
  CarSearchHandler,
  NativeCarLibrary,
  NativeCarMediaItem,
} from "./CarLibrary.types";
import NativeModule from "./ExpoMediaControlCarModule";

/** Thrown when the library passed to `setLibrary()` or returned by a handler is invalid */
export class CarLibraryValidationError extends Error {
  constructor(
    message: string,
    public readonly itemId?: string,
  ) {
    super(message);
    this.name = "CarLibraryValidationError";
  }
}

const LIST_STYLES: readonly CarListStyle[] = ["list", "grid"];

function toNativeItem(
  item: CarMediaItem,
  path: string,
  seenIds: Set<string> | null,
): NativeCarMediaItem {
  if (!item || typeof item !== "object") {
    throw new CarLibraryValidationError(`${path} must be an object`);
  }
  if (typeof item.id !== "string" || item.id.length === 0) {
    throw new CarLibraryValidationError(
      `${path}.id must be a non-empty string`,
    );
  }
  const at = `${path} ("${item.id}")`;
  if (seenIds) {
    if (seenIds.has(item.id)) {
      throw new CarLibraryValidationError(
        `Duplicate item id "${item.id}"`,
        item.id,
      );
    }
    seenIds.add(item.id);
  }
  if (typeof item.title !== "string") {
    throw new CarLibraryValidationError(
      `${at}.title must be a string`,
      item.id,
    );
  }
  if (item.subtitle !== undefined && typeof item.subtitle !== "string") {
    throw new CarLibraryValidationError(
      `${at}.subtitle must be a string`,
      item.id,
    );
  }
  if (item.children !== undefined && !Array.isArray(item.children)) {
    throw new CarLibraryValidationError(
      `${at}.children must be an array`,
      item.id,
    );
  }
  if (item.children !== undefined && item.browsable === false) {
    throw new CarLibraryValidationError(
      `${at} has children but browsable is false`,
      item.id,
    );
  }
  if (item.style !== undefined && !LIST_STYLES.includes(item.style)) {
    throw new CarLibraryValidationError(
      `${at}.style must be "list" or "grid"`,
      item.id,
    );
  }
  if (
    item.duration !== undefined &&
    (typeof item.duration !== "number" ||
      !isFinite(item.duration) ||
      item.duration < 0)
  ) {
    throw new CarLibraryValidationError(
      `${at}.duration must be a non-negative number of seconds`,
      item.id,
    );
  }

  let artworkUri: string | undefined;
  if (typeof item.artwork === "string") {
    artworkUri = item.artwork;
  } else if (item.artwork !== undefined) {
    if (!item.artwork || typeof item.artwork.uri !== "string") {
      throw new CarLibraryValidationError(
        `${at}.artwork must be a URI or { uri }`,
        item.id,
      );
    }
    artworkUri = item.artwork.uri;
  }

  const browsable = item.browsable ?? item.children !== undefined;
  const playable = item.playable ?? !browsable;
  if (!browsable && !playable) {
    throw new CarLibraryValidationError(
      `${at} must be browsable, playable or both`,
      item.id,
    );
  }

  const nativeItem: NativeCarMediaItem = {
    id: item.id,
    title: item.title,
    playable,
    browsable,
  };
  // Left out rather than null: Expo's Map<String, Any?> conversion rejects null values
  if (browsable && item.children) {
    nativeItem.children = item.children.map((child, index) =>
      toNativeItem(child, `${path}.children[${index}]`, seenIds),
    );
  }
  if (item.subtitle) nativeItem.subtitle = item.subtitle;
  if (artworkUri) nativeItem.artworkUri = artworkUri;
  if (item.style) nativeItem.style = item.style;
  if (item.duration !== undefined) nativeItem.duration = item.duration;
  if (item.explicit) nativeItem.explicit = true;
  return nativeItem;
}

/** Validates a library and converts it to the format the native side expects */
export function toNativeLibrary(library: CarLibraryContent): NativeCarLibrary {
  if (!library || typeof library !== "object" || !Array.isArray(library.tabs)) {
    throw new CarLibraryValidationError(
      "The library must be an object with a tabs array",
    );
  }
  if (library.style !== undefined && !LIST_STYLES.includes(library.style)) {
    throw new CarLibraryValidationError('style must be "list" or "grid"');
  }
  const seenIds = new Set<string>();
  const nativeLibrary: NativeCarLibrary = {
    tabs: library.tabs.map((tab, index) =>
      toNativeItem(tab, `tabs[${index}]`, seenIds),
    ),
  };
  if (library.style) nativeLibrary.style = library.style;
  return nativeLibrary;
}

/** Validates a list of items returned by the children loader or the search handler */
export function toNativeItems(
  items: CarMediaItem[],
  source: string,
): NativeCarMediaItem[] {
  if (!Array.isArray(items)) {
    throw new CarLibraryValidationError(
      `${source} must return an array of items`,
    );
  }
  const seenIds = new Set<string>();
  return items.map((item, index) =>
    toNativeItem(item, `${source}[${index}]`, seenIds),
  );
}

// =============================================
// HANDLERS
// =============================================

let childrenLoader: CarChildrenLoader | null = null;
let searchHandler: CarSearchHandler | null = null;
let loadChildrenSubscription: EventSubscription | null = null;
let searchSubscription: EventSubscription | null = null;

function reportError(context: string, error: unknown) {
  console.warn(`[expo-media-control-car] ${context}:`, error);
}

async function handleLoadChildren(requestId: string, parentId: string) {
  let items: NativeCarMediaItem[] | null = null;
  try {
    if (childrenLoader) {
      items = toNativeItems(
        await childrenLoader(parentId),
        "The children loader",
      );
    }
  } catch (error) {
    reportError(`Loading the children of "${parentId}" failed`, error);
  }
  await NativeModule.resolveChildren(requestId, items);
}

async function handleSearch(requestId: string, query: string) {
  let items: NativeCarMediaItem[] | null = null;
  try {
    if (searchHandler) {
      items = toNativeItems(await searchHandler(query), "The search handler");
    }
  } catch (error) {
    reportError(`Searching for "${query}" failed`, error);
  }
  await NativeModule.resolveSearch(requestId, items);
}

function syncHandlers() {
  if (childrenLoader && !loadChildrenSubscription) {
    loadChildrenSubscription = NativeModule.addListener(
      "onLoadChildren",
      (event) => {
        handleLoadChildren(event.requestId, event.parentId).catch((error) =>
          reportError("Answering a children request failed", error),
        );
      },
    );
  } else if (!childrenLoader && loadChildrenSubscription) {
    loadChildrenSubscription.remove();
    loadChildrenSubscription = null;
  }

  if (searchHandler && !searchSubscription) {
    searchSubscription = NativeModule.addListener("onSearch", (event) => {
      handleSearch(event.requestId, event.query).catch((error) =>
        reportError("Answering a search request failed", error),
      );
    });
  } else if (!searchHandler && searchSubscription) {
    searchSubscription.remove();
    searchSubscription = null;
  }

  return NativeModule.setHandlers({
    childrenLoader: childrenLoader !== null,
    search: searchHandler !== null,
  });
}

// =============================================
// PUBLIC API
// =============================================

/**
 * Sets the library shown in Android Auto and CarPlay. The native side keeps a copy, so the car
 * can browse it even when the JavaScript runtime isn't running (Android) or is still starting.
 *
 * Call it again whenever the library changes; the car UI refreshes the affected screens.
 */
async function setLibrary(library: CarLibraryContent): Promise<void> {
  await NativeModule.setLibrary(toNativeLibrary(library));
}

/**
 * Loads the children of browsable items that were given without `children`. Called when the
 * driver opens such a folder. Results are cached natively: the cached list is shown right away
 * the next time and refreshed in the background.
 *
 * Pass `null` to remove the loader.
 */
async function setChildrenLoader(
  loader: CarChildrenLoader | null,
): Promise<void> {
  childrenLoader = loader;
  await syncHandlers();
}

/**
 * Answers searches typed in the car and voice requests ("Hey Google / Siri, play X"). Return
 * the matching items; the driver picks one, which arrives as a play request with its `itemId`.
 *
 * Pass `null` to remove the handler.
 */
async function setSearchHandler(
  handler: CarSearchHandler | null,
): Promise<void> {
  searchHandler = handler;
  await syncHandlers();
}

/**
 * Called when the car asks to play something. Start it with your own player and report it
 * with `MediaControl.updateMetadata()` / `updatePlaybackState()` as usual.
 *
 * A request that arrives before any listener is registered (for example while the app is
 * starting) is delivered to the first listener.
 */
function addPlayRequestListener(
  listener: CarPlayRequestListener,
): EventSubscription {
  const subscription = NativeModule.addListener("onPlayRequest", listener);
  NativeModule.flushPendingPlayRequest().catch((error) =>
    reportError("Delivering a pending play request failed", error),
  );
  return subscription;
}

/**
 * Tells the car that the children of `parentId` changed, for folders loaded with the children
 * loader. Use `setLibrary()` for folders that are part of the library.
 */
async function notifyChildrenChanged(parentId: string): Promise<void> {
  if (typeof parentId !== "string" || parentId.length === 0) {
    throw new CarLibraryValidationError("parentId must be a non-empty string");
  }
  await NativeModule.notifyChildrenChanged(parentId);
}

export const CarLibrary = {
  setLibrary,
  setChildrenLoader,
  setSearchHandler,
  addPlayRequestListener,
  notifyChildrenChanged,
};
