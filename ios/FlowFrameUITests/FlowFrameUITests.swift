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
        let tab = app.buttons["tasksTab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 5))
        tab.tap()
        XCTAssertTrue(app.navigationBars["下载"].waitForExistence(timeout: 5))
    }

    @MainActor
    private func openAbout(in app: XCUIApplication) {
        let tab = app.buttons["aboutTab"]
        XCTAssertTrue(tab.waitForExistence(timeout: 5))
        tab.tap()
        XCTAssertTrue(app.navigationBars["关于 FlowFrame"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["iOS 实验版"].exists)
    }

    @MainActor
    private func attachScreenshot(of app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
