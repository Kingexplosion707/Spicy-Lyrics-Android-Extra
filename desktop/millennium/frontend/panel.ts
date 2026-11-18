// The lyrics panel inside a Steam window: a floating glass card (drag it by its header, resize it
// from its corner), or the whole window when expanded. Words light up as they're sung, Spicy
// Lyrics style: a fill sweeping through each word, a lift and a glow on long notes.

import { joinWords, Line, Lyrics, Word } from './lyrics';

export type NowPlaying = {
	title: string;
	artist: string;
	album: string;
	durationMs: number;
	playing: boolean;
	cover: string;
	/** Where the song is now, in ms (already counting time since Windows last reported it). */
	position: () => number;
};

export type PanelActions = {
	control: (command: string) => void;
	setTranslation: (target: string) => void;
};

type Row = (Line & { index: number; interlude: false }) | { interlude: true; start: number; end: number; index: -1 };

const STORE = 'spicy-lyrics-plus:';
export const settings = {
	get<T>(key: string, fallback: T): T {
		try {
			const raw = localStorage.getItem(STORE + key);
			return raw == null ? fallback : (JSON.parse(raw) as T);
		} catch {
			return fallback;
		}
	},
	set(key: string, value: unknown) {
		try {
			localStorage.setItem(STORE + key, JSON.stringify(value));
		} catch {
			// Storage off: lasts this session.
		}
	},
};

