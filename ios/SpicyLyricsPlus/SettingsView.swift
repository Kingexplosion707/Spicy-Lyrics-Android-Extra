import SwiftUI

/// The app's own settings: where the music comes from, Spotify sign-in, keeping the screen on.
/// Everything about how lyrics look lives in the lyrics screen's own settings (the ⋯ button).
struct SettingsView: View {
    @ObservedObject var nowPlaying: NowPlaying
    @ObservedObject var spotify: SpotifySource
    @AppStorage("keepAwake") private var keepAwake = true
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Follow", selection: $nowPlaying.source) {
                        ForEach(Source.allCases) { Text($0.label).tag($0) }
                    }
                } header: {
                    Text("Music")
                } footer: {
                    Text("iOS only lets an app see what Apple Music plays. Spotify is followed through your Spotify account, so sign in below. Automatic follows whichever is playing.")
                }

                Section {
                    if spotify.signedIn {
                        Label("Signed in to Spotify", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                        Button("Sign out", role: .destructive) { spotify.signOut() }
                    } else {
                        TextField("Spotify client ID", text: $spotify.clientId)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.system(.body, design: .monospaced))
                        Button("Sign in to Spotify") { spotify.signIn() }
                            .disabled(spotify.clientId.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    if !spotify.message.isEmpty {
                        Text(spotify.message).font(.footnote).foregroundStyle(.red)
                    }
                } header: {
                    Text("Spotify")
                } footer: {
                    Text("Use the client ID of your own app at developer.spotify.com (the same one as the Android app), with \(SpotifySource.redirect) as a redirect URI. Play, pause and skip need Spotify Premium.")
                }

                Section {
                    Toggle("Keep the screen on", isOn: $keepAwake)
                } header: {
                    Text("Screen")
                }

                Section {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                } footer: {
                    Text("Lyrics from Apple Music and Spicy Lyrics (via RMM Revival), Spotify and LRCLIB. Canvas from Spicy Lyrics' Canvas service.")
                }
            }
            .navigationTitle("Spicy Lyrics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .preferredColorScheme(.dark)
    }
}
