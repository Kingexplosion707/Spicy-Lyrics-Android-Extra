import SwiftUI
import UniformTypeIdentifiers
import WebKit

/// The lyrics screen: the Spicy Lyrics Plus web app (bundled in Web/), served from slp://app/ so
/// it has a real origin and keeps its settings, and fed by [NowPlaying] through host-ios.js.
struct LyricsWebView: UIViewRepresentable {
    let nowPlaying: NowPlaying
    let openSettings: () -> Void

    func makeCoordinator() -> Bridge { Bridge(nowPlaying: nowPlaying, openSettings: openSettings) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(BundleScheme(), forURLScheme: "slp")
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.websiteDataStore = .default()
        config.userContentController.add(context.coordinator, name: "slp")
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .black
        web.scrollView.backgroundColor = .black
        web.scrollView.isScrollEnabled = false
        web.scrollView.bounces = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.allowsLinkPreview = false
        if #available(iOS 16.4, *) { web.isInspectable = true }
        context.coordinator.web = web
        web.load(URLRequest(url: URL(string: "slp://app/index.html")!))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

/// Serves the bundled web app from the app's Web folder.
@MainActor
final class BundleScheme: NSObject, @preconcurrency WKURLSchemeHandler {
    private let root = Bundle.main.resourceURL!.appendingPathComponent("Web")

    func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
        guard let url = task.request.url else { return }
        var path = url.path
        if path.isEmpty || path == "/" { path = "/index.html" }
        let file = root.appendingPathComponent(String(path.dropFirst())).standardizedFileURL
        guard file.path.hasPrefix(root.standardizedFileURL.path), let data = try? Data(contentsOf: file) else {
            task.didReceive(HTTPURLResponse(url: url, statusCode: 404, httpVersion: "HTTP/1.1", headerFields: nil)!)
            task.didFinish()
            return
        }
        let type = UTType(filenameExtension: file.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
        let headers = ["Content-Type": type, "Content-Length": "\(data.count)", "Cache-Control": "no-cache"]
        task.didReceive(HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: headers)!)
        task.didReceive(data)
        task.didFinish()
    }

    func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {}
}

/// Messages between host-ios.js and the app.
@MainActor
final class Bridge: NSObject, @preconcurrency WKScriptMessageHandler {
    weak var web: WKWebView?
    private let nowPlaying: NowPlaying
    private let openSettings: () -> Void
    private let canvas = CanvasFinder()
    private var ready = false

    init(nowPlaying: NowPlaying, openSettings: @escaping () -> Void) {
        self.nowPlaying = nowPlaying
        self.openSettings = openSettings
        super.init()
        nowPlaying.onChange = { [weak self] state in self?.push(state) }
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        handle(message.body as? [String: Any] ?? [:])
    }

    private func handle(_ body: [String: Any]) {
        let id = body["id"] as? Int ?? 0
        switch body["type"] as? String {
        case "ready":
            ready = true
            push(nowPlaying.state)
        case "command":
            if let value = body["value"] as? String { nowPlaying.command(value) }
        case "settings":
            openSettings()
        case "fetch":
            guard let raw = body["url"] as? String, let url = URL(string: raw), url.scheme == "https" else {
                resolve(id, ok: false, json: "\"bad url\""); return
            }
            let headers = body["headers"] as? [String: String] ?? [:]
            Task { await self.fetch(id, url, headers) }
        case "canvas":
            let uri = body["uri"] as? String ?? ""
            let title = body["title"] as? String ?? ""
            let artist = body["artist"] as? String ?? ""
            Task {
                var trackId = uri.hasPrefix("spotify:track:") ? String(uri.dropFirst(14)) : ""
                if trackId.isEmpty { trackId = await self.nowPlaying.spotify.findTrackId(title: title, artist: artist) ?? "" }
                let url = trackId.isEmpty ? nil : await self.canvas.find(trackId)
                self.resolve(id, ok: true, json: Self.json(url ?? ""))
            }
        default:
            break
        }
    }

    /// A request without CORS for the web app: JSON comes back parsed, anything else as text.
    private func fetch(_ id: Int, _ url: URL, _ headers: [String: String]) async {
        var req = URLRequest(url: url, timeoutInterval: 12)
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        headers.forEach { req.setValue($0.value, forHTTPHeaderField: $0.key) }
        guard let (data, response) = try? await URLSession.shared.data(for: req) else { resolve(id, ok: false, json: "\"network\""); return }
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { resolve(id, ok: false, json: Self.json("HTTP \(code)")); return }
        if let parsed = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]),
           JSONSerialization.isValidJSONObject(parsed),
           let back = try? JSONSerialization.data(withJSONObject: parsed),
           let text = String(data: back, encoding: .utf8) {
            resolve(id, ok: true, json: text)
        } else {
            resolve(id, ok: true, json: Self.json(String(decoding: data, as: UTF8.self)))
        }
    }

    private func resolve(_ id: Int, ok: Bool, json: String) {
        web?.evaluateJavaScript("window.SLP_NATIVE&&SLP_NATIVE.resolve(\(id),\(ok),\(json))")
    }

    private func push(_ s: PlaybackState) {
        guard ready else { return }
        var obj: [String: Any] = [
            "isPlaying": s.isPlaying, "position": s.positionMs, "timestamp": Int(s.timestamp.timeIntervalSince1970 * 1000),
            "source": s.source, "liked": s.liked, "shuffle": s.shuffle, "repeat": s.repeatMode, "next": s.next,
        ]
        if let t = s.track {
            obj["track"] = ["id": t.id, "title": t.title, "artist": t.artist, "album": t.album, "coverUrl": t.coverUrl, "durationMs": t.durationMs]
        }
        guard let data = try? JSONSerialization.data(withJSONObject: obj), let text = String(data: data, encoding: .utf8) else { return }
        web?.evaluateJavaScript("window.SLP_NATIVE&&SLP_NATIVE.state(\(text))")
    }

    private static func json(_ s: String) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: [s]), let text = String(data: data, encoding: .utf8) else { return "\"\"" }
        return String(text.dropFirst().dropLast())
    }
}

/// The song's Spotify Canvas (a looping MP4), from Spicy Lyrics' Canvas service, by Spotify track ID.
actor CanvasFinder {
    private var memo: [String: String] = [:]
    private var pausedUntil = Date.distantPast

    func find(_ trackId: String) async -> String? {
        guard trackId.count == 22, trackId.allSatisfy({ $0.isLetter || $0.isNumber }) else { return nil }
        if let known = memo[trackId] { return known.isEmpty ? nil : known }
        guard Date() >= pausedUntil,
              let url = URL(string: "https://regional.canvasstore.slnode.spicylyrics.org/provider/canvaslist/\(trackId)") else { return nil }
        var req = URLRequest(url: url, timeoutInterval: 10)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        guard let (data, response) = try? await URLSession.shared.data(for: req), (response as? HTTPURLResponse)?.statusCode == 200,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let entries = json["data"] as? [String: Any] else {
            pausedUntil = Date().addingTimeInterval(60)
            return nil
        }
        let canvas = entries[trackId] as? [String: Any]
        let found = (canvas?["url"] as? String).flatMap { $0.hasPrefix("https://") && $0.contains(".mp4") ? $0 : nil } ?? ""
        if entries.keys.contains(trackId) { memo[trackId] = found }
        return found.isEmpty ? nil : found
    }
}
