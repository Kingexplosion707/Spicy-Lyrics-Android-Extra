import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

/// Spotify through its Web API, signed in with the user's own Spotify app client ID (the same one
/// the Android app uses; redirect URI spicylyricsplus://callback). Reading what plays works on
/// every account; the controls need Premium.
@MainActor
final class SpotifySource: NSObject, ObservableObject {
    static let redirect = "spicylyricsplus://callback"
    static let scope = "user-read-playback-state user-read-currently-playing user-modify-playback-state user-library-read user-library-modify"

    @Published var clientId: String = UserDefaults.standard.string(forKey: "spotifyClientId") ?? "" {
        didSet { UserDefaults.standard.set(clientId.trimmingCharacters(in: .whitespaces), forKey: "spotifyClientId") }
    }
    @Published private(set) var signedIn: Bool = UserDefaults.standard.string(forKey: "spotifyRefresh") != nil
    @Published var message: String = ""

    private var accessToken: String? = UserDefaults.standard.string(forKey: "spotifyAccess")
    private var expiresAt: Date = Date(timeIntervalSince1970: UserDefaults.standard.double(forKey: "spotifyExpires"))
    private var session: ASWebAuthenticationSession?
    private var liked: (id: String, value: Bool, at: Date) = ("", false, .distantPast)
    private var next: (id: String, value: String, at: Date) = ("", "", .distantPast)

    // MARK: sign in (PKCE)

    func signIn() {
        let id = clientId.trimmingCharacters(in: .whitespaces)
        guard !id.isEmpty else { message = "Enter your Spotify client ID first."; return }
        let verifier = Self.random(48)
        let state = Self.random(16)
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64Url
        var url = URLComponents(string: "https://accounts.spotify.com/authorize")!
        url.queryItems = [
            .init(name: "client_id", value: id), .init(name: "response_type", value: "code"),
            .init(name: "redirect_uri", value: Self.redirect), .init(name: "code_challenge_method", value: "S256"),
            .init(name: "code_challenge", value: challenge), .init(name: "scope", value: Self.scope), .init(name: "state", value: state),
        ]
        let s = ASWebAuthenticationSession(url: url.url!, callbackURLScheme: "spicylyricsplus") { [weak self] callback, error in
            Task { @MainActor in
                guard let self else { return }
                if let error { self.message = (error as NSError).code == 1 ? "" : error.localizedDescription; return }
                guard let callback, let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems else { return }
                if let e = items.first(where: { $0.name == "error" })?.value { self.message = "Spotify said: \(e)"; return }
                guard items.first(where: { $0.name == "state" })?.value == state,
                      let code = items.first(where: { $0.name == "code" })?.value else { self.message = "Sign-in failed, try again."; return }
                await self.exchange(["grant_type": "authorization_code", "code": code, "redirect_uri": Self.redirect, "client_id": id, "code_verifier": verifier])
            }
        }
        s.presentationContextProvider = self
        s.prefersEphemeralWebBrowserSession = false
        session = s
        s.start()
    }

    func signOut() {
        for k in ["spotifyAccess", "spotifyRefresh", "spotifyExpires"] { UserDefaults.standard.removeObject(forKey: k) }
        accessToken = nil
        signedIn = false
    }


    private func exchange(_ form: [String: String]) async {
        var req = URLRequest(url: URL(string: "https://accounts.spotify.com/api/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.httpBody = form.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")" }.joined(separator: "&").data(using: .utf8)
        guard let (data, response) = try? await URLSession.shared.data(for: req),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["access_token"] as? String else {
            message = "Spotify didn't sign you in. Check the client ID and that \(Self.redirect) is a redirect URI of your Spotify app."
            if form["grant_type"] == "refresh_token" { signOut() }
            return
        }
        accessToken = access
        expiresAt = Date().addingTimeInterval(TimeInterval((json["expires_in"] as? Int) ?? 3600) - 60)
        UserDefaults.standard.set(access, forKey: "spotifyAccess")
        UserDefaults.standard.set(expiresAt.timeIntervalSince1970, forKey: "spotifyExpires")
        if let refresh = json["refresh_token"] as? String { UserDefaults.standard.set(refresh, forKey: "spotifyRefresh") }
        signedIn = true
        message = ""
    }

    private func token() async -> String? {
        if let t = accessToken, Date() < expiresAt { return t }
        guard let refresh = UserDefaults.standard.string(forKey: "spotifyRefresh") else { return nil }
        await exchange(["grant_type": "refresh_token", "refresh_token": refresh, "client_id": clientId.trimmingCharacters(in: .whitespaces)])
        return accessToken
    }

    // MARK: reading and controlling

    private func api(_ method: String, _ path: String, query: [String: String] = [:], body: [String: Any]? = nil) async -> (Int, [String: Any]?) {
        guard let t = await token() else { return (401, nil) }
        var url = URLComponents(string: "https://api.spotify.com/v1" + path)!
        if !query.isEmpty { url.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) } }
        var req = URLRequest(url: url.url!)
        req.httpMethod = method
        req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        if let body { req.httpBody = try? JSONSerialization.data(withJSONObject: body); req.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        guard let (data, response) = try? await URLSession.shared.data(for: req) else { return (0, nil) }
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        if code == 401 { accessToken = nil }
        return (code, data.isEmpty ? nil : (try? JSONSerialization.jsonObject(with: data)) as? [String: Any])
    }

