import Foundation

/// An item of the car library, as sent from JavaScript (see `NativeCarMediaItem` in TypeScript)
struct CarLibraryItem: Equatable {
  let id: String
  let title: String
  let subtitle: String?
  let artworkUri: String?
  let playable: Bool
  let browsable: Bool
  /// nil: loaded on demand with the JavaScript children loader
  let children: [CarLibraryItem]?
  let style: String?
  let duration: Double?
  let explicit: Bool

  init?(dictionary: [String: Any]) {
    guard let id = dictionary["id"] as? String, !id.isEmpty else {
      return nil
    }
    self.id = id
    self.title = dictionary["title"] as? String ?? ""
    self.subtitle = dictionary["subtitle"] as? String
    let artwork = dictionary["artworkUri"] as? String
    self.artworkUri = artwork?.isEmpty == false ? artwork : nil
    self.playable = dictionary["playable"] as? Bool ?? false
    self.browsable = dictionary["browsable"] as? Bool ?? false
    self.children = (dictionary["children"] as? [Any]).map(CarLibraryItem.items(from:))
    self.style = dictionary["style"] as? String
    self.duration = (dictionary["duration"] as? NSNumber)?.doubleValue
    self.explicit = dictionary["explicit"] as? Bool ?? false
  }

  static func items(from list: [Any]) -> [CarLibraryItem] {
    return list.compactMap { ($0 as? [String: Any]).flatMap(CarLibraryItem.init(dictionary:)) }
  }

  var dictionary: [String: Any] {
    var result: [String: Any] = [
      "id": id,
      "title": title,
      "playable": playable,
      "browsable": browsable
    ]
    result["subtitle"] = subtitle
    result["artworkUri"] = artworkUri
    result["children"] = children?.map { $0.dictionary }
    result["style"] = style
    result["duration"] = duration
    if explicit {
      result["explicit"] = true
    }
    return result
  }
}

/// The whole library: the top-level entries (tabs) and how they are displayed
struct CarLibraryTree: Equatable {
  let tabs: [CarLibraryItem]
  let style: String?

  init(dictionary: [String: Any]) {
    self.tabs = (dictionary["tabs"] as? [Any]).map(CarLibraryItem.items(from:)) ?? []
    self.style = dictionary["style"] as? String
  }

  var dictionary: [String: Any] {
    var result: [String: Any] = ["tabs": tabs.map { $0.dictionary }]
    result["style"] = style
    return result
  }

  /// Every item in the tree, depth first
  func allItems() -> [CarLibraryItem] {
    var result: [CarLibraryItem] = []
    func visit(_ items: [CarLibraryItem]) {
      for item in items {
        result.append(item)
        if let children = item.children {
          visit(children)
        }
      }
    }
    visit(tabs)
    return result
  }
}

/// Connection to the JavaScript side, set by the module while the runtime is up
protocol CarJsBridge: AnyObject {
  func requestChildren(requestId: String, parentId: String)
  func deliverPlayRequest(_ request: [String: Any])
}

/**
 The native copy of the car library, shared by the CarPlay templates and the Expo module.

 The tree from `setLibrary()` is saved to disk, so CarPlay can show it right after the phone
 connects, before JavaScript has started. Folders loaded with the JavaScript children loader are
 cached in memory and refreshed in the background when opened again.

 Must be used on the main thread.
 */
final class CarLibraryStore {
  static let shared = CarLibraryStore()

  /// How long CarPlay waits for JavaScript before it gets the cached or an empty result
  static let jsTimeout: TimeInterval = 8

  weak var bridge: CarJsBridge?
  var hasChildrenLoader = false

  /// Called with the ids of folders whose children changed; nil means the whole library
  var changeListeners: [UUID: (Set<String>?) -> Void] = [:]

  /// A play request that arrived while no JavaScript listener was registered
  var pendingPlayRequest: [String: Any]?

  private(set) var library: CarLibraryTree?
  private var treeItems: [String: CarLibraryItem] = [:]
  private var loadedChildren: [String: [CarLibraryItem]] = [:]
  private var loadedItems: [String: CarLibraryItem] = [:]
  private var pendingChildren: [String: ([CarLibraryItem]?) -> Void] = [:]
  private var childrenCallbacks: [String: [([CarLibraryItem]?) -> Void]] = [:]
  private var didLoadFromDisk = false

