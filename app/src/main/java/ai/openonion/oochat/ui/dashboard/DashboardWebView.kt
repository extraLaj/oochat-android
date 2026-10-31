package ai.openonion.oochat.ui.dashboard

import ai.openonion.oochat.util.FileLogger
import ai.openonion.oochat.util.LogTags
import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts one agent-authored dashboard in a locked-down [WebView].
 *
 * The document itself — CSP, bridge, and the agent's HTML as body content — is
 * built by [DashboardDocument]; read its doc first, it carries the security
 * model. This file is the Android half: the settings that make the WebView the
 * equivalent of the web client's `sandbox="allow-scripts"` iframe, the
 * navigation refusal that keeps the page from replacing itself, and the
 * [SnapshotSwitcher] that replaces one snapshot with the next without a blank
 * frame in between.
 *
 * `javaScriptEnabled = true` is required — the bridge is a script. Everything
 * else a WebView can reach is switched off.
 */
@Composable
fun DashboardWebView(
    document: String,
    onRunSkill: (skill: String, args: String) -> Unit,
    onNavigationBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    awaitVisualState: VisualStateWaiter = ::postVisualState
) {
    // The bridge is called from a WebView thread and must see the *current*
    // handler, not the one captured when the WebView was first created.
    val currentRunSkill by rememberUpdatedState(onRunSkill)
    val currentNavigationBlocked by rememberUpdatedState(onNavigationBlocked)
    val bridge = remember { SkillBridge { skill, args -> currentRunSkill(skill, args) } }
    val surface = MaterialTheme.colorScheme.surface

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SnapshotSwitcher(context, onNavigationBlocked = { currentNavigationBlocked() }, awaitVisualState) {
                configureForUntrustedContent()
                addJavascriptInterface(bridge, DashboardDocument.BRIDGE_NAME)
            }
        },
        update = { switcher ->
            // Painted before the first snapshot draws, so a dark theme does
            // not flash white.
            switcher.setPageBackground(surface.toArgb())
            switcher.show(document)
        },
        onRelease = { it.destroy() }
    )
}

/**
 * Waits until [WebView]'s DOM, as it is now, is ready to draw, then calls
 * back with the same request id. [postVisualState] in the app; a test drives
 * it by hand, since Robolectric's WebView never draws.
 */
typealias VisualStateWaiter = (view: WebView, requestId: Long, onReady: (requestId: Long) -> Unit) -> Unit

internal fun postVisualState(view: WebView, requestId: Long, onReady: (Long) -> Unit) =
    view.postVisualStateCallback(
        requestId,
        object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) = onReady(requestId)
        }
    )

/**
 * Two WebViews, one on screen. Loading a new snapshot into the page being
 * shown blanks it until the new document paints, so a newer snapshot loads
 * into the hidden one instead, and the two swap only once the new page is
 * ready to draw. Until then the old page stays up, still scrollable and still
 * clickable.
 *
 * "Ready to draw" is two signals: `onPageFinished` (the whole document is
 * parsed — `onPageCommitVisible` can come with only part of a 2 MiB page) and
 * then a visual state callback, which is what guarantees the next draw shows
 * that DOM. Posted any earlier, the callback would cover the old document.
 *
 * Neither signal says *which* load it belongs to, so a load in the hidden page
 * is never interrupted: a snapshot that arrives meanwhile waits, only the
 * latest is kept, and it loads once the current one is done. Every signal
 * from the hidden page is then about its one load in flight. Each load also
 * gets a fresh request id, and a callback whose id is not the latest is
 * ignored.
 *
 * The hidden page keeps its last snapshot rather than being blanked: blanking
 * would be a load of its own, for no gain.
 *
 * Main thread only, as WebView is.
 */
