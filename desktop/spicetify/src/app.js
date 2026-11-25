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
			// Outside Spotify (wallpaper, desktop app) the host shares the choice with the other copies.
			if (!key.startsWith("motion-cache:")) window.SLP_HOST?.settingsChanged?.(key, value);
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
		if (!lines.length) return null;
		const writers = Array.from(doc.getElementsByTagName("songwriter"))
			.map((node) => node.textContent.trim())
			.filter(Boolean);
		return { synced: wordTimed ? "word" : "line", lines, writers: Array.from(new Set(writers)) };
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

	const SOURCE_NAMES = ["rmm", "spotify", "lrclib"];

	/** The best lyrics, skipping the sources in [skip] ("Wrong lyrics?" asks the next one). */
	async function lyricsFor(track, apple, skip = []) {
		const cacheKey = track.uri + "|" + skip.join(",");
		if (lyricsCache.has(cacheKey)) return lyricsCache.get(cacheKey);
		let best = null;
		// Outside Spotify, the host may have Spotify's own word-by-word lyrics (through Spicetify).
		if (!skip.includes("spotify") && window.SLP_HOST?.lyrics) {
			try {
				const hosted = await window.SLP_HOST.lyrics(track);
				if (hosted?.lines?.length) {
					hosted.sourceId = "spotify";
					if (hosted.synced === "word") {
						lyricsCache.set(cacheKey, hosted);
						return hosted;
					}
					best = hosted;
				}
			} catch {
				// Ask the usual sources.
			}
		}
		for (const name of SOURCE_NAMES.filter((n) => !skip.includes(n))) {
			try {
				const found = await sources[name](track, apple);
				if (found) {
					found.source = { rmm: "Apple Music / Spicy Lyrics (via RMM Revival)", spotify: "Spotify", lrclib: "LRCLIB" }[name];
					found.sourceId = name;
				}
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
		lyricsCache.set(cacheKey, result);
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

	// -------------------------------------------------------------------------------------
	// Romanization: each line in Latin letters (Google's transliteration; Korean is done here)
	// -------------------------------------------------------------------------------------

	const NON_LATIN = /[Ͱ-ϿЀ-ӿ֐-ۿऀ-෿฀-໿ᄀ-ᇿ぀-ヿ㐀-鿿가-힯]/;

	/** Hangul to Revised Romanization, syllable by syllable (no sound changes across syllables). */
	function koreanRoman(text) {
		const I = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"];
		const V = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"];
		const F = ["", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "p", "l", "l", "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t"];
		return text.replace(/[가-힣]/g, (ch) => {
			const code = ch.charCodeAt(0) - 0xac00;
			return I[Math.floor(code / 588)] + V[Math.floor((code % 588) / 28)] + F[code % 28];
		});
	}

	const romanCache = new Map();

	/** Each line romanized, "" where it is already Latin. */
	async function romanize(lines) {
		const key = lines.map((l) => l.text).join("\n");
		if (romanCache.has(key)) return romanCache.get(key);
		const out = lines.map(() => "");
		const todo = lines.map((l, i) => [i, l.text]).filter(([, t]) => NON_LATIN.test(t));
		for (let start = 0; start < todo.length; start += 30) {
			const chunk = todo.slice(start, start + 30);
			let parts = [];
			try {
				const query = new URLSearchParams({ client: "gtx", sl: "auto", tl: "en", dt: "rm", q: chunk.map(([, t]) => t.replace(/\n/g, " ")).join("\n") });
				const data = await getJson(`https://translate.googleapis.com/translate_a/single?${query}`);
				const rows = data?.[0] || [];
				parts = rows
					.map((row) => row?.[3] || "")
					.join("")
					.split("\n");
			} catch {
				parts = [];
			}
			chunk.forEach(([index, text], n) => {
				const value = (parts.length === chunk.length ? parts[n] : "").trim();
				out[index] = value || (/[가-힣]/.test(text) ? koreanRoman(text) : "");
			});
		}
		romanCache.set(key, out);
		return out;
	}

	// -------------------------------------------------------------------------------------
	// Moving backgrounds: Spotify Canvas and Apple Music's animated album covers
	// -------------------------------------------------------------------------------------

	async function getText(url) {
		try {
			const body = await Spicetify.CosmosAsync.get(url);
			if (typeof body === "string" && body.length > 200) return body;
		} catch {
			// Fall through to fetch.
		}
		const response = await fetch(url);
		if (!response.ok) throw new Error(`HTTP ${response.status}`);
		return response.text();
	}

	/** The song's Spotify Canvas video, or "". */
	async function canvasFor(uri) {
		try {
			const hosted = await window.SLP_HOST?.canvas?.(uri);
			if (hosted) return hosted;
		} catch {
			// Spotify's own lookup.
		}
		try {
			const definition = Spicetify.GraphQL?.Definitions?.canvas;
			if (!definition) return "";
			const result = await Spicetify.GraphQL.Request(definition, { uri, trackUri: uri });
			const url = result?.data?.trackUnion?.canvas?.url || "";
			return /\.(mp4|webm)(\?|$)/.test(url) ? url : "";
		} catch {
			return "";
		}
	}

	const motionCovers = new Map();

	/** The album's animated cover on Apple Music (an HLS stream), or "". */
	async function motionCoverFor(track) {
		try {
			const hosted = await window.SLP_HOST?.motionCover?.(track);
			if (hosted !== undefined && hosted !== null) return hosted;
		} catch {
			// Look it up here.
		}
		const key = plain(track.artist) + "|" + plain(track.album || track.title);
		if (motionCovers.has(key)) return motionCovers.get(key);
		let url = "";
		try {
			const term = encodeURIComponent(`${track.artist} ${track.album || track.title}`);
			const found = await getJson(`https://itunes.apple.com/search?entity=album&limit=10&term=${term}`);
			const albums = (found?.results || []).filter((a) => loose(a.artistName, track.artist) && (!track.album || loose(a.collectionName, track.album)));
			for (const album of albums.slice(0, 3)) {
				const html = await getText(String(album.collectionViewUrl || "").replace(/\?.*$/, ""));
				const match = html.match(/<amp-ambient-video[^>]+src="([^"]+\.m3u8)"/);
				if (match) {
					url = match[1];
					break;
				}
			}
		} catch {
			url = "";
		}
		motionCovers.set(key, url);
		return url;
	}

	let hlsModule = null;
	/** hls.js, loaded once when an animated cover first plays (Chromium doesn't play HLS itself). */
	function hls() {
		if (!hlsModule) hlsModule = import("https://cdn.jsdelivr.net/npm/hls.js@1.5.17/dist/hls.mjs").then((m) => m.default);
		return hlsModule;
	}

	// -------------------------------------------------------------------------------------
	// Interludes: • • • wherever the singing stops for 3 seconds or more
	// -------------------------------------------------------------------------------------

	const INTERLUDE_MS = 3000;

	/** The lines with interlude rows in the gaps (and before the first line). */
	function timeline(lines) {
		const out = [];
		let previousEnd = 0;
		lines.forEach((line, index) => {
			const firstWord = line.words.length ? line.words[0].start : line.start;
			const gapStart = index === 0 ? 0 : previousEnd;
			const gapEnd = index === 0 ? firstWord : line.start;
			if (gapEnd - gapStart >= INTERLUDE_MS) out.push({ interlude: true, start: gapStart, end: gapEnd, index: -1 });
			out.push({ ...line, index });
			const lastWord = line.words.length ? line.words[line.words.length - 1].end : line.end;
			previousEnd = Math.max(previousEnd, lastWord);
		});
		return out;
	}

	/** The three dots' [start, end) times: each fills a third, the last done 550ms before the line. */
	function dotTimes(start, end) {
		const total = end - start;
		const base = total / 3;
		const pad = -550 / 3;
		const d1 = Math.max(start, start + base + pad);
		const d2 = Math.max(d1, start + base * 2 + pad * 2);
		const d3 = Math.max(d2, start + total - 550);
		return [
			[start, d1],
			[d1, d2],
			[d2, d3],
		];
	}

	/** The lyrics as an LRC file (word timing kept as <mm:ss.xx> stamps when there is any). */
	function toLrc(track, lyrics) {
		const stamp = (ms) => {
			const total = Math.max(0, ms) / 1000;
			const m = Math.floor(total / 60);
			return `${String(m).padStart(2, "0")}:${(total - m * 60).toFixed(2).padStart(5, "0")}`;
		};
		const head = [`[ti:${track.title}]`, `[ar:${track.artists}]`, track.album ? `[al:${track.album}]` : "", "[by:Spicy Lyrics Plus]"].filter(Boolean);
		const body = lyrics.lines.map((line) =>
			lyrics.synced === "word"
				? `[${stamp(line.start)}]` + line.words.map((w, i) => `<${stamp(w.start)}>${i > 0 && !w.joined ? " " : ""}${w.text}`).join("")
				: `[${stamp(line.start)}]${line.text}`,
		);
		return head.concat(body).join("\n") + "\n";
	}

	const coverUrl = (raw) => {
		if (!raw) return "";
		if (raw.startsWith("spotify:image:")) return `https://i.scdn.co/image/${raw.split(":")[2]}`;
		return raw;
	};

	return { settings, lyricsFor, SOURCE_NAMES, romanize, canvasFor, motionCoverFor, hls, timeline, dotTimes, toLrc, itunesSong, genreOf, artistGenre, themeFor, currentTrack, accentOf, coverUrl, joinWords, translate };
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
// Backgrounds: the cover (blurred, moved by the motion page's camera), animated cover blobs,
// the song's Spotify Canvas, or Apple Music's animated album cover
// -----------------------------------------------------------------------------------------

/** A looping, muted video: an .mp4 (Canvas) or an HLS stream (animated cover). */
function LoopVideo({ src, className }) {
	const { React } = Spicetify;
	const node = React.useRef(null);
	React.useEffect(() => {
		const video = node.current;
		if (!video || !src) return undefined;
		video.muted = true;
		let player = null;
		let alive = true;
		if (/\.m3u8(\?|$)/.test(src) && video.canPlayType("application/vnd.apple.mpegurl")) {
			// Safari and the iOS app play HLS themselves.
			video.src = src;
			video.play().catch(() => {});
		} else if (/\.m3u8(\?|$)/.test(src)) {
			SLP.hls()
				.then((Hls) => {
					if (!alive) return;
					if (Hls?.isSupported?.()) {
						player = new Hls({ capLevelToPlayerSize: true, maxBufferLength: 10 });
						player.loadSource(src);
						player.attachMedia(video);
					} else video.src = src;
					video.play().catch(() => {});
				})
				.catch(() => {});
		} else {
			video.src = src;
			video.play().catch(() => {});
		}
		return () => {
			alive = false;
			player?.destroy?.();
			video.removeAttribute("src");
			video.load();
		};
	}, [src]);
	return Spicetify.React.createElement("video", { ref: node, className, muted: true, loop: true, playsInline: true, autoPlay: true });
}

function Backdrop({ kind, cover, video, blur, dim, refNode }) {
	const e = Spicetify.React.createElement;
	const layers = [];
	if (video && (kind === "canvas" || kind === "motion" || kind === "auto")) {
		layers.push(e(LoopVideo, { key: video, src: video, className: "slp-bgvideo", style: null }));
		layers.push(e("style", { key: "vb" }, `.slp-bgvideo{filter:blur(${Math.round(blur / 6)}px) saturate(1.2)}`));
	} else if (kind === "animated" && cover) {
		for (let i = 0; i < 3; i += 1) layers.push(e("div", { key: i, className: `slp-blob slp-blob${i}`, style: { backgroundImage: `url("${cover}")`, filter: `blur(${blur}px) saturate(1.6)` } }));
	} else if (cover) {
		layers.push(e("div", { key: "c", ref: refNode, className: "slp-backdrop", style: { backgroundImage: `url("${cover}")`, filter: `blur(${blur}px) saturate(1.5) brightness(.75)` } }));
	}
	layers.push(e("div", { key: "dim", className: "slp-dim", style: { opacity: dim } }));
	return e("div", { className: "slp-bg" }, layers);
}

// -----------------------------------------------------------------------------------------
// Interlude dots
// -----------------------------------------------------------------------------------------

function InterludeDots({ row, position, active }) {
	const e = Spicetify.React.createElement;
	const times = SLP.dotTimes(row.start, row.end);
	const done = position >= times[2][1];
	return e(
		"div",
		{ className: "slp-dots" + (active ? " slp-dots-on" : "") + (active && done ? " slp-dots-out" : "") },
		times.map(([start, end], d) => {
			const p = active ? Math.min(1, Math.max(0, (position - start) / Math.max(1, end - start))) : 0;
			return e("span", {
				key: d,
				className: "slp-dot",
				style: { opacity: 0.28 + 0.72 * p, transform: `scale(${0.82 + 0.28 * p})`, boxShadow: p > 0 ? `0 0 ${Math.round(14 * p)}px rgba(255,255,255,${0.55 * p})` : "none" },
			});
		}),
	);
}

/** Over motion lyrics: the same dots, centred, while the singing rests. */
function MotionDots({ rows, position }) {
	const e = Spicetify.React.createElement;
	const row = rows.find((r) => r.interlude && position >= r.start && position < r.end);
	if (!row) return null;
	return e("div", { className: "slp-motion-dots" }, e(InterludeDots, { row, position, active: true }));
}

// -----------------------------------------------------------------------------------------
// Spicy: word by word
// -----------------------------------------------------------------------------------------

function SpicyStage({ rows, position, onSeek, translated, romanized, look, writers, onCopyLine }) {
	const { React } = Spicetify;
	const e = React.createElement;
	const box = React.useRef(null);
	const heldUntil = React.useRef(0);
	const [away, setAway] = React.useState(false);
	let current = -1;
	for (let index = 0; index < rows.length; index += 1) if (rows[index].start <= position) current = index;

	const scrollToCurrent = React.useCallback(
		(force) => {
			const node = box.current?.querySelector(`[data-row="${current}"]`);
			if (!node || !box.current) return;
			if (!force && Date.now() < heldUntil.current) return;
			const height = box.current.clientHeight;
			const top = node.offsetTop - (look.topLine ? height * 0.24 : height / 2 - node.offsetHeight / 2);
			box.current.scrollTo({ top, behavior: look.smooth ? "smooth" : "auto" });
			setAway(false);
		},
		[current, look.topLine, look.smooth],
	);
	React.useEffect(() => scrollToCurrent(false), [scrollToCurrent]);

	const glow = look.glow;
	const word = (w, i, key) => {
		const span = Math.max(1, w.end - w.start);
		const progress = Math.min(1, Math.max(0, (position - w.start) / span));
		const singing = progress > 0 && progress < 1;
		const long = span > 900;
		const lift = singing ? -0.05 * Math.sin(progress * Math.PI) : 0;
		const grow = long && singing ? 1 + 0.06 * Math.sin(progress * Math.PI) : 1;
		const shine = singing && glow > 0 ? `drop-shadow(0 0 ${(0.18 * glow * (long ? 1.7 : 1)).toFixed(2)}em ${long ? "color-mix(in srgb,var(--slp-accent,#fff) 60%,#fff)" : "rgba(255,255,255,.5)"})` : "none";
		return [
			i > 0 && !w.joined ? " " : null,
			e("span", { key: key + i, className: "slp-word", style: { "--p": `${(progress * 110 - 10).toFixed(1)}%`, transform: `translateY(${lift}em) scale(${grow})`, filter: shine } }, w.text),
		];
	};
	const bracket = (text) => (look.brackets ? `(${text.replace(/^\(|\)$/g, "")})` : text);

	return e(
		"div",
		{ className: "slp-spicy-wrap" },
		e(
			"div",
			{
				ref: box,
				className: "slp-spicy" + (look.align === "center" ? " slp-center" : ""),
				style: {
					fontSize: `calc(clamp(26px, 3.2vw, 54px) * ${look.scale})`,
					fontWeight: look.weight,
					"--slp-gap": look.spacing,
					"--slp-ink": look.color || "#fff",
					fontFamily: look.font ? `"${look.font}", var(--font-family, sans-serif)` : undefined,
				},
				onWheel: () => {
					heldUntil.current = Date.now() + 3500;
					setAway(true);
				},
			},
			rows.map((row, r) => {
				const distance = Math.abs(r - current);
				const state = r === current ? " slp-active" : r < current ? " slp-past" : "";
				const blur = look.blurDistant && r !== current ? Math.min(3, 0.6 + distance * 0.55) : 0;
				const style = { filter: blur ? `blur(${blur.toFixed(1)}px)` : "none", opacity: look.fadePast && r < current ? 0.22 : undefined };
				if (row.interlude) {
					return e("div", { key: r, "data-row": r, className: "slp-line slp-interlude" + state, style: { ...style, filter: "none" } }, e(InterludeDots, { row, position, active: r === current }));
				}
				const active = r === current;
				return e(
					"div",
					{
						key: r,
						"data-row": r,
						className: "slp-line" + state + (row.opposite ? " slp-opposite" : ""),
						style,
						onClick: () => onSeek(row.start),
						onContextMenu: (ev) => {
							ev.preventDefault();
							onCopyLine(row.text);
						},
						title: "Click: play from here · Right-click: copy",
					},
					active ? row.words.map((w, i) => word(w, i, "w")) : row.text,
					row.background.length
						? e(
								"div",
								{ className: "slp-bgv" },
								row.background.map((words, b) => e("span", { key: b }, active && !look.brackets ? words.map((w, i) => word(w, i, `b${b}`)) : bracket(SLP.joinWords(words)))),
							)
						: null,
					romanized?.[row.index] ? e("div", { className: "slp-roman" }, romanized[row.index]) : null,
					translated?.[row.index] ? e("div", { className: "slp-translation" }, translated[row.index]) : null,
				);
			}),
			writers?.length ? e("div", { className: "slp-writers" }, `Written by ${writers.join(", ")}`) : null,
		),
		away ? e("button", { className: "slp-back", onClick: () => scrollToCurrent(true) }, "↓ Back to the lyrics") : null,
	);
}

// -----------------------------------------------------------------------------------------
// The cover stage: the song beside the lyrics (full screen, or the Stage button)
// -----------------------------------------------------------------------------------------

const ICONS = {
	prev: "M6 5h2v14H6zM20 5v14L9.5 12z",
	next: "M16 5h2v14h-2zM4 5l10.5 7L4 19z",
	play: "M8 5.5v13a1 1 0 0 0 1.5.86l10.6-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z",
	pause: "M6 5h4v14H6zM14 5h4v14h-4z",
	shuffle: "M17 3l4 4-4 4V8h-2.6l-2.2 2.6-1.3-1.5L13.5 6H17V3zM3 6h4.5l9.9 12H17v-3l4 4-4 4v-3h-3.5L4.6 8H3V6zm0 12h1.6l2.6-3.1 1.3 1.5L5.5 20H3v-2z",
	repeat: "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z",
	heart: "M12 21s-7.5-4.6-9.6-9.2C.9 8.5 3 5 6.5 5c2 0 3.5 1.2 4.5 2.6C12 6.2 13.5 5 15.5 5 19 5 21.1 8.5 19.6 11.8 17.5 16.4 12 21 12 21z",
};
const icon = (path, size = 22) =>
	Spicetify.React.createElement("svg", { viewBox: "0 0 24 24", width: size, height: size, fill: "currentColor" }, Spicetify.React.createElement("path", { d: path }));
const clock = (ms) => {
	const s = Math.max(0, Math.floor(ms / 1000));
	return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
};

function CoverStage({ track, cover, video, playback, vinyl, upNext }) {
	const e = Spicetify.React.createElement;
	const P = Spicetify.Player;
	const duration = P.getDuration?.() || track.durationMs || 0;
	const shuffle = !!P.getShuffle?.();
	const repeat = P.getRepeat?.() || 0;
	const liked = !!P.getHeart?.();
	const share = duration ? Math.min(1, playback.position / duration) : 0;
	return e(
		"div",
		{ className: "slp-cover-col" },
		e(
			"div",
			{ className: "slp-art" + (vinyl ? " slp-vinyl" : "") + (vinyl && !playback.playing ? " slp-paused" : "") },
			video && !vinyl ? e(LoopVideo, { key: video, src: video, className: "slp-art-video" }) : cover ? e("img", { src: cover, alt: "" }) : null,
		),
		e("div", { className: "slp-song" }, track.title),
		e("div", { className: "slp-by" }, track.artists, track.album ? e("span", null, ` · ${track.album}`) : null),
		e(
			"div",
			{
				className: "slp-seek",
				onClick: (ev) => {
					const rect = ev.currentTarget.getBoundingClientRect();
					if (duration) P.seek(Math.round(((ev.clientX - rect.left) / rect.width) * duration));
				},
			},
			e("div", { className: "slp-seek-fill", style: { width: `${share * 100}%` } }),
		),
		e("div", { className: "slp-clock" }, e("span", null, clock(playback.position)), e("span", null, clock(duration))),
		e(
			"div",
			{ className: "slp-controls" },
			e("button", { className: shuffle ? "slp-lit" : "", title: "Shuffle", onClick: () => P.toggleShuffle() }, icon(ICONS.shuffle, 20)),
			e("button", { title: "Previous", onClick: () => P.back() }, icon(ICONS.prev, 26)),
			e("button", { className: "slp-big", title: "Play/pause", onClick: () => P.togglePlay() }, icon(playback.playing ? ICONS.pause : ICONS.play, 30)),
			e("button", { title: "Next", onClick: () => P.next() }, icon(ICONS.next, 26)),
			e("button", { className: repeat ? "slp-lit" : "", title: repeat === 2 ? "Repeat one" : "Repeat", onClick: () => P.toggleRepeat() }, icon(ICONS.repeat, 20), repeat === 2 ? e("small", null, "1") : null),
			e("button", { className: liked ? "slp-lit" : "", title: liked ? "Remove from Liked Songs" : "Save to Liked Songs", onClick: () => P.toggleHeart() }, icon(ICONS.heart, 20)),
		),
		upNext ? e("div", { className: "slp-upnext" }, e("b", null, "Up next "), upNext) : null,
	);
}

// -----------------------------------------------------------------------------------------
// Settings
// -----------------------------------------------------------------------------------------

const DEFAULTS = {
	mode: "motion",
	theme: "Auto",
	shade: 0.35,
	letterbox: false,
	translate: "",
	romanize: false,
	offset: 0,
	scale: 1,
	weight: 800,
	spacing: 0.55,
	align: "left",
	glow: 1,
	blurDistant: true,
	fadePast: false,
	topLine: false,
	smooth: true,
	color: "",
	font: "",
	brackets: false,
	credits: true,
	bg: "auto",
	bgBlur: 70,
	bgDim: 0.35,
	stage: false,
	vinyl: false,
	artVideo: true,
	upNext: true,
};

const FONTS = ["", "Inter", "Poppins", "Montserrat", "Outfit", "Nunito", "Space Grotesk", "Playfair Display", "Bebas Neue", "Noto Sans JP"];

function useFont(font) {
	Spicetify.React.useEffect(() => {
		if (!font) return;
		const id = "slp-font-" + font.replace(/\s+/g, "-");
		if (document.getElementById(id)) return;
		const link = document.createElement("link");
		link.id = id;
		link.rel = "stylesheet";
		link.href = `https://fonts.googleapis.com/css2?family=${encodeURIComponent(font)}:wght@400;500;600;700;800;900&display=swap`;
		document.head.appendChild(link);
	}, [font]);
}

function Drawer({ s, set, onClose, actions, lyrics, sleep, setSleep }) {
	const e = Spicetify.React.createElement;
	const row = (label, control, hint) => e("label", { className: "slp-row" }, e("span", null, label, hint ? e("small", null, hint) : null), control);
	const toggle = (key) => e("input", { type: "checkbox", className: "slp-switch", checked: !!s[key], onChange: () => set(key, !s[key]) });
	const range = (key, min, max, step) => e("input", { type: "range", min, max, step, value: s[key], onChange: (ev) => set(key, Number(ev.target.value)) });
	const select = (key, options) =>
		e(
			"select",
			{ value: s[key], onChange: (ev) => set(key, typeof DEFAULTS[key] === "number" ? Number(ev.target.value) : ev.target.value) },
			options.map(([value, name]) => e("option", { key: value, value }, name)),
		);
	const section = (title, ...children) => e("div", { className: "slp-section" }, e("h4", null, title), ...children);
	const sleepLeft = typeof sleep === "number" ? Math.max(0, Math.ceil((sleep - Date.now()) / 60000)) : 0;
	return e(
		"div",
		{ className: "slp-drawer" },
		e("div", { className: "slp-drawer-head" }, e("b", null, "Spicy Lyrics Plus"), e("button", { onClick: onClose, title: "Close" }, "✕")),
		section(
			"Background",
			row(
				"Type",
				select("bg", [
					["auto", "Auto (animated cover, Canvas, cover)"],
					["motion", "Animated cover"],
					["canvas", "Canvas"],
					["animated", "Moving cover"],
					["cover", "Still cover"],
				]),
			),
			row("Blur", range("bgBlur", 0, 140, 5)),
			row("Dim", range("bgDim", 0, 0.85, 0.05)),
		),
		section(
			"Lyrics look",
			row("Size", range("scale", 0.6, 1.8, 0.05)),
			row(
				"Weight",
				select("weight", [
					[500, "Medium"],
					[600, "Semibold"],
					[700, "Bold"],
					[800, "Extra bold"],
					[900, "Black"],
				]),
			),
			row("Line spacing", range("spacing", 0.2, 1.4, 0.05)),
			row(
				"Align",
				select("align", [
					["left", "Left"],
					["center", "Centre"],
				]),
			),
			row("Font", select("font", FONTS.map((f) => [f, f || "Spotify"]))),
			row("Colour", e("input", { type: "color", value: s.color || "#ffffff", onChange: (ev) => set("color", ev.target.value) }), s.color ? e("a", { onClick: () => set("color", "") }, " reset") : null),
			row("Glow", range("glow", 0, 2, 0.1)),
			row("Blur distant lines", toggle("blurDistant")),
			row("Fade away previous lines", toggle("fadePast")),
			row("Current line at the top", toggle("topLine")),
			row("Smooth scrolling", toggle("smooth")),
			row("Background vocals in brackets", toggle("brackets")),
			row("Show songwriters", toggle("credits")),
		),
		section(
			"Motion lyrics",
			row("Theme", select("theme", SLP_THEMES.map((t) => [t.id, t.label]))),
			row("Shade", range("shade", 0, 1, 0.05)),
			row("Letterbox bars", toggle("letterbox")),
		),
		section(
			"Translation & romanization",
			row("Translate to", select("translate", TRANSLATE_TARGETS)),
			row("Romanize (Japanese, Korean, Chinese, Cyrillic…)", toggle("romanize")),
		),
		section(
			"Stage & full screen",
			row("Song beside the lyrics", toggle("stage"), "always on in full screen"),
			row("Vinyl", toggle("vinyl")),
			row("Animated cover / Canvas on the art", toggle("artVideo")),
			row("Up next", toggle("upNext")),
		),
		section(
			"Sync",
			row(
				"Lyrics offset",
				e(
					"span",
					{ className: "slp-step" },
					e("button", { onClick: () => set("offset", s.offset - 100) }, "−"),
					`${s.offset > 0 ? "+" : ""}${(s.offset / 1000).toFixed(1)}s`,
					e("button", { onClick: () => set("offset", s.offset + 100) }, "+"),
				),
			),
		),
		section(
			"Sleep timer",
			e(
				"div",
				{ className: "slp-chips" },
				[
					[0, "Off"],
					[15, "15 min"],
					[30, "30 min"],
					[60, "1 hour"],
					["end", "End of song"],
				].map(([value, name]) =>
					e(
						"button",
						{
							key: name,
							className: (value === 0 && !sleep) || (value === "end" && sleep === "end") ? "slp-on" : "",
							onClick: () => setSleep(value === 0 ? null : value === "end" ? "end" : Date.now() + value * 60000),
						},
						name,
					),
				),
			),
			typeof sleep === "number" ? e("small", null, `Pauses in ${sleepLeft} min`) : sleep === "end" ? e("small", null, "Pauses when this song ends") : null,
		),
		section(
			"Lyrics",
			e(
				"div",
				{ className: "slp-chips" },
				e("button", { onClick: actions.copy, disabled: !lyrics?.lines.length }, "Copy lyrics"),
				e("button", { onClick: actions.save, disabled: !lyrics?.lines.length }, "Save as .lrc"),
				e("button", { onClick: actions.next }, "Wrong lyrics? Try another source"),
			),
			lyrics?.source ? e("small", null, `From ${lyrics.source}${lyrics.synced === "line" ? " (word timing generated)" : ""}`) : null,
		),
		e("button", { className: "slp-reset", onClick: actions.reset }, "Reset all settings"),
	);
}

const SLP_CSS = `
.slp-root{position:relative;height:100%;min-height:calc(100vh - 140px);overflow:hidden;color:#fff;background:#0b0b10;border-radius:inherit;font-family:var(--font-family,"SpotifyMixUI",sans-serif)}
.slp-root:fullscreen{min-height:100vh;border-radius:0}
.slp-root.slp-idle,.slp-root.slp-idle *{cursor:none!important}
.slp-bg{position:absolute;inset:0;overflow:hidden}
.slp-bg::before{content:"";position:absolute;inset:0;background:radial-gradient(120% 90% at 30% 20%,var(--slp-accent,#3a3a48) 0%,transparent 60%),radial-gradient(100% 80% at 80% 90%,color-mix(in srgb,var(--slp-accent,#3a3a48) 60%,#000) 0%,transparent 65%),#0b0b10;opacity:.55}
.slp-backdrop{position:absolute;inset:-25%;background-size:cover;background-position:center;transform:rotate(var(--slp-rot,0deg)) scale(calc(1.25 * var(--slp-scale,1))) translate(var(--slp-x,0px),var(--slp-y,0px));transition:transform .25s linear,background-image .8s;will-change:transform}
.slp-blob{position:absolute;width:90%;height:90%;background-size:cover;background-position:center;border-radius:50%;opacity:.85;will-change:transform}
.slp-blob0{left:-15%;top:-20%;animation:slp-orbit 34s linear infinite}
.slp-blob1{right:-20%;top:10%;animation:slp-orbit 46s linear infinite reverse}
.slp-blob2{left:10%;bottom:-35%;animation:slp-orbit 58s linear infinite}
@keyframes slp-orbit{from{transform:rotate(0deg) translateX(6%) rotate(0deg) scale(1.1)}to{transform:rotate(360deg) translateX(6%) rotate(-360deg) scale(1.1)}}
.slp-bgvideo{position:absolute;inset:-4%;width:108%;height:108%;object-fit:cover}
.slp-dim{position:absolute;inset:0;background:#000}
.slp-main{position:absolute;inset:0;display:flex}
.slp-lyrics-col{position:relative;flex:1;min-width:0}
.slp-staged .slp-cover-col{display:flex}
.slp-cover-col{display:none;flex:0 0 min(42%,560px);flex-direction:column;justify-content:center;align-items:stretch;padding:72px 2vw 48px 5vw;z-index:2}
.slp-art{position:relative;width:min(100%,46vh);aspect-ratio:1;border-radius:14px;overflow:hidden;box-shadow:0 30px 70px rgba(0,0,0,.5);margin-bottom:26px;background:#222}
.slp-art img,.slp-art-video{width:100%;height:100%;object-fit:cover;display:block}
.slp-vinyl{border-radius:50%;animation:slp-spin 9s linear infinite;box-shadow:0 30px 70px rgba(0,0,0,.6),0 0 0 10px #111,0 0 0 11px rgba(255,255,255,.08)}
.slp-vinyl::after{content:"";position:absolute;left:50%;top:50%;width:12%;height:12%;margin:-6%;border-radius:50%;background:#0b0b10;box-shadow:0 0 0 6px rgba(255,255,255,.12)}
.slp-vinyl.slp-paused{animation-play-state:paused}
@keyframes slp-spin{to{transform:rotate(360deg)}}
.slp-song{font-size:clamp(22px,2.4vw,36px);font-weight:800;letter-spacing:-.02em;line-height:1.15}
.slp-by{font-size:clamp(14px,1.2vw,18px);opacity:.7;margin-top:6px}
.slp-seek{position:relative;height:6px;border-radius:3px;background:rgba(255,255,255,.2);margin-top:24px;cursor:pointer}
.slp-seek:hover{height:8px;margin-top:23px}
.slp-seek-fill{height:100%;border-radius:3px;background:#fff}
.slp-clock{display:flex;justify-content:space-between;font-size:12px;opacity:.6;margin-top:7px;font-variant-numeric:tabular-nums}
.slp-controls{display:flex;align-items:center;justify-content:space-between;margin-top:14px;max-width:420px}
.slp-controls button{position:relative;background:none;border:0;color:#fff;opacity:.85;cursor:pointer;width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;transition:transform .1s,background .15s,opacity .15s}
.slp-controls button:hover{opacity:1;background:rgba(255,255,255,.1)}
.slp-controls button:active{transform:scale(.92)}
.slp-controls .slp-big{width:62px;height:62px;background:#fff;color:#000;opacity:1}
.slp-controls .slp-big:hover{background:#fff;transform:scale(1.05)}
.slp-controls .slp-lit{color:var(--slp-accent-bright,#1ed760);opacity:1}
.slp-controls small{position:absolute;bottom:6px;right:8px;font-size:9px;font-weight:800}
.slp-upnext{margin-top:18px;font-size:12.5px;opacity:.6;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.slp-toolbar{position:absolute;top:12px;right:16px;left:16px;z-index:5;display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px;align-items:center;pointer-events:none;transition:opacity .4s}
.slp-idle .slp-toolbar,.slp-idle .slp-credit{opacity:0}
.slp-toolbar>*{pointer-events:auto}
.slp-pill{display:flex;gap:6px;align-items:center;background:rgba(0,0,0,.4);backdrop-filter:blur(14px);border:1px solid rgba(255,255,255,.12);border-radius:999px;padding:5px 8px}
.slp-pill select,.slp-pill button{background:transparent;color:#fff;border:1px solid rgba(255,255,255,.22);border-radius:999px;padding:4px 10px;font-size:12.5px;cursor:pointer}
.slp-pill select option,.slp-drawer select option{color:#000}
.slp-pill button.slp-on{background:#fff;color:#000}
.slp-pill label{font-size:11px;opacity:.75;display:flex;gap:6px;align-items:center}
.slp-credit{position:absolute;left:18px;bottom:12px;z-index:4;font-size:11px;opacity:.55;transition:opacity .4s}
.slp-spicy-wrap{position:absolute;inset:0}
.slp-spicy{position:absolute;inset:0;overflow-y:auto;padding:42vh 6vw;scrollbar-width:none;-webkit-mask-image:linear-gradient(transparent,#000 16%,#000 84%,transparent)}
.slp-staged .slp-spicy{padding-left:3vw;padding-right:5vw}
.slp-spicy::-webkit-scrollbar{display:none}
.slp-center{text-align:center}
.slp-center .slp-line{transform-origin:center}
.slp-center .slp-dots{transform-origin:center}
.slp-opposite .slp-dots{transform-origin:right center}
.slp-line{line-height:1.18;letter-spacing:-.02em;margin:0 0 calc(var(--slp-gap,.55) * 1em);color:color-mix(in srgb,var(--slp-ink) 36%,transparent);cursor:pointer;transition:color .4s,filter .5s,transform .5s cubic-bezier(.2,.8,.2,1),opacity .5s;transform-origin:left center}
.slp-line:hover{color:color-mix(in srgb,var(--slp-ink) 70%,transparent)}
.slp-line.slp-past{color:color-mix(in srgb,var(--slp-ink) 52%,transparent)}
.slp-line.slp-active{opacity:1;transform:scale(1.03)}
.slp-line.slp-opposite{text-align:right;transform-origin:right center}
.slp-word{display:inline-block;white-space:pre;background:linear-gradient(90deg,var(--slp-ink) var(--p),color-mix(in srgb,var(--slp-ink) 36%,transparent) calc(var(--p) + 10%));-webkit-background-clip:text;background-clip:text;color:transparent;transition:transform .12s linear}
.slp-bgv{font-size:.55em;font-weight:700;opacity:.8;margin-top:.15em}
.slp-roman{font-size:.5em;font-weight:600;color:color-mix(in srgb,var(--slp-ink) 72%,transparent);margin-top:.2em;letter-spacing:0}
.slp-translation{font-size:.48em;font-weight:600;color:color-mix(in srgb,var(--slp-ink) 62%,transparent);margin-top:.2em;letter-spacing:0}
.slp-writers{font-size:.38em;font-weight:600;opacity:.5;margin-top:2em;letter-spacing:0}
.slp-interlude{cursor:default;padding:.15em 0}
.slp-dots{display:inline-flex;gap:.34em;align-items:center;transform-origin:left center;transition:transform .45s cubic-bezier(.3,1.6,.5,1),opacity .4s}
.slp-dots-on{animation:slp-breathe 2.4s ease-in-out infinite}
.slp-dots-out{transform:scale(.4)!important;opacity:0;animation:none}
@keyframes slp-breathe{0%,100%{transform:scale(1)}50%{transform:scale(1.12)}}
.slp-dot{width:.3em;height:.3em;border-radius:50%;background:var(--slp-ink,#fff);transition:opacity .15s,transform .15s}
.slp-motion-dots .slp-dots{transform-origin:center}
.slp-motion-dots{position:absolute;left:0;right:0;bottom:7%;display:flex;justify-content:center;font-size:44px;z-index:3;pointer-events:none}
.slp-back{position:absolute;left:50%;bottom:28px;transform:translateX(-50%);z-index:4;background:rgba(255,255,255,.92);color:#000;border:0;border-radius:999px;padding:9px 18px;font-weight:700;font-size:13px;cursor:pointer;box-shadow:0 8px 24px rgba(0,0,0,.35)}
.slp-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:22px;color:rgba(255,255,255,.75);text-align:center;padding:0 10vw;z-index:1}
.slp-drawer{position:absolute;top:0;right:0;bottom:0;width:min(380px,92%);z-index:10;overflow-y:auto;background:rgba(14,14,20,.88);backdrop-filter:blur(24px);border-left:1px solid rgba(255,255,255,.1);padding:0 18px 24px;font-size:13px;animation:slp-slide .22s ease-out}
@keyframes slp-slide{from{transform:translateX(30px);opacity:0}}
.slp-drawer-head{position:sticky;top:0;display:flex;justify-content:space-between;align-items:center;padding:16px 0 10px;background:linear-gradient(rgba(14,14,20,.98) 70%,transparent);z-index:1;font-size:15px}
.slp-drawer-head button{background:rgba(255,255,255,.1);border:0;color:#fff;border-radius:50%;width:30px;height:30px;cursor:pointer}
.slp-section{margin-top:14px;padding:12px;border-radius:14px;background:rgba(255,255,255,.05)}
.slp-section h4{margin:0 0 8px;font-size:11px;text-transform:uppercase;letter-spacing:.08em;opacity:.55}
.slp-section>small{display:block;margin-top:8px;opacity:.55}
.slp-row{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:7px 0;cursor:pointer}
.slp-row>span{display:flex;flex-direction:column}
.slp-row small{opacity:.5;font-size:11px}
.slp-row a{color:#fff;opacity:.6;cursor:pointer;font-size:11px}
.slp-row select{background:rgba(255,255,255,.08);color:#fff;border:1px solid rgba(255,255,255,.15);border-radius:8px;padding:5px 8px;max-width:190px}
.slp-row input[type=range]{width:130px;accent-color:var(--slp-accent,#fff)}
.slp-row input[type=color]{width:34px;height:26px;border:0;background:none;padding:0}
.slp-switch{appearance:none;width:38px;height:22px;border-radius:11px;background:rgba(255,255,255,.2);position:relative;cursor:pointer;transition:background .2s;flex:none}
.slp-switch::after{content:"";position:absolute;left:3px;top:3px;width:16px;height:16px;border-radius:50%;background:#fff;transition:transform .2s}
.slp-switch:checked{background:#1ed760}
.slp-switch:checked::after{transform:translateX(16px)}
.slp-step{display:flex;align-items:center;gap:8px;font-variant-numeric:tabular-nums}
.slp-step button,.slp-chips button,.slp-reset{background:rgba(255,255,255,.1);border:0;color:#fff;border-radius:999px;padding:6px 12px;cursor:pointer;font-size:12px}
.slp-chips{display:flex;flex-wrap:wrap;gap:6px}
.slp-chips button.slp-on{background:#fff;color:#000}
.slp-chips button:disabled{opacity:.4;cursor:default}
.slp-reset{margin-top:16px;opacity:.7}
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
	["it", "Italiano"],
	["ar", "العربية"],
	["hi", "हिन्दी"],
	["ko", "한국어"],
	["ja", "日本語"],
	["zh-CN", "中文"],
	["ru", "Русский"],
	["tr", "Türkçe"],
];

function copyText(text) {
	try {
		if (Spicetify.Platform?.ClipboardAPI?.copy) Spicetify.Platform.ClipboardAPI.copy(text);
		else navigator.clipboard.writeText(text);
		Spicetify.showNotification?.("Copied");
	} catch {
		Spicetify.showNotification?.("Couldn't copy", true);
	}
}

function upNextText() {
	try {
		const next = Spicetify.Queue?.nextTracks?.find((t) => t?.contextTrack?.metadata?.title);
		const meta = next?.contextTrack?.metadata;
		return meta ? `${meta.title} · ${meta.artist_name || ""}` : "";
	} catch {
		return "";
	}
}

function SpicyLyricsPlus() {
	const { React } = Spicetify;
	const e = React.createElement;
	const root = React.useRef(null);
	const backdrop = React.useRef(null);
	const [s, setS] = React.useState(() => {
		const out = {};
		for (const key of Object.keys(DEFAULTS)) out[key] = SLP.settings.get(key, DEFAULTS[key]);
		return out;
	});
	const set = React.useCallback((key, value) => {
		SLP.settings.set(key, value);
		setS((old) => ({ ...old, [key]: value }));
	}, []);
	const [track, setTrack] = React.useState(SLP.currentTrack());
	const [lyrics, setLyrics] = React.useState(null);
	const [skip, setSkip] = React.useState([]);
	const [accent, setAccent] = React.useState("");
	const [genre, setGenre] = React.useState(null);
	const [translated, setTranslated] = React.useState(null);
	const [romanized, setRomanized] = React.useState(null);
	const [canvas, setCanvas] = React.useState("");
	const [motionCover, setMotionCover] = React.useState("");
	const [drawer, setDrawer] = React.useState(false);
	const [fullscreen, setFullscreen] = React.useState(false);
	const [idle, setIdle] = React.useState(false);
	const [sleep, setSleep] = React.useState(null);
	const [upNext, setUpNext] = React.useState("");
	const [playback, setPlayback] = React.useState({ position: 0, playing: false });
	const apple = React.useRef(null);
	const official = typeof window._spicy_lyrics === "object";
	const wallpaper = window.SLP_HOST?.mode === "wallpaper";

	// Settings changed by another copy (the desktop app changing the wallpaper, or back).
	React.useEffect(() => {
		const reload = () =>
			setS(() => {
				const out = {};
				for (const key of Object.keys(DEFAULTS)) out[key] = SLP.settings.get(key, DEFAULTS[key]);
				return out;
			});
		window.addEventListener("slp-settings", reload);
		return () => window.removeEventListener("slp-settings", reload);
	}, []);
	useFont(s.font);

	// Follow the player.
	React.useEffect(() => {
		const onSong = () => {
			setTrack(SLP.currentTrack());
			setUpNext(upNextText());
		};
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
		const queue = setInterval(() => setUpNext(upNextText()), 5000);
		setUpNext(upNextText());
		const onFull = () => setFullscreen(document.fullscreenElement === root.current);
		document.addEventListener("fullscreenchange", onFull);
		return () => {
			Spicetify.Player.removeEventListener("songchange", onSong);
			cancelAnimationFrame(frame);
			clearInterval(queue);
			document.removeEventListener("fullscreenchange", onFull);
		};
	}, []);

	// The song: genre, colour, lyrics, Canvas and animated cover.
	React.useEffect(() => {
		let alive = true;
		setLyrics(null);
		setSkip([]);
		setGenre(null);
		setAccent("");
		setCanvas("");
		setMotionCover("");
		apple.current = null;
		if (!track) return undefined;
		(async () => {
			let genre = null;
			try {
				const found = await SLP.itunesSong(track);
				apple.current = found.song;
				genre = SLP.genreOf(found.song?.primaryGenreName || found.artistSongs[0]?.primaryGenreName);
			} catch {
				// Ask about the artist instead.
			}
			if (!genre) genre = await SLP.artistGenre(track);
			if (alive) setGenre(genre);
			SLP.accentOf(track.uri, SLP.coverUrl(track.cover)).then((hex) => alive && setAccent(hex));
			SLP.canvasFor(track.uri).then((url) => alive && setCanvas(url));
			SLP.motionCoverFor(track).then((url) => alive && setMotionCover(url));
			const found = await SLP.lyricsFor(track, apple.current);
			if (alive) setLyrics(found);
		})();
		return () => {
			alive = false;
		};
	}, [track?.uri]);

	// "Wrong lyrics?": the next source.
	React.useEffect(() => {
		if (!track || !skip.length) return undefined;
		let alive = true;
		setLyrics(null);
		SLP.lyricsFor(track, apple.current, skip).then((found) => {
			if (!alive) return;
			if (!found.lines.length && skip.length >= SLP.SOURCE_NAMES.length) Spicetify.showNotification?.("No other source has this song");
			setLyrics(found);
		});
		return () => {
			alive = false;
		};
	}, [skip]);

	React.useEffect(() => {
		let alive = true;
		setTranslated(null);
		if (!s.translate || !lyrics?.lines.length) return undefined;
		SLP.translate(lyrics.lines, s.translate)
			.then((lines) => alive && setTranslated(lines.some(Boolean) ? lines : null))
			.catch(() => {});
		return () => {
			alive = false;
		};
	}, [lyrics, s.translate]);

	React.useEffect(() => {
		let alive = true;
		setRomanized(null);
		if (!s.romanize || !lyrics?.lines.length) return undefined;
		SLP.romanize(lyrics.lines)
			.then((lines) => alive && setRomanized(lines.some(Boolean) ? lines : null))
			.catch(() => {});
		return () => {
			alive = false;
		};
	}, [lyrics, s.romanize]);

	// Sleep timer.
	React.useEffect(() => {
		if (!sleep) return undefined;
		if (sleep === "end") {
			const onSong = () => {
				Spicetify.Player.pause();
				setSleep(null);
			};
			Spicetify.Player.addEventListener("songchange", onSong);
			return () => Spicetify.Player.removeEventListener("songchange", onSong);
		}
		const timer = setInterval(() => {
			if (Date.now() >= sleep) {
				Spicetify.Player.pause();
				setSleep(null);
				Spicetify.showNotification?.("Sleep timer: paused");
			}
		}, 5000);
		return () => clearInterval(timer);
	}, [sleep]);

	// Full screen: the controls hide when the mouse rests; keys play, pause and seek.
	const staged = s.stage || fullscreen;
	React.useEffect(() => {
		if (!staged && !wallpaper) {
			setIdle(false);
			return undefined;
		}
		let timer = 0;
		const wake = () => {
			setIdle(false);
			clearTimeout(timer);
			timer = setTimeout(() => setIdle(true), 2600);
		};
		const node = root.current;
		node?.addEventListener("mousemove", wake);
		wake();
		const onKey = (ev) => {
			if (document.fullscreenElement !== root.current || ev.target?.tagName === "INPUT" || ev.target?.tagName === "SELECT") return;
			const P = Spicetify.Player;
			if (ev.code === "Space") P.togglePlay();
			else if (ev.code === "ArrowRight") P.seek(P.getProgress() + 5000);
			else if (ev.code === "ArrowLeft") P.seek(Math.max(0, P.getProgress() - 5000));
			else if (ev.code === "KeyN") P.next();
			else if (ev.code === "KeyP") P.back();
			else if (ev.code === "KeyM") set("mode", s.mode === "motion" ? "spicy" : "motion");
			else return;
			ev.preventDefault();
			wake();
		};
		document.addEventListener("keydown", onKey);
		return () => {
			clearTimeout(timer);
			node?.removeEventListener("mousemove", wake);
			document.removeEventListener("keydown", onKey);
		};
	}, [staged, s.mode]);

	const onBackdrop = React.useCallback((frame) => {
		const node = backdrop.current;
		if (!node) return;
		node.style.setProperty("--slp-rot", `${frame.rotate || 0}deg`);
		node.style.setProperty("--slp-scale", String(frame.scale || 1));
		node.style.setProperty("--slp-x", `${frame.x || 0}px`);
		node.style.setProperty("--slp-y", `${frame.y || 0}px`);
	}, []);

	const theme = SLP.themeFor(s.theme, genre, track?.uri || "");
	const cover = SLP.coverUrl(track?.cover);
	const hasLyrics = lyrics && lyrics.lines.length > 0;
	const position = playback.position + s.offset;
	const rows = React.useMemo(() => (lyrics ? SLP.timeline(lyrics.lines) : []), [lyrics]);
	const subtitle = translated || romanized;
	const video = s.bg === "motion" ? motionCover : s.bg === "canvas" ? canvas : s.bg === "auto" ? motionCover || canvas : "";
	const artVideo = s.artVideo ? motionCover || canvas : "";
	const toggleFull = () => (document.fullscreenElement ? document.exitFullscreen() : root.current?.requestFullscreen?.());

	const actions = {
		copy: () => hasLyrics && copyText(lyrics.lines.map((l) => l.text).join("\n")),
		save: () => {
			if (!hasLyrics) return;
			const blob = new Blob([SLP.toLrc(track, lyrics)], { type: "text/plain" });
			const link = document.createElement("a");
			link.href = URL.createObjectURL(blob);
			link.download = `${track.artist} - ${track.title}.lrc`.replace(/[\\/:*?"<>|]/g, "_");
			link.click();
			setTimeout(() => URL.revokeObjectURL(link.href), 5000);
		},
		next: () => lyrics?.sourceId && setSkip((old) => [...old, lyrics.sourceId]),
		reset: () => {
			for (const key of Object.keys(DEFAULTS)) set(key, DEFAULTS[key]);
		},
	};

	const toolbar = e(
		"div",
		{ className: "slp-toolbar" },
		e(
			"div",
			{ className: "slp-pill" },
			e("button", { className: s.mode === "motion" ? "slp-on" : "", onClick: () => set("mode", "motion") }, "Motion"),
			e("button", { className: s.mode === "spicy" ? "slp-on" : "", onClick: () => set("mode", "spicy") }, "Word by word"),
			official ? e("button", { title: "Open the official Spicy Lyrics", onClick: () => Spicetify.Platform.History.push({ pathname: "/SpicyLyrics" }) }, "Spicy Lyrics ↗") : null,
		),
		s.mode === "motion"
			? e(
					"div",
					{ className: "slp-pill" },
					e(
						"select",
						{ value: s.theme, onChange: (ev) => set("theme", ev.target.value), title: "Motion lyrics theme" },
						SLP_THEMES.map((t) => e("option", { key: t.id, value: t.id }, t.id === "Auto" && s.theme === "Auto" ? `Auto: ${theme.label}` : t.label)),
					),
				)
			: null,
		e(
			"div",
			{ className: "slp-pill" },
			e("select", { value: s.translate, onChange: (ev) => set("translate", ev.target.value), title: "Translate" }, TRANSLATE_TARGETS.map(([code, name]) => e("option", { key: code, value: code }, name))),
			e("button", { className: s.romanize ? "slp-on" : "", title: "Romanize", onClick: () => set("romanize", !s.romanize) }, "Aa"),
			e("button", { title: "Lyrics earlier", onClick: () => set("offset", s.offset - 100) }, "−"),
			e("label", { title: "Lyrics offset" }, `${s.offset > 0 ? "+" : ""}${(s.offset / 1000).toFixed(1)}s`),
			e("button", { title: "Lyrics later", onClick: () => set("offset", s.offset + 100) }, "+"),
		),
		e(
			"div",
			{ className: "slp-pill" },
			e("button", { className: s.stage ? "slp-on" : "", title: "The song beside the lyrics", onClick: () => set("stage", !s.stage) }, "Stage"),
			e("button", { className: fullscreen ? "slp-on" : "", title: "Full screen (Space, ←/→, N, P, M)", onClick: toggleFull }, "⛶"),
			e("button", { className: drawer ? "slp-on" : "", title: "Settings", onClick: () => setDrawer(!drawer) }, "⚙"),
		),
	);

	let stage;
	if (!track) stage = e("div", { className: "slp-empty" }, "Play something on Spotify.");
	else if (!lyrics) stage = e("div", { className: "slp-empty" }, "Finding lyrics…");
	else if (!hasLyrics) stage = e("div", { className: "slp-empty" }, "No synced lyrics for this song.");
	else if (s.mode === "motion")
		stage = [
			e(MotionStage, {
				key: track.uri,
				track,
				lyrics,
				accent,
				theme,
				playback: { position, playing: playback.playing },
				shade: s.shade,
				letterbox: s.letterbox,
				translated: subtitle,
				onBackdrop,
			}),
			e(MotionDots, { key: "dots", rows, position }),
		];
	else
		stage = e(SpicyStage, {
			rows,
			position,
			translated,
			romanized,
			look: s,
			writers: s.credits ? lyrics.writers : null,
			onSeek: (ms) => Spicetify.Player.seek(Math.max(0, ms - s.offset)),
			onCopyLine: copyText,
		});

	return e(
		"div",
		{
			ref: root,
			className: "slp-root" + (staged && track ? " slp-staged" : "") + (idle && !drawer ? " slp-idle" : "") + (wallpaper ? " slp-wallpaper" : ""),
			style: accent ? { "--slp-accent": accent } : null,
		},
		e("style", null, SLP_CSS),
		e(Backdrop, { kind: s.bg, cover, video, blur: s.bgBlur, dim: s.bgDim, refNode: backdrop }),
		e(
			"div",
			{ className: "slp-main" },
			track ? e(CoverStage, { track, cover, video: artVideo, playback, vinyl: s.vinyl, upNext: s.upNext ? upNext : "" }) : null,
			e("div", { className: "slp-lyrics-col" }, stage),
		),
		hasLyrics && lyrics.source && !staged ? e("div", { className: "slp-credit" }, `Lyrics: ${lyrics.source}${lyrics.synced === "line" ? " · word timing generated" : ""}`) : null,
		toolbar,
		drawer ? e(Drawer, { s, set, onClose: () => setDrawer(false), actions, lyrics, sleep, setSleep }) : null,
	);
}
