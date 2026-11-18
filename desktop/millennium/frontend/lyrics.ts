// Lyrics: finding them and turning them into lines of timed words.
//
// Sources, best first: RMM Revival (Apple Music lyrics and Spicy Lyrics syncs, word by word, by
// the song's Apple Music ID), then LRCLIB. Line-timed lyrics get word timing spread over each
// line, so every line still lights up word by word.

export type Word = { text: string; start: number; end: number; joined: boolean };
export type Line = { start: number; end: number; text: string; words: Word[]; background: Word[][]; opposite: boolean };
export type Lyrics = { synced: 'word' | 'line' | 'none'; lines: Line[]; source?: string; artwork?: string };
export type Track = { title: string; artist: string; album: string; durationMs: number };

export type Fetcher = (url: string) => Promise<string>;

const plain = (text: string) =>
	String(text || '')
		.toLowerCase()
		.normalize('NFKD')
		.replace(/[̀-ͯ]/g, '')
		.replace(/\s*[([].*?(feat|ft\.|with|remaster|version|edit|live|mix).*?[)\]]/g, '')
		.replace(/[^\p{L}\p{N}]+/gu, ' ')
		.trim();

const loose = (a: string, b: string) => {
	const x = plain(a);
	const y = plain(b);
	return !!x && !!y && (x === y || x.includes(y) || y.includes(x));
};

/** The first artist of "A, B & C" / "A feat. B". */
export const leadArtist = (artist: string) => String(artist || '').split(/\s*(?:,|;|&| feat\.? | ft\.? | x | and )\s*/i)[0] || artist;

const seconds = (raw: string | null): number | null => {
	if (raw == null || raw === '') return null;
	const text = String(raw).trim();
	if (text.endsWith('ms')) return parseFloat(text) / 1000;
	if (text.endsWith('s') && !text.includes(':')) return parseFloat(text);
	return text
		.split(':')
		.map(parseFloat)
		.reduce((total, part) => total * 60 + part, 0);
};

export const joinWords = (words: Word[]) => words.map((w, i) => (i > 0 && !w.joined ? ' ' : '') + w.text).join('');

export function parseTtml(text: string): Lyrics | null {
	const doc = new DOMParser().parseFromString(text, 'application/xml');
	if (doc.querySelector('parsererror')) return null;
	const paragraphs = Array.from(doc.getElementsByTagName('p'));
	if (!paragraphs.length) return null;
	const META = 'http://www.w3.org/ns/ttml#metadata';
	const agentOf = (node: Element) => node.getAttribute('ttm:agent') || node.getAttributeNS(META, 'agent');
	const roleOf = (node: Element) => node.getAttribute('ttm:role') || node.getAttributeNS(META, 'role');
	const firstAgent = paragraphs.map(agentOf).find(Boolean);
	let wordTimed = false;
	const lines: Line[] = [];
	for (const p of paragraphs) {
		const words: Word[] = [];
		const background: Word[][] = [];
		const collect = (node: Element, into: Word[]) => {
			let space = false;
			for (const child of Array.from(node.childNodes)) {
				if (child.nodeType === 3) {
					if (/\s/.test(child.nodeValue || '')) space = true;
					continue;
				}
				if (child.nodeType !== 1 || (child as Element).localName !== 'span') continue;
				const el = child as Element;
				const role = roleOf(el);
				if (role === 'x-bg') {
					const bg: Word[] = [];
					collect(el, bg);
					if (bg.length) background.push(bg);
					continue;
				}
				if (role === 'x-translation' || role === 'x-roman') continue;
				const start = seconds(el.getAttribute('begin'));
				const end = seconds(el.getAttribute('end'));
				const value = el.textContent || '';
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
		const start = (seconds(p.getAttribute('begin')) ?? 0) * 1000;
		const end = (seconds(p.getAttribute('end')) ?? 0) * 1000;
		const lineText = words.length ? joinWords(words) : (p.textContent || '').trim();
		if (!lineText) continue;
		const agent = agentOf(p);
		lines.push({ start, end: Math.max(end, start), text: lineText, words, background, opposite: !!agent && !!firstAgent && agent !== firstAgent });
	}
	return lines.length ? { synced: wordTimed ? 'word' : 'line', lines } : null;
}

const STAMP = /\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)\]/g;
const stampMs = (min: string, sec: string) => (parseInt(min, 10) * 60 + parseFloat(sec.replace(':', '.'))) * 1000;

export function parseLrc(text: string): Lyrics | null {
	const lines: Line[] = [];
	let wordTimed = false;
	for (const raw of String(text).split(/\r?\n/)) {
		const stamps = Array.from(raw.matchAll(STAMP));
		if (!stamps.length) continue;
		let body = raw.replace(STAMP, '');
		const words: Word[] = [];
		const wordStamps = Array.from(body.matchAll(/<(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)>([^<]*)/g));
		if (wordStamps.length) {
			wordTimed = true;
			wordStamps.forEach((match, index) => {
				const start = stampMs(match[1], match[2]);
				const next = wordStamps[index + 1];
				const end = next ? stampMs(next[1], next[2]) : start + 600;
				const value = match[3];
				if (value.trim()) words.push({ text: value.trim(), start, end, joined: words.length > 0 && !/^\s/.test(value) });
			});
			body = joinWords(words);
		}
		for (const stamp of stamps) {
			const start = stampMs(stamp[1], stamp[2]);
			lines.push({ start, end: start, text: body.trim(), words: words.slice(), background: [], opposite: false });
		}
	}
	lines.sort((a, b) => a.start - b.start);
	for (let index = 0; index < lines.length; index += 1) {
		const next = lines[index + 1];
		lines[index].end = next ? next.start : lines[index].start + 5000;
	}
	const sung = lines.filter((line) => line.text);
	return sung.length ? { synced: wordTimed ? 'word' : 'line', lines: sung } : null;
}