internal class SnapshotSwitcher(
    context: Context,
    private val onNavigationBlocked: () -> Unit,
    private val awaitVisualState: VisualStateWaiter,
    configure: WebView.() -> Unit
) : FrameLayout(context) {

    /** The page on screen. */
    var front: WebView
        private set

    /** The page a newer snapshot loads into, kept [View.INVISIBLE] — not GONE, or it never draws. */
    var back: WebView
        private set

    /** The latest document asked for, whether shown yet, loading, or waiting. */
    private var requested: String? = null

    /** The document loading in [back], or null when [back] is idle. */
    private var loading: String? = null

    /** The request id of the latest load into [back]. */
    private var loadId = 0L

    init {
        back = newPage(configure).also { it.visibility = View.INVISIBLE }
        front = newPage(configure)
    }

    /**
     * Shows [document], or starts loading it in the background. Compared by
     * identity: the caller builds a new document (with a new nonce) for a new
     * snapshot and passes the same instance otherwise, and `update` runs on
     * every recomposition — reloading the same page would lose scroll and any
     * filter the user typed for reasons unrelated to the dashboard.
     */
    fun show(document: String) {
        if (document === requested) return
        requested = document
        when {
            // The first snapshot has no old page to keep up, so it loads in place.
            front.tag == null -> load(front, document)
            // Picked up when the load in flight is done; see onReady.
            loading != null -> Unit
            else -> loadInBack(document)
        }
    }

    fun setPageBackground(color: Int) {
        front.setBackgroundColor(color)
        back.setBackgroundColor(color)
    }

    fun destroy() {
        removeAllViews()
        front.destroy()
        back.destroy()
    }

    private fun loadInBack(document: String) {
        loading = document
        loadId++
        load(back, document)
    }

    private fun onPageFinished(view: WebView) {
        // The page on screen has nothing to replace, and an idle back page
        // has nothing in flight.
        if (view !== back || loading == null) return
        awaitVisualState(back, loadId, ::onReady)
    }

    private fun onReady(requestId: Long) {
        if (requestId != loadId) return
        val ready = loading ?: return
        loading = null
        val latest = requested
        if (ready !== latest) {
            // Superseded while it loaded: load the latest rather than put a
            // stale page on screen.
            if (latest != null && latest !== front.tag) loadInBack(latest)
            return
        }
        back = front.also { front = back }
        front.visibility = View.VISIBLE
        back.visibility = View.INVISIBLE
    }

    private fun load(view: WebView, document: String) {
        view.tag = document
        // A null base URL is what makes the origin opaque: no same-origin
        // peer, no file:// resolution, no app storage. It is also why the CSP
        // has to travel inside the document as a meta tag — there are no
        // response headers to put it in.
        view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
    }

    private fun newPage(configure: WebView.() -> Unit): WebView =
        WebView(context).apply {
            configure()
            // Rasterised while hidden, so the first frame after a swap is
            // not drawn tile by tile.
            settings.offscreenPreRaster = true
            webViewClient = BlockAllNavigationClient(
                onBlocked = onNavigationBlocked,
                onPageFinished = ::onPageFinished
            )
        }.also {
            addView(it, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
}

/**
 * Everything a WebView will do for a page unless told otherwise. The agent
 * wrote this page, so all of it is off — only scripting stays on, for the
 * bridge.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForUntrustedContent() {
    settings.apply {
        // The nonced bridge is a script; without this, skill buttons are dead.
        // The agent's own scripts are refused by the CSP, not by this switch.
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        setGeolocationEnabled(false)
        // No localStorage/sessionStorage: a dashboard holds no state of its
        // own, and storage shared across snapshots would outlive the agent it
        // belongs to.
        domStorageEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
    }
    // No WebChromeClient is set, so window.open, alert/confirm/prompt and
    // permission requests are all ignored rather than surfaced as app dialogs.
}

/**
 * The one `@JavascriptInterface` the page can reach. A single method with two
 * string parameters — every method on this object is callable by any script
 * that runs, so the object *is* the attack surface and nothing else goes on it.
 *
 * What arrives here is an untrusted intent. It is validated outside the
 * WebView (see [DashboardSkillIntent]) and then sent through the normal chat
 * path, so the worst a forged call can produce is a visible `/skill` turn.
 */
private class SkillBridge(private val onRunSkill: (String, String) -> Unit) {
    @JavascriptInterface
    fun runSkill(skill: String?, args: String?) {
        onRunSkill(skill.orEmpty(), args.orEmpty())
    }
}

/**
 * Refuses every navigation. A dashboard is one self-contained page: the bridge
 * already cancels link clicks, and this catches what it cannot see — a
 * `<meta http-equiv="refresh">`, a redirect, a form submission.
 *
 * The URL is never handed to an external browser. Opening an agent-supplied
 * link would turn a page that cannot phone home into one that can, with the
 * user's tap as the authorisation.
 */
private class BlockAllNavigationClient(
    private val onBlocked: () -> Unit,
    private val onPageFinished: (WebView) -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // Scheme only — the rest of an agent-supplied URL has no business in
        // a log file.
        FileLogger.w(LogTags.DASHBOARD, "Blocked dashboard navigation to ${request?.url?.scheme} URL")
        onBlocked()
        return true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        view?.let(onPageFinished)
    }
}
