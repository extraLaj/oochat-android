package ai.openonion.oochat.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException
import java.net.URI

/**
 * Unencrypted `http://` and `ws://` only to the user's own network (W16AAPPLE-86):
 * localhost, the private and link-local ranges and Tailscale's `100.64.0.0/10`, as
 * literal addresses, IPv4 or IPv6. Android's network security config cannot express
 * IP ranges, so it permits cleartext and this is the rule.
 */
object CleartextPolicy {
    const val REFUSED = "Unencrypted connections (http:// or ws://) are only allowed to an agent on " +
        "your own network. Use an https:// or wss:// address for this server."

    /** A URL with no scheme is dialled unencrypted, so it is judged as one. */
    fun allows(url: String): Boolean {
        val trimmed = url.trim()
        val uri = runCatching { URI(if ("://" in trimmed) trimmed else "ws://$trimmed") }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() in ENCRYPTED_SCHEMES) return true
        // URI's host, not a prefix match: in http://10.0.0.1@example.com the host is example.com.
        return isPrivateHost(uri.host ?: return false)
    }

    /**
     * No name is resolved: a hostname could point anywhere by the time it is dialled.
     * That includes `.local` and Tailscale's MagicDNS names; their addresses are allowed.
     */
    internal fun isPrivateHost(host: String): Boolean {
        val literal = host.lowercase().removeSurrounding("[", "]")
        if (literal == "localhost") return true
        return if (':' in literal) isPrivateIpv6(literal) else isPrivateIpv4(literal)
    }

    /** 10/8, 172.16/12, 192.168/16, loopback, link-local 169.254/16 and Tailscale's 100.64/10. */
    private fun isPrivateIpv4(literal: String): Boolean {
        val octets = literal.split('.').map { part ->
            part.toIntOrNull()?.takeIf { it in 0..255 && it.toString() == part } ?: return false
        }
        if (octets.size != 4) return false
        val (a, b) = octets
        return a == 10 || a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            // Shared address space (RFC 6598): Tailscale's, but also mobile carriers' own
            // networks, so on mobile data an http://100.x address can be inside the carrier.
            // Accepted: almost nobody types one except for Tailscale.
            (a == 100 && b in 64..127)
    }

    /** Loopback, unique local fc00::/7 (Tailscale's fd7a:115c:a1e0::/48 among them) and link-local fe80::/10. */
    private fun isPrivateIpv6(literal: String): Boolean {
        // OkHttp's canonical form: ::1 however it was written, and an IPv4-mapped address as IPv4.
        val canonical = runCatching { HttpUrl.Builder().scheme("http").host(literal).build().host }
            .getOrNull() ?: return false
        if (':' !in canonical) return isPrivateIpv4(canonical)
        if (canonical == "::1") return true
        val first = canonical.substringBefore(':').toIntOrNull(16) ?: return false
        return first and 0xfe00 == 0xfc00 || first and 0xffc0 == 0xfe80
    }

    /** Refuses every request the rule does not allow, so no caller can dial around it. */
    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps && !isPrivateHost(url.host)) throw IOException(REFUSED)
        chain.proceed(chain.request())
    }

    private val ENCRYPTED_SCHEMES = setOf("https", "wss")
}
