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
				if (found) found.source = { rmm: "Apple Music / Spicy Lyrics (via RMM Revival)", spotify: "Spotify", lrclib: "LRCLIB" }[name];
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
			// No genre found: still a different look per song, not Classic every time.
			const all = SLP_THEMES.filter((theme) => theme.id !== "Auto" && theme.spec).map((theme) => theme.id);
			const from = pool.length ? pool : all;
			id = from.length ? from[hash(uri) % from.length] : "Classic";
		}
		return SLP_THEMES.find((theme) => theme.id === id) || SLP_THEMES.find((theme) => theme.id === "Classic");
	}

	/**
	 * The song's genre when Apple Music doesn't have the song: the artist's genres on Spotify,
	 * then the artist's main genre on Apple Music.
	 */
	async function artistGenre(track) {
		if (track.artistId) {
			try {
				const artist = await getJson(`https://api.spotify.com/v1/artists/${track.artistId}`);
				const genre = genreOf((artist?.genres || []).join(" "));
				if (genre) return genre;
			} catch {
				// Next.
			}
		}
		try {
			const term = encodeURIComponent(track.artist);
			const found = await getJson(`https://itunes.apple.com/search?entity=musicArtist&limit=5&term=${term}`);
			const artist = (found?.results || []).find((item) => loose(item.artistName, track.artist));
			return genreOf(artist?.primaryGenreName);
		} catch {
			return null;
		}
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
			artistId: (item.artists?.[0]?.uri || meta.artist_uri || "").split(":")[2] || "",
			title: item.name || meta.title || "",
			artist: (artists?.length ? artists[0] : meta.artist_name) || "",
			artists: artists?.length ? artists.join(", ") : meta.artist_name || "",
			album: item.album?.name || meta.album_title || "",
			cover:
				item.album?.images?.[0]?.url ||
				item.images?.[0]?.url ||
				meta.image_xlarge_url ||
				meta.image_large_url ||
				meta.image_url ||
				meta.image_small_url ||
				"",
			durationMs: Number(item.duration?.milliseconds ?? meta.duration ?? 0),
		};
	}

	/** The cover's colour for the motion page: Spotify's own extractor, else the cover's most vivid tone. */
	async function accentOf(uri, cover) {
		try {
			const colors = await Spicetify.colorExtractor(uri);
			const hex = colors?.VIBRANT || colors?.LIGHT_VIBRANT || colors?.PROMINENT || colors?.vibrant;
			if (/^#[0-9a-f]{6}$/i.test(hex || "")) return hex;
		} catch {
			// Read the cover ourselves.
		}
		if (!cover) return "";
		try {
			const image = await new Promise((resolve, reject) => {
				const img = new Image();
				img.crossOrigin = "anonymous";
				img.onload = () => resolve(img);
				img.onerror = reject;
				img.src = cover;
			});
			const canvas = document.createElement("canvas");
			canvas.width = canvas.height = 32;
			const ctx = canvas.getContext("2d");
			ctx.drawImage(image, 0, 0, 32, 32);
			const data = ctx.getImageData(0, 0, 32, 32).data;
			let best = null;
			let bestScore = -1;
			for (let i = 0; i < data.length; i += 4) {
				const [r, g, b] = [data[i], data[i + 1], data[i + 2]];
				const max = Math.max(r, g, b);
				const min = Math.min(r, g, b);
				const sat = max ? (max - min) / max : 0;
				const light = max / 255;
				const score = sat * (1 - Math.abs(light - 0.65));
				if (score > bestScore) {
					bestScore = score;
					best = [r, g, b];
				}
			}
			if (!best) return "";
			return "#" + best.map((v) => v.toString(16).padStart(2, "0")).join("");
		} catch {
			return "";
		}
	}

	const translations = new Map();

	/** Each line in [target] (Google Translate's free endpoint), empty where it is already that language. */
	async function translate(lines, target) {
		const key = target + "|" + lines.map((l) => l.text).join("\n");
		if (translations.has(key)) return translations.get(key);
		const out = [];
		for (let start = 0; start < lines.length; start += 40) {
			const chunk = lines.slice(start, start + 40).map((l) => l.text.replace(/\n/g, " "));
			const query = new URLSearchParams({ client: "gtx", sl: "auto", tl: target, dt: "t", q: chunk.join("\n") });
			const data = await getJson(`https://translate.googleapis.com/translate_a/single?${query}`);
			const text = (data?.[0] || []).map((part) => part?.[0] || "").join("");
			const parts = text.split("\n");
			const source = data?.[2];
			chunk.forEach((original, index) => {
				const translated = (parts[index] || "").trim();
				out.push(source === target || plain(translated) === plain(original) ? "" : translated);
			});
		}
		translations.set(key, out);
		return out;
	}

	const coverUrl = (raw) => {
		if (!raw) return "";
		if (raw.startsWith("spotify:image:")) return `https://i.scdn.co/image/${raw.split(":")[2]}`;
		return raw;
	};

	return { settings, lyricsFor, itunesSong, genreOf, artistGenre, themeFor, currentTrack, accentOf, coverUrl, joinWords, translate };
})();

