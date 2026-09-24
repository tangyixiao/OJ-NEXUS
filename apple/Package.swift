// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "OJNexusApple",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(name: "OJNexusCore", targets: ["OJNexusCore"]),
        .library(name: "OJNexusUI", targets: ["OJNexusUI"]),
        .executable(name: "OJNexusMacOS", targets: ["OJNexusMacOS"]),
        .executable(name: "OJNexusIOS", targets: ["OJNexusIOS"]),
    ],
    targets: [
        .target(name: "OJNexusCore"),
        .target(name: "OJNexusUI", dependencies: ["OJNexusCore"]),
        .executableTarget(name: "OJNexusMacOS", dependencies: ["OJNexusUI"], path: "Apps/OJNexusMacOS"),
        .executableTarget(name: "OJNexusIOS", dependencies: ["OJNexusUI"], path: "Apps/OJNexusIOS"),
        .testTarget(name: "OJNexusCoreTests", dependencies: ["OJNexusCore", "OJNexusUI"]),
    ]
)
