// Spicy Lyrics Plus inside Spotify desktop (a Spicetify custom app).
//
// It follows what Spotify plays and shows its lyrics two ways:
// - Motion: the same cinematic motion lyrics page the phone app plays (SLP_MOTION_PAGE), with
//   its themes (SLP_THEMES) and Auto picking one by the song's genre (SLP_GENRE_POOLS);
// - Spicy: word-by-word lyrics, each word lighting up as it's sung, with a glow.
// Lyrics come from RMM Revival (Apple Music and Spicy Lyrics syncs, word by word), Spotify's own
// lyrics and LRCLIB; line-timed lyrics get word timing spread over each line.
// SLP_* constants are laid in by build.py.

/* global Spicetify, SLP_MOTION_PAGE, SLP_THEME_SCRIPT, SLP_THEMES, SLP_GENRE_POOLS */

const SLP = (() => {
	const STORE = "spicy-lyrics-plus:";
	const settings = {
		get(key, fallback) {
			try {
				const raw = localStorage.getItem(STORE + key);
				return raw == null ? fallback : JSON.parse(raw);
			} catch {
				return fallback;
			}
		},
		set(key, value) {
			try {
				localStorage.setItem(STORE + key, JSON.stringify(value));
			} catch {
				// Storage full or off: the choice lasts for this session only.
			}
		},
	};

	// -------------------------------------------------------------------------------------
	// Network: Spotify's own request path first (no CORS), the browser's after
	// -------------------------------------------------------------------------------------

	async function getJson(url, headers) {
		try {
			const body = await Spicetify.CosmosAsync.get(url, undefined, headers);
			if (body && typeof body === "object") return body;
			if (typeof body === "string" && body.trim()) return JSON.parse(body);
		} catch {
			// Fall through to fetch.
		}
		const response = await fetch(url, { headers });
		if (!response.ok) throw new Error(`HTTP ${response.status}`);
		return response.json();
	}

	const plain = (text) =>
		String(text || "")
			.toLowerCase()
			.normalize("NFKD")
			.replace(/[̀-ͯ]/g, "")
			.replace(/\s*[([].*?(feat|ft\.|with|remaster|version|edit|live|mix).*?[)\]]/g, "")
			.replace(/[^\p{L}\p{N}]+/gu, " ")
			.trim();
	const loose = (a, b) => {
		const x = plain(a);
		const y = plain(b);
		return !!x && !!y && (x === y || x.includes(y) || y.includes(x));
	};

	// -------------------------------------------------------------------------------------
	// Lyrics: { synced: "word" | "line" | "none", lines: [Line] }
	// Line = { start, end, text, words: [Word], background: [[Word]], opposite }
	// Word = { text, start, end, joined } (joined: no space before it)
	// -------------------------------------------------------------------------------------

	const seconds = (raw) => {
		if (raw == null || raw === "") return null;
		const text = String(raw).trim();
		if (text.endsWith("ms")) return parseFloat(text) / 1000;
		if (text.endsWith("s") && !text.includes(":")) return parseFloat(text);
		const parts = text.split(":").map(parseFloat);
		return parts.reduce((total, part) => total * 60 + part, 0);
	};

	function parseTtml(text) {
		const doc = new DOMParser().parseFromString(text, "application/xml");
		if (doc.querySelector("parsererror")) return null;
		const paragraphs = [...doc.getElementsByTagName("p")];
		if (!paragraphs.length) return null;
		const agentOf = (node) => node.getAttribute("ttm:agent") || node.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "agent");
		const roleOf = (node) => node.getAttribute("ttm:role") || node.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role");
		const firstAgent = paragraphs.map(agentOf).find(Boolean);
		let wordTimed = false;
		const lines = [];
		for (const p of paragraphs) {
			const words = [];
			const background = [];
			const collect = (node, into) => {
				let space = false;
				for (const child of node.childNodes) {
					if (child.nodeType === 3) {
						if (/\s/.test(child.nodeValue)) space = true;
						continue;
					}
					if (child.nodeType !== 1 || child.localName !== "span") continue;
					const role = roleOf(child);
					if (role === "x-bg") {
						const bg = [];
						collect(child, bg);
						if (bg.length) background.push(bg);
						continue;
					}
					if (role === "x-translation" || role === "x-roman") continue;
					const start = seconds(child.getAttribute("begin"));
					const end = seconds(child.getAttribute("end"));
					const value = child.textContent;
					if (start == null || !value.trim()) continue;
					wordTimed = true;
					into.push({
						text: value.trim(),
						start: start * 1000,
						end: (end ?? start) * 1000,
						joined: into.length > 0 && !space && !/^\s/.test(value),
					});
					space = /\s$/.test(value);
				}
			};
			collect(p, words);
			const start = (seconds(p.getAttribute("begin")) ?? 0) * 1000;
			const end = (seconds(p.getAttribute("end")) ?? 0) * 1000;
			const text = words.length ? joinWords(words) : p.textContent.trim();
			if (!text) continue;
			const agent = agentOf(p);
			lines.push({ start, end: Math.max(end, start), text, words, background, opposite: !!agent && !!firstAgent && agent !== firstAgent });
		}
		return lines.length ? { synced: wordTimed ? "word" : "line", lines } : null;
	}

	function parseLrc(text) {
		const lines = [];
		let wordTimed = false;
		for (const raw of String(text).split(/\r?\n/)) {
			const stamps = [...raw.matchAll(/\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)\]/g)];
			if (!stamps.length) continue;
			let body = raw.replace(/\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)\]/g, "");
			const words = [];
			const wordStamps = [...body.matchAll(/<(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)>([^<]*)/g)];
			if (wordStamps.length) {
				wordTimed = true;
				wordStamps.forEach((match, index) => {
					const start = (parseInt(match[1], 10) * 60 + parseFloat(match[2].replace(":", "."))) * 1000;
					const next = wordStamps[index + 1];
					const end = next ? (parseInt(next[1], 10) * 60 + parseFloat(next[2].replace(":", "."))) * 1000 : start + 600;
					const value = match[3];
					if (value.trim()) words.push({ text: value.trim(), start, end, joined: words.length > 0 && !/^\s/.test(value) });
				});
				body = words.map((w) => w.text).join(" ");
			}
			for (const stamp of stamps) {
				const start = (parseInt(stamp[1], 10) * 60 + parseFloat(stamp[2].replace(":", "."))) * 1000;
				lines.push({ start, end: start, text: body.trim(), words: words.slice(), background: [], opposite: false });
			}
		}
		lines.sort((a, b) => a.start - b.start);
		for (let index = 0; index < lines.length; index += 1) {
			const next = lines[index + 1];
			lines[index].end = next ? next.start : lines[index].start + 5000;
		}
		const sung = lines.filter((line) => line.text);
		return sung.length ? { synced: wordTimed ? "word" : "line", lines: sung } : null;
	}

	const joinWords = (words) => words.map((w, i) => (i > 0 && !w.joined ? " " : "") + w.text).join("");

	/**
	 * Word timing spread over a line-timed line: longer words take longer, and the line is sung
	 * at a natural pace, leaving a rest at its end instead of stretching the last word.
	 */
	function generateWords(line) {
		const parts = line.text.split(/\s+/).filter(Boolean);
		if (!parts.length) return [];
		const span = Math.max(400, line.end - line.start);
		const natural = parts.reduce((total, part) => total + 180 + part.length * 55, 0);
		const sung = Math.min(span * 0.92, Math.max(span * 0.5, natural));
		const weights = parts.map((part) => 1 + part.length);
		const total = weights.reduce((a, b) => a + b, 0);
		let at = line.start;
		return parts.map((part, index) => {
			const length = (sung * weights[index]) / total;
			const word = { text: part, start: at, end: at + length, joined: false };
			at += length;
			return word;
		});
	}

	async function itunesSong(track) {
		const term = encodeURIComponent(`${track.artist} ${track.title}`);
		const found = await getJson(`https://itunes.apple.com/search?entity=song&limit=10&term=${term}`);
		const songs = (found?.results || []).filter((item) => loose(item.artistName, track.artist));
		const same = songs.filter((item) => loose(item.trackName, track.title));
		const pool = same.length ? same : [];
		pool.sort((a, b) => Math.abs((a.trackTimeMillis || 0) - track.durationMs) - Math.abs((b.trackTimeMillis || 0) - track.durationMs));
		return { song: pool[0] || null, artistSongs: songs };
	}

	const sources = {
		/** Apple Music's lyrics and Spicy Lyrics' syncs, by Apple Music ID. */
		async rmm(track, apple) {
			if (!apple?.trackId) return null;
			const data = await getJson(`https://lyrics.rmmreviv.al/lyrics?id=${apple.trackId}`);
			const text = ["ttml", "content", "syncedLyrics", "lyrics"].map((key) => data?.[key]).find((value) => typeof value === "string" && value.trim());
			if (!text) return null;
			if (/<tt[\s>]/i.test(text)) return parseTtml(text);
			if (/\[\d{1,3}:\d{2}/.test(text)) return parseLrc(text);
			return null;
		},
		/** Spotify's own lyrics. */
		async spotify(track) {
			if (!track.id) return null;
			const data = await getJson(
				`https://spclient.wg.spotify.com/color-lyrics/v2/track/${track.id}?format=json&vocalRemoval=false&market=from_token`,
				{ "app-platform": "WebPlayer" },
			);
			const lyrics = data?.lyrics;
			if (!lyrics || lyrics.syncType !== "LINE_SYNCED") return null;
			const lines = (lyrics.lines || [])
				.map((line, index, all) => ({
					start: Number(line.startTimeMs),
					end: Number(all[index + 1]?.startTimeMs ?? Number(line.startTimeMs) + 5000),
					text: String(line.words || "").replace(/♪/g, "").trim(),
					words: [],
					background: [],
					opposite: false,
				}))
				.filter((line) => line.text);
			return lines.length ? { synced: "line", lines } : null;
		},
		/** LRCLIB. */
		async lrclib(track) {
			const query = new URLSearchParams({ artist_name: track.artist, track_name: track.title, album_name: track.album || "", duration: String(Math.round(track.durationMs / 1000)) });
			const data = await getJson(`https://lrclib.net/api/get?${query}`, { "Lrclib-Client": "Spicy Lyrics Plus desktop" });
			return data?.syncedLyrics ? parseLrc(data.syncedLyrics) : null;
		},
	};

	const lyricsCache = new Map();

	async function lyricsFor(track, apple) {
		if (lyricsCache.has(track.uri)) return lyricsCache.get(track.uri);
		let best = null;
		for (const name of ["rmm", "spotify", "lrclib"]) {
			try {
				const found = await sources[name](track, apple);
				if (found && found.synced === "word") {
					best = found;
					break;
				}
				if (found && !best) best = found;
			} catch {
				// Next source.
			}
		}
		const result = best || { synced: "none", lines: [] };
		for (const line of result.lines) if (!line.words.length) line.words = generateWords(line);
		lyricsCache.set(track.uri, result);
		if (lyricsCache.size > 30) lyricsCache.delete(lyricsCache.keys().next().value);
		return result;
	}

	// -------------------------------------------------------------------------------------
	// Genre and themes
	// -------------------------------------------------------------------------------------

	function genreOf(name) {
		const genre = String(name || "").trim().toLowerCase();
		if (!genre) return null;
		const has = (...words) => words.some((word) => genre.includes(word));
		if (has("k-pop", "j-pop", "kpop", "jpop", "c-pop", "mandopop", "cantopop", "anime", "j-rock", "k-rock", "vocaloid")) return "AsianPop";
		if (has("metal", "hardcore", "grindcore", "deathcore")) return "Metal";
		if (has("hip-hop", "hip hop", "rap", "trap", "drill", "grime")) return "HipHop";
		if (has("reggaeton", "latin", "urbano", "salsa", "bachata", "cumbia", "regional mexican", "sertanejo", "pop latino", "tropical", "flamenco", "brazilian", "mpb", "samba", "bossa")) return "Latin";
		if (has("r&b", "rnb", "soul", "funk", "neo-soul", "motown", "disco")) return "RnB";
		if (has("electronic", "dance", "house", "techno", "trance", "edm", "dubstep", "drum & bass", "drum and bass", "electronica", "garage", "hardstyle", "synthwave", "idm", "breakbeat")) return "Electronic";
		if (has("reggae", "dancehall", "ska", "dub", "afrobeat", "afrobeats", "afro-pop", "afropop", "amapiano", "soca", "calypso")) return "Reggae";
		if (has("gospel", "christian", "worship", "praise", "inspirational")) return "Gospel";
		if (has("jazz", "blues", "swing", "bebop", "big band", "lounge")) return "Jazz";
		if (has("classical", "soundtrack", "score", "opera", "orchestral", "instrumental", "new age", "ambient", "piano")) return "Classical";
		if (has("country", "folk", "americana", "bluegrass", "singer/songwriter", "singer-songwriter", "acoustic")) return "Country";
		if (has("alternative", "indie", "punk", "emo", "grunge", "shoegaze", "lo-fi", "lofi", "post-rock")) return "Alternative";
		if (has("rock")) return "Rock";
		if (has("pop", "children", "teen", "vocal", "holiday", "christmas")) return "Pop";
		return null;
	}

	const hash = (text) => {
		let value = 2166136261;
		for (const ch of String(text)) {
			value ^= ch.codePointAt(0);
			value = Math.imul(value, 16777619);
		}
		return value >>> 0;
	};

	/** The theme that plays: Auto picks one of the song's genre's, the same one every time for a song. */
	function themeFor(choice, genre, uri) {
		let id = choice;
		if (choice === "Auto") {
			const pool = (genre && SLP_GENRE_POOLS[genre]) || [];
			id = pool.length ? pool[hash(uri) % pool.length] : "Classic";
		}
		return SLP_THEMES.find((theme) => theme.id === id) || SLP_THEMES.find((theme) => theme.id === "Classic");
	}

	// -------------------------------------------------------------------------------------
	// The track playing
	// -------------------------------------------------------------------------------------

	function currentTrack() {
		const item = Spicetify.Player.data?.item || Spicetify.Player.data?.track;
		if (!item?.uri) return null;
		const meta = item.metadata || {};
		const artists = item.artists?.map((a) => a.name).filter(Boolean);
		return {
			uri: item.uri,
			id: item.uri.startsWith("spotify:track:") ? item.uri.split(":")[2] : "",
			title: item.name || meta.title || "",
			artist: (artists?.length ? artists[0] : meta.artist_name) || "",
			artists: artists?.length ? artists.join(", ") : meta.artist_name || "",
			album: item.album?.name || meta.album_title || "",
			cover: item.album?.images?.[0]?.url || meta.image_xlarge_url || meta.image_large_url || meta.image_url || "",
			durationMs: Number(item.duration?.milliseconds ?? meta.duration ?? 0),
		};
	}

	async function accentOf(uri) {
		try {
			const colors = await Spicetify.colorExtractor(uri);
			const hex = colors?.VIBRANT || colors?.LIGHT_VIBRANT || colors?.PROMINENT;
			return /^#[0-9a-f]{6}$/i.test(hex || "") ? hex : "";
		} catch {
			return "";
		}
	}

	const coverUrl = (raw) => {
		if (!raw) return "";
		if (raw.startsWith("spotify:image:")) return `https://i.scdn.co/image/${raw.split(":")[2]}`;
		return raw;
	};

	return { settings, lyricsFor, itunesSong, genreOf, themeFor, currentTrack, accentOf, coverUrl, joinWords };
})();

