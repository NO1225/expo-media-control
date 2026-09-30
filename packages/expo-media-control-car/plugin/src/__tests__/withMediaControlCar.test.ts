import { addCarPlayScenes } from "..";

describe("addCarPlayScenes", () => {
  it("adds the phone and CarPlay scenes to an app without a scene manifest", () => {
    expect(addCarPlayScenes(undefined)).toEqual({
      UIApplicationSupportsMultipleScenes: true,
      UISceneConfigurations: {
        UIWindowSceneSessionRoleApplication: [
          {
            UISceneConfigurationName: "Phone",
            UISceneDelegateClassName: "ExpoMediaControlCarPhoneSceneDelegate",
          },
        ],
        CPTemplateApplicationSceneSessionRoleApplication: [
          {
            UISceneClassName: "CPTemplateApplicationScene",
            UISceneConfigurationName: "CarPlay",
            UISceneDelegateClassName: "ExpoMediaControlCarPlaySceneDelegate",
          },
        ],
      },
    });
  });

  it("keeps an existing phone scene and is idempotent", () => {
    const existing = {
      UISceneConfigurations: {
        UIWindowSceneSessionRoleApplication: [
          {
            UISceneConfigurationName: "Default",
            UISceneDelegateClassName: "EXExpoAppSceneDelegate",
          },
        ],
      },
    };
    const once = addCarPlayScenes(existing);
    const twice = addCarPlayScenes(once);
    expect(twice).toEqual(once);
    expect(
      twice.UISceneConfigurations?.UIWindowSceneSessionRoleApplication,
    ).toEqual(
      existing.UISceneConfigurations.UIWindowSceneSessionRoleApplication,
    );
    expect(
      twice.UISceneConfigurations
        ?.CPTemplateApplicationSceneSessionRoleApplication,
    ).toHaveLength(1);
  });
});
