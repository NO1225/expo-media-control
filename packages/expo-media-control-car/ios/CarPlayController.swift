import CarPlay
import UIKit

/**
 Builds CarPlay's templates from `CarLibraryStore`:
 - Browsable top-level entries become tabs of a `CPTabBarTemplate` (lists otherwise)
 - Folders are `CPListTemplate`s; folders without children are loaded from JavaScript
 - Picking a playable item sends a play request and opens the system Now Playing template, which
   shows the metadata and sends the remote commands that expo-media-control already handles

 Must be used on the main thread.
 */
final class CarPlayController {
  static let shared = CarPlayController()

  /// CarPlay audio apps may show at most five templates in the navigation stack
  private static let maximumDepth = 5

  private var interfaceController: CPInterfaceController?
  private var changeListenerId: UUID?
  /// Visible folder templates by folder id, to refresh them when their children change
  private var folderTemplates: [String: WeakTemplate] = [:]
  private var rootListTemplates: [CPListTemplate] = []
  private let artworkCache = NSCache<NSString, UIImage>()

  func connect(_ interfaceController: CPInterfaceController) {
    self.interfaceController = interfaceController
    let store = CarLibraryStore.shared
    store.loadFromDiskIfNeeded()
    if changeListenerId == nil {
      changeListenerId = store.addChangeListener { [weak self] parentIds in
        self?.libraryDidChange(parentIds)
      }
    }
    interfaceController.setRootTemplate(makeRootTemplate(), animated: false, completion: nil)
  }

  func disconnect() {
    interfaceController = nil
    folderTemplates.removeAll()
    rootListTemplates.removeAll()
    if let id = changeListenerId {
      CarLibraryStore.shared.removeChangeListener(id)
      changeListenerId = nil
    }
  }

  // MARK: - Templates

  private func makeRootTemplate() -> CPTemplate {
    let tabs = CarLibraryStore.shared.library?.tabs ?? []
    if tabs.isEmpty {
      let template = CPListTemplate(title: nil, sections: [])
      template.emptyViewTitleVariants = [CarPlayStrings.emptyLibrary]
      rootListTemplates = [template]
      return template
    }

    if tabs.count == 1, let folder = tabs.first, folder.browsable, !folder.playable {
      let template = makeListTemplate(title: folder.title, items: nil, folderId: folder.id)
      rootListTemplates = [template]
      return template
    }

    let maximumTabs = CPTabBarTemplate.maximumTabCount
    if tabs.count > 1 && tabs.allSatisfy({ $0.browsable }) && tabs.count <= maximumTabs {
      let tabTemplates = tabs.map { tab -> CPListTemplate in
        let template = makeListTemplate(title: tab.title, items: nil, folderId: tab.id)
        template.tabTitle = tab.title
        template.tabImage = UIImage(systemName: "music.note.list")
        return template
      }
      rootListTemplates = tabTemplates
      return CPTabBarTemplate(templates: tabTemplates)
    }

    let template = makeListTemplate(title: CarPlayStrings.libraryTitle, items: tabs, folderId: nil)
    rootListTemplates = [template]
    return template
  }

  /// A list for `folderId`'s children, or for `items` when given (the root list)
  private func makeListTemplate(title: String, items: [CarLibraryItem]?, folderId: String?) -> CPListTemplate {
    let template = CPListTemplate(title: title, sections: [])
    template.emptyViewTitleVariants = [CarPlayStrings.emptyFolder]
    if let items = items {
      template.updateSections([makeSection(items)])
    } else if let folderId = folderId {
      folderTemplates[folderId] = WeakTemplate(template)
      loadChildren(of: folderId, into: template, completion: nil)
    }
    return template
  }

  private func loadChildren(of folderId: String, into template: CPListTemplate, completion: (() -> Void)?) {
    let store = CarLibraryStore.shared
    let cached = store.cachedChildren(folderId)
    if let cached = cached {
      template.updateSections([makeSection(cached)])
    }
    if store.findItem(folderId)?.children != nil {
      completion?()
      return
    }
    if cached != nil {
      // Show the cached list right away and refresh it in the background
      completion?()
      store.loadChildren(folderId) { _ in }
      return
    }
    store.loadChildren(folderId) { [weak self, weak template] items in
      if let template = template {
        self?.updateSections(of: template, with: items ?? [])
      }
      completion?()
    }
  }

  private func makeSection(_ items: [CarLibraryItem]) -> CPListSection {
    let limit = CPListTemplate.maximumItemCount
    let listItems: [CPListTemplateItem] = items.prefix(limit).map { self.makeListItem($0) }
    return CPListSection(items: listItems)
  }

  private func updateSections(of template: CPListTemplate, with items: [CarLibraryItem]) {
    template.updateSections([makeSection(items)])
  }