// -----------------------------------------------------------------------------------------
// Motion lyrics: the phone app's motion page in a frame
// -----------------------------------------------------------------------------------------

function motionSongJson(track, lyrics, accent, uri) {
	const syllables = (words) =>
		words.map((word, index) => ({
			text: word.text + (words[index + 1] && !words[index + 1].joined ? " " : ""),
			startTime: Math.round(word.start),
			endTime: Math.round(Math.max(word.end, word.start + 1)),
		}));
	return {
		uri,
		title: track.title,
		artist: track.artists,
		accent,
		language: "",
		lyrics: lyrics.lines.map((line) => {
			const lead = syllables(line.words);
			const out = { text: line.text, startTime: Math.round(line.start), endTime: Math.round(line.end), syllables: lead };
			if (line.background.length && lead.length) {
				out.vocals = {
					lead: { text: line.text, role: "lead", speaker: "", syllables: lead },
					background: line.background.map((words) => ({ text: SLP.joinWords(words), role: "background", speaker: "", syllables: syllables(words) })),
				};
			}
			return out;
		}),
	};
}

function MotionStage({ track, lyrics, accent, theme, playback, shade }) {
	const { React } = Spicetify;
	const frame = React.useRef(null);
	const ready = React.useRef(false);
	const sent = React.useRef({ song: null });

	const page = React.useMemo(() => {
		const bridge =
			"<script>window.ivLyricsMotionNative={postMessage:function(s){try{parent.__slpMotionMessage&&parent.__slpMotionMessage(s)}catch(e){}}};</script>";
		return SLP_MOTION_PAGE.includes("<head>") ? SLP_MOTION_PAGE.replace("<head>", "<head>" + bridge) : bridge + SLP_MOTION_PAGE;
	}, []);

	const host = () => frame.current?.contentWindow?.ivLyricsMotionHost;

	const look = React.useCallback(() => {
		const doc = frame.current?.contentDocument;
		if (!doc?.head) return;
		const edges =
			"linear-gradient(to bottom,transparent 0%,#000 8%,#000 92%,transparent 100%),linear-gradient(to right,transparent 0%,#000 6%,#000 94%,transparent 100%)";
		const mask = `-webkit-mask-image:${edges};mask-image:${edges};-webkit-mask-composite:source-in;mask-composite:intersect`;
		let style = doc.getElementById("spicy-look");
		if (!style) {
			style = doc.createElement("style");
			style.id = "spicy-look";
			doc.head.appendChild(style);
		}
		style.textContent = `.ivlyrics-motion-scrim{opacity:${shade}!important}.ivlyrics-motion-hud{display:none!important}#ivlyrics-motion-root{${mask}}`;
	}, [shade]);

	const pushSong = React.useCallback(() => {
		const h = host();
		if (!ready.current || !h) return;
		const themed = theme?.spec;
		const uri = themed ? `${track.uri}|${theme.id}` : track.uri;
		if (sent.current.song === uri + lyrics.lines.length) return;
		sent.current.song = uri + lyrics.lines.length;
		const win = frame.current.contentWindow;
		try {
			win.eval(SLP_THEME_SCRIPT);
			win.spicyMotionTheme = themed ? theme.spec : null;
		} catch {
			// The page plays its own direction.
		}
		h.setTrack(motionSongJson(track, lyrics, accent, uri));
	}, [track, lyrics, accent, theme]);

	React.useEffect(() => {
		window.__slpMotionMessage = (text) => {
			let message;
			try {
				message = JSON.parse(text);
			} catch {
				return;
			}
			const h = host();
			if (message.type === "ready") {
				ready.current = true;
				sent.current.song = null;
				h?.configure({
					labels: { directing: "Directing…", failed: "These lyrics can't play as motion lyrics." },
					ai: false,
					background: true,
					insets: { top: 24, right: 24, bottom: 24, left: 24 },
				});
				look();
				pushSong();
			} else if (message.type === "cacheGet") {
				h?.resolve(message.id, { ok: true, value: SLP.settings.get(`motion-cache:${message.key}`, "") || "" });
			} else if (message.type === "cacheSet") {
				SLP.settings.set(`motion-cache:${message.key}`, message.value);
			} else if (message.type === "direct") {
				h?.resolve(message.id, { ok: false, error: "No AI direction on desktop" });
			}
		};
		return () => {
			window.__slpMotionMessage = null;
		};
	}, [look, pushSong]);

	React.useEffect(() => {
		pushSong();
	}, [pushSong]);

	React.useEffect(() => {
		look();
	}, [look]);

	React.useEffect(() => {
		host()?.setPlayback({ position: Math.round(playback.position), playing: playback.playing, rate: 1 });
	}, [playback]);

	return React.createElement("iframe", {
		ref: frame,
		srcDoc: page,
		title: "Motion lyrics",
		style: { position: "absolute", inset: 0, width: "100%", height: "100%", border: "0", background: "transparent" },
	});
}

