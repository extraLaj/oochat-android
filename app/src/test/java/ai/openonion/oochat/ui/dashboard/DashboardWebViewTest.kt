package ai.openonion.oochat.ui.dashboard

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import ai.openonion.oochat.ui.theme.ConnectOnionTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowWebView

/** Verifies the Android half of the untrusted dashboard sandbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardWebViewTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var blocked = 0

    private var document by mutableStateOf("<h1>Dashboard</h1>")

    private fun showWebView(first: String = "<h1>Dashboard</h1>") {
        document = first
        composeRule.setContent {
            DashboardWebView(
                document = document,
                onRunSkill = { _, _ -> },
                onNavigationBlocked = { blocked++ }
            )
        }
        composeRule.waitForIdle()
    }

    /** Swaps in a newer snapshot, as a new DASHBOARD_SNAPSHOT would. */
    private fun replaceWith(next: String) {
        document = next
        composeRule.waitForIdle()
    }

    private fun webViews(): List<WebView> {
        val found = mutableListOf<WebView>()
        fun walk(view: View) {
            if (view is WebView) found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(composeRule.activity.window.decorView)
        return found
    }

    private fun switcher(): SnapshotSwitcher = webViews().first().parent as SnapshotSwitcher

    /** The page on screen; the switcher keeps exactly one visible. */
    private fun shown(): WebView = webViews().single { it.visibility == View.VISIBLE }

    private fun loaded(view: WebView): String? = shadowOf(view).lastLoadDataWithBaseURL?.data

    /** What WebView reports once a load is ready to draw; Robolectric never fires it itself. */
    private fun commitVisible(view: WebView) {
        composeRule.runOnUiThread { shadowOf(view).webViewClient.onPageCommitVisible(view, "about:blank") }
        composeRule.waitForIdle()
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    @Test
    fun `untrusted content cannot use WebView file content storage or window capabilities`() {
        showWebView()

        // Both pages: the hidden one holds a snapshot too.
        assertEquals(2, webViews().size)
        webViews().forEach { view ->
            val settings = view.settings
            assertTrue("the nonced bridge requires JavaScript", settings.javaScriptEnabled)
            assertFalse(settings.allowFileAccess)
            assertFalse(settings.allowContentAccess)
            assertFalse(settings.allowFileAccessFromFileURLs)
            assertFalse(settings.allowUniversalAccessFromFileURLs)
            assertFalse(settings.domStorageEnabled)
            assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(settings.supportMultipleWindows())
            assertNull("permission and dialog requests must not be surfaced", shadowOf(view).webChromeClient)
        }
    }

    @Test
    fun `the snapshot is loaded with an opaque origin`() {
        val document = "<h1>Dashboard</h1>"
        showWebView(document)

        val load = shadowOf(shown()).lastLoadDataWithBaseURL
        assertNotNull(load)
        assertNull(load.baseUrl)
        assertEquals(document, load.data)
        assertEquals("text/html", load.mimeType)
        assertEquals("utf-8", load.encoding)
        assertNull(load.historyUrl)
    }

    @Test
    fun `only the defined skill bridge is exposed to JavaScript`() {
        showWebView()

        webViews().forEach { view ->
            val interfaces = registeredInterfaces(shadowOf(view))
            assertEquals(setOf(DashboardDocument.BRIDGE_NAME), interfaces.keys)

            val bridge = interfaces.getValue(DashboardDocument.BRIDGE_NAME)
            val callable = bridge.javaClass.declaredMethods.filter {
                it.isAnnotationPresent(JavascriptInterface::class.java)
            }
            assertEquals(listOf("runSkill"), callable.map { it.name })
            assertEquals(listOf(String::class.java, String::class.java), callable.single().parameterTypes.toList())
        }
    }

    @Test
    fun `every attempted navigation is refused and reported`() {
        showWebView()

        val destinations = listOf(
            "https://example.test/collect",
            "file:///data/local/tmp/secret",
            "content://ai.openonion.oochat/secret",
            "intent://dashboard#Intent;scheme=https;end"
        )

        webViews().forEach { view ->
            val client = shadowOf(view).webViewClient
            destinations.forEach { destination ->
                assertTrue(destination, client.shouldOverrideUrlLoading(view, request(destination)))
            }
        }
        assertEquals(2 * destinations.size, blocked)
    }

    @Test
    fun `the first snapshot loads straight into the page on screen`() {
        showWebView("<h1>v1</h1>")

        assertEquals("<h1>v1</h1>", loaded(shown()))
        assertNull("nothing has loaded off screen yet", loaded(switcher().back))
    }

    @Test
    fun `a newer snapshot loads off screen and the old page stays up until it can draw`() {
        showWebView("<h1>v1</h1>")
        val old = shown()

        replaceWith("<h1>v2</h1>")

        assertSame("the old page must not be blanked while the new one loads", old, shown())
        assertEquals("<h1>v1</h1>", loaded(old))
        assertEquals("<h1>v2</h1>", loaded(switcher().back))
        assertEquals(View.INVISIBLE, switcher().back.visibility)
    }

    @Test
    fun `the newer snapshot is swapped in once it is ready to draw`() {
        showWebView("<h1>v1</h1>")
        val old = shown()
        replaceWith("<h1>v2</h1>")
        val next = switcher().back

        commitVisible(next)

        assertSame(next, shown())
        assertEquals("<h1>v2</h1>", loaded(shown()))
        assertEquals("the old page is hidden, not left on top", View.INVISIBLE, old.visibility)
    }

    @Test
    fun `a commit on the page already shown swaps nothing`() {
        showWebView("<h1>v1</h1>")
        val old = shown()
        replaceWith("<h1>v2</h1>")

        commitVisible(old)

        assertSame(old, shown())
    }

    @Test
    fun `snapshots keep alternating between the two pages`() {
        showWebView("<h1>v1</h1>")
        listOf("<h1>v2</h1>", "<h1>v3</h1>", "<h1>v4</h1>").forEach { next ->
            replaceWith(next)
            commitVisible(switcher().back)
            assertEquals(next, loaded(shown()))
        }
        assertEquals(2, webViews().size)
    }

    @Test
    fun `a snapshot superseded while loading is never swapped in`() {
        showWebView("<h1>v1</h1>")
        replaceWith("<h1>v2</h1>")
        replaceWith("<h1>v3</h1>")

        commitVisible(switcher().back)

        assertEquals("<h1>v3</h1>", loaded(shown()))
    }

    @Test
    fun `recomposing with the same snapshot does not reload it`() {
        showWebView("<h1>v1</h1>")
        val view = shown()
        val before = shadowOf(view).lastLoadDataWithBaseURL

        // What every recomposition's `update` does.
        composeRule.runOnUiThread { switcher().show(document) }
        composeRule.waitForIdle()

        assertSame(before, shadowOf(view).lastLoadDataWithBaseURL)
        assertNull(loaded(switcher().back))
    }

    @Test
    fun `the screen wraps the snapshot off the main thread and loads it`() {
        composeRule.setContent { screen(html = "<h1>v1</h1>") }

        composeRule.waitUntil(5_000) { webViews().isNotEmpty() && loaded(shown()) != null }
        val document = loaded(shown())!!
        assertTrue(document.contains("<h1>v1</h1>"))
        assertTrue("the wrapper, not the raw snapshot", document.contains("Content-Security-Policy"))
    }

    @Test
    fun `the screen never claims there is no page while a snapshot is being wrapped`() {
        composeRule.setContent { screen(html = "<h1>v1</h1>") }

        composeRule.onNodeWithText("No Home page yet").assertDoesNotExist()
    }

    @Test
    fun `a newer snapshot reaches the page off screen while the old one stays up`() {
        var html by mutableStateOf("<h1>v1</h1>")
        composeRule.setContent { screen(html = html) }
        composeRule.waitUntil(5_000) { webViews().isNotEmpty() && loaded(shown()) != null }
        val old = shown()

        html = "<h1>v2</h1>"
        composeRule.waitUntil(5_000) { loaded(switcher().back)?.contains("<h1>v2</h1>") == true }

        assertSame(old, shown())
        assertTrue(loaded(old)!!.contains("<h1>v1</h1>"))
        composeRule.onNodeWithText("No Home page yet").assertDoesNotExist()
    }

    @Test
    fun `a 2 MiB snapshot is wrapped and loaded whole`() {
        val big = "<p>" + "x".repeat(2 * 1024 * 1024 - 7) + "</p>"
        composeRule.setContent { screen(html = big) }

        composeRule.waitUntil(10_000) { webViews().isNotEmpty() && loaded(shown()) != null }
        assertTrue(loaded(shown())!!.contains(big))
    }

    @Composable
    private fun screen(html: String?) = ConnectOnionTheme {
        DashboardScreen(html = html, allowedSkills = emptyList(), onRunSkill = {}, onNavigateBack = {})
    }

    /** Robolectric records every addJavascriptInterface call but exposes lookups only by name. */
    @Suppress("UNCHECKED_CAST")
    private fun registeredInterfaces(shadow: ShadowWebView): Map<String, Any> {
        val field = ShadowWebView::class.java.getDeclaredField("javascriptInterfaces")
        field.isAccessible = true
        return field.get(shadow) as Map<String, Any>
    }
}
