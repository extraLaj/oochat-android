package ai.openonion.oochat.network

import ai.openonion.oochat.di.sharedHttpClient
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

/** Unencrypted connections go only to the user's own network (W16AAPPLE-86). */
class CleartextPolicyTest {

    @Test
    fun `an encrypted URL is allowed wherever it points`() {
        listOf("https://agent.example.com", "wss://agent.example.com/ws", "HTTPS://203.0.113.5:8000").forEach {
            assertTrue(it, CleartextPolicy.allows(it))
        }
    }

    @Test
    fun `an unencrypted URL is allowed to localhost and the private ranges`() {
        listOf(
            "http://192.168.1.20:8000",
            "ws://192.168.1.20:8000/ws",
            "http://10.0.2.2:8000/",
            "http://172.16.0.1",
            "http://172.31.255.255",
            "http://127.0.0.1:8000",
            "http://localhost:8000",
            "ws://[::1]:8000/ws",
            "192.168.1.20:8000"
        ).forEach { assertTrue(it, CleartextPolicy.allows(it)) }
    }

    @Test
    fun `an unencrypted URL is allowed to Tailscale, link-local and private IPv6 addresses`() {
        listOf(
            "http://100.64.0.1:8000",
            "http://100.101.102.103:8000",
            "http://100.127.255.255",
            "http://169.254.10.20:8000",
            "ws://[fd7a:115c:a1e0::1]:8000/ws",
            "http://[fc00::1]",
            "http://[fe80::1]:8000",
            "http://[FEBF::1]",
            "http://[0:0:0:0:0:0:0:1]:8000",
            "http://[::ffff:192.168.1.20]:8000"
        ).forEach { assertTrue(it, CleartextPolicy.allows(it)) }
    }

    @Test
    fun `an unencrypted URL is refused to public IPv6 and just outside the added ranges`() {
        listOf(
            "http://100.63.255.255",
            "http://100.128.0.1",
            "http://169.253.1.1",
            "http://[2001:db8::1]:8000",
            "http://[fe00::1]",
            "http://[fec0::1]",
            "http://[::ffff:8.8.8.8]",
            "http://[::]",
            "http://[not:an:address]",
            "http://mypc.local:8000",
            "http://mypc.tail1234.ts.net:8000"
        ).forEach { assertFalse(it, CleartextPolicy.allows(it)) }
    }

    @Test
    fun `an unencrypted URL is refused anywhere else`() {
        listOf(
            "http://agent.example.com",
            "ws://203.0.113.5:8000/ws",
            "http://172.15.0.1",
            "http://172.32.0.1",
            "http://192.169.1.1",
            "http://11.0.0.1",
            "agent.example.com:8000",
            "http://mypc.lan:8000"
        ).forEach { assertFalse(it, CleartextPolicy.allows(it)) }
    }

    @Test
    fun `a private address that is not the host does not make a URL private`() {
        listOf(
            "http://192.168.1.20@agent.example.com",
            "http://192.168.1.20.example.com",
            "http://agent.example.com/192.168.1.20",
            "http://010.1.1.1",
            "http://10.1.1",
            "http://3232235796",
            "http://"
        ).forEach { assertFalse(it, CleartextPolicy.allows(it)) }
    }

    @Test
    fun `the interceptor refuses an unencrypted request to a public address before it is sent`() {
        val client = OkHttpClient.Builder().addInterceptor(CleartextPolicy.interceptor).build()

        val error = assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("http://agent.example.com/info").build()).execute()
        }

        assertEquals(CleartextPolicy.REFUSED, error.message)
    }

    @Test
    fun `the shared client refuses a redirect to an unencrypted public address before sending it`() {
        MockWebServer().use { local ->
            MockWebServer().use { elsewhere ->
                // A public name for the second server: it is refused by name, never reached.
                val publicName = "agent.example.com"
                local.enqueue(
                    MockResponse().setResponseCode(302)
                        .setHeader("Location", "http://$publicName:${elsewhere.port}/info")
                )
                val client = sharedHttpClient.newBuilder()
                    .dns(
                        object : Dns {
                            override fun lookup(hostname: String): List<InetAddress> =
                                if (hostname == publicName) {
                                    InetAddress.getAllByName(elsewhere.hostName).toList()
                                } else {
                                    Dns.SYSTEM.lookup(hostname)
                                }
                        }
                    )
                    .build()

                val error = assertThrows(IOException::class.java) {
                    client.newCall(Request.Builder().url(local.url("/info")).build()).execute().close()
                }

                assertEquals(CleartextPolicy.REFUSED, error.message)
                assertEquals(1, local.requestCount)
                assertEquals(0, elsewhere.requestCount)
            }
        }
    }

    @Test
    fun `the interceptor lets an unencrypted request to this machine through`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val client = OkHttpClient.Builder().addInterceptor(CleartextPolicy.interceptor).build()

            val body = client.newCall(Request.Builder().url(server.url("/info")).build()).execute().use { it.body?.string() }

            assertEquals("ok", body)
        }
    }
}