// -----------------------------------------------------------------------------------------
// Spicy: word by word
// -----------------------------------------------------------------------------------------

function SpicyStage({ lyrics, position, onSeek }) {
	const { React } = Spicetify;
	const box = React.useRef(null);
	const lines = lyrics.lines;
	let current = -1;
	for (let index = 0; index < lines.length; index += 1) if (lines[index].start <= position) current = index;

	React.useEffect(() => {
		const node = box.current?.querySelector(`[data-line="${current}"]`);
		node?.scrollIntoView({ behavior: "smooth", block: "center" });
	}, [current]);

	const word = (w, i, words, key) => {
		const span = Math.max(1, w.end - w.start);
		const progress = Math.min(1, Math.max(0, (position - w.start) / span));
		const singing = progress > 0 && progress < 1;
		return React.createElement(
			"span",
			{
				key: key + i,
				className: "slp-word" + (singing ? " slp-singing" : ""),
				style: {
					"--p": `${(progress * 100).toFixed(1)}%`,
					transform: `translateY(${singing ? -2 * Math.sin(progress * Math.PI) : 0}px)`,
				},
			},
			(i > 0 && !w.joined ? " " : "") + w.text,
		);
	};

	return React.createElement(
		"div",
		{ ref: box, className: "slp-spicy" },
		lines.map((line, index) =>
			React.createElement(
				"div",
				{
					key: index,
					"data-line": index,
					className:
						"slp-line" +
						(index === current ? " slp-active" : index < current ? " slp-past" : "") +
						(line.opposite ? " slp-opposite" : ""),
					onClick: () => onSeek(line.start),
				},
				index === current ? line.words.map((w, i) => word(w, i, line.words, "w")) : line.text,
				line.background.length
					? React.createElement(
							"div",
							{ className: "slp-bg" },
							line.background.map((words, b) =>
								React.createElement(
									"span",
									{ key: b },
									index === current ? words.map((w, i) => word(w, i, words, `b${b}`)) : SLP.joinWords(words),
								),
							),
						)
					: null,
			),
		),
	);
}