// -----------------------------------------------------------------------------------------
// Motion lyrics: the phone app's motion page in a frame
// -----------------------------------------------------------------------------------------

function motionSongJson(track, lyrics, accent, uri, translated) {
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
		lyrics: lyrics.lines.map((line, index) => {
			const lead = syllables(line.words);
			const out = { text: line.text, startTime: Math.round(line.start), endTime: Math.round(line.end), syllables: lead };
			if (translated?.[index]) out.text2 = translated[index];
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

function MotionStage({ track, lyrics, accent, theme, playback, shade, letterbox, translated, onBackdrop }) {
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

	// The page sits on the app's own background (the cover, moved by the page's camera), with
	// its scrim at the shade chosen and its letterbox bars only if wanted.
	const look = React.useCallback(() => {
		const doc = frame.current?.contentDocument;
		if (!doc?.head) return;
		let style = doc.getElementById("spicy-look");
		if (!style) {
			style = doc.createElement("style");
			style.id = "spicy-look";
			doc.head.appendChild(style);
		}
		style.textContent =
			`html,body{background:transparent!important}` +
			`.ivlyrics-motion-scrim{opacity:${shade}!important}` +
			`.ivlyrics-motion-hud{display:none!important}` +
			(letterbox ? "" : `.ivlyrics-motion-bars{display:none!important}`);
	}, [shade, letterbox]);

	const pushSong = React.useCallback(() => {
		const h = host();
		if (!ready.current || !h) return;
		const themed = theme?.spec;
		const uri = themed ? `${track.uri}|${theme.id}` : track.uri;
		const key = uri + lyrics.lines.length + (translated ? translated.length : 0) + accent;
		if (sent.current.song === key) return;
		sent.current.song = key;
		const win = frame.current.contentWindow;
		try {
			win.eval(SLP_THEME_SCRIPT);
			win.spicyMotionTheme = themed ? theme.spec : null;
		} catch {
			// The page plays its own direction.
		}
		h.setTrack(motionSongJson(track, lyrics, accent, uri, translated));
	}, [track, lyrics, accent, theme, translated]);

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
					insets: { top: 64, right: 24, bottom: 24, left: 24 },
				});
				look();
				pushSong();
			} else if (message.type === "background") {
				onBackdrop(message);
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
	}, [look, pushSong, onBackdrop]);

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
		allowtransparency: "true",
		style: { position: "absolute", inset: 0, width: "100%", height: "100%", border: "0", background: "transparent", colorScheme: "normal" },
	});
}

// -----------------------------------------------------------------------------------------
// Spicy: word by word
// -----------------------------------------------------------------------------------------