  private lazy var libraryFileUrl: URL? = {
    guard let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
      return nil
    }
    return directory
      .appendingPathComponent("expo-media-control-car", isDirectory: true)
      .appendingPathComponent("library.json")
  }()

  /// Loads the saved library once
  func loadFromDiskIfNeeded() {
    guard !didLoadFromDisk else {
      return
    }
    didLoadFromDisk = true
    guard library == nil,
      let url = libraryFileUrl,
      let data = try? Data(contentsOf: url),
      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
      return
    }
    apply(CarLibraryTree(dictionary: json))
  }

  func setLibrary(_ tree: CarLibraryTree) {
    didLoadFromDisk = true
    apply(tree)
    save(tree)
    notifyChange(nil)
  }

  func findItem(_ id: String) -> CarLibraryItem? {
    return treeItems[id] ?? loadedItems[id]
  }

  /// Children given in the tree, or the cached result of the children loader
  func cachedChildren(_ parentId: String) -> [CarLibraryItem]? {
    return treeItems[parentId]?.children ?? loadedChildren[parentId]
  }

  var canLoadChildren: Bool {
    return bridge != nil && hasChildrenLoader
  }

  /// Asks JavaScript for the children of `parentId`. Calls `completion` with nil when JavaScript
  /// fails or doesn't answer in time. Concurrent requests for one folder share a JavaScript call.
  func loadChildren(_ parentId: String, completion: @escaping ([CarLibraryItem]?) -> Void) {
    if childrenCallbacks[parentId] != nil {
      childrenCallbacks[parentId]?.append(completion)
      return
    }
    guard let bridge = bridge, hasChildrenLoader else {
      completion(nil)
      return
    }
    childrenCallbacks[parentId] = [completion]
    let requestId = UUID().uuidString
    pendingChildren[requestId] = { [weak self] items in
      guard let self = self else {
        return
      }
      if let items = items {
        let changed = self.loadedChildren[parentId] != items
        self.remember(items)
        self.loadedChildren[parentId] = items
        if changed {
          self.notifyChange([parentId])
        }
      }
      let callbacks = self.childrenCallbacks.removeValue(forKey: parentId) ?? []
      callbacks.forEach { $0(items) }
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + CarLibraryStore.jsTimeout) { [weak self] in
      self?.resolveChildren(requestId: requestId, items: nil)
    }
    bridge.requestChildren(requestId: requestId, parentId: parentId)
  }

  func resolveChildren(requestId: String, items: [CarLibraryItem]?) {
    pendingChildren.removeValue(forKey: requestId)?(items)
  }

  /// Drops the cached children of a loaded folder and tells CarPlay to reload it
  func invalidateChildren(_ parentId: String) {
    loadedChildren.removeValue(forKey: parentId)
    notifyChange([parentId])
  }

  /// Fails every open request, for when JavaScript goes away
  func cancelPendingRequests() {
    let pending = pendingChildren
    pendingChildren.removeAll()
    pending.values.forEach { $0(nil) }
  }

  func deliverPlayRequest(_ request: [String: Any]) {
    if let bridge = bridge {
      bridge.deliverPlayRequest(request)
    } else {
      pendingPlayRequest = request
    }
  }

  func addChangeListener(_ listener: @escaping (Set<String>?) -> Void) -> UUID {
    let id = UUID()
    changeListeners[id] = listener
    return id
  }

  func removeChangeListener(_ id: UUID) {
    changeListeners.removeValue(forKey: id)
  }

  // MARK: - Private

  private func apply(_ tree: CarLibraryTree) {
    library = tree
    treeItems.removeAll()
    for item in tree.allItems() {
      treeItems[item.id] = item
    }
  }

  private func remember(_ items: [CarLibraryItem]) {
    for item in items {
      loadedItems[item.id] = item
      if let children = item.children {
        remember(children)
      }
    }
  }

  private func notifyChange(_ parentIds: Set<String>?) {
    changeListeners.values.forEach { $0(parentIds) }
  }

  private func save(_ tree: CarLibraryTree) {
    guard let url = libraryFileUrl else {
      return
    }
    let dictionary = tree.dictionary
    DispatchQueue.global(qos: .utility).async {
      do {
        let data = try JSONSerialization.data(withJSONObject: dictionary)
        try FileManager.default.createDirectory(
          at: url.deletingLastPathComponent(),
          withIntermediateDirectories: true
        )
        try data.write(to: url, options: .atomic)
      } catch {
        NSLog("[ExpoMediaControlCar] Couldn't save the car library: \(error)")
      }
    }
  }
}
