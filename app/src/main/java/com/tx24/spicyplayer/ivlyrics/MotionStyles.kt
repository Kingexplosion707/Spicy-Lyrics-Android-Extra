package com.tx24.spicyplayer.ivlyrics

import com.tx24.spicyplayer.lyrics.motion.MotionTheme
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import java.util.Locale

/*
 * Spicy's look over ivLyrics' motion page, as CSS and a little script sent into it: the shade,
 * soft letterbox bars, the Spicy word fill and glow (when blended) and the themes.
 */
internal object MotionStyles {
    /** Everything Spicy lays over the page, as one stylesheet. */
    fun css(shade: Float, blend: Boolean, theme: MotionTheme): String = buildString {
        append(".ivlyrics-motion-scrim{opacity:").append("%.2f".format(Locale.US, shade.coerceIn(0f, 1f))).append("!important}")
        append(".ivlyrics-motion-hud{display:none!important}")
        // The page's cinematic bars as soft shadows that melt into the background, not black slabs.
        append(".ivlyrics-motion-bar{background:linear-gradient(to bottom,rgba(0,0,0,.42),rgba(0,0,0,0))!important;height:14%!important}")
        append(".ivlyrics-motion-bar.is-bottom{background:linear-gradient(to top,rgba(0,0,0,.42),rgba(0,0,0,0))!important}")
        if (blend) append(BLEND)
        append(THEMES[theme].orEmpty())
    }

    /**
     * Spicy's word fill: the word being sung fills from left to right over its own time (set per
     * word by [hookScript]) with Spicy's soft glow; sung words keep a gentle glow. The page's own
     * entrances, layouts and camera stay as they are.
     */
    private const val BLEND = """
@keyframes spicy-fill{from{background-position:100% 0}to{background-position:0 0}}
.ivlyrics-motion-word.is-active .ivlyrics-motion-solid{
 background-image:linear-gradient(90deg,currentColor 0%,currentColor 46%,rgba(255,255,255,.34) 54%,rgba(255,255,255,.34) 100%)!important;
 background-size:220% 100%!important;-webkit-background-clip:text!important;background-clip:text!important;
 -webkit-text-fill-color:transparent!important;text-shadow:none!important;
 animation:spicy-fill var(--spicy-d,.45s) linear forwards!important;
 filter:drop-shadow(0 0 .16em rgba(255,255,255,.55)) drop-shadow(0 0 .5em color-mix(in srgb,var(--iv-motion-accent,#fff) 45%,transparent))!important}
.ivlyrics-motion-word .ivlyrics-motion-solid{text-shadow:0 0 .28em rgba(255,255,255,.28),0 .04em .2em rgba(0,0,0,.3)}
.ivlyrics-motion-word.is-active{transform-origin:50% 60%}
"""

    /**
     * Installs the hook that gives each word its fill time: when the page marks a word as being
     * sung, it finds that word in the song's timing (sent by [wordsScript]) at the position the
     * app last reported and sets how long its fill takes. Installed once; harmless again.
     */
    val hookScript = """
(function(){if(window.__spicyHook||!document.body)return;window.__spicyHook=1;
var S=window.__spicy=window.__spicy||{w:[],p:0,a:0,pl:false};
function now(){return S.p+(S.pl?performance.now()-S.a:0);}
function dur(){var t=now(),w=S.w,lo=0,hi=w.length-1,f=-1;while(lo<=hi){var m=(lo+hi)>>1;if(w[m][0]<=t+90){f=m;lo=m+1;}else hi=m-1;}
 if(f<0)return 450;var e=w[f][1];return Math.max(120,Math.min(4000,e-t));}
new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var el=ms[i].target;
 if(el.classList&&el.classList.contains('ivlyrics-motion-word')&&el.classList.contains('is-active')&&!el.__spicyOn){el.__spicyOn=1;el.style.setProperty('--spicy-d',dur()+'ms');}
 else if(el.classList&&!el.classList.contains('is-active'))el.__spicyOn=0;}})
 .observe(document.body,{subtree:true,attributes:true,attributeFilter:['class']});})()
""".trimIndent()

    /** The song's word timings for the hook, sorted by start. */
    fun wordsScript(lines: List<Line>): String {
        val words = lines.filter { it.role != LineRole.INTERLUDE }.flatMap { it.words }.sortedBy { it.startMs }
        return "window.__spicy=window.__spicy||{w:[],p:0,a:0,pl:false};window.__spicy.w=[" +
            words.joinToString(",") { "[${it.startMs},${maxOf(it.endMs, it.startMs + 1)}]" } + "];"
    }