/** The lines with interludes (• • •) wherever the singing stops for a while. */
function withInterludes(lines) {
	const out = [];
	const GAP = 5000;
	if (lines.length && lines[0].start > GAP) out.push({ interlude: true, start: 0, end: lines[0].start - 250 });
	lines.forEach((line, index) => {
		out.push({ ...line, index });
		const next = lines[index + 1];
		const endOfThis = Math.max(line.end, line.words.length ? line.words[line.words.length - 1].end : line.end);
		if (next && next.start - endOfThis > GAP) out.push({ interlude: true, start: endOfThis + 200, end: next.start - 250 });
	});
	return out;
}

function SpicyStage({ lyrics, position, onSeek, translated, scale }) {
	const { React } = Spicetify;
	const box = React.useRef(null);
	const rows = React.useMemo(() => withInterludes(lyrics.lines), [lyrics]);
	let current = -1;
	for (let index = 0; index < rows.length; index += 1) if (rows[index].start <= position) current = index;
	if (current >= 0 && rows[current].interlude && position > rows[current].end) current = Math.min(rows.length - 1, current);

	React.useEffect(() => {
		const node = box.current?.querySelector(`[data-row="${current}"]`);
		node?.scrollIntoView({ behavior: "smooth", block: "center" });
	}, [current]);

	const word = (w, i, key) => {
		const span = Math.max(1, w.end - w.start);
		const progress = Math.min(1, Math.max(0, (position - w.start) / span));
		const singing = progress > 0 && progress < 1;
		const long = span > 900;
		return React.createElement(
			"span",
			{
				key: key + i,
				className: "slp-word" + (singing ? " slp-singing" : "") + (long && singing ? " slp-long" : ""),
				style: {
					"--p": `${(progress * 100).toFixed(1)}%`,
					transform: `translateY(${singing ? -3 * Math.sin(progress * Math.PI) : 0}px) scale(${long && singing ? 1 + 0.06 * Math.sin(progress * Math.PI) : 1})`,
				},
			},
			(i > 0 && !w.joined ? " " : "") + w.text,
		);
	};

	return React.createElement(
		"div",
		{ ref: box, className: "slp-spicy", style: { fontSize: `calc(clamp(28px, 3.4vw, 56px) * ${scale})` } },
		rows.map((row, r) => {
			const state = r === current ? " slp-active" : r < current ? " slp-past" : "";
			if (row.interlude) {
				const progress = Math.min(1, Math.max(0, (position - row.start) / Math.max(1, row.end - row.start)));
				return React.createElement(
					"div",
					{ key: r, "data-row": r, className: "slp-line slp-interlude" + state },
					[0, 1, 2].map((d) =>
						React.createElement("span", {
							key: d,
							className: "slp-dot",
							style: { opacity: r === current ? 0.3 + 0.7 * Math.min(1, Math.max(0, progress * 3 - d)) : 0.35 },
						}),
					),
				);
			}
			const active = r === current;
			return React.createElement(
				"div",
				{
					key: r,
					"data-row": r,
					className: "slp-line" + state + (row.opposite ? " slp-opposite" : ""),
					onClick: () => onSeek(row.start),
				},
				active ? row.words.map((w, i) => word(w, i, "w")) : row.text,
				row.background.length
					? React.createElement(
							"div",
							{ className: "slp-bg" },
							row.background.map((words, b) =>
								React.createElement("span", { key: b }, active ? words.map((w, i) => word(w, i, `b${b}`)) : SLP.joinWords(words)),
							),
						)
					: null,
				translated?.[row.index] ? React.createElement("div", { className: "slp-translation" }, translated[row.index]) : null,
			);
		}),
	);
}

