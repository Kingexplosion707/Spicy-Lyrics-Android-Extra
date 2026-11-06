# Canvas Worker

The Cloudflare Worker at `canvas.tx24.dev` that Spicy Lyrics Mobile 0.14.4 asks for Spotify Canvas. Later versions ask Spicy Lyrics' Canvas service (`regional.canvasstore.slnode.spicylyrics.org`) directly, and this Worker forwards to that service too, so keep it up until 0.14.4 is gone. It answers `GET /api/canvas?trackId=<Spotify track ID>` with `{"canvasesList":[{"canvasUrl":"…mp4","trackUri":"spotify:track:…"}]}`, the shape 0.14.4 reads, and translates the canvaslist answers (`{"data":{"<id>":{"url":…}|null}}`) into it.

For each lookup it:

1. Answers from the data centre's cache if the track was looked up there in the last day (found or not).
2. Otherwise asks each upstream in `UPSTREAMS` (default: Spicy Lyrics' canvaslist) in turn, then once more after 0.6 s if all of them failed and there is time left. One lookup spends at most 5 seconds upstream, inside the app's 6-second timeout.
3. If every upstream is still failing, serves the canvas remembered in KV (for 30 days, found canvases only) with `X-Canvas-Stale: 1`. If nothing is remembered, it returns a 502 so the app treats it as "couldn't ask" rather than "no canvas".

No Spotify account or secret is involved. The Worker sees only track IDs.

## Deploy

```
cd server/canvas
npm install
npx wrangler login
npx wrangler kv namespace create CANVAS   # only on a new account: put the id in wrangler.toml
npx wrangler deploy
```

It's served at `https://canvas.tx24.dev` (the `routes` entry in `wrangler.toml`; the domain's DNS must be on the same Cloudflare account), and `workers_dev = false` keeps the `workers.dev` URL off. 0.14.4 calls `https://canvas.tx24.dev/api/canvas`.

The free plan covers this: 100,000 requests a day, plus 100,000 KV reads and 1,000 KV writes a day. KV is written only for newly found canvases. If the writes run out, the Worker keeps answering and only stops remembering new ones until the next day. A rate-limiting rule on the route (Security → WAF → Rate limiting rules, one is free) keeps scrapers from eating the quota.

## Test

```
npm test          # node's test runner, no network
npm run typecheck
```