const CSS = `
#slp-root{position:fixed;z-index:99999;right:24px;bottom:24px;width:420px;height:540px;min-width:300px;min-height:260px;display:none;flex-direction:column;overflow:hidden;color:#fff;border-radius:18px;background:#0b0b10;box-shadow:0 24px 64px rgba(0,0,0,.6),0 0 0 1px rgba(255,255,255,.08);resize:both;font-family:"Motiva Sans","Segoe UI",Arial,sans-serif;user-select:none}
#slp-root.slp-open{display:flex}
#slp-root.slp-expanded{inset:40px 24px 24px 24px!important;width:auto!important;height:auto!important;resize:none;border-radius:14px}
#slp-root .slp-bgimg{position:absolute;inset:-30%;background-size:cover;background-position:center;filter:blur(60px) saturate(1.6) brightness(.55);transform:scale(1.2);animation:slp-drift 40s ease-in-out infinite alternate;transition:background-image .8s}
#slp-root .slp-tint{position:absolute;inset:0;background:radial-gradient(120% 90% at 20% 10%,var(--slp-accent,#3a3a48) 0%,transparent 60%),linear-gradient(rgba(8,8,12,.25),rgba(8,8,12,.55));opacity:.6}
@keyframes slp-drift{from{transform:scale(1.2) rotate(0deg)}to{transform:scale(1.35) rotate(12deg)}}
#slp-root .slp-head{position:relative;z-index:2;display:flex;align-items:center;gap:10px;padding:12px 12px 8px 14px;cursor:grab}
#slp-root.slp-expanded .slp-head{cursor:default}
#slp-root .slp-cover{width:42px;height:42px;border-radius:8px;background:#222 center/cover;flex:none;box-shadow:0 4px 14px rgba(0,0,0,.4)}
#slp-root .slp-meta{flex:1;min-width:0}
#slp-root .slp-title{font-weight:700;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#slp-root .slp-artist{font-size:12px;opacity:.7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#slp-root .slp-btn{background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.14);color:#fff;border-radius:999px;height:28px;min-width:28px;padding:0 9px;font-size:12px;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;gap:4px}
#slp-root .slp-btn:hover{background:rgba(255,255,255,.18)}
#slp-root .slp-btn.slp-on{background:#fff;color:#000}
#slp-root .slp-tools{position:relative;z-index:2;display:none;flex-wrap:wrap;gap:6px;padding:0 12px 8px 14px;align-items:center;font-size:11px}
#slp-root.slp-showtools .slp-tools{display:flex}
#slp-root .slp-tools select{background:rgba(0,0,0,.35);color:#fff;border:1px solid rgba(255,255,255,.18);border-radius:999px;height:26px;padding:0 8px;font-size:12px}
#slp-root .slp-tools select option{color:#000}
#slp-root .slp-tools .slp-label{opacity:.7;margin-left:4px}
#slp-root .slp-lyrics{position:relative;z-index:1;flex:1;overflow-y:auto;padding:38% 22px;scrollbar-width:none;-webkit-mask-image:linear-gradient(transparent,#000 16%,#000 84%,transparent)}
#slp-root.slp-expanded .slp-lyrics{padding:30vh 7vw}
#slp-root .slp-lyrics::-webkit-scrollbar{display:none}
#slp-root .slp-line{font-weight:800;line-height:1.2;letter-spacing:-.015em;margin:0 0 .6em;color:rgba(255,255,255,.38);cursor:pointer;transition:color .4s,filter .5s,transform .5s,opacity .5s;filter:blur(1.6px);transform-origin:left center;opacity:.85}
#slp-root .slp-line:hover{filter:none;color:rgba(255,255,255,.7)}
#slp-root .slp-line.slp-past{color:rgba(255,255,255,.55);filter:blur(1px)}
#slp-root .slp-line.slp-active{filter:none;opacity:1;transform:scale(1.04)}
#slp-root .slp-line.slp-opposite{text-align:right;transform-origin:right center}
#slp-root .slp-word{display:inline-block;white-space:pre;background:linear-gradient(90deg,#fff var(--p,0%),rgba(255,255,255,.38) calc(var(--p,0%) + 12%));-webkit-background-clip:text;background-clip:text;color:transparent;transition:transform .12s linear}
#slp-root .slp-line:not(.slp-active) .slp-word{background:none;color:inherit}
#slp-root .slp-word.slp-singing{filter:drop-shadow(0 0 calc(var(--slp-size) * .18) rgba(255,255,255,.45))}
#slp-root .slp-word.slp-long{filter:drop-shadow(0 0 calc(var(--slp-size) * .3) var(--slp-glow,#fff))}
#slp-root .slp-bgv{font-size:.58em;font-weight:700;opacity:.85;margin-top:.15em}
#slp-root .slp-tr{font-size:.5em;font-weight:600;color:rgba(255,255,255,.66);margin-top:.22em}
#slp-root .slp-interlude{display:flex;gap:.35em;cursor:default;padding:.15em 0}
#slp-root .slp-dot{width:.3em;height:.3em;border-radius:50%;background:#fff;box-shadow:0 0 12px rgba(255,255,255,.5);opacity:.35;transition:opacity .2s}
#slp-root .slp-empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;text-align:center;padding:0 24px;font-size:15px;color:rgba(255,255,255,.75);z-index:1;pointer-events:none}
#slp-root .slp-foot{position:relative;z-index:2;padding:6px 14px 12px}
#slp-root .slp-bar{height:4px;border-radius:2px;background:rgba(255,255,255,.18);cursor:pointer;overflow:hidden}
#slp-root .slp-fill{height:100%;width:0;background:#fff;border-radius:2px}
#slp-root .slp-ctl{display:flex;align-items:center;justify-content:center;gap:10px;margin-top:8px}
#slp-root .slp-ctl .slp-btn{height:34px;min-width:34px;font-size:14px}
#slp-root .slp-ctl .slp-play{height:40px;min-width:40px;background:#fff;color:#000;font-size:16px}
#slp-root .slp-time{font-size:11px;opacity:.6;font-variant-numeric:tabular-nums;width:40px}
#slp-root .slp-credit{font-size:10px;opacity:.45;text-align:center;margin-top:6px}
#slp-pill{position:fixed;z-index:99998;right:24px;bottom:24px;display:none;align-items:center;gap:8px;max-width:320px;padding:7px 14px 7px 7px;border-radius:999px;background:rgba(18,18,24,.82);backdrop-filter:blur(16px);border:1px solid rgba(255,255,255,.14);color:#fff;font:600 12px "Motiva Sans","Segoe UI",Arial,sans-serif;cursor:pointer;box-shadow:0 10px 30px rgba(0,0,0,.45)}
#slp-pill.slp-show{display:flex}
#slp-pill .slp-cover{width:26px;height:26px;border-radius:50%;background:#333 center/cover;animation:slp-spin 8s linear infinite;flex:none}
#slp-pill.slp-paused .slp-cover{animation-play-state:paused}
#slp-pill span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
@keyframes slp-spin{to{transform:rotate(360deg)}}
`;

