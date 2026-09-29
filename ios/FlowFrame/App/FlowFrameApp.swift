import SwiftUI

@main
struct FlowFrameApp: App {
    @StateObject private var downloads = DownloadStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(downloads)
                .tint(Brand.purple)
                .onChange(of: scenePhase) { phase in
                    downloads.setForeground(phase == .active)
                }
        }
    }
}

enum Brand {
    static let purple = Color(red: 0.48, green: 0.33, blue: 0.96)
    static let cyan = Color(red: 0.12, green: 0.74, blue: 0.79)
    static let gradient = LinearGradient(colors: [purple, cyan], startPoint: .topLeading, endPoint: .bottomTrailing)
}
