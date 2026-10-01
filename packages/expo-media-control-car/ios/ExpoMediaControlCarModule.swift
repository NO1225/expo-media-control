import ExpoModulesCore

/**
 Receives the car library from JavaScript and sends CarPlay's and Siri's requests (children to
 load, play requests) back as events.
 */
public class ExpoMediaControlCarModule: Module, CarJsBridge {
  private static let loadChildrenEvent = "onLoadChildren"
  private static let searchEvent = "onSearch"
  private static let playRequestEvent = "onPlayRequest"

  private var hasPlayRequestListener = false

  public func definition() -> ModuleDefinition {
    Name("ExpoMediaControlCar")

    Events(
      ExpoMediaControlCarModule.loadChildrenEvent,
      ExpoMediaControlCarModule.searchEvent,
      ExpoMediaControlCarModule.playRequestEvent
    )

    OnDestroy {
      DispatchQueue.main.async { [weak self] in
        self?.detach()
      }
    }

    AsyncFunction("setLibrary") { (library: [String: Any]) in
      self.attach()
      CarLibraryStore.shared.setLibrary(CarLibraryTree(dictionary: library))
    }.runOnQueue(.main)

    AsyncFunction("setHandlers") { (handlers: [String: Any]) in
      self.attach()
      CarLibraryStore.shared.hasChildrenLoader = handlers["childrenLoader"] as? Bool ?? false
      // CarPlay has no search screen for audio apps; Siri requests arrive as play requests
    }.runOnQueue(.main)

    AsyncFunction("resolveChildren") { (requestId: String, items: [[String: Any]]?) in
      CarLibraryStore.shared.resolveChildren(
        requestId: requestId,
        items: items.map { CarLibraryItem.items(from: $0) }
      )
    }.runOnQueue(.main)

    AsyncFunction("resolveSearch") { (_: String, _: [[String: Any]]?) in
      // Searches are only requested on Android
    }.runOnQueue(.main)

    AsyncFunction("notifyChildrenChanged") { (parentId: String) in
      CarLibraryStore.shared.invalidateChildren(parentId)
    }.runOnQueue(.main)

    /// Called by JavaScript after adding a play request listener
    AsyncFunction("flushPendingPlayRequest") {
      self.attach()
      self.hasPlayRequestListener = true
      if let request = CarLibraryStore.shared.pendingPlayRequest {
        CarLibraryStore.shared.pendingPlayRequest = nil
        self.sendEvent(ExpoMediaControlCarModule.playRequestEvent, request.mapValues { $0 as Any? })
      }
    }.runOnQueue(.main)
  }

  // MARK: - CarJsBridge

  func requestChildren(requestId: String, parentId: String) {
    sendEvent(ExpoMediaControlCarModule.loadChildrenEvent, [
      "requestId": requestId,
      "parentId": parentId
    ])
  }

  func deliverPlayRequest(_ request: [String: Any]) {
    if hasPlayRequestListener {
      sendEvent(ExpoMediaControlCarModule.playRequestEvent, request.mapValues { $0 as Any? })
    } else {
      CarLibraryStore.shared.pendingPlayRequest = request
    }
  }

  // MARK: - Private

  private func attach() {
    let store = CarLibraryStore.shared
    store.loadFromDiskIfNeeded()
    store.bridge = self
  }

  private func detach() {
    let store = CarLibraryStore.shared
    if store.bridge === self {
      store.bridge = nil
      store.hasChildrenLoader = false
      store.cancelPendingRequests()
    }
  }
}
