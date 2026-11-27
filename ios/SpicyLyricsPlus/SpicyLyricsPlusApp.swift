import SwiftUI

@main
struct SpicyLyricsPlusApp: App {
    @StateObject private var nowPlaying = NowPlaying()
    @AppStorage("keepAwake") private var keepAwake = true
    @State private var showSettings = false
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            LyricsWebView(nowPlaying: nowPlaying, openSettings: { showSettings = true })
                .ignoresSafeArea()
                .background(Color.black)
                .statusBarHidden(true)
                .persistentSystemOverlays(.hidden)
                .preferredColorScheme(.dark)
                .sheet(isPresented: $showSettings) {
                    SettingsView(nowPlaying: nowPlaying, spotify: nowPlaying.spotify)
                }
                .onAppear {
                    nowPlaying.start()
                    UIApplication.shared.isIdleTimerDisabled = keepAwake
                    // First run, nothing to follow yet: show where the music comes from.
                    if !UserDefaults.standard.bool(forKey: "welcomed") {
                        UserDefaults.standard.set(true, forKey: "welcomed")
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { showSettings = true }
                    }
                }
                .onChange(of: keepAwake) { UIApplication.shared.isIdleTimerDisabled = $0 }
                .onChange(of: phase) { if $0 == .active { nowPlaying.refresh() } }
        }
    }
}