const SLP_CSS = `
.slp-root{position:relative;height:100%;min-height:calc(100vh - 140px);overflow:hidden;color:#fff;background:#0b0b10;border-radius:inherit;font-family:var(--font-family,"SpotifyMixUI",sans-serif)}
.slp-root:fullscreen{min-height:100vh}
.slp-base{position:absolute;inset:0;background:radial-gradient(120% 90% at 30% 20%,var(--slp-accent,#3a3a48) 0%,transparent 60%),radial-gradient(100% 80% at 80% 90%,color-mix(in srgb,var(--slp-accent,#3a3a48) 60%,#000) 0%,transparent 65%),#0b0b10;opacity:.55;transition:background .8s}
.slp-backdrop{position:absolute;inset:-25%;background-size:cover;background-position:center;filter:blur(70px) saturate(1.5) brightness(.6);transform:rotate(var(--slp-rot,0deg)) scale(calc(1.25 * var(--slp-scale,1))) translate(var(--slp-x,0px),var(--slp-y,0px));transition:transform .25s linear,background-image .8s;will-change:transform}
.slp-toolbar{position:absolute;top:12px;right:16px;left:16px;z-index:5;display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px;align-items:center;pointer-events:none}
.slp-toolbar>*{pointer-events:auto}
.slp-pill{display:flex;gap:6px;align-items:center;background:rgba(0,0,0,.4);backdrop-filter:blur(14px);border:1px solid rgba(255,255,255,.12);border-radius:999px;padding:5px 8px}
.slp-pill select,.slp-pill button{background:transparent;color:#fff;border:1px solid rgba(255,255,255,.22);border-radius:999px;padding:4px 10px;font-size:12.5px;cursor:pointer}
.slp-pill select option{color:#000}
.slp-pill button.slp-on{background:#fff;color:#000}
.slp-pill label{font-size:11px;opacity:.75;display:flex;gap:6px;align-items:center}
.slp-pill input[type=range]{width:72px;accent-color:var(--slp-accent,#fff)}
.slp-credit{position:absolute;left:18px;bottom:12px;z-index:4;font-size:11px;opacity:.55}
.slp-spicy{position:absolute;inset:0;overflow-y:auto;padding:42vh 8vw;scrollbar-width:none;-webkit-mask-image:linear-gradient(transparent,#000 18%,#000 82%,transparent)}
.slp-spicy::-webkit-scrollbar{display:none}
.slp-line{font-weight:800;line-height:1.18;letter-spacing:-.02em;margin:0 0 .55em;color:rgba(255,255,255,.35);cursor:pointer;transition:color .4s,filter .5s,transform .5s,opacity .5s;filter:blur(2px);transform-origin:left center;opacity:.8}
.slp-line.slp-past{color:rgba(255,255,255,.5);filter:blur(1.2px)}
.slp-line.slp-active{filter:none;opacity:1;transform:scale(1.03)}
.slp-line.slp-opposite{text-align:right;transform-origin:right center}
.slp-word{display:inline-block;white-space:pre;background:linear-gradient(90deg,#fff var(--p),rgba(255,255,255,.35) calc(var(--p) + 10%));-webkit-background-clip:text;background-clip:text;color:transparent;transition:transform .12s linear}
.slp-singing{filter:drop-shadow(0 0 10px rgba(255,255,255,.45))}
.slp-long{filter:drop-shadow(0 0 18px color-mix(in srgb,var(--slp-accent,#fff) 70%,#fff))}
.slp-bg{font-size:.55em;font-weight:700;opacity:.8;margin-top:.15em}
.slp-translation{font-size:.48em;font-weight:600;color:rgba(255,255,255,.65);margin-top:.2em;filter:none}
.slp-interlude{display:flex;gap:.35em;cursor:default;padding:.2em 0}
.slp-opposite.slp-interlude{justify-content:flex-end}
.slp-dot{width:.32em;height:.32em;border-radius:50%;background:#fff;transition:opacity .2s;box-shadow:0 0 12px rgba(255,255,255,.5)}
.slp-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:22px;color:rgba(255,255,255,.75);text-align:center;padding:0 10vw;z-index:1}
`;

