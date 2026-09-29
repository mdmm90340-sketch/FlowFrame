import XCTest

final class FlowFrameUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testHomeStartsWithEmptyInputAndDisabledParseButton() {
        let app = launchApp()
        defer { app.terminate() }

        let input = app.textViews["shareInput"]
        let parseButton = app.buttons["parseButton"]
        XCTAssertTrue(input.isHittable)
        XCTAssertTrue(parseButton.exists)
        XCTAssertFalse(parseButton.isEnabled)

        input.tap()
        input.typeText("   \n  ")
        XCTAssertFalse(parseButton.isEnabled, "Whitespace alone must not start parsing.")
    }

    @MainActor
    func testUnsupportedLinkShowsFriendlyError() {
        let app = launchApp()
        defer { app.terminate() }

        let input = app.textViews["shareInput"]
        input.tap()
        input.typeText("https://example.com/video")

        let parseButton = app.buttons["parseButton"]
        XCTAssertTrue(parseButton.isEnabled)
        parseButton.tap()

        let alert = app.alerts["暂时无法解析"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        XCTAssertTrue(alert.staticTexts["只支持抖音与哔哩哔哩的公开作品链接。"].exists)
        alert.buttons["好"].tap()
        XCTAssertFalse(alert.exists)
        XCTAssertTrue(input.exists, "Dismissing the error must return to the editable home screen.")
    }

    @MainActor
    func testDownloadsAndAboutNavigation() {
        let app = launchApp()
        defer { app.terminate() }

        openDownloads(in: app)
        openAbout(in: app)
    }

    @MainActor
    func testMainScreensHaveScreenshotAttachments() {
        let app = launchApp()
        defer { app.terminate() }

        attachScreenshot(of: app, named: "FlowFrame - Home")
        openDownloads(in: app)
        attachScreenshot(of: app, named: "FlowFrame - Downloads")
        openAbout(in: app)
        attachScreenshot(of: app, named: "FlowFrame - About")
    }

    @MainActor
    private func launchApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.textViews["shareInput"].waitForExistence(timeout: 10))
        return app
    }

    @MainActor
    private func openDownloads(in app: XCUIApplication) {
        tapTab(in: app, identifier: "tasksTab", title: "下载")
        XCTAssertTrue(app.navigationBars["下载"].waitForExistence(timeout: 5))
    }

    @MainActor
    private func openAbout(in app: XCUIApplication) {
        tapTab(in: app, identifier: "aboutTab", title: "关于")
        XCTAssertTrue(app.navigationBars["关于 FlowFrame"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["iOS 实验版"].exists)
    }

    @MainActor
    private func tapTab(in app: XCUIApplication, identifier: String, title: String) {
        // SwiftUI can expose the NavigationStack identifier on its content instead
        // of its native tab item. Both queries stay scoped to actual tab buttons.
        let tab = app.tabBars.buttons.matching(NSPredicate(
            format: "identifier == %@ OR label == %@", identifier, title
        )).firstMatch
        XCTAssertTrue(tab.waitForExistence(timeout: 5))
        tab.tap()
    }

    @MainActor
    private func attachScreenshot(of app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
