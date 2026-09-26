package com.skydex.server.hypixel

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val SKIN_URL = "https://textures.minecraft.net/texture/abc123"
private const val FACE = 0xFF112233.toInt()
private const val HAT = 0xFFAABBCC.toInt()

/** A skin of [width]x[height] with the face filled with [face] and the hat layer with [hat]. */
internal fun skin(width: Int = 64, height: Int = width, face: Int = FACE, hat: Int = 0, type: Int = BufferedImage.TYPE_INT_ARGB) =
    BufferedImage(width, height, type).apply {
        val s = width / 64
        for (y in 8 * s until 16 * s) for (x in 8 * s until 16 * s) setRGB(x, y, face)
        for (y in 8 * s until 16 * s) for (x in 40 * s until 48 * s) setRGB(x, y, hat)
    }

internal fun BufferedImage.bytes(format: String = "png"): ByteArray =
    ByteArrayOutputStream().also { check(ImageIO.write(this, format, it)) { format } }.toByteArray()

class SkinTest {
    @Test
    fun transparentHatShowsTheFace() {
        assertEquals(List(64) { FACE }, faceFromSkin(skin(hat = 0)))
        // A transparent face pixel still comes out opaque.
        assertEquals(List(64) { 0xFF112233.toInt() }, faceFromSkin(skin(face = 0x00112233, hat = 0)))
    }

    @Test
    fun opaqueHatCoversTheFace() {
        assertEquals(List(64) { HAT }, faceFromSkin(skin(hat = HAT)))
    }

    @Test
    fun halfTransparentHatIsBlended() {
        // Red at alpha 128 over blue: (255 * 128) / 255 = 128 red, (255 * 127) / 255 = 127 blue.
        val face = faceFromSkin(skin(face = 0xFF0000FF.toInt(), hat = 0x80FF0000.toInt()))
        assertEquals(List(64) { 0xFF80007F.toInt() }, face)
    }

    @Test
    fun legacySkinIgnoresAFullyOpaqueHat() {
        assertEquals(List(64) { FACE }, faceFromSkin(skin(64, 32, hat = 0xFF000000.toInt())))
        // A mostly opaque hat (alpha >= 128 everywhere) counts as opaque too.
        assertEquals(List(64) { FACE }, faceFromSkin(skin(64, 32, hat = 0xC0AABBCC.toInt())))
        // HD legacy skins follow the same rule.
        assertEquals(List(64) { FACE }, faceFromSkin(skin(128, 64, hat = HAT)))
    }

    @Test
    fun legacySkinWithTransparencyUsesTheHat() {
        val image = skin(64, 32, hat = HAT).apply { setRGB(47, 15, 0) }
        val face = faceFromSkin(image)!!
        assertEquals(HAT, face[0])
        assertEquals(FACE, face[63])
    }

    @Test
    fun modernSkinAlwaysUsesTheHat() {
        assertEquals(List(64) { 0xFF000000.toInt() }, faceFromSkin(skin(hat = 0xFF000000.toInt())))
    }

    @Test
    fun hdSkinIsSampled() {
        val image = skin(128)
        // Top-left pixel of face cell (1, 1) is sampled; the rest of the 2x2 cell is ignored.
        image.setRGB(18, 18, 0xFF010203.toInt())
        image.setRGB(19, 19, 0xFF040506.toInt())
        val face = faceFromSkin(image)!!
        assertEquals(64, face.size)
        assertEquals(0xFF010203.toInt(), face[9])
        assertEquals(FACE, face[0])
        assertEquals(FACE, face[63])
    }