const LANGS: [string, string][] = [
	['', 'Off'],
	['en', 'English'],
	['es', 'Spanish'],
	['fr', 'French'],
	['de', 'German'],
	['pt', 'Portuguese'],
	['it', 'Italian'],
	['ar', 'Arabic'],
	['fa', 'Persian'],
	['tr', 'Turkish'],
	['ru', 'Russian'],
	['hi', 'Hindi'],
	['ja', 'Japanese'],
	['ko', 'Korean'],
	['zh-CN', 'Chinese'],
];

const time = (ms: number) => {
	const s = Math.max(0, Math.floor(ms / 1000));
	return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

function withInterludes(lines: Line[]): Row[] {
	const out: Row[] = [];
	const GAP = 5000;
	if (lines.length && lines[0].start > GAP) out.push({ interlude: true, start: 0, end: lines[0].start - 250, index: -1 });
	lines.forEach((line, index) => {
		out.push({ ...line, index, interlude: false });
		const next = lines[index + 1];
		const endOfThis = Math.max(line.end, line.words.length ? line.words[line.words.length - 1].end : line.end);
		if (next && next.start - endOfThis > GAP) out.push({ interlude: true, start: endOfThis + 200, end: next.start - 250, index: -1 });
	});
	return out;
}

/** A dominant color of an image, brightened, for the tint and the glow. */
function accentOf(doc: Document, src: string, done: (color: string) => void) {
	if (!src) return;
	const img = doc.createElement('img');
	img.crossOrigin = 'anonymous';
	img.onload = () => {
		try {
			const canvas = doc.createElement('canvas');
			canvas.width = canvas.height = 24;
			const ctx = canvas.getContext('2d');
			if (!ctx) return;
			ctx.drawImage(img, 0, 0, 24, 24);
			const data = ctx.getImageData(0, 0, 24, 24).data;
			let best = [90, 90, 120];
			let score = -1;
			for (let i = 0; i < data.length; i += 4) {
				const [r, g, b] = [data[i], data[i + 1], data[i + 2]];
				const max = Math.max(r, g, b);
				const min = Math.min(r, g, b);
				const s = (max - min) * (max > 40 ? 1 : 0.2);
				if (s > score) {
					score = s;
					best = [r, g, b];
				}
			}
			const lift = 255 / Math.max(1, Math.max(...best));
			const [r, g, b] = best.map((c) => Math.round(Math.min(255, c * Math.min(1.6, lift))));
			done(`rgb(${r},${g},${b})`);
		} catch {
			// A cover that can't be read (cross-origin): keep the last color.
		}
	};
	img.src = src;
}

export class Panel {
	private root: HTMLDivElement;
	private pill: HTMLDivElement;
	private els: Record<string, HTMLElement> = {};
	private rows: Row[] = [];
	private rowEls: HTMLElement[] = [];
	private wordEls: { el: HTMLElement; w: Word }[][] = [];
	private current = -2;
	private now: NowPlaying | null = null;
	private raf = 0;
	private userScrollUntil = 0;
	private coverSrc = '';
	private alive = true;

	constructor(private doc: Document, private actions: PanelActions) {
		const style = doc.createElement('style');
		style.id = 'slp-style';
		style.textContent = CSS;
		doc.head.appendChild(style);
		this.root = doc.createElement('div');
		this.root.id = 'slp-root';
		this.root.innerHTML = `
			<div class="slp-bgimg"></div><div class="slp-tint"></div>
			<div class="slp-head">
				<div class="slp-cover"></div>
				<div class="slp-meta"><div class="slp-title">Spicy Lyrics</div><div class="slp-artist">Play something in Spotify</div></div>
				<button class="slp-btn" data-act="tools" title="Options">⋯</button>
				<button class="slp-btn" data-act="expand" title="Expand (Ctrl+Shift+L to hide)">⤢</button>
				<button class="slp-btn" data-act="close" title="Hide">✕</button>
			</div>
			<div class="slp-tools">
				<select data-act="translate" title="Translate"></select>
				<span class="slp-label">Size</span>
				<button class="slp-btn" data-act="smaller">A−</button><button class="slp-btn" data-act="bigger">A+</button>
				<span class="slp-label">Sync</span>
				<button class="slp-btn" data-act="earlier" title="Lyrics earlier">−</button><span data-el="offset">0.0s</span><button class="slp-btn" data-act="later" title="Lyrics later">+</button>
				<button class="slp-btn" data-act="pill" title="Show the mini player when hidden">Mini</button>
			</div>
			<div class="slp-lyrics"></div>
			<div class="slp-empty"></div>
			<div class="slp-foot">
				<div class="slp-bar"><div class="slp-fill"></div></div>
				<div class="slp-ctl">
					<span class="slp-time" data-el="pos">0:00</span>
					<button class="slp-btn" data-act="previous">⏮</button>
					<button class="slp-btn slp-play" data-act="toggle">▶</button>
					<button class="slp-btn" data-act="next">⏭</button>
					<span class="slp-time" data-el="dur" style="text-align:right">0:00</span>
				</div>
				<div class="slp-credit"></div>
			</div>`;
		this.pill = doc.createElement('div');
		this.pill.id = 'slp-pill';
		this.pill.innerHTML = `<div class="slp-cover"></div><span>♪ Lyrics</span>`;
		this.pill.title = 'Spicy Lyrics (Ctrl+Shift+L)';
		doc.body.appendChild(this.root);
		doc.body.appendChild(this.pill);
		for (const key of ['bgimg', 'cover', 'title', 'artist', 'lyrics', 'empty', 'fill', 'bar', 'credit', 'tools']) {
			this.els[key] = this.root.querySelector(`.slp-${key}`) as HTMLElement;
		}
		this.root.querySelectorAll('[data-el]').forEach((el) => (this.els[(el as HTMLElement).dataset.el as string] = el as HTMLElement));
		this.els.play = this.root.querySelector('.slp-play') as HTMLElement;
		const select = this.root.querySelector('select') as HTMLSelectElement;
		const chosen = settings.get('translate', '');
		select.innerHTML = LANGS.map(([code, name]) => `<option value="${code}"${code === chosen ? ' selected' : ''}>🌐 ${name}</option>`).join('');
		select.addEventListener('change', () => {
			settings.set('translate', select.value);
			this.actions.setTranslation(select.value);
		});
		this.root.addEventListener('click', (event) => this.onClick(event));
		this.pill.addEventListener('click', () => this.setOpen(true));
		this.els.bar.addEventListener('click', (event) => {
			const rect = this.els.bar.getBoundingClientRect();
			const share = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
			if (this.now?.durationMs) this.actions.control(`seek ${Math.round(share * this.now.durationMs)}`);
		});
		this.els.lyrics.addEventListener('wheel', () => (this.userScrollUntil = Date.now() + 3000), { passive: true });
		doc.addEventListener('keydown', this.onKey);
		this.dragging();
		this.restoreBox();
		this.applySize();
		this.applyOffsetLabel();
		this.root.classList.toggle('slp-expanded', settings.get('expanded', false));
		this.root.querySelector('[data-act="pill"]')?.classList.toggle('slp-on', settings.get('pill', true));
		this.setOpen(settings.get('open', true), false);
		this.loop();
	}

	get attached() {
		return this.alive && this.doc.body?.contains(this.root);
	}

	destroy() {
		this.alive = false;
		this.doc.defaultView?.cancelAnimationFrame(this.raf);
		this.doc.removeEventListener('keydown', this.onKey);
		this.root.remove();
		this.pill.remove();
		this.doc.getElementById('slp-style')?.remove();
	}

	private onKey = (event: KeyboardEvent) => {
		if (event.ctrlKey && event.shiftKey && (event.key === 'L' || event.key === 'l')) {
			event.preventDefault();
			this.setOpen(!this.root.classList.contains('slp-open'));
		}
	};

	private onClick(event: MouseEvent) {
		const target = (event.target as HTMLElement).closest('[data-act],[data-row]') as HTMLElement | null;
		if (!target) return;
		const act = target.dataset.act;
		if (target.dataset.row != null && !act) {
			const row = this.rows[Number(target.dataset.row)];
			if (row && !row.interlude) this.actions.control(`seek ${Math.max(0, Math.round(row.start - settings.get('offset', 0)))}`);
			this.userScrollUntil = 0;
			return;
		}
		switch (act) {
			case 'close':
				this.setOpen(false);
				break;
			case 'expand': {
				const on = !this.root.classList.contains('slp-expanded');
				this.root.classList.toggle('slp-expanded', on);
				settings.set('expanded', on);
				this.applySize();
				this.current = -2;
				break;
			}
			case 'tools':
				this.root.classList.toggle('slp-showtools');
				target.classList.toggle('slp-on', this.root.classList.contains('slp-showtools'));
				break;
			case 'smaller':
			case 'bigger': {
				const scale = Math.min(2, Math.max(0.6, settings.get('scale', 1) + (act === 'bigger' ? 0.1 : -0.1)));
				settings.set('scale', Math.round(scale * 10) / 10);
				this.applySize();
				break;
			}
			case 'earlier':
			case 'later':
				settings.set('offset', settings.get('offset', 0) + (act === 'later' ? 250 : -250));
				this.applyOffsetLabel();
				break;
			case 'pill': {
				const on = !settings.get('pill', true);
				settings.set('pill', on);
				target.classList.toggle('slp-on', on);
				this.refreshPill();
				break;
			}
			case 'toggle':
			case 'next':
			case 'previous':
				this.actions.control(act);
				break;
		}
	}

	setOpen(open: boolean, remember = true) {
		this.root.classList.toggle('slp-open', open);
		if (remember) settings.set('open', open);
		this.current = -2;
		this.refreshPill();
	}

	private refreshPill() {
		const show = !this.root.classList.contains('slp-open') && settings.get('pill', true) && !!this.now?.title;
		this.pill.classList.toggle('slp-show', show);
		this.pill.classList.toggle('slp-paused', !this.now?.playing);
	}

	private applySize() {
		const expanded = this.root.classList.contains('slp-expanded');
		const base = expanded ? 'clamp(30px, 3.6vw, 60px)' : '26px';
		const size = `calc(${base} * ${settings.get('scale', 1)})`;
		this.els.lyrics.style.fontSize = size;
		this.root.style.setProperty('--slp-size', size);
	}

	private applyOffsetLabel() {
		const offset = settings.get('offset', 0);
		this.els.offset.textContent = `${offset > 0 ? '+' : ''}${(offset / 1000).toFixed(2)}s`;
	}

	private dragging() {
		const head = this.root.querySelector('.slp-head') as HTMLElement;
		head.addEventListener('mousedown', (down) => {
			if ((down.target as HTMLElement).closest('button') || this.root.classList.contains('slp-expanded')) return;
			const rect = this.root.getBoundingClientRect();
			const dx = down.clientX - rect.left;
			const dy = down.clientY - rect.top;
			const view = this.doc.defaultView as Window;
			const move = (event: MouseEvent) => {
				const left = Math.min(view.innerWidth - 80, Math.max(0, event.clientX - dx));
				const top = Math.min(view.innerHeight - 60, Math.max(0, event.clientY - dy));
				Object.assign(this.root.style, { left: `${left}px`, top: `${top}px`, right: 'auto', bottom: 'auto' });
			};
			const up = () => {
				this.doc.removeEventListener('mousemove', move);
				this.doc.removeEventListener('mouseup', up);
				this.saveBox();
			};
			this.doc.addEventListener('mousemove', move);
			this.doc.addEventListener('mouseup', up);
		});
		this.root.addEventListener('mouseup', () => this.saveBox());
	}

	private saveBox() {
		if (this.root.classList.contains('slp-expanded')) return;
		const { left, top, width, height } = this.root.style;
		settings.set('box', { left, top, width: width || `${this.root.offsetWidth}px`, height: height || `${this.root.offsetHeight}px` });
	}

	private restoreBox() {
		const box = settings.get<Record<string, string> | null>('box', null);
		if (!box) return;
		if (box.left) Object.assign(this.root.style, { left: box.left, top: box.top, right: 'auto', bottom: 'auto' });
		if (box.width) Object.assign(this.root.style, { width: box.width, height: box.height });
	}

	setStatus(text: string) {
		this.els.empty.textContent = text;
		this.els.empty.style.display = text ? 'flex' : 'none';
	}

	setNowPlaying(now: NowPlaying | null) {
		this.now = now;
		this.els.title.textContent = now?.title || 'Spicy Lyrics';
		this.els.artist.textContent = now?.title ? now.artist : 'Play something in Spotify';
		this.els.play.textContent = now?.playing ? '⏸' : '▶';
		this.els.dur.textContent = time(now?.durationMs || 0);
		(this.pill.querySelector('span') as HTMLElement).textContent = now?.title ? `♪ ${now.title} · ${now.artist}` : '♪ Lyrics';
		if (!now?.title) {
			this.setLyrics(null);
			this.setStatus('Play something in Spotify (or any player) and its lyrics show here.');
		}
		this.refreshPill();
	}

	/** The cover: Apple Music's large artwork when found, else the player's own thumbnail. */
	setCover(primary: string, fallback: string) {
		const apply = (src: string) => {
			if (src === this.coverSrc) return;
			this.coverSrc = src;
			const css = src ? `url("${src}")` : '';
			this.els.cover.style.backgroundImage = css;
			this.els.bgimg.style.backgroundImage = css;
			(this.pill.querySelector('.slp-cover') as HTMLElement).style.backgroundImage = css;
			accentOf(this.doc, src, (color) => {
				this.root.style.setProperty('--slp-accent', color);
				this.root.style.setProperty('--slp-glow', color);
			});
		};
		if (!primary) return apply(fallback);
		const probe = this.doc.createElement('img');
		probe.onload = () => apply(primary);
		probe.onerror = () => apply(fallback);
		probe.src = primary;
		if (!this.coverSrc) apply(fallback);
	}

	setLyrics(lyrics: Lyrics | null, translated: string[] | null = null) {
		this.rows = lyrics ? withInterludes(lyrics.lines) : [];
		this.current = -2;
		this.wordEls = [];
		const box = this.els.lyrics;
		box.textContent = '';
		this.rowEls = this.rows.map((row, r) => {
			const el = this.doc.createElement('div');
			el.dataset.row = String(r);
			this.wordEls[r] = [];
			if (row.interlude) {
				el.className = 'slp-line slp-interlude';
				for (let d = 0; d < 3; d += 1) {
					const dot = this.doc.createElement('span');
					dot.className = 'slp-dot';
					el.appendChild(dot);
				}
			} else {
				const line = row as Line & { index: number };
				el.className = 'slp-line' + (line.opposite ? ' slp-opposite' : '');
				const addWords = (words: Word[], into: HTMLElement) =>
					words.forEach((w, i) => {
						const span = this.doc.createElement('span');
						span.className = 'slp-word';
						span.textContent = w.text;
						if (i > 0 && !w.joined) into.appendChild(this.doc.createTextNode(' '));
						into.appendChild(span);
						this.wordEls[r].push({ el: span, w });
					});
				addWords(line.words, el);
				for (const words of line.background) {
					const bg = this.doc.createElement('div');
					bg.className = 'slp-bgv';
					if (words.length) addWords(words, bg);
					else bg.textContent = joinWords(words);
					el.appendChild(bg);
				}
				const tr = translated?.[line.index];
				if (tr) {
					const t = this.doc.createElement('div');
					t.className = 'slp-tr';
					t.textContent = tr;
					el.appendChild(t);
				}
			}
			box.appendChild(el);
			return el;
		});
		if (lyrics) {
			this.setStatus(lyrics.lines.length ? '' : 'No synced lyrics for this song.');
			this.els.credit.textContent = lyrics.source ? `Lyrics: ${lyrics.source}${lyrics.synced === 'line' ? ' (line synced)' : ''}` : '';
		} else {
			this.els.credit.textContent = '';
		}
		box.scrollTop = 0;
	}

	private loop = () => {
		if (!this.alive) return;
		const view = this.doc.defaultView;
		if (!view) return;
		this.raf = view.requestAnimationFrame(this.loop);
		if (!this.root.classList.contains('slp-open') || !this.now) return;
		const raw = this.now.position();
		this.els.pos.textContent = time(raw);
		this.els.fill.style.width = `${this.now.durationMs ? Math.min(100, (raw / this.now.durationMs) * 100) : 0}%`;
		if (!this.rows.length) return;
		const position = raw + settings.get('offset', 0) + 120; // a touch early reads as on time
		let current = -1;
		for (let index = 0; index < this.rows.length; index += 1) if (this.rows[index].start <= position) current = index;
		if (current !== this.current) {
			const previous = this.current;
			this.current = current;
			this.rowEls.forEach((el, index) => {
				el.classList.toggle('slp-active', index === current);
				el.classList.toggle('slp-past', index < current);
			});
			if (previous >= 0) this.resetWords(previous);
			if (current >= 0 && Date.now() > this.userScrollUntil) {
				const el = this.rowEls[current];
				const box = this.els.lyrics;
				const target = el.offsetTop - box.clientHeight / 2 + el.offsetHeight / 2;
				box.scrollTo({ top: target, behavior: 'smooth' });
			}
		}
		if (current < 0) return;
		const row = this.rows[current];
		if (row.interlude) {
			const progress = Math.min(1, Math.max(0, (position - row.start) / Math.max(1, row.end - row.start)));
			this.rowEls[current].querySelectorAll('.slp-dot').forEach((dot, d) => {
				(dot as HTMLElement).style.opacity = String(0.3 + 0.7 * Math.min(1, Math.max(0, progress * 3 - d)));
			});
			return;
		}
		for (const { el, w } of this.wordEls[current]) {
			const span = Math.max(1, w.end - w.start);
			const progress = Math.min(1, Math.max(0, (position - w.start) / span));
			const singing = progress > 0 && progress < 1;
			const long = span > 900 && singing;
			el.style.setProperty('--p', `${(progress * 100).toFixed(1)}%`);
			const lift = singing ? -0.06 * Math.sin(progress * Math.PI) : 0;
			el.style.transform = `translateY(${lift}em) scale(${long ? 1 + 0.06 * Math.sin(progress * Math.PI) : 1})`;
			el.classList.toggle('slp-singing', singing);
			el.classList.toggle('slp-long', long);
		}
	};

	private resetWords(row: number) {
		for (const { el } of this.wordEls[row] || []) {
			el.style.removeProperty('--p');
			el.style.transform = '';
			el.classList.remove('slp-singing', 'slp-long');
		}
	}
}
