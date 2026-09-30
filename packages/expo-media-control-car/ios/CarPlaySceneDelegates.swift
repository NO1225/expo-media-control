import CarPlay
import UIKit

/**
 Scene delegate for the CarPlay screen, registered in the app's Info.plist by the config plugin
 (`CPTemplateApplicationSceneSessionRoleApplication`).
 */
@objc(ExpoMediaControlCarPlaySceneDelegate)
public final class ExpoMediaControlCarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
  public func templateApplicationScene(
    _ templateApplicationScene: CPTemplateApplicationScene,
    didConnect interfaceController: CPInterfaceController
  ) {
    CarPlayController.shared.connect(interfaceController)
  }

  public func templateApplicationScene(
    _ templateApplicationScene: CPTemplateApplicationScene,
    didDisconnectInterfaceController interfaceController: CPInterfaceController
  ) {
    CarPlayController.shared.disconnect()
  }
}

/**
 Scene delegate for the phone screen.

 Declaring a CarPlay scene switches the app to the scene life cycle, so the phone UI needs a
 window scene too. The app delegate of an Expo app already creates the window and starts React
 Native at launch (also when CarPlay alone launches the app, so JavaScript runs for the car).
 This delegate shows that window in the phone's scene and passes scene events (links, user
 activities, quick actions, life cycle) on to the app delegate, which no longer gets them from
 UIKit.
 */
@objc(ExpoMediaControlCarPhoneSceneDelegate)
public final class ExpoMediaControlCarPhoneSceneDelegate: UIResponder, UIWindowSceneDelegate {
  public var window: UIWindow?

  public func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    guard let windowScene = scene as? UIWindowScene else {
      return
    }
    let appWindow = (UIApplication.shared.delegate?.window ?? nil) ?? UIWindow()
    if let currentScene = appWindow.windowScene,
      currentScene !== windowScene,
      currentScene.activationState != .unattached {
      // The React Native view can only be in one window: close extra windows (iPad multitasking)
      UIApplication.shared.requestSceneSessionDestruction(session, options: nil, errorHandler: nil)
      return
    }

    appWindow.windowScene = windowScene
    appWindow.frame = windowScene.coordinateSpace.bounds
    window = appWindow
    appWindow.makeKeyAndVisible()

    if !connectionOptions.urlContexts.isEmpty {
      self.scene(scene, openURLContexts: connectionOptions.urlContexts)
    }
    for userActivity in connectionOptions.userActivities {
      self.scene(scene, continue: userActivity)
    }
    if let shortcutItem = connectionOptions.shortcutItem {
      self.windowScene(windowScene, performActionFor: shortcutItem) { _ in }
    }
  }

  public func sceneDidDisconnect(_ scene: UIScene) {
    window = nil
  }

  public func sceneDidBecomeActive(_ scene: UIScene) {
    appDelegate?.applicationDidBecomeActive?(UIApplication.shared)
  }

  public func sceneWillResignActive(_ scene: UIScene) {
    appDelegate?.applicationWillResignActive?(UIApplication.shared)
  }

  public func sceneWillEnterForeground(_ scene: UIScene) {
    appDelegate?.applicationWillEnterForeground?(UIApplication.shared)
  }

  public func sceneDidEnterBackground(_ scene: UIScene) {
    appDelegate?.applicationDidEnterBackground?(UIApplication.shared)
  }

  public func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    let application = UIApplication.shared
    for context in URLContexts {
      var options: [UIApplication.OpenURLOptionsKey: Any] = [:]
      if let sourceApplication = context.options.sourceApplication {
        options[.sourceApplication] = sourceApplication
      }
      if let annotation = context.options.annotation {
        options[.annotation] = annotation
      }
      options[.openInPlace] = context.options.openInPlace
      _ = appDelegate?.application?(application, open: context.url, options: options)
    }
  }

  public func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    _ = appDelegate?.application?(UIApplication.shared, continue: userActivity, restorationHandler: { _ in })
  }

  public func windowScene(
    _ windowScene: UIWindowScene,
    performActionFor shortcutItem: UIApplicationShortcutItem,
    completionHandler: @escaping (Bool) -> Void
  ) {
    if let appDelegate = appDelegate,
      appDelegate.responds(to: #selector(UIApplicationDelegate.application(_:performActionFor:completionHandler:))) {
      appDelegate.application?(UIApplication.shared, performActionFor: shortcutItem, completionHandler: completionHandler)
    } else {
      completionHandler(false)
    }
  }

  private var appDelegate: UIApplicationDelegate? {
    return UIApplication.shared.delegate
  }
}