    @Test
    fun badSizesGiveNull() {
        for ((w, h) in listOf(32 to 32, 64 to 48, 64 to 128, 65 to 65, 96 to 96, 1088 to 1088)) {
            assertNull(faceFromSkin(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)), "${w}x$h")
        }
    }

    private fun profileWith(textures: String?) = MojangProfile(
        "id", "Tester",
        listOfNotNull(MojangProperty("other", "x"), textures?.let { MojangProperty("textures", it) }),
    )

    private fun texturesFor(url: String) =
        Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())

    @Test
    fun skinUrlFromTexturesProperty() {
        assertEquals(SKIN_URL, profileWith(texturesFor("http://textures.minecraft.net/texture/abc123")).skinUrl())
        assertEquals(SKIN_URL, profileWith(texturesFor(SKIN_URL)).skinUrl())
    }

    @Test
    fun skinUrlRejectsOtherHostsAndGarbage() {
        val rejected = listOf(
            "https://evil.example/texture/abc",
            "https://textures.minecraft.net@evil.example/texture/abc",
            "https://textures.minecraft.net.evil.example/texture/abc",
            "file://textures.minecraft.net/etc/passwd",
            "ftp://textures.minecraft.net/texture/abc",
            "//textures.minecraft.net/texture/abc",
            "not a url",
        )
        for (url in rejected) assertNull(profileWith(texturesFor(url)).skinUrl(), url)

        assertNull(profileWith(null).skinUrl())
        assertNull(profileWith("!!! not base64 !!!").skinUrl())
        assertNull(profileWith(Base64.getEncoder().encodeToString("not json".toByteArray())).skinUrl())
        assertNull(profileWith(Base64.getEncoder().encodeToString("""{"textures":{}}""".toByteArray())).skinUrl())
        assertNull(
            profileWith(Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":5}}}""".toByteArray()))
                .skinUrl(),
        )
    }

    @Test
    fun clientDownloadsAndCropsPng() = runBlocking<Unit> {
        val (engine, http) = mockHttp { respond(skin(hat = 0).bytes(), headers = headersOf(HttpHeaders.ContentType, "image/png")) }

        assertEquals(List(64) { FACE }, SkinClient(http).face(SKIN_URL))
        assertEquals(SKIN_URL, engine.requestHistory.single().url.toString())
    }

    @Test
    fun clientRejectsErrorsAndNonPngImages() = runBlocking<Unit> {
        val rgb = skin(type = BufferedImage.TYPE_INT_RGB)
        val bodies = mapOf(
            "/missing" to null,
            "/jpeg" to rgb.bytes("jpg"),
            "/gif" to rgb.bytes("gif"),
            "/bmp" to rgb.bytes("bmp"),
            "/text" to "hello".toByteArray(),
        )
        val (_, http) = mockHttp { request ->
            val body = bodies[request.url.encodedPath]
            if (body == null) respond("", HttpStatusCode.NotFound) else respond(body)
        }
        val client = SkinClient(http)
        for (path in bodies.keys) assertNull(client.face("https://textures.minecraft.net$path"), path)
    }

    @Test
    fun clientRejectsOversizedSkins() = runBlocking<Unit> {
        val png = skin().bytes()
        // A valid PNG followed by padding still decodes, so only the size limit can reject these.
        val padded = png + ByteArray(70_000)
        val (engine, http) = mockHttp { request ->
            when (request.url.encodedPath) {
                "/declared" -> respond(png, headers = headersOf(HttpHeaders.ContentLength, "100000"))
                "/streamed" -> respond(ByteReadChannel(padded))
                else -> respond(png)
            }
        }
        val client = SkinClient(http)

        assertNotNull(client.face("https://textures.minecraft.net/ok"))
        assertNull(client.face("https://textures.minecraft.net/declared"))
        assertNull(client.face("https://textures.minecraft.net/streamed"))
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun hugeDeclaredDimensionsAreRejectedBeforeDecoding() = runBlocking<Unit> {
        val bombs = listOf(pngHeader(100_000, 100_000), pngHeader(1_048_576, 1_048_576), pngHeader(2048, 2048))
        for (bytes in bombs) assertNull(decodeSkin(bytes))
        val (_, http) = mockHttp { respond(pngHeader(65_536, 65_536)) }
        assertNull(SkinClient(http).face(SKIN_URL))
    }

    @Test
    fun facesAreCachedForADayAndFailuresForFiveMinutes() = runBlocking<Unit> {
        var now = 1_000_000L
        var ok = true
        val (engine, http) = mockHttp { if (ok) respond(skin().bytes()) else respond("", HttpStatusCode.NotFound) }
        val client = SkinClient(http) { now }

        assertNotNull(client.face(SKIN_URL))
        now += 23 * 60 * 60_000L
        assertNotNull(client.face(SKIN_URL))
        assertEquals(1, engine.requestHistory.size)
        now += 60 * 60_000L
        assertNotNull(client.face(SKIN_URL))
        assertEquals(2, engine.requestHistory.size)

        ok = false
        val broken = "https://textures.minecraft.net/texture/broken"
        assertNull(client.face(broken))
        now += 4 * 60_000L
        assertNull(client.face(broken))
        assertEquals(3, engine.requestHistory.size)
        now += 2 * 60_000L
        ok = true
        assertNotNull(client.face(broken))
        assertEquals(4, engine.requestHistory.size)
    }

    /** A PNG signature and IHDR chunk claiming [width]x[height] RGBA, with no pixel data. */
    private fun pngHeader(width: Int, height: Int): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val typeBytes = type.toByteArray()
            val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
            return ByteBuffer.allocate(12 + data.size).putInt(data.size).put(typeBytes).put(data).putInt(crc).array()
        }
        val ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put(8).put(6).put(0).put(0).put(0).array()
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return signature + chunk("IHDR", ihdr) + chunk("IEND", ByteArray(0))
    }
}
