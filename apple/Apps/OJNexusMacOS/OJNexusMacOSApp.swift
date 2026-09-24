import SwiftUI
import OJNexusUI

@main
struct OJNexusMacOSApp: App {
    var body: some Scene {
        WindowGroup("OJ NEXUS") {
            NexusRootView()
                .frame(minWidth: 900, minHeight: 600)
        }
    }
}