const SLP_CSS = `
.slp-root{position:relative;height:100%;min-height:calc(100vh - 140px);overflow:hidden;color:#fff;font-family:var(--font-family,"SpotifyMixUI",sans-serif)}
.slp-backdrop{position:absolute;inset:-10%;background-size:cover;background-position:center;filter:blur(60px) saturate(1.6) brightness(.55);transform:scale(1.2);transition:background-image .8s}
.slp-toolbar{position:absolute;top:12px;right:16px;z-index:5;display:flex;gap:8px;align-items:center;background:rgba(0,0,0,.35);backdrop-filter:blur(12px);border-radius:999px;padding:6px 10px}
.slp-toolbar select,.slp-toolbar button{background:transparent;color:#fff;border:1px solid rgba(255,255,255,.25);border-radius:999px;padding:4px 10px;font-size:13px;cursor:pointer}
.slp-toolbar select option{color:#000}
.slp-toolbar button.slp-on{background:#fff;color:#000}
.slp-toolbar input{width:70px}
.slp-spicy{position:absolute;inset:0;overflow-y:auto;padding:40vh 8vw;scrollbar-width:none;-webkit-mask-image:linear-gradient(transparent,#000 18%,#000 82%,transparent)}
.slp-spicy::-webkit-scrollbar{display:none}
.slp-line{font-size:clamp(28px,3.4vw,56px);font-weight:800;line-height:1.18;letter-spacing:-.02em;margin:0 0 .55em;color:rgba(255,255,255,.35);cursor:pointer;transition:color .4s,filter .4s,transform .4s;filter:blur(1.5px);transform-origin:left center}
.slp-line.slp-past{color:rgba(255,255,255,.5)}
.slp-line.slp-active{filter:none;color:rgba(255,255,255,.35);transform:scale(1.02)}
.slp-line.slp-opposite{text-align:right;transform-origin:right center}
.slp-word{display:inline-block;white-space:pre;background:linear-gradient(90deg,#fff var(--p),rgba(255,255,255,.35) calc(var(--p) + 8%));-webkit-background-clip:text;background-clip:text;color:transparent;transition:transform .12s linear}
.slp-singing{text-shadow:0 0 18px rgba(255,255,255,.45)}
.slp-bg{font-size:.55em;font-weight:700;opacity:.8;margin-top:.15em}
.slp-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:22px;color:rgba(255,255,255,.7);text-align:center;padding:0 10vw}
`;

