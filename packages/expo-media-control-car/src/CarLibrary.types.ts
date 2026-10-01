/**
 * How a folder's children are displayed. Android Auto supports both; CarPlay always shows lists
 * (a `grid` folder is shown as a list with larger artwork where CarPlay supports it).
 */
export type CarListStyle = "list" | "grid";

/**
 * An item in the car library: a folder (browsable), something the driver can play (playable),
 * or both.
 */
export interface CarMediaItem {
  /** Unique id. Play requests and the children loader refer to items by this id. */
  id: string;
  /** Main text */
  title: string;
  /** Secondary text, such as the artist, show or episode date */
  subtitle?: string;
  /** Artwork as an `http(s)://`, `file://` or `content://` URI */
  artwork?: { uri: string } | string;
  /** Can the driver play this item? Defaults to `true` for items that aren't browsable. */
  playable?: boolean;
  /**
   * Can the driver open this item to see its children? Defaults to `true` when `children` is
   * set. A browsable item without `children` is loaded with the children loader when opened.
   */
  browsable?: boolean;
  /** The folder's items. Leave out to load them on demand with `setChildrenLoader()`. */
  children?: CarMediaItem[];
  /** How this folder's children are displayed */
  style?: CarListStyle;
  /** Duration in seconds, shown by some head units */
  duration?: number;
  /** Marks explicit content (shown as an "E" badge where supported) */
  explicit?: boolean;
}

/** The whole library shown in the car */
export interface CarLibraryContent {
  /**
   * Top-level entries. Browsable entries become tabs in Android Auto and CarPlay; both show at
   * most 4 tabs, the rest are dropped (CarPlay) or moved into a list (Android Auto, depending
   * on the head unit).
   */
  tabs: CarMediaItem[];
  /** How the top-level entries are displayed when they aren't shown as tabs */
  style?: CarListStyle;
}

/** A request from the car to play something */
export interface CarPlayRequest {
  /** The id of the item the driver picked. Missing for voice requests without an exact item. */
  itemId?: string;
  /**
   * The spoken or typed search query of a voice request ("Hey Google / Siri, play X").
   * An empty string means "play something" without naming anything.
   */
  query?: string;
  /** Whether the car also asked to start playback right away (false for "prepare" requests) */
  playWhenReady: boolean;
  /** Start position in seconds, if the car asked for one */
  position?: number;
}

export type CarChildrenLoader = (
  parentId: string,
) => Promise<CarMediaItem[]> | CarMediaItem[];

export type CarSearchHandler = (
  query: string,
) => Promise<CarMediaItem[]> | CarMediaItem[];

export type CarPlayRequestListener = (request: CarPlayRequest) => void;

/** Item format sent to the native side */
export interface NativeCarMediaItem {
  id: string;
  title: string;
  subtitle?: string;
  artworkUri?: string;
  playable: boolean;
  browsable: boolean;
  /** Missing: load on demand with the children loader */
  children?: NativeCarMediaItem[];
  style?: CarListStyle;
  duration?: number;
  explicit?: boolean;
}

export interface NativeCarLibrary {
  tabs: NativeCarMediaItem[];
  style?: CarListStyle;
}
