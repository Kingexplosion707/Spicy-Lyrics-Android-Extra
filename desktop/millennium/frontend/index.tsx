// Spicy Lyrics Plus for Steam: Spicy-style word-by-word lyrics for whatever plays on the PC
// (Spotify first), in a floating panel inside the Steam window.

import { definePlugin, Millennium } from 'millennium';
import { useState } from 'react';
import { leadArtist, Lyrics, lyricsFor, translate } from './lyrics';
import { NowPlaying, Panel, settings } from './panel';

// Calls into backend/main.lua (its ---@ffi functions), each given up after a while so a stuck
// backend shows up as a message instead of a panel that never changes.
const call = <T,>(run: () => Promise<T>, ms = 15000): Promise<T> =>
	Promise.race([run(), new Promise<T>((_, reject) => setTimeout(() => reject(new Error('the plugin backend did not answer')), ms))]);
const nowPlaying = () => call(() => backend.now_playing(), 8000);
const control = (command: string) => call(() => backend.control(command));
const fetchText = (url: string) => call(() => backend.fetch_text(url), 20000);

const fetcher = async (url: string) => {
	try {
		return (await fetchText(url)) || '';
	} catch {
		return '';
	}
};

type State = {
	title?: string;
	artist?: string;
	album?: string;
	durationMs?: number;
	positionMs?: number;
	updatedAt?: number;
	now?: number;
	playing?: boolean;
	cover?: string;
};

const panels = new Set<Panel>();
let now: NowPlaying | null = null;
let songKey = '';
let lyrics: Lyrics | null = null;
let translation: string[] | null = null;
let polling = 0;
let lastMessage = '';

const each = (fn: (panel: Panel) => void) => {
	for (const panel of Array.from(panels)) {
		if (!panel.attached) {
			panels.delete(panel);
			continue;
		}
		fn(panel);
	}
};

async function applyTranslation(target: string) {
	translation = null;
	if (target && lyrics?.lines.length) {
		try {
			translation = await translate(fetcher, lyrics.lines, target);
		} catch {
			translation = null;
		}
	}
	each((panel) => panel.setLyrics(lyrics, translation));
}

async function loadLyrics(key: string, state: State) {
	lyrics = null;
	translation = null;
	each((panel) => {
		panel.setLyrics(null);
		panel.setStatus('Finding lyrics…');
	});
	const track = { title: state.title || '', artist: state.artist || '', album: state.album || '', durationMs: state.durationMs || 0 };
	const found = await lyricsFor(fetcher, track);
	if (key !== songKey) return;
	lyrics = found;
	each((panel) => {
		panel.setLyrics(found);
		panel.setCover(found.artwork || '', state.cover || '');
		if (!found.lines.length) panel.setStatus(`No synced lyrics found for “${track.title}” by ${leadArtist(track.artist)}.`);
	});
	const target = settings.get('translate', '');
	if (target) await applyTranslation(target);
}

async function poll() {
	let state: State & { error?: string; sessions?: number } = {};
	let problem = '';
	try {
		const text = await nowPlaying();
		state = typeof text === 'string' ? JSON.parse(text || '{}') : (text as any) || {};
	} catch (error) {
		problem = `Couldn't get what's playing: ${(error as Error)?.message || error}`;
	}
	if (state.error) problem = state.error;
	if (!state.title) {
		const message =
			problem ||
			(state.sessions === 0
				? 'Nothing is playing. Play something in Spotify (or any player that shows in Windows’ media controls).'
				: 'Play something in Spotify and its lyrics show here.');
		if (now || message !== lastMessage) {
			now = null;
			songKey = '';
			lastMessage = message;
			each((panel) => panel.setNowPlaying(null, message));
		}
		return;
	}
	lastMessage = '';
	const anchor = state.positionMs || 0;
	const at = state.updatedAt && state.updatedAt > 0 ? state.updatedAt : state.now || Date.now();
	const duration = state.durationMs || 0;
	const playing = !!state.playing;
	now = {
		title: state.title,
		artist: state.artist || '',
		album: state.album || '',
		durationMs: duration,
		playing,
		cover: state.cover || '',
		position: () => {
			const value = playing ? anchor + (Date.now() - at) : anchor;
			return duration > 0 ? Math.min(duration, Math.max(0, value)) : Math.max(0, value);
		},
	};
	each((panel) => panel.setNowPlaying(now));
	const key = `${state.artist}|${state.title}`;
	if (key !== songKey) {
		songKey = key;
		each((panel) => panel.setCover('', state.cover || ''));
		loadLyrics(key, state);
	}
}