// -----------------------------------------------------------------------------------------
// The page
// -----------------------------------------------------------------------------------------

function render() {
	const { React } = Spicetify;
	return React.createElement(SpicyLyricsPlus, null);
}

function SpicyLyricsPlus() {
	const { React } = Spicetify;
	const [track, setTrack] = React.useState(SLP.currentTrack());
	const [lyrics, setLyrics] = React.useState(null);
	const [accent, setAccent] = React.useState("");
	const [genre, setGenre] = React.useState(null);
	const [mode, setMode] = React.useState(SLP.settings.get("mode", "motion"));
	const [themeChoice, setThemeChoice] = React.useState(SLP.settings.get("theme", "Auto"));
	const [shade, setShade] = React.useState(SLP.settings.get("shade", 0.35));
	const [playback, setPlayback] = React.useState({ position: 0, playing: false });

	React.useEffect(() => {
		const onSong = () => setTrack(SLP.currentTrack());
		Spicetify.Player.addEventListener("songchange", onSong);
		let frame = 0;
		let last = 0;
		const tick = (now) => {
			frame = requestAnimationFrame(tick);
			if (now - last < 33) return;
			last = now;
			setPlayback({ position: Spicetify.Player.getProgress(), playing: Spicetify.Player.isPlaying() });
		};
		frame = requestAnimationFrame(tick);
		return () => {
			Spicetify.Player.removeEventListener("songchange", onSong);
			cancelAnimationFrame(frame);
		};
	}, []);

	React.useEffect(() => {
		let alive = true;
		setLyrics(null);
		setGenre(null);
		setAccent("");
		if (!track) return undefined;
		(async () => {
			let apple = null;
			try {
				const found = await SLP.itunesSong(track);
				apple = found.song;
				const name = found.song?.primaryGenreName || found.artistSongs[0]?.primaryGenreName;
				if (alive) setGenre(SLP.genreOf(name));
			} catch {
				// No genre: Auto plays Classic.
			}
			SLP.accentOf(track.uri).then((hex) => alive && setAccent(hex));
			const found = await SLP.lyricsFor(track, apple);
			if (alive) setLyrics(found);
		})();
		return () => {
			alive = false;
		};
	}, [track?.uri]);

	const choose = (key, setter) => (value) => {
		setter(value);
		SLP.settings.set(key, value);
	};
	const theme = SLP.themeFor(themeChoice, genre, track?.uri || "");
	const cover = SLP.coverUrl(track?.cover);
	const hasLyrics = lyrics && lyrics.lines.length > 0;

	const toolbar = React.createElement(
		"div",
		{ className: "slp-toolbar" },
		React.createElement("button", { className: mode === "motion" ? "slp-on" : "", onClick: () => choose("mode", setMode)("motion") }, "Motion"),
		React.createElement("button", { className: mode === "spicy" ? "slp-on" : "", onClick: () => choose("mode", setMode)("spicy") }, "Spicy"),
		mode === "motion"
			? React.createElement(
					"select",
					{ value: themeChoice, onChange: (e) => choose("theme", setThemeChoice)(e.target.value), title: "Motion lyrics theme" },
					SLP_THEMES.map((t) =>
						React.createElement("option", { key: t.id, value: t.id }, t.id === "Auto" && themeChoice === "Auto" ? `Auto: ${theme.label}` : t.label),
					),
				)
			: null,
		mode === "motion"
			? React.createElement("input", {
					type: "range",
					min: 0,
					max: 1,
					step: 0.05,
					value: shade,
					title: "Shade",
					onChange: (e) => choose("shade", setShade)(Number(e.target.value)),
				})
			: null,
	);

	let stage;
	if (!track) stage = React.createElement("div", { className: "slp-empty" }, "Play something on Spotify.");
	else if (!lyrics) stage = React.createElement("div", { className: "slp-empty" }, "Finding lyrics…");
	else if (!hasLyrics) stage = React.createElement("div", { className: "slp-empty" }, "No synced lyrics for this song.");
	else if (mode === "motion")
		stage = React.createElement(MotionStage, { key: track.uri, track, lyrics, accent, theme, playback, shade });
	else stage = React.createElement(SpicyStage, { lyrics, position: playback.position, onSeek: (ms) => Spicetify.Player.seek(ms) });

	return React.createElement(
		"div",
		{ className: "slp-root" },
		React.createElement("style", null, SLP_CSS),
		cover ? React.createElement("div", { className: "slp-backdrop", style: { backgroundImage: `url("${cover}")` } }) : null,
		stage,
		toolbar,
	);
}