// -----------------------------------------------------------------------------------------
// The page
// -----------------------------------------------------------------------------------------

function render() {
	const { React } = Spicetify;
	return React.createElement(SpicyLyricsPlus, null);
}

const TRANSLATE_TARGETS = [
	["", "No translation"],
	["en", "English"],
	["fa", "فارسی"],
	["es", "Español"],
	["fr", "Français"],
	["de", "Deutsch"],
	["pt", "Português"],
	["ar", "العربية"],
	["ko", "한국어"],
	["ja", "日本語"],
	["zh-CN", "中文"],
	["ru", "Русский"],
	["tr", "Türkçe"],
];

function SpicyLyricsPlus() {
	const { React } = Spicetify;
	const root = React.useRef(null);
	const backdrop = React.useRef(null);
	const [track, setTrack] = React.useState(SLP.currentTrack());
	const [lyrics, setLyrics] = React.useState(null);
	const [accent, setAccent] = React.useState("");
	const [genre, setGenre] = React.useState(null);
	const [translated, setTranslated] = React.useState(null);
	const [mode, setMode] = React.useState(SLP.settings.get("mode", "motion"));
	const [themeChoice, setThemeChoice] = React.useState(SLP.settings.get("theme", "Auto"));
	const [shade, setShade] = React.useState(SLP.settings.get("shade", 0.35));
	const [letterbox, setLetterbox] = React.useState(SLP.settings.get("letterbox", false));
	const [target, setTarget] = React.useState(SLP.settings.get("translate", ""));
	const [offset, setOffset] = React.useState(SLP.settings.get("offset", 0));
	const [scale, setScale] = React.useState(SLP.settings.get("scale", 1));
	const [playback, setPlayback] = React.useState({ position: 0, playing: false });
	const official = typeof window._spicy_lyrics === "object";

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
		setTranslated(null);
		if (!track) return undefined;
		(async () => {
			let apple = null;
			let genre = null;
			try {
				const found = await SLP.itunesSong(track);
				apple = found.song;
				genre = SLP.genreOf(found.song?.primaryGenreName || found.artistSongs[0]?.primaryGenreName);
			} catch {
				// Ask about the artist instead.
			}
			if (!genre) genre = await SLP.artistGenre(track);
			if (alive) setGenre(genre);
			SLP.accentOf(track.uri, SLP.coverUrl(track.cover)).then((hex) => alive && setAccent(hex));
			const found = await SLP.lyricsFor(track, apple);
			if (alive) setLyrics(found);
		})();
		return () => {
			alive = false;
		};
	}, [track?.uri]);

	React.useEffect(() => {
		let alive = true;
		setTranslated(null);
		if (!target || !lyrics?.lines.length) return undefined;
		SLP.translate(lyrics.lines, target)
			.then((lines) => alive && setTranslated(lines.some(Boolean) ? lines : null))
			.catch(() => {});
		return () => {
			alive = false;
		};
	}, [lyrics, target]);

	const onBackdrop = React.useCallback((frame) => {
		const node = backdrop.current;
		if (!node) return;
		node.style.setProperty("--slp-rot", `${frame.rotate || 0}deg`);
		node.style.setProperty("--slp-scale", String(frame.scale || 1));
		node.style.setProperty("--slp-x", `${frame.x || 0}px`);
		node.style.setProperty("--slp-y", `${frame.y || 0}px`);
	}, []);

	const choose = (key, setter) => (value) => {
		setter(value);
		SLP.settings.set(key, value);
	};
	const theme = SLP.themeFor(themeChoice, genre, track?.uri || "");
	const cover = SLP.coverUrl(track?.cover);
	const hasLyrics = lyrics && lyrics.lines.length > 0;
	const position = playback.position + offset;
	const e = React.createElement;

	const toolbar = e(
		"div",
		{ className: "slp-toolbar" },
		e(
			"div",
			{ className: "slp-pill" },
			e("button", { className: mode === "motion" ? "slp-on" : "", onClick: () => choose("mode", setMode)("motion") }, "Motion"),
			e("button", { className: mode === "spicy" ? "slp-on" : "", onClick: () => choose("mode", setMode)("spicy") }, "Word by word"),
			official
				? e("button", { title: "Open the official Spicy Lyrics", onClick: () => Spicetify.Platform.History.push({ pathname: "/SpicyLyrics" }) }, "Spicy Lyrics ↗")
				: null,
		),
		mode === "motion"
			? e(
					"div",
					{ className: "slp-pill" },
					e(
						"select",
						{ value: themeChoice, onChange: (ev) => choose("theme", setThemeChoice)(ev.target.value), title: "Motion lyrics theme" },
						SLP_THEMES.map((t) => e("option", { key: t.id, value: t.id }, t.id === "Auto" && themeChoice === "Auto" ? `Auto: ${theme.label}` : t.label)),
					),
					e("label", null, "Shade", e("input", { type: "range", min: 0, max: 1, step: 0.05, value: shade, onChange: (ev) => choose("shade", setShade)(Number(ev.target.value)) })),
					e("button", { className: letterbox ? "slp-on" : "", title: "Cinematic letterbox bars", onClick: () => choose("letterbox", setLetterbox)(!letterbox) }, "Bars"),
				)
			: e(
					"div",
					{ className: "slp-pill" },
					e("label", null, "Size", e("input", { type: "range", min: 0.7, max: 1.5, step: 0.05, value: scale, onChange: (ev) => choose("scale", setScale)(Number(ev.target.value)) })),
				),
		e(
			"div",
			{ className: "slp-pill" },
			e(
				"select",
				{ value: target, onChange: (ev) => choose("translate", setTarget)(ev.target.value), title: "Translate the lyrics" },
				TRANSLATE_TARGETS.map(([code, name]) => e("option", { key: code, value: code }, name)),
			),
			e("button", { title: "Lyrics earlier", onClick: () => choose("offset", setOffset)(offset - 100) }, "−"),
			e("label", { title: "Lyrics offset" }, `${offset > 0 ? "+" : ""}${(offset / 1000).toFixed(1)}s`),
			e("button", { title: "Lyrics later", onClick: () => choose("offset", setOffset)(offset + 100) }, "+"),
			e(
				"button",
				{
					title: "Full screen",
					onClick: () => (document.fullscreenElement ? document.exitFullscreen() : root.current?.requestFullscreen?.()),
				},
				"⛶",
			),
		),
	);

	let stage;
	if (!track) stage = e("div", { className: "slp-empty" }, "Play something on Spotify.");
	else if (!lyrics) stage = e("div", { className: "slp-empty" }, "Finding lyrics…");
	else if (!hasLyrics) stage = e("div", { className: "slp-empty" }, "No synced lyrics for this song.");
	else if (mode === "motion")
		stage = e(MotionStage, {
			key: track.uri,
			track,
			lyrics,
			accent,
			theme,
			playback: { position, playing: playback.playing },
			shade,
			letterbox,
			translated,
			onBackdrop,
		});
	else stage = e(SpicyStage, { lyrics, position, translated, scale, onSeek: (ms) => Spicetify.Player.seek(Math.max(0, ms - offset)) });

	return e(
		"div",
		{ ref: root, className: "slp-root", style: accent ? { "--slp-accent": accent } : null },
		e("style", null, SLP_CSS),
		e("div", { className: "slp-base" }),
		cover ? e("div", { ref: backdrop, className: "slp-backdrop", style: { backgroundImage: `url("${cover}")` } }) : null,
		stage,
		hasLyrics && lyrics.source ? e("div", { className: "slp-credit" }, `Lyrics: ${lyrics.source}${lyrics.synced === "line" ? " · word timing generated" : ""}`) : null,
		toolbar,
	);
}
