import ExpoModulesCore
import Intents
import ObjectiveC
import UIKit

/**
 Handles "Hey Siri, play X in <app>" (`INPlayMediaIntent`) inside the app, in the car and on the
 phone. The request is passed to JavaScript as a play request with the spoken query.

 Needs the Siri capability and `INPlayMediaIntent` in `INIntentsSupported`; the config plugin
 adds both with the `siri` option.
 */
final class CarSiriIntentHandler: NSObject, INPlayMediaIntentHandling {
  static let shared = CarSiriIntentHandler()

  func handle(intent: INPlayMediaIntent, completion: @escaping (INPlayMediaIntentResponse) -> Void) {
    var request: [String: Any] = ["playWhenReady": true]
    if let identifier = intent.mediaItems?.first?.identifier, !identifier.isEmpty {
      request["itemId"] = identifier
    }
    let search = intent.mediaSearch
    request["query"] = search?.mediaName ?? search?.albumName ?? search?.artistName ?? search?.genreNames?.first ?? ""

    DispatchQueue.main.async {
      CarLibraryStore.shared.loadFromDiskIfNeeded()
      CarLibraryStore.shared.deliverPlayRequest(request)
      completion(INPlayMediaIntentResponse(code: .success, userActivity: nil))
    }
  }
}

/**
 Installs the Siri intent handler on the app delegate. `ExpoAppDelegate` doesn't forward
 `application(_:handlerFor:)` to subscribers, so the method is added to the app delegate class at
 launch unless the app already implements it (then call `CarSiriIntentHandler` yourself).
 */
public class ExpoMediaControlCarAppDelegateSubscriber: ExpoAppDelegateSubscriber {
  public func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    installIntentHandler(on: application)
    return true
  }

  private func installIntentHandler(on application: UIApplication) {
    let supportedIntents = Bundle.main.object(forInfoDictionaryKey: "INIntentsSupported") as? [String] ?? []
    guard supportedIntents.contains("INPlayMediaIntent"),
      let delegate = application.delegate,
      let delegateClass = object_getClass(delegate) else {
      return
    }
    let selector = #selector(UIApplicationDelegate.application(_:handlerFor:))
    if class_getInstanceMethod(delegateClass, selector) != nil {
      return
    }
    let block: @convention(block) (AnyObject, UIApplication, INIntent) -> Any? = { _, _, intent in
      return intent is INPlayMediaIntent ? CarSiriIntentHandler.shared : nil
    }
    class_addMethod(delegateClass, selector, imp_implementationWithBlock(block), "@@:@@")
  }
}
