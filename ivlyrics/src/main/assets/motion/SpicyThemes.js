// Spicy Lyrics Plus themes for motion lyrics. Run by the app on the page before each song
// (SpicyBridge.motionSong), followed by `window.spicyMotionTheme = {...}` or `= null`.
//
// It wraps the page's own director: the built-in direction is composed as usual (its timing,
// sections, emphasis and pacing stay), then each shot's look is re-picked from the theme's
// pools, seeded by the song so a song always plays the same way in a theme. A theme is:
//   { id, mood, palette: ["#rrggbb", "#rrggbb"], layouts, enters, fx, accents, types, motifs,
//     upper, bars, rule, serif, tilt }
// Every list is optional; names the page doesn't know are skipped.
(function SpicyMotionThemes(root) {
	"use strict";
	if (root.spicyMotionThemesInstalled) return;
	const base = root.ivLyricsMotionDirector;
	if (!base || typeof base.localDirection !== "function") return;
	root.spicyMotionThemesInstalled = true;

	const JOINED_RE = /[\p{Script=Arabic}\p{Script=Hebrew}\p{Script=Devanagari}\p{Script=Bengali}]/u;
	const HEX_RE = /^#[0-9a-f]{6}$/i;
	const hash = (text) => {
		let value = 2166136261;
		const source = String(text ?? "");
		for (let index = 0; index < source.length; index += 1) {
			value ^= source.charCodeAt(index);
			value = Math.imul(value, 16777619);
		}
		return value >>> 0;
	};
	const random = (seed) => {
		let state = (seed >>> 0) || 1;
		return () => {
			state = (state + 0x6D2B79F5) >>> 0;
			let value = state;
			value = Math.imul(value ^ (value >>> 15), value | 1);
			value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
			return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
		};
	};
	const known = (list, allowed) => (Array.isArray(list) ? list : []).filter((name) => allowed.includes(name));
	const pick = (rand, list, fallback, avoid) => {
		if (!list.length) return fallback;
		// Not the same as the shot before, where there's a choice: the edit keeps moving.
		const choices = list.length > 1 && avoid ? list.filter((name) => name !== avoid) : list;
		return choices[Math.floor(rand() * choices.length)] ?? fallback;
	};
	const motifNames = () => root.ivLyricsMotionShapes?.NAMES || [];

	const themed = (model, options = {}) => {
		const direction = base.localDirection(model, options);
		const theme = root.spicyMotionTheme;
		if (!theme || typeof theme !== "object" || !direction || !Array.isArray(direction.shots)) return direction;
		const lines = Array.isArray(model) ? model : [];
		const rand = random((Number(options.seed) >>> 0) ^ hash(theme.id));
		const layouts = known(theme.layouts, base.LAYOUTS);
		const enters = known(theme.enters, base.ENTERS);
		const fx = known(theme.fx, base.FX);
		const accents = known(theme.accents, base.ACCENTS);
		const types = known(theme.types, base.TYPES);
		const motifs = known(theme.motifs, motifNames());
		const tilt = Number.isFinite(theme.tilt) ? theme.tilt : 1;
		let last = {};
		const looks = new Map();
		const shots = direction.shots.map((shot, index) => {
			// A section that comes back gets the look it had the first time, as the director does.
			const count = shot.to - shot.from + 1;
			const text = lines.slice(shot.from, shot.to + 1).map((line) => line?.text || "").join(" ");
			const key = text.trim().toLowerCase() || null;
			if (key !== null && looks.has(key)) return { ...shot, ...looks.get(key) };
			const fits = layouts.filter((name) => (base.MAX_LINES[name] || 6) >= count);
			const shotFx = JOINED_RE.test(text) ? fx.filter((name) => !base.LETTER_FX.includes(name)) : fx;
			const look = {
				layout: pick(rand, fits, shot.layout, last.layout),
				// The first shot never cuts in from nothing.
				enter: pick(rand, index === 0 ? enters.filter((name) => name !== "cut") : enters, shot.enter, last.enter),
				fx: pick(rand, shotFx, shot.fx, last.fx),
				accent: pick(rand, accents, shot.accent, last.accent),
				type: pick(rand, types, shot.type),
			};
			if (look.type === "serif") look.serif = true;
			else if (typeof theme.serif === "boolean") look.serif = theme.serif && rand() < 0.25;
			if (typeof theme.upper === "boolean") look.upper = theme.upper;
			if (typeof theme.bars === "boolean") look.bars = theme.bars && rand() < 0.6;
			if (typeof theme.rule === "boolean") look.rule = theme.rule && rand() < 0.5;
			if (Number.isFinite(shot.dutch)) look.dutch = Math.round(Math.max(-12, Math.min(12, shot.dutch * tilt)));
			if (motifs.length) {
				const chosen = [];
				const wanted = Math.min(motifs.length, Array.isArray(shot.motifs) && shot.motifs.length ? shot.motifs.length : 2);
				while (chosen.length < wanted) {
					const name = motifs[Math.floor(rand() * motifs.length)];
					if (!chosen.includes(name)) chosen.push(name);
				}
				look.motifs = chosen;
			}
			last = look;
			if (key !== null) looks.set(key, look);
			return { ...shot, ...look };
		});
		const palette = (Array.isArray(theme.palette) ? theme.palette : []).filter((value) => HEX_RE.test(String(value))).slice(0, 3);
		return {
			...direction,
			mood: base.MOODS.includes(theme.mood) ? theme.mood : direction.mood,
			palette: palette.length ? palette.map((value) => value.toLowerCase()) : direction.palette,
			shots,
		};
	};

	root.ivLyricsMotionDirector = Object.freeze({ ...base, localDirection: themed });
})(typeof window !== "undefined" ? window : globalThis);