/** Word timing spread over a line-timed line: longer words take longer, with a rest at the end. */
export function generateWords(line: Line): Word[] {
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

const json = async (fetcher: Fetcher, url: string): Promise<any> => {
	const text = await fetcher(url);
	if (!text) return null;
	try {
		return JSON.parse(text);
	} catch {
		return null;
	}
};

type AppleSong = { trackId: number; trackName: string; artistName: string; trackTimeMillis?: number; artworkUrl100?: string; primaryGenreName?: string };

async function appleSong(fetcher: Fetcher, track: Track): Promise<AppleSong | null> {
	const artist = leadArtist(track.artist);
	const term = encodeURIComponent(`${artist} ${track.title}`);
	const found = await json(fetcher, `https://itunes.apple.com/search?entity=song&limit=10&term=${term}`);
	const songs: AppleSong[] = (found?.results || []).filter((item: AppleSong) => loose(item.artistName, artist) && loose(item.trackName, track.title));
	if (track.durationMs > 0) songs.sort((a, b) => Math.abs((a.trackTimeMillis || 0) - track.durationMs) - Math.abs((b.trackTimeMillis || 0) - track.durationMs));
	return songs[0] || null;
}

async function fromRmm(fetcher: Fetcher, apple: AppleSong | null): Promise<Lyrics | null> {
	if (!apple?.trackId) return null;
	const data = await json(fetcher, `https://lyrics.rmmreviv.al/lyrics?id=${apple.trackId}`);
	const text = ['ttml', 'content', 'syncedLyrics', 'lyrics'].map((key) => data?.[key]).find((value) => typeof value === 'string' && value.trim());
	if (!text) return null;
	if (/<tt[\s>]/i.test(text)) return parseTtml(text);
	if (/\[\d{1,3}:\d{2}/.test(text)) return parseLrc(text);
	return null;
}

async function fromLrclib(fetcher: Fetcher, track: Track): Promise<Lyrics | null> {
	const query = new URLSearchParams({
		artist_name: leadArtist(track.artist),
		track_name: track.title,
		album_name: track.album || '',
		duration: String(Math.round(track.durationMs / 1000)),
	});
	const data = await json(fetcher, `https://lrclib.net/api/get?${query}`);
	if (data?.syncedLyrics) return parseLrc(data.syncedLyrics);
	// No exact match: search, and take the closest length.
	const search = await json(fetcher, `https://lrclib.net/api/search?${new URLSearchParams({ track_name: track.title, artist_name: leadArtist(track.artist) })}`);
	const hits = (Array.isArray(search) ? search : []).filter((hit: any) => hit?.syncedLyrics);
	if (track.durationMs > 0) hits.sort((a: any, b: any) => Math.abs(a.duration * 1000 - track.durationMs) - Math.abs(b.duration * 1000 - track.durationMs));
	return hits[0] ? parseLrc(hits[0].syncedLyrics) : null;
}

const cache = new Map<string, Lyrics>();

export async function lyricsFor(fetcher: Fetcher, track: Track): Promise<Lyrics> {
	const key = `${track.artist}|${track.title}`;
	const cached = cache.get(key);
	if (cached) return cached;
	let apple: AppleSong | null = null;
	try {
		apple = await appleSong(fetcher, track);
	} catch {
		apple = null;
	}
	let best: Lyrics | null = null;
	const tries: [string, () => Promise<Lyrics | null>][] = [
		['Apple Music / Spicy Lyrics (via RMM Revival)', () => fromRmm(fetcher, apple)],
		['LRCLIB', () => fromLrclib(fetcher, track)],
	];
	for (const [source, run] of tries) {
		try {
			const found = await run();
			if (found) found.source = source;
			if (found?.synced === 'word') {
				best = found;
				break;
			}
			if (found && !best) best = found;
		} catch {
			// Next source.
		}
	}
	const result: Lyrics = best || { synced: 'none', lines: [] };
	for (const line of result.lines) if (!line.words.length) line.words = generateWords(line);
	if (apple?.artworkUrl100) result.artwork = apple.artworkUrl100.replace(/100x100bb/, '600x600bb');
	cache.set(key, result);
	if (cache.size > 40) cache.delete(cache.keys().next().value as string);
	return result;
}

const translations = new Map<string, string[]>();

/** Each line in [target] (Google Translate's free endpoint), empty where it is already that language. */
export async function translate(fetcher: Fetcher, lines: Line[], target: string): Promise<string[]> {
	const key = target + '|' + lines.map((l) => l.text).join('\n');
	const cached = translations.get(key);
	if (cached) return cached;
	const out: string[] = [];
	for (let start = 0; start < lines.length; start += 40) {
		const chunk = lines.slice(start, start + 40).map((l) => l.text.replace(/\n/g, ' '));
		const query = new URLSearchParams({ client: 'gtx', sl: 'auto', tl: target, dt: 't', q: chunk.join('\n') });
		const data = await json(fetcher, `https://translate.googleapis.com/translate_a/single?${query}`);
		const text = (data?.[0] || []).map((part: any) => part?.[0] || '').join('');
		const parts = text.split('\n');
		const source = data?.[2];
		chunk.forEach((original, index) => {
			const translated = (parts[index] || '').trim();
			out.push(source === target || plain(translated) === plain(original) ? '' : translated);
		});
	}
	translations.set(key, out);
	return out;
}
