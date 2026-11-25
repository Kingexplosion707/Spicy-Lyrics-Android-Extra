// Spicy Lyrics Plus on iPhone and iPad.
//
// The Spicetify app (app.js) expects to live inside Spotify. This host gives it the same
// `Spicetify` surface, fed by the native app: what plays (Apple Music, or Spotify through the
// Web API), the controls, liked / shuffle / repeat / up next, and a request path without CORS
// (CosmosAsync.get goes through the app), so every lyrics source, the Canvas and Apple Music's
// animated covers work as they do inside Spotify.
(function () {
  "use strict";

  const native = (msg) => { try { window.webkit.messageHandlers.slp.postMessage(msg); } catch (_) {} };

  const st = { item: null, key: "", pos: 0, posAt: Date.now(), playing: false, player: {}, source: "" };
  const listeners = { songchange: new Set(), onplaypause: new Set(), onprogress: new Set() };
  const emit = (type) => listeners[type].forEach((fn) => { try { fn({ type, data: st.item }); } catch (_) {} });

  // ---- native requests ----------------------------------------------------------------------
  let nextId = 1;
  const pending = new Map();
  function ask(msg, ms) {
    return new Promise((resolve, reject) => {
      const id = nextId++;
      const timer = setTimeout(() => { pending.delete(id); reject(new Error("timeout")); }, ms || 15000);
      pending.set(id, { resolve, reject, timer });
      native(Object.assign({ id }, msg));
    });
  }

  function itemFrom(track) {
    const id = track.id || "";
    const artists = String(track.artist || "").split(/\s*,\s*/).filter(Boolean).map((name) => ({ name, uri: "" }));
    return {
      uri: id ? "spotify:track:" + id : "local:" + encodeURIComponent(track.title || "") + ":" + encodeURIComponent(track.artist || ""),
      name: track.title || "",
      artists: artists.length ? artists : [{ name: track.artist || "", uri: "" }],
      album: { name: track.album || "", images: track.coverUrl ? [{ url: track.coverUrl }] : [] },
      duration: { milliseconds: Number(track.durationMs) || 0 },
      metadata: { title: track.title || "", artist_name: track.artist || "", album_title: track.album || "", image_url: track.coverUrl || "" },
    };
  }

  const position = () => Math.max(0, st.pos + (st.playing ? Date.now() - st.posAt : 0));

  window.SLP_NATIVE = {
    /** What plays, about once a second. */
    state(s) {
      const track = s && s.track;
      document.documentElement.classList.toggle("slp-nothing", !track);
      st.source = (s && s.source) || "";
      st.player = { liked: !!(s && s.liked), shuffle: !!(s && s.shuffle), repeat: (s && s.repeat) || 0, next: (s && s.next) || "" };
      let changed = false;
      if (track) {
        const key = (track.id || "") + "|" + track.artist + "|" + track.title;
        if (key !== st.key) {
          st.key = key;
          st.item = itemFrom(track);
          changed = true;
        } else if (track.coverUrl && !st.item.album.images.length) {
          st.item.album.images = [{ url: track.coverUrl }];
        }
      }
      const wasPlaying = st.playing;
      const playing = !!(s && s.isPlaying);
      // Polled positions jitter by a few hundred ms: only follow a real jump (a seek, a pause).
      if (typeof s.position === "number") {
        const reported = s.position + (playing ? Date.now() - s.timestamp : 0);
        if (changed || playing !== wasPlaying || Math.abs(reported - position()) > 450) {
          st.pos = s.position;
          st.posAt = s.timestamp;
        }
      }
      st.playing = playing;
      if (changed) emit("songchange");
      if (wasPlaying !== st.playing) emit("onplaypause");
    },
    resolve(id, ok, value) {
      const p = pending.get(id);
      if (!p) return;
      pending.delete(id);
      clearTimeout(p.timer);
      ok ? p.resolve(value) : p.reject(new Error(String(value || "failed")));
    },
    toast(text) { window.Spicetify.showNotification(text); },
  };

  // ---- what app.js asks for -----------------------------------------------------------------
  window.SLP_HOST = {
    mode: "app",
    platform: "ios",
    // No Spotify word lyrics here: app.js asks Apple Music (RMM), Spotify and LRCLIB itself.
    lyrics: null,
    /** The Spotify Canvas, from Spicy Lyrics' Canvas service (the app finds the Spotify track). */
    async canvas(uri) {
      if (!st.item) return "";
      try {
        const url = await ask({ type: "canvas", uri: String(uri || ""), title: st.item.name, artist: st.item.metadata.artist_name }, 12000);
        return /^https:\/\/.+\.(mp4|webm)(\?|$)/.test(url || "") ? url : "";
      } catch (_) {
        return "";
      }
    },
    // Animated covers: app.js looks them up through CosmosAsync (no CORS here); iOS plays HLS itself.
    motionCover: null,
    settingsChanged() {},
  };

  // ---- the Spicetify surface ---------------------------------------------------------------
  const command = (c) => { native({ type: "command", value: c }); return true; };
  let toastTimer = 0;
  window.Spicetify = {
    React: window.React,
    ReactDOM: window.ReactDOM,
    Player: {
      get data() { return st.item ? { item: st.item, isPaused: !st.playing } : null; },
      addEventListener(type, fn) { (listeners[type] || new Set()).add(fn); },
      removeEventListener(type, fn) { listeners[type] && listeners[type].delete(fn); },
      getProgress: position,
      isPlaying: () => st.playing,
      getDuration: () => (st.item ? st.item.duration.milliseconds : 0),
      togglePlay() { command("toggle"); st.pos = position(); st.posAt = Date.now(); st.playing = !st.playing; emit("onplaypause"); },
      play: () => command("play"),
      pause: () => command("pause"),
      next: () => command("next"),
      back: () => command("previous"),
      seek(ms) { command("seek " + Math.max(0, Math.round(ms))); st.pos = Math.max(0, ms); st.posAt = Date.now(); },
      getShuffle: () => !!st.player.shuffle,
      getRepeat: () => st.player.repeat || 0,
      getHeart: () => !!st.player.liked,
      toggleShuffle() { command("shuffle"); st.player.shuffle = !st.player.shuffle; },
      toggleRepeat() { command("repeat"); st.player.repeat = ((st.player.repeat || 0) + 1) % 3; },
      toggleHeart() { if (st.source === "spotify") { command("like"); st.player.liked = !st.player.liked; } else window.Spicetify.showNotification("Liking works with Spotify"); },
    },
    /** Requests without CORS, through the app. JSON comes back parsed, anything else as text. */
    CosmosAsync: {
      get(url, _body, headers) { return ask({ type: "fetch", url: String(url), headers: headers || {} }, 15000); },
    },
    Platform: { History: { push() {} } },
    colorExtractor: () => Promise.reject(new Error("not in Spotify")),
    get Queue() {
      const next = String(st.player.next || "");
      if (!next) return { nextTracks: [] };
      const [title, artist] = next.split(" · ");
      return { nextTracks: [{ contextTrack: { metadata: { title, artist_name: artist || "" } } }] };
    },
    showNotification(text, isError) {
      let el = document.getElementById("slp-toast");
      if (!el) { el = document.createElement("div"); el.id = "slp-toast"; document.body.appendChild(el); }
      el.textContent = text;
      el.className = isError ? "error show" : "show";
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => { el.className = ""; }, 2400);
    },
  };

  // The app's own settings (where the music comes from, Spotify sign-in, keep awake).
  window.SLP_IOS = {
    openSettings: () => native({ type: "settings" }),
  };
  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-slp-settings]").forEach((el) => el.addEventListener("click", () => window.SLP_IOS.openSettings()));
  });

  document.documentElement.classList.add("slp-nothing", "slp-ios");
  native({ type: "ready" });
})();