  private func makeListItem(_ item: CarLibraryItem) -> CPListItem {
    let listItem = CPListItem(text: item.title, detailText: item.subtitle, image: placeholderImage(for: item))
    listItem.accessoryType = item.browsable && !item.playable ? .disclosureIndicator : .none
    listItem.isExplicitContent = item.explicit
    listItem.handler = { [weak self] _, completion in
      guard let self = self else {
        completion()
        return
      }
      self.select(item, completion: completion)
    }
    if let artworkUri = item.artworkUri {
      loadArtwork(artworkUri) { [weak listItem] image in
        listItem?.setImage(image)
      }
    }
    return listItem
  }

  private func select(_ item: CarLibraryItem, completion: @escaping () -> Void) {
    guard let interfaceController = interfaceController else {
      completion()
      return
    }
    // Items that are both browsable and playable (albums, playlists) start playing when picked
    if item.playable {
      CarLibraryStore.shared.deliverPlayRequest(["itemId": item.id, "playWhenReady": true])
      showNowPlaying(completion: completion)
      return
    }
    guard interfaceController.templates.count < CarPlayController.maximumDepth else {
      completion()
      return
    }
    let template = CPListTemplate(title: item.title, sections: [])
    template.emptyViewTitleVariants = [CarPlayStrings.emptyFolder]
    folderTemplates[item.id] = WeakTemplate(template)
    // CarPlay shows a spinner on the item until completion is called
    loadChildren(of: item.id, into: template) {
      interfaceController.pushTemplate(template, animated: true) { _, _ in completion() }
    }
  }

  private func showNowPlaying(completion: @escaping () -> Void) {
    guard let interfaceController = interfaceController else {
      completion()
      return
    }
    let nowPlaying = CPNowPlayingTemplate.shared
    if interfaceController.topTemplate === nowPlaying
      || interfaceController.templates.count >= CarPlayController.maximumDepth {
      completion()
      return
    }
    interfaceController.pushTemplate(nowPlaying, animated: true) { _, _ in completion() }
  }

  // MARK: - Updates

  private func libraryDidChange(_ parentIds: Set<String>?) {
    guard let interfaceController = interfaceController else {
      return
    }
    guard let parentIds = parentIds else {
      // A new library: rebuild from the root
      folderTemplates = folderTemplates.filter { $0.value.template != nil }
      interfaceController.setRootTemplate(makeRootTemplate(), animated: false, completion: nil)
      return
    }
    for parentId in parentIds {
      guard let template = folderTemplates[parentId]?.template,
        let children = CarLibraryStore.shared.cachedChildren(parentId) else {
        continue
      }
      updateSections(of: template, with: children)
    }
  }

  // MARK: - Artwork

  private func placeholderImage(for item: CarLibraryItem) -> UIImage? {
    return UIImage(systemName: item.browsable ? "folder" : "music.note")
  }

  private func loadArtwork(_ uri: String, completion: @escaping (UIImage) -> Void) {
    if let cached = artworkCache.object(forKey: uri as NSString) {
      completion(cached)
      return
    }
    guard let url = URL(string: uri) else {
      return
    }
    let deliver: (UIImage?) -> Void = { [weak self] image in
      guard let image = image else {
        return
      }
      let scaled = CarPlayController.scale(image, to: CPListItem.maximumImageSize)
      DispatchQueue.main.async {
        self?.artworkCache.setObject(scaled, forKey: uri as NSString)
        completion(scaled)
      }
    }
    if url.isFileURL {
      DispatchQueue.global(qos: .userInitiated).async {
        deliver(UIImage(contentsOfFile: url.path))
      }
      return
    }
    URLSession.shared.dataTask(with: url) { data, _, _ in
      deliver(data.flatMap(UIImage.init(data:)))
    }.resume()
  }

  private static func scale(_ image: UIImage, to maximumSize: CGSize) -> UIImage {
    let size = image.size
    guard size.width > maximumSize.width || size.height > maximumSize.height,
      size.width > 0, size.height > 0 else {
      return image
    }
    let ratio = min(maximumSize.width / size.width, maximumSize.height / size.height)
    let targetSize = CGSize(width: size.width * ratio, height: size.height * ratio)
    return UIGraphicsImageRenderer(size: targetSize).image { _ in
      image.draw(in: CGRect(origin: .zero, size: targetSize))
    }
  }
}

private final class WeakTemplate {
  weak var template: CPListTemplate?

  init(_ template: CPListTemplate) {
    self.template = template
  }
}

/// Texts shown in CarPlay. Apps can translate them with these keys in their Localizable.strings.
enum CarPlayStrings {
  static var emptyLibrary: String {
    return NSLocalizedString(
      "expo_media_control_car_empty_library",
      value: "Open the app on your phone to load your library",
      comment: "Shown in CarPlay before the app has set a library"
    )
  }

  static var emptyFolder: String {
    return NSLocalizedString(
      "expo_media_control_car_empty_folder",
      value: "Nothing here yet",
      comment: "Shown in CarPlay for an empty folder"
    )
  }

  static var libraryTitle: String {
    return NSLocalizedString(
      "expo_media_control_car_root_title",
      value: "Library",
      comment: "Title of the CarPlay library list"
    )
  }
}
