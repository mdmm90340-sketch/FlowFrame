import SwiftUI

struct RootView: View {
    @EnvironmentObject private var downloads: DownloadStore
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomeView { selectedTab = 1 }
            }
            .tabItem { Label("解析", systemImage: "link") }
            .tag(0)
            .accessibilityIdentifier("homeTab")

            NavigationStack { DownloadsView() }
                .tabItem { Label("下载", systemImage: "arrow.down.circle") }
                .badge(downloads.pendingCount)
                .tag(1)
                .accessibilityIdentifier("tasksTab")

            NavigationStack { AboutView() }
                .tabItem { Label("关于", systemImage: "sparkles") }
                .tag(2)
                .accessibilityIdentifier("aboutTab")
        }
    }
}

struct NoticeCard: View {
    let icon: String
    let text: String

    var body: some View {
        Label {
            Text(text).font(.footnote).fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: icon).foregroundStyle(Brand.purple)
        }
        .foregroundStyle(.secondary)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.purple.opacity(0.07), in: RoundedRectangle(cornerRadius: 16))
    }
}
