/**
 * The Canvas address 0.14.4 asks. It answers `GET /api/canvas?trackId=<id>` with
 * `{"canvasesList":[{"canvasUrl":"…mp4","trackUri":"…"}]}`, the shape that version reads, by asking
 * each upstream in turn (retrying once after a server error) and remembering the answers. Later
 * versions ask Spicy Lyrics' Canvas service directly; this forwards to it too.
 *
 * - every answer, canvas or none, in the data centre's cache for [EDGE_TTL_S];
 * - found canvases in KV (global) for [KV_TTL_S], served when every upstream is failing.
 *
 * Upstream failures are 502, never an empty list, so the app tells "no canvas" from "couldn't ask".
 */

export interface Env {
  /** Found canvases by track ID; optional, without it only the per-data-centre cache is kept. */
  CANVAS?: KVNamespace;
  /**
   * Comma-separated lookup services, tried in order. One whose path ends in `/provider/canvaslist`
   * is asked as `<url>/<id>` and answers `{"data":{"<id>":{"url":…}|null}}`; any other as
   * `<url>?trackId=<id>`, answering `{"canvasesList":[…]}`.
   */
  UPSTREAMS?: string;
}

export interface Deps {
  fetch: (input: string, init?: RequestInit) => Promise<Response>;
  /** The data centre's cache, or null to skip it (tests). */
  cache: Cache | null;
  sleep: (ms: number) => Promise<void>;
  now: () => number;
  /** Runs work after the response is sent. */
  waitUntil: (promise: Promise<unknown>) => void;
}

export const DEFAULT_UPSTREAMS = ["https://regional.canvasstore.slnode.spicylyrics.org/provider/canvaslist"];
export const EDGE_TTL_S = 24 * 60 * 60;
export const KV_TTL_S = 30 * 24 * 60 * 60;
/** How long one lookup may spend upstream in all, inside the app's 6-second timeout. */
const BUDGET_MS = 5000;
const UPSTREAM_TIMEOUT_MS = 4000;
const RETRY_DELAY_MS = 600;
/** Names this Worker to the upstreams, so one can allow it past bot protection. */
const USER_AGENT = "SpicyLyricsMobile-CanvasWorker/1 (+https://canvas.tx24.dev)";

export interface Canvas {
  canvasUrl: string;
  trackUri: string;
}

export async function handle(request: Request, env: Env, deps: Deps): Promise<Response> {
  const url = new URL(request.url);
  if (request.method !== "GET" && request.method !== "HEAD") return json({ error: "Method not allowed" }, 405);
  if (url.pathname !== "/api/canvas") return json({ error: "Not found" }, 404);
  const trackId = url.searchParams.get("trackId") ?? "";
  if (!isTrackId(trackId)) return json({ error: "trackId must be a Spotify track ID" }, 400);

  const cacheKey = new Request(`${url.origin}/api/canvas?trackId=${trackId}`);
  const cached = await deps.cache?.match(cacheKey);
  if (cached) return cached;

  const answer = await lookUp(trackId, upstreams(env), deps);
  if (answer === "invalid") return json({ error: "trackId was refused upstream" }, 400);
  if (answer === null) {
    const remembered = await readKv(env, trackId);
    if (remembered) return json({ canvasesList: [remembered] }, 200, { "X-Canvas-Stale": "1" });
    return json({ error: "Canvas lookup services are failing" }, 502);
  }

  const response = json({ canvasesList: answer }, 200, { "Cache-Control": `public, max-age=${EDGE_TTL_S}` });
  if (deps.cache) deps.waitUntil(deps.cache.put(cacheKey, response.clone()));
  if (answer.length > 0 && env.CANVAS) {
    deps.waitUntil(env.CANVAS.put(trackId, JSON.stringify(answer[0]), { expirationTtl: KV_TTL_S }).catch(() => {}));
  }
  return response;
}

/**
 * The canvases from the first upstream that answers: a list (empty for none), "invalid" when one
 * refused the ID as a bad request, or null when none could answer. A second pass runs only while
 * at least a second of [BUDGET_MS] is left, and no ask outlasts what's left of it.
 */
