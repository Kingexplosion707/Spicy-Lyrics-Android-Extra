import Foundation
import MediaPlayer
import UIKit

/// What plays, from Apple Music (the system music player) or Spotify (Web API with the user's
/// own client ID), in the shape the web app's host expects.
struct Track: Equatable {
    var id: String = ""          // Spotify track id, when known
    var title: String
    var artist: String
    var album: String
    var coverUrl: String
    var durationMs: Int
}

struct PlaybackState {
    var track: Track?
    var positionMs: Int = 0
    var timestamp: Date = Date()
    var isPlaying: Bool = false
    var source: String = ""
    var liked: Bool = false
    var shuffle: Bool = false
    var repeatMode: Int = 0
    var next: String = ""
}

enum Source: String, CaseIterable, Identifiable {
    case auto, appleMusic, spotify
    var id: String { rawValue }
    var label: String {
        switch self {
        case .auto: return "Automatic"
        case .appleMusic: return "Apple Music"
        case .spotify: return "Spotify"
        }
    }
}

@MainActor
final class NowPlaying: ObservableObject {
    @Published private(set) var state = PlaybackState()
    @Published var source: Source = Source(rawValue: UserDefaults.standard.string(forKey: "source") ?? "") ?? .auto {
        didSet { UserDefaults.standard.set(source.rawValue, forKey: "source"); refresh() }
    }

    let spotify = SpotifySource()
    private let appleMusic = AppleMusicSource()
    private var timer: Timer?
    var onChange: ((PlaybackState) -> Void)?

    func start() {
        appleMusic.start()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
        refresh()
    }

    func refresh() {
        Task {
            var next: PlaybackState?
            switch source {
            case .appleMusic:
                next = appleMusic.read()
            case .spotify:
                next = await spotify.read()
            case .auto:
                // Whichever is playing; Spotify first when both are (or neither).
                let fromSpotify = spotify.signedIn ? await spotify.read() : nil
                let fromApple = appleMusic.read()
                if let s = fromSpotify, s.isPlaying { next = s }
                else if let a = fromApple, a.isPlaying { next = a }
                else { next = fromSpotify ?? fromApple }
            }
            let value = next ?? PlaybackState()
            state = value
            onChange?(value)
        }
    }

    func command(_ command: String) {
        let active = state.source
        Task {
            if active == "spotify" { await spotify.command(command) }
            else { appleMusic.command(command) }
            try? await Task.sleep(nanoseconds: 350_000_000)
            refresh()
        }
    }
}

/// The system music player (Apple Music app). Needs the user's media-library permission.
final class AppleMusicSource {
    private let player = MPMusicPlayerController.systemMusicPlayer
    private var authorized = false
    private var coverCache: (key: String, url: String) = ("", "")

    func start() {
        MPMediaLibrary.requestAuthorization { [weak self] status in
            self?.authorized = status == .authorized
            if status == .authorized {
                DispatchQueue.main.async { self?.player.beginGeneratingPlaybackNotifications() }
            }
        }
    }

    func read() -> PlaybackState? {
        guard authorized, let item = player.nowPlayingItem, let title = item.title, !title.isEmpty else { return nil }
        let key = "\(item.persistentID)"
        if coverCache.key != key {
            coverCache = (key, Self.dataUrl(item.artwork?.image(at: CGSize(width: 600, height: 600))))
        }
        let track = Track(
            title: title,
            artist: item.artist ?? item.albumArtist ?? "",
            album: item.albumTitle ?? "",
            coverUrl: coverCache.url,
            durationMs: Int(item.playbackDuration * 1000)
        )
        var s = PlaybackState(track: track)
        s.positionMs = Int(max(0, player.currentPlaybackTime) * 1000)
        s.timestamp = Date()
        s.isPlaying = player.playbackState == .playing
        s.source = "applemusic"
        s.shuffle = player.shuffleMode != .off
        s.repeatMode = player.repeatMode == .one ? 2 : (player.repeatMode == .all ? 1 : 0)
        return s
    }

    func command(_ command: String) {
        DispatchQueue.main.async { [player] in
            switch command {
            case "toggle": player.playbackState == .playing ? player.pause() : player.play()
            case "play": player.play()
            case "pause": player.pause()
            case "next": player.skipToNextItem()
            case "previous": player.currentPlaybackTime > 3 ? player.skipToBeginning() : player.skipToPreviousItem()
            case "shuffle": player.shuffleMode = player.shuffleMode == .off ? .songs : .off
            case "repeat":
                player.repeatMode = player.repeatMode == .none ? .all : (player.repeatMode == .all ? .one : .none)
            default:
                if command.hasPrefix("seek "), let ms = Double(command.dropFirst(5)) { player.currentPlaybackTime = ms / 1000 }
            }
        }
    }

    private static func dataUrl(_ image: UIImage?) -> String {
        guard let data = image?.jpegData(compressionQuality: 0.85) else { return "" }
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }
}
