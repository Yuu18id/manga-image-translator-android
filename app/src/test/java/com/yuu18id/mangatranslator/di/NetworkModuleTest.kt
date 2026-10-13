package com.yuu18id.mangatranslator.di

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class NetworkModuleTest {

    @Test
    fun testIsLocalOrPrivateHostLoopbackAndPrivateRanges() {
        // Localhost names and loopbacks
        assertTrue("localhost should be considered local", NetworkModule.isLocalOrPrivateHost("localhost"))
        assertTrue("LOCALHOST case-insensitive should be considered local", NetworkModule.isLocalOrPrivateHost("LOCALHOST"))
        assertTrue("127.0.0.1 should be considered local", NetworkModule.isLocalOrPrivateHost("127.0.0.1"))
        assertTrue("127.0.0.2 should be considered local", NetworkModule.isLocalOrPrivateHost("127.0.0.2"))
        assertTrue("10.0.2.2 emulator loopback should be considered local", NetworkModule.isLocalOrPrivateHost("10.0.2.2"))
        assertTrue("IPv6 loopback ::1 should be considered local", NetworkModule.isLocalOrPrivateHost("::1"))
        assertTrue("IPv6 bracketed loopback [::1] should be considered local", NetworkModule.isLocalOrPrivateHost("[::1]"))

        // Class A private (10.0.0.0/8)
        assertTrue("10.0.0.1 should be considered local/private", NetworkModule.isLocalOrPrivateHost("10.0.0.1"))
        assertTrue("10.255.255.255 should be considered local/private", NetworkModule.isLocalOrPrivateHost("10.255.255.255"))

        // Class B private (172.16.0.0/12)
        assertTrue("172.16.0.1 should be considered local/private", NetworkModule.isLocalOrPrivateHost("172.16.0.1"))
        assertTrue("172.24.1.1 should be considered local/private", NetworkModule.isLocalOrPrivateHost("172.24.1.1"))
        assertTrue("172.31.255.255 should be considered local/private", NetworkModule.isLocalOrPrivateHost("172.31.255.255"))

        // Class C private (192.168.0.0/16)
        assertTrue("192.168.1.1 should be considered local/private", NetworkModule.isLocalOrPrivateHost("192.168.1.1"))
        assertTrue("192.168.0.100 should be considered local/private", NetworkModule.isLocalOrPrivateHost("192.168.0.100"))
    }

    @Test
    fun testIsLocalOrPrivateHostPublicDomainsAndIpsRejected() {
        // Public domain names
        assertFalse("Public domain api.openai.com should not be local", NetworkModule.isLocalOrPrivateHost("api.openai.com"))
        assertFalse("Public domain translate.googleapis.com should not be local", NetworkModule.isLocalOrPrivateHost("translate.googleapis.com"))
        assertFalse("Public domain google.com should not be local", NetworkModule.isLocalOrPrivateHost("google.com"))

        // Public IPs
        assertFalse("8.8.8.8 should not be local", NetworkModule.isLocalOrPrivateHost("8.8.8.8"))
        assertFalse("1.1.1.1 should not be local", NetworkModule.isLocalOrPrivateHost("1.1.1.1"))
        assertFalse("172.15.0.1 outside 172.16-31 should not be local", NetworkModule.isLocalOrPrivateHost("172.15.0.1"))
        assertFalse("172.32.0.1 outside 172.16-31 should not be local", NetworkModule.isLocalOrPrivateHost("172.32.0.1"))
        assertFalse("192.169.1.1 outside 192.168 should not be local", NetworkModule.isLocalOrPrivateHost("192.169.1.1"))

        // Edge cases
        assertFalse("Empty string should not be local", NetworkModule.isLocalOrPrivateHost(""))
        assertFalse("Malformed IP should not be local", NetworkModule.isLocalOrPrivateHost("192.168.1"))
        assertFalse("Malformed IP should not be local", NetworkModule.isLocalOrPrivateHost("999.999.999.999"))
    }

    @Test
    fun testCleartextInterceptorBlocksPublicHttpTraffic() {
        val client = NetworkModule.provideOkHttpClient()
        val request = Request.Builder()
            .url("http://example.com/api/test")
            .build()

        try {
            client.newCall(request).execute()
            fail("Expected IOException when attempting cleartext HTTP to public host")
        } catch (e: IOException) {
            assertTrue(
                "Exception message should mention cleartext HTTP is not permitted",
                e.message?.contains("Cleartext HTTP traffic to public host 'example.com' is not permitted") == true
            )
        }
    }

    @Test
    fun testRateLimitRetryInterceptorRetriesOn429() {
        val client = NetworkModule.provideOkHttpClient()
        var callCount = 0
        val testClient = client.newBuilder()
            .addInterceptor { chain ->
                callCount++
                if (callCount == 1) {
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "0")
                        .body("rate limited".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("success".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/test")
            .build()

        val response = testClient.newCall(request).execute()
        assertEquals(200, response.code)
        assertEquals("success", response.body?.string())
        assertEquals("Should have retried once on HTTP 429", 2, callCount)
    }

    @Test
    fun testRateLimitRetryInterceptorStopsAfterMaxRetries() {
        val client = NetworkModule.provideOkHttpClient()
        var callCount = 0
        val testClient = client.newBuilder()
            .addInterceptor { chain ->
                callCount++
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .header("Retry-After", "0")
                    .body("rate limited".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/test")
            .build()

        val response = testClient.newCall(request).execute()
        assertEquals(429, response.code)
        // 1 initial attempt + 2 retries = 3 attempts total
        assertEquals("Should stop after max 2 retries (3 total attempts)", 3, callCount)
    }
}