function startPolling() {
	if (polling) return;
	poll();
	polling = window.setInterval(poll, 700);
}

/** Puts the panel into a Steam window's document (the main window only). */
function attach(popup: any) {
	try {
		const name = String(popup?.m_strName || popup?.name || '');
		const title = String(popup?.m_strTitle || popup?.title || '');
		const win: Window | undefined = popup?.m_popup || popup?.window;
		const doc = win?.document;
		if (!doc?.body) return;
		const main = name.startsWith('SP Desktop') || title === 'Steam';
		if (!main) return;
		for (const panel of Array.from(panels)) if ((panel as any).doc === doc && panel.attached) return;
		doc.getElementById('slp-root')?.remove();
		doc.getElementById('slp-pill')?.remove();
		doc.getElementById('slp-style')?.remove();
		const panel = new Panel(doc, {
			control: (command) => {
				control(command).then(() => setTimeout(poll, 250));
			},
			setTranslation: (target) => applyTranslation(target),
		});
		panels.add(panel);
		panel.setNowPlaying(now, lastMessage);
		if (lyrics) panel.setLyrics(lyrics, translation);
		startPolling();
	} catch (error) {
		console.error('[Spicy Lyrics] attach failed', error);
	}
}

function attachExisting() {
	const manager = (window as any).g_PopupManager;
	const popups = manager?.m_mapPopups;
	if (popups?.forEach) popups.forEach((popup: any) => attach(popup));
	else if (manager?.GetExistingPopup) attach(manager.GetExistingPopup('SP Desktop_uid0'));
}

const Settings = () => {
	const [open, setOpen] = useState<boolean>(settings.get('open', true));
	const toggle = () => {
		const next = !open;
		setOpen(next);
		each((panel) => panel.setOpen(next));
	};
	return (
		<div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '8px 0', lineHeight: 1.5 }}>
			<div>
				Lyrics for what plays on this PC (Spotify first, or any app in Windows' media controls), word by word, in a panel in the
				Steam window. Drag it by its top, resize it from its corner, ⤢ fills the window, ⋯ has translation, text size and sync.
			</div>
			<div>
				Shortcut: <b>Ctrl+Shift+L</b> shows or hides it.
			</div>
			<div>
				<button className="DialogButton" style={{ width: 'auto', padding: '0 16px' }} onClick={toggle}>
					{open ? 'Hide the lyrics panel' : 'Show the lyrics panel'}
				</button>
			</div>
			<div style={{ opacity: 0.6, fontSize: 12 }}>
				Lyrics from RMM Revival (Apple Music and Spicy Lyrics syncs) and LRCLIB. Part of Spicy Lyrics Plus.
			</div>
		</div>
	);
};

const Icon = () => <span style={{ fontSize: 16, marginRight: 5 }}>♪</span>;

export default definePlugin(() => {
	try {
		Millennium.AddWindowCreateHook?.((popup: any) => attach(popup));
	} catch (error) {
		console.error('[Spicy Lyrics] window hook failed', error);
	}
	attachExisting();
	// The main window can be made after this runs, or rebuilt: keep looking (cheap).
	window.setInterval(attachExisting, 3000);
	return {
		title: 'Spicy Lyrics Plus',
		icon: <Icon />,
		content: <Settings />,
	};
});