    /** Where the song is, for the hook's fill times. */
    fun positionScript(positionMs: Long, playing: Boolean) =
        "if(window.__spicy){__spicy.p=$positionMs;__spicy.a=performance.now();__spicy.pl=$playing;}"

    /** Lays [css] into the page, replacing what was laid before. */
    fun styleScript(css: String) =
        "(function(){if(!document.head)return;var s=document.getElementById('spicy-look');" +
            "if(!s){s=document.createElement('style');s.id='spicy-look';document.head.appendChild(s);}s.textContent=${js(css)};})()"

    private fun js(text: String) = buildString {
        append('"')
        for (c in text) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> {}
            '<' -> append("\\u003c")
            else -> append(c)
        }
        append('"')
    }

    /** A full-screen layer over the page for a theme's texture (scanlines, grain, a tint). */
    private fun layer(css: String) = "body::after{content:'';position:fixed;inset:0;pointer-events:none;z-index:50;$css}"

    private const val GRAIN = "url(\"data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.55'/%3E%3C/svg%3E\")"

    /** Sets both of the page's accent colours everywhere (the theme's palette over the cover's). */
    private fun accents(a: String, b: String) = "*{--iv-motion-accent:$a!important;--iv-motion-accent-2:$b!important;--iv-motion-deep:${a}!important;--iv-motion-deep-2:${b}!important}"

    private val SOLID = ".ivlyrics-motion-solid"

    private val THEMES: Map<MotionTheme, String> = mapOf(
        MotionTheme.Neon to accents("#00f0ff", "#ff2bd6") +
            "$SOLID{text-shadow:0 0 .12em #fff,0 0 .35em var(--iv-motion-accent),0 0 .9em var(--iv-motion-accent)!important}" +
            "@keyframes neon-flick{0%,92%,100%{opacity:1}93%{opacity:.55}95%{opacity:.9}97%{opacity:.6}}" +
            ".ivlyrics-motion-word.is-emph $SOLID{animation:neon-flick 3.2s infinite}" +
            layer("background:radial-gradient(ellipse at 50% 120%,rgba(255,43,214,.18),transparent 60%)"),
        MotionTheme.Synthwave to accents("#ff6ec7", "#ffd319") +
            "$SOLID{background-image:linear-gradient(180deg,#ffe36e 0%,#ff6ec7 55%,#7b2ff7 100%);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;filter:drop-shadow(0 0 .2em rgba(255,110,199,.6))}" +
            layer("background:repeating-linear-gradient(to bottom,rgba(0,0,0,.16) 0 2px,transparent 2px 4px),linear-gradient(to top,rgba(123,47,247,.28),transparent 55%)"),
        MotionTheme.Street to accents("#ffe600", "#ff3b30") +
            "$SOLID{-webkit-text-stroke:.045em #0b0b0b;paint-order:stroke fill;text-shadow:.06em .07em 0 #0b0b0b,.1em .12em 0 rgba(0,0,0,.35)!important}" +
            ".ivlyrics-motion-word.is-active{rotate:-1.5deg}" +
            layer("background:$GRAIN;opacity:.12;mix-blend-mode:overlay"),
        MotionTheme.Rock to accents("#ff2a2a", "#f2f2f2") +
            "$SOLID{text-shadow:0 .05em 0 rgba(0,0,0,.6),0 0 .4em rgba(255,42,42,.35)!important;letter-spacing:.01em}" +
            "@keyframes rock-jolt{0%,100%{translate:0 0}25%{translate:.02em -.02em}75%{translate:-.02em .02em}}" +
            ".ivlyrics-motion-word.is-active $SOLID{animation:rock-jolt .18s 2}" +
            layer("background:$GRAIN;opacity:.18;mix-blend-mode:overlay"),
        MotionTheme.Metal to accents("#c0c0c8", "#8b0000") +
            "$SOLID{background-image:linear-gradient(180deg,#ffffff 0%,#9a9aa6 45%,#3b3b44 52%,#d8d8e0 100%);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;filter:drop-shadow(0 .04em 0 #000) drop-shadow(0 0 .3em rgba(139,0,0,.55))}" +
            layer("background:radial-gradient(ellipse at center,transparent 40%,rgba(60,0,0,.45) 100%)"),
        MotionTheme.Pop to accents("#ff7eb6", "#7ad7ff") +
            "$SOLID{text-shadow:0 0 .3em rgba(255,126,182,.55),0 .05em .15em rgba(0,0,0,.2)!important}" +
            "@keyframes pop-bounce{0%{scale:1}40%{scale:1.12}100%{scale:1}}" +
            ".ivlyrics-motion-word.is-active $SOLID{animation:pop-bounce .32s ease-out}" +
            layer("background:radial-gradient(circle at 20% 15%,rgba(255,126,182,.16),transparent 45%),radial-gradient(circle at 80% 85%,rgba(122,215,255,.16),transparent 45%)"),
        MotionTheme.LoFi to accents("#f3d9b1", "#9ec5ab") +
            "$SOLID{color:#f6ead6!important;text-shadow:0 0 .25em rgba(243,217,177,.35)!important;filter:blur(.012em)}" +
            layer("background:$GRAIN,linear-gradient(rgba(60,40,20,.18),rgba(60,40,20,.18));opacity:.55;mix-blend-mode:soft-light"),
        MotionTheme.Jazz to accents("#d4af37", "#f5e6c4") +
            "$SOLID{background-image:linear-gradient(180deg,#fff3c4,#d4af37 60%,#8a6d1c);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;filter:drop-shadow(0 .03em .08em rgba(0,0,0,.5))}" +
            layer("background:radial-gradient(ellipse at center,transparent 45%,rgba(20,12,0,.5) 100%)"),
        MotionTheme.Country to accents("#e8a33d", "#f4e4c1") +
            "$SOLID{color:#f7ead0!important;text-shadow:0 .05em 0 rgba(90,50,10,.6),0 0 .3em rgba(232,163,61,.3)!important}" +
            layer("background:linear-gradient(rgba(112,66,20,.22),rgba(112,66,20,.22)),$GRAIN;mix-blend-mode:multiply;opacity:.7"),
        MotionTheme.Classical to accents("#e9dcc0", "#b8a07a") +
            "$SOLID{color:#fbf6ea!important;text-shadow:0 .02em .12em rgba(0,0,0,.35)!important;letter-spacing:.015em}" +
            ".ivlyrics-motion-word.is-active $SOLID{text-shadow:0 0 .4em rgba(233,220,192,.55)!important}" +
            layer("background:radial-gradient(ellipse at center,transparent 50%,rgba(0,0,0,.35) 100%)"),
        MotionTheme.Soul to accents("#c86bfa", "#ff8fab") +
            "$SOLID{text-shadow:0 0 .35em rgba(200,107,250,.55),0 0 .9em rgba(255,143,171,.25)!important}" +
            layer("background:radial-gradient(ellipse at 50% 100%,rgba(120,40,160,.28),transparent 65%)"),
        MotionTheme.Rave to accents("#39ff14", "#ff00e6") +
            "@keyframes rave-hue{from{filter:hue-rotate(0deg) drop-shadow(0 0 .25em currentColor)}to{filter:hue-rotate(360deg) drop-shadow(0 0 .25em currentColor)}}" +
            ".ivlyrics-motion-word.is-emph $SOLID,.ivlyrics-motion-word.is-active $SOLID{color:var(--iv-motion-accent)!important;animation:rave-hue 2.4s linear infinite}" +
            layer("background:repeating-linear-gradient(90deg,rgba(57,255,20,.05) 0 3px,transparent 3px 9px)"),
        MotionTheme.Blue to accents("#7fb2ff", "#c9dcff") +
            "$SOLID{color:#e8f0ff!important;text-shadow:0 0 .35em rgba(127,178,255,.45)!important}" +
            layer("background:linear-gradient(rgba(20,40,90,.3),rgba(20,40,90,.3));mix-blend-mode:multiply"),
        MotionTheme.Latin to accents("#ff5a36", "#ffd23f") +
            "$SOLID{text-shadow:0 0 .3em rgba(255,90,54,.45),0 .05em 0 rgba(0,0,0,.35)!important}" +
            "@keyframes latin-sway{0%,100%{rotate:0deg}50%{rotate:2deg}}" +
            ".ivlyrics-motion-word.is-active $SOLID{animation:latin-sway .5s ease-in-out 2}" +
            layer("background:radial-gradient(circle at 15% 85%,rgba(255,210,63,.18),transparent 45%),radial-gradient(circle at 85% 15%,rgba(255,90,54,.18),transparent 45%)"),
        MotionTheme.Punk to accents("#ff1f8f", "#d6ff00") +
            "$SOLID{-webkit-text-stroke:.03em #000;paint-order:stroke fill;text-shadow:-.04em .04em 0 var(--iv-motion-accent-2),.04em -.03em 0 var(--iv-motion-accent)!important}" +
            "@keyframes punk-glitch{0%,90%,100%{translate:0 0}92%{translate:.04em 0}94%{translate:-.04em .02em}96%{translate:0 -.02em}}" +
            "$SOLID{animation:punk-glitch 2.6s infinite}" +
            layer("background:$GRAIN;opacity:.22;mix-blend-mode:overlay"),
    )
}
