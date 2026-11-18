package ai.openonion.oochat.ui.home

/**
 * What an agent's own Home page is on this device: whether there is one and which screen opens
 * it. The chat screen and the Dashboard both ask this, so they cannot disagree.
 *
 * An agent can send a Dashboard page, a Control Center app, or both. [appSupported] says whether
 * the device's WebView can host the app; it loads the WebView, so it is only asked when the answer
 * changes something.
 */
internal class AgentHome(
    private val hasDashboard: Boolean,
    private val hasApp: Boolean,
    private val appSupported: () -> Boolean
) {
    /** The agent sent a Home page of some kind, so there is something for the Home button to open. */
    val hasPage: Boolean get() = hasDashboard || hasApp

    /**
     * Home opens the Control Center rather than the Dashboard: wherever the device can host the
     * app, and also when there is no Dashboard, so the screen can say why it cannot.
     */
    val opensControlCenter: Boolean get() = hasApp && (!hasDashboard || appSupported())
}
