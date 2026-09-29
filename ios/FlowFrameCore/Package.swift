// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "FlowFrameCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "FlowFrameCore", targets: ["FlowFrameCore"])],
    targets: [
        .target(name: "FlowFrameCore"),
        .testTarget(name: "FlowFrameCoreTests", dependencies: ["FlowFrameCore"])
    ]
)