    func read() async -> PlaybackState? {
        guard signedIn else { return nil }
        let requested = Date()
        let (code, json) = await api("GET", "/me/player", query: ["additional_types": "track,episode"])
        guard code == 200, let json, let item = json["item"] as? [String: Any] else { return nil }
        let id = item["id"] as? String ?? ""
        let artists = (item["artists"] as? [[String: Any]])?.compactMap { $0["name"] as? String } ?? []
        let album = item["album"] as? [String: Any]
        let images = (album?["images"] as? [[String: Any]]) ?? (item["images"] as? [[String: Any]]) ?? []
        let track = Track(
            id: id, title: item["name"] as? String ?? "", artist: artists.joined(separator: ", "),
            album: album?["name"] as? String ?? "", coverUrl: images.first?["url"] as? String ?? "",
            durationMs: item["duration_ms"] as? Int ?? 0
        )
        var s = PlaybackState(track: track)
        // Spotify's progress is from when it answered: count half the round trip.
        s.positionMs = (json["progress_ms"] as? Int ?? 0) + Int(Date().timeIntervalSince(requested) * 500)
        s.timestamp = Date()
        s.isPlaying = json["is_playing"] as? Bool ?? false
        s.source = "spotify"
        s.shuffle = json["shuffle_state"] as? Bool ?? false
        s.repeatMode = ["off": 0, "context": 1, "track": 2][json["repeat_state"] as? String ?? "off"] ?? 0
        s.liked = await isLiked(id)
        s.next = await upNext(id)
        return s
    }

    private func isLiked(_ id: String) async -> Bool {
        guard !id.isEmpty else { return false }
        if liked.id == id, Date().timeIntervalSince(liked.at) < 20 { return liked.value }
        // contains answers a bare JSON array, which `api` doesn't return: ask it directly.
        guard let t = await token(), let url = URL(string: "https://api.spotify.com/v1/me/tracks/contains?ids=\(id)") else { return false }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        let value = ((try? await URLSession.shared.data(for: req)).flatMap { try? JSONSerialization.jsonObject(with: $0.0) as? [Bool] })?.first ?? false
        liked = (id, value, Date())
        return value
    }

    private var found: [String: String] = [:]

    /// A song's Spotify track ID by title and artist (for the Canvas of what Apple Music plays).
    func findTrackId(title: String, artist: String) async -> String? {
        guard signedIn, !title.isEmpty else { return nil }
        let key = "\(artist)|\(title)".lowercased()
        if let id = found[key] { return id.isEmpty ? nil : id }
        let (code, json) = await api("GET", "/search", query: ["q": "track:\(title) artist:\(artist)", "type": "track", "limit": "5"])
        guard code == 200 else { return nil }
        let items = ((json?["tracks"] as? [String: Any])?["items"] as? [[String: Any]]) ?? []
        let lead = artist.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces).lowercased() } ?? ""
        let match = items.first { item in
            let names = (item["artists"] as? [[String: Any]])?.compactMap { ($0["name"] as? String)?.lowercased() } ?? []
            return names.contains { $0 == lead || lead.hasPrefix($0) || $0.hasPrefix(lead) }
        }
        let id = match?["id"] as? String ?? ""
        found[key] = id
        return id.isEmpty ? nil : id
    }

    private func upNext(_ id: String) async -> String {
        if next.id == id, Date().timeIntervalSince(next.at) < 30 { return next.value }
        let (_, json) = await api("GET", "/me/player/queue")
        let first = (json?["queue"] as? [[String: Any]])?.first
        let artists = (first?["artists"] as? [[String: Any]])?.compactMap { $0["name"] as? String }.joined(separator: ", ") ?? ""
        let value = first.flatMap { $0["name"] as? String }.map { "\($0) · \(artists)" } ?? ""
        next = (id, value, Date())
        return value
    }

    func command(_ command: String) async {
        switch command {
        case "toggle":
            let (_, json) = await api("GET", "/me/player")
            let playing = json?["is_playing"] as? Bool ?? false
            _ = await api("PUT", playing ? "/me/player/pause" : "/me/player/play")
        case "play": _ = await api("PUT", "/me/player/play")
        case "pause": _ = await api("PUT", "/me/player/pause")
        case "next": _ = await api("POST", "/me/player/next")
        case "previous": _ = await api("POST", "/me/player/previous")
        case "shuffle":
            let (_, json) = await api("GET", "/me/player")
            _ = await api("PUT", "/me/player/shuffle", query: ["state": (json?["shuffle_state"] as? Bool ?? false) ? "false" : "true"])
        case "repeat":
            let (_, json) = await api("GET", "/me/player")
            let next = ["off": "context", "context": "track", "track": "off"][json?["repeat_state"] as? String ?? "off"] ?? "off"
            _ = await api("PUT", "/me/player/repeat", query: ["state": next])
        case "like":
            guard !liked.id.isEmpty else { return }
            _ = await api(liked.value ? "DELETE" : "PUT", "/me/tracks", query: ["ids": liked.id])
            liked = (liked.id, !liked.value, Date())
        default:
            if command.hasPrefix("seek ") { _ = await api("PUT", "/me/player/seek", query: ["position_ms": String(command.dropFirst(5))]) }
        }
    }

    private static func random(_ bytes: Int) -> String {
        var data = Data(count: bytes)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, bytes, $0.baseAddress!) }
        return data.base64Url
    }
}

extension SpotifySource: @preconcurrency ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }
}

extension Data {
    var base64Url: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}