async function lookUp(trackId: string, urls: string[], deps: Deps): Promise<Canvas[] | "invalid" | null> {
  const deadline = deps.now() + BUDGET_MS;
  for (let pass = 0; pass < 2; pass++) {
    if (pass > 0) {
      if (deadline - deps.now() < RETRY_DELAY_MS + 1000) break;
      await deps.sleep(RETRY_DELAY_MS);
    }
    for (const base of urls) {
      const left = deadline - deps.now();
      if (left <= 0) return null;
      const answer = await askOne(base, trackId, deps, Math.min(UPSTREAM_TIMEOUT_MS, left)).catch(() => null);
      if (answer !== null) return answer;
    }
  }
  return null;
}

async function askOne(base: string, trackId: string, deps: Deps, timeoutMs: number): Promise<Canvas[] | "invalid" | null> {
  const target = new URL(base);
  const canvaslist = target.pathname.replace(/\/+$/, "").endsWith("/provider/canvaslist");
  if (canvaslist) target.pathname = `${target.pathname.replace(/\/+$/, "")}/${trackId}`;
  else target.searchParams.set("trackId", trackId);
  const response = await deps.fetch(target.toString(), {
    headers: { Accept: "application/json", "User-Agent": USER_AGENT },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (response.status === 400) return "invalid";
  if (!response.ok) {
    // Shows in `wrangler tail`: which upstream refused, how, and with what page.
    const body = (await response.text().catch(() => "")).slice(0, 300);
    console.log(`upstream ${target.host} answered ${response.status} ${response.headers.get("cf-mitigated") ?? ""} ${body}`);
    return null;
  }
  const body = await response.json();
  return canvaslist ? parseCanvaslist(body, trackId) : parse(body, trackId);
}

/**
 * The canvas in a canvaslist answer: `{"data":{"<id>":{"url":"…mp4",…}}}`, or `"<id>":null` for
 * none. An image canvas counts as none. Null (a failure) when the body has no `data`, no entry for
 * [trackId], or an `{"error":…}` entry for it.
 */
export function parseCanvaslist(body: unknown, trackId: string): Canvas[] | null {
  const data = (body as { data?: unknown } | null)?.data;
  if (!data || typeof data !== "object" || !(trackId in data)) return null;
  const entry = (data as Record<string, unknown>)[trackId] as { url?: unknown; error?: unknown } | null;
  if (entry === null) return [];
  if (typeof entry !== "object" || entry.error !== undefined) return null;
  const url = typeof entry.url === "string" ? entry.url : "";
  return isVideoUrl(url) ? [{ canvasUrl: url, trackUri: `spotify:track:${trackId}` }] : [];
}

/**
 * The canvases in an upstream answer, `{"canvasesList":[…]}` or the same under `data`, keeping
 * HTTPS MP4s for [trackId]. Null when the body isn't that shape, which counts as a failure.
 */
export function parse(body: unknown, trackId: string): Canvas[] | null {
  const root = body as { canvasesList?: unknown; data?: { canvasesList?: unknown } } | null;
  const list = root?.canvasesList ?? root?.data?.canvasesList;
  if (!Array.isArray(list)) return null;
  const out: Canvas[] = [];
  for (const entry of list) {
    const canvasUrl = typeof entry?.canvasUrl === "string" ? entry.canvasUrl : "";
    const trackUri = typeof entry?.trackUri === "string" ? entry.trackUri : "";
    if (trackUri && trackUri.split(":").pop() !== trackId) continue;
    if (!isVideoUrl(canvasUrl)) continue;
    out.push({ canvasUrl, trackUri: trackUri || `spotify:track:${trackId}` });
  }
  return out;
}

async function readKv(env: Env, trackId: string): Promise<Canvas | null> {
  if (!env.CANVAS) return null;
  try {
    const value = await env.CANVAS.get(trackId);
    if (!value) return null;
    const canvas = JSON.parse(value) as Canvas;
    return isVideoUrl(canvas.canvasUrl) ? canvas : null;
  } catch {
    return null;
  }
}

function upstreams(env: Env): string[] {
  const listed = (env.UPSTREAMS ?? "").split(",").map((s) => s.trim()).filter((s) => s.startsWith("https://"));
  return listed.length > 0 ? listed : DEFAULT_UPSTREAMS;
}

/** A Spotify track ID: 22 base-62 characters. */
export function isTrackId(id: string): boolean {
  return /^[A-Za-z0-9]{22}$/.test(id);
}

function isVideoUrl(url: string): boolean {
  return url.startsWith("https://") && url.split("?")[0].toLowerCase().endsWith(".mp4");
}

function json(body: unknown, status: number, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...(status === 200 ? {} : { "Cache-Control": "no-store" }),
      ...headers,
    },
  });
}
