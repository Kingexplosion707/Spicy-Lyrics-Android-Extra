// Spicy Lyrics Plus outside Spotify: the wallpaper and the desktop app.
//
// The Spicetify app (app.js) expects to live inside Spotify. This host gives it the same
// `Spicetify` surface, fed by the Live Lyrics relay (ws://127.0.0.1:8973): what plays (from
// Spotify through the Spicetify extension, or any player through Windows' media controls),
// Spotify's own word-by-word lyrics, the Canvas, Apple Music animated covers, liked / shuffle /
// repeat / up next, and the playback commands. Settings are shared through the relay, so the
// desktop app changes the wallpaper and the other way round.
(function () {
  "use strict";

  const params = new URLSearchParams(location.search);
  const MODE = window.SLP_MODE || params.get("mode") || (typeof window.wallpaperRegisterAudioListener === "function" ? "wallpaper" : "app");
  const STORE = "spicy-lyrics-plus:";
  const BASE_PORT = Number(params.get("port")) || 8973;

  // ---- state from the relay -----------------------------------------------------------------
  const st = {
    item: null,             // Spicetify-shaped track
    key: "",
    pos: 0,
    posAt: Date.now(),
    playing: false,
    lyrics: null,           // relay shape
    lyricsKey: "",
    lyricsFinal: false,     // Spotify answered (lyrics or none) for lyricsKey
    canvas: "",
    canvasFor: "",
    art: "",
    artFor: "",
    player: {},
    connected: false,
    port: 0,
  };
  const listeners = { songchange: new Set(), onplaypause: new Set(), onprogress: new Set() };
  const emit = (type) => listeners[type].forEach((fn) => { try { fn({ type, data: st.item }); } catch (_) {} });
  const waiters = new Set();
  const wake = () => waiters.forEach((w) => w());

  const plain = (t) => String(t || "").toLowerCase().normalize("NFKD").replace(/[̀-ͯ]/g, "").replace(/[^\p{L}\p{N}]+/gu, " ").trim();

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

  function onMessage(msg) {
    if (msg.command) return;
    if (msg.settings) { applySettings(msg.settings, msg.full); return; }
    if (msg.player) { st.player = msg.player; return; }
    if (msg.canvasVideo) {
      st.canvas = msg.canvasVideo.url ? relayUrl(msg.canvasVideo.url) : "";
      st.canvasFor = st.key;
      wake();
      return;
    }
    if (msg.animatedArt) {
      st.art = msg.animatedArt.url ? relayUrl(msg.animatedArt.url) : "";
      st.artFor = plain(msg.animatedArt.artist) + "|" + plain(msg.animatedArt.album);
      wake();
      return;
    }
    if (msg.cleared) return;
    let changed = false;
    if (msg.track) {
      const key = plain(msg.track.artist) + "|" + plain(msg.track.title);
      if (key !== st.key) {
        st.key = key;
        st.item = itemFrom(msg.track);
        st.lyrics = null;
        st.lyricsKey = key;
        st.lyricsFinal = false;
        st.canvas = "";
        st.canvasFor = "";
        changed = true;
      } else if (msg.track.coverUrl && !st.item.album.images.length) {
        st.item.album.images = [{ url: msg.track.coverUrl }];
      }
      if (msg.lyrics && msg.lyrics.lines && msg.lyrics.lines.length) st.lyrics = msg.lyrics;
      if (!msg.pending) st.lyricsFinal = true;
      wake();
    }
    const wasPlaying = st.playing;
    if (typeof msg.position === "number" && typeof msg.timestamp === "number") {
      st.pos = msg.position;
      st.posAt = msg.timestamp;
    }
    if (typeof msg.isPlaying === "boolean") st.playing = msg.isPlaying;
    if (changed) emit("songchange");
    if (wasPlaying !== st.playing) emit("onplaypause");
  }

  const relayUrl = (u) => String(u).replace(/^http:\/\/localhost:/, "http://127.0.0.1:");

  // ---- relay connection ---------------------------------------------------------------------
  let ws = null;
  let attempt = 0;
  function connect() {
    const port = BASE_PORT + (attempt % 4);
    let sock;
    try { sock = new WebSocket("ws://127.0.0.1:" + port); } catch (_) { attempt++; setTimeout(connect, 2000); return; }
    let opened = false;
    const timer = setTimeout(() => { if (sock.readyState === 0) sock.close(); }, 4000);
    sock.onopen = () => {
      clearTimeout(timer);
      opened = true;
      ws = sock;
      st.connected = true;
      st.port = port;
      document.documentElement.classList.remove("slp-offline");
    };
    sock.onmessage = (ev) => { try { onMessage(JSON.parse(ev.data)); } catch (_) {} };
    sock.onclose = () => {
      clearTimeout(timer);
      if (ws === sock) { ws = null; st.connected = false; document.documentElement.classList.add("slp-offline"); }
      if (!opened) attempt++;
      setTimeout(connect, opened ? 1500 : 3000);
    };
    sock.onerror = () => { try { sock.close(); } catch (_) {} };
  }
  const send = (obj) => { if (ws && ws.readyState === 1) { ws.send(JSON.stringify(obj)); return true; } return false; };
  const command = (c) => send({ command: c });

  // ---- shared settings ----------------------------------------------------------------------
  let applying = false;
  function applySettings(all, full) {
    applying = true;
    try {
      for (const [k, v] of Object.entries(all || {})) {
        try { localStorage.setItem(STORE + k, JSON.stringify(v)); } catch (_) {}
      }
    } finally {
      applying = false;
    }
    window.dispatchEvent(new Event("slp-settings"));
    if (full) seeded = true;
  }
  let seeded = false;

  // ---- what app.js asks for -----------------------------------------------------------------
  function until(test, ms) {
    return new Promise((resolve) => {
      if (test()) return resolve(true);
      const done = (v) => { clearTimeout(timer); waiters.delete(check); resolve(v); };
      const check = () => { if (test()) done(true); };
      const timer = setTimeout(() => done(false), ms);
      waiters.add(check);
    });
  }

  /** The relay's lyrics in app.js's shape (words joined back from Spotify's syllables). */
  function convertLyrics(ly) {
    if (!ly || !Array.isArray(ly.lines) || !ly.lines.length || ly.type === "static") return null;
    const words = (list) => {
      const out = [];
      let prevRaw = "";
      (list || []).forEach((w, i) => {
        const raw = String(w.text != null ? w.text : "");
        const text = raw.trim();
        if (text) out.push({ text, start: Number(w.startMs) || 0, end: Number(w.endMs) || Number(w.startMs) || 0, joined: i > 0 && out.length > 0 && !/\s$/.test(prevRaw) && !/^\s/.test(raw) });
        prevRaw = raw;
      });
      return out;
    };
    const lines = [];
    for (const l of ly.lines) {
      const text = String(l.text || "").trim();
      if (!text) continue;
      if (l.isBackground && lines.length) { lines[lines.length - 1].background.push(words(l.words).length ? words(l.words) : [{ text, start: l.startMs, end: l.endMs, joined: false }]); continue; }
      lines.push({ start: Number(l.startMs) || 0, end: Number(l.endMs) || 0, text, words: words(l.words), background: [], opposite: false });
    }
    if (!lines.length) return null;
    return { synced: ly.type === "syllable" ? "word" : "line", lines, source: "Spotify" };
  }

  const keyOf = (track) => plain(track.artist || (track.artists || "")) + "|" + plain(track.title);

  window.SLP_HOST = {
    mode: MODE,
    async lyrics(track) {
      const key = keyOf(track);
      if (key !== st.lyricsKey && !keyMatches(key)) return null;
      await until(() => st.lyricsFinal || !!st.lyrics, 4000);
      return convertLyrics(st.lyrics);
    },
    async canvas() {
      await until(() => !!st.canvas && st.canvasFor === st.key, 3500);
      return st.canvasFor === st.key ? st.canvas : "";
    },
    async motionCover(track) {
      const key = plain(track.artist) + "|" + plain(track.album || track.title);
      await until(() => st.art && st.artFor === key, 6000);
      return st.art && st.artFor === key ? st.art : "";
    },
    settingsChanged(key, value) {
      if (!applying) send({ settings: { [key]: value } });
    },
  };
  // app.js's track keys use the lead artist; the relay's the whole credit.
  function keyMatches(key) {
    const [a, t] = key.split("|");
    const [ra, rt] = st.lyricsKey.split("|");
    return t === rt && (ra.startsWith(a) || a.startsWith(ra));
  }

  // ---- the Spicetify surface ---------------------------------------------------------------
  const position = () => Math.max(0, st.pos + (st.playing ? Date.now() - st.posAt : 0));
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
      togglePlay() { if (command("toggle")) { st.pos = position(); st.posAt = Date.now(); st.playing = !st.playing; } },
      play: () => command("play"),
      pause: () => command("pause"),
      next: () => command("next"),
      back: () => command("previous"),
      seek(ms) { if (command("seek " + Math.max(0, Math.round(ms)))) { st.pos = Math.max(0, ms); st.posAt = Date.now(); } },
      getShuffle: () => !!st.player.shuffle,
      getRepeat: () => st.player.repeat || 0,
      getHeart: () => !!st.player.liked,
      toggleShuffle() { command("shuffle"); st.player.shuffle = !st.player.shuffle; },
      toggleRepeat() { command("repeat"); st.player.repeat = ((st.player.repeat || 0) + 1) % 3; },
      toggleHeart() { command("like"); st.player.liked = !st.player.liked; },
    },
    CosmosAsync: { get: () => Promise.reject(new Error("not in Spotify")) },
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

  // Wallpaper Engine: its own settings panel has one switch per copy (opens nothing) - the real
  // settings live in the app; the audio level drives a subtle pulse.
  if (MODE === "wallpaper") {
    document.documentElement.classList.add("slp-in-wallpaper");
    if (typeof window.wallpaperRegisterAudioListener === "function") {
      let level = 0;
      window.wallpaperRegisterAudioListener((data) => {
        if (!data || !data.length) return;
        let sum = 0;
        for (let i = 0; i < 16; i++) sum += data[i] || 0;
        level += (Math.min(1, (sum / 16) * 2.2) - level) * 0.3;
        document.documentElement.style.setProperty("--slp-audio", level.toFixed(3));
      });
    }
  }

  document.documentElement.classList.add("slp-offline");
  connect();
})();
