package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads Minecraft skins and crops them to a flat face the app can draw without fetching images itself. Faces are
 * cached per skin URL for a day (a new skin gets a new URL); when a skin can't be downloaded or read, "no face" is
 * cached for five minutes so a broken skin isn't fetched on every request.
 */
class SkinClient(
    private val http: HttpClient,
    clock: () -> Long = System::currentTimeMillis,
) {
    private class Face(val pixels: List<Int>?)

    private val log = LoggerFactory.getLogger(SkinClient::class.java)
    private val faces = TtlCache<String, Face>(FACE_TTL_MILLIS, clock) {
        if (it.pixels == null) FAILURE_TTL_MILLIS else FACE_TTL_MILLIS
    }

    /** The face for [skinUrl] as 64 ARGB ints (see [faceFromSkin]), or null if it can't be downloaded or read. */
    suspend fun face(skinUrl: String): List<Int>? = faces.getOrPut(skinUrl) {
        val pixels = try {
            download(skinUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not load the skin {}: {}", skinUrl, e.toString())
            null
        }
        Face(pixels)
    }.pixels

    private suspend fun download(skinUrl: String): List<Int>? {
        val bytes = try {
            http.prepareGet(skinUrl).execute { response ->
                if (!response.status.isSuccess()) {
                    throw UpstreamException("Skin download returned ${response.status.value}")
                }
                if ((response.contentLength() ?: 0) > MAX_SKIN_BYTES) throw UpstreamException("Skin is too large")
                // Read one byte past the limit to tell a skin of exactly the limit from a larger one.
                response.bodyAsChannel().readRemaining(MAX_SKIN_BYTES + 1L).readByteArray()
            }
        } catch (e: IOException) {
            throw UpstreamException("Mojang is unreachable", e)
        }
        if (bytes.size > MAX_SKIN_BYTES) throw UpstreamException("Skin is too large")
        val image = withContext(Dispatchers.IO) { decodeSkin(bytes) }
        return image?.let(::faceFromSkin)
    }

    private companion object {
        const val MAX_SKIN_BYTES = 64 * 1024
        const val FACE_TTL_MILLIS = 24 * 60 * 60_000L
        const val FAILURE_TTL_MILLIS = 5 * 60_000L
    }
}

/**
 * Decodes [bytes] as a PNG skin, or null if it isn't one. The size is checked from the header before any pixels are
 * decoded, so a tiny file claiming huge dimensions can't exhaust memory.
 */
internal fun decodeSkin(bytes: ByteArray): BufferedImage? {
    val readers = ImageIO.getImageReadersByFormatName("png")
    if (!readers.hasNext()) return null
    val reader = readers.next()
    return try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            reader.setInput(input, true, true)
            if (isSkinSize(reader.getWidth(0), reader.getHeight(0))) reader.read(0) else null
        }
    } catch (e: IOException) {
        null
    } finally {
        reader.dispose()
    }
}

/** 64x64 or legacy 64x32 skins and their HD multiples up to 1024 wide. */
private fun isSkinSize(width: Int, height: Int) =
    width in 64..1024 && width % 64 == 0 && (height == width || height == width / 2)

/**
 * The 8x8 front face of a skin with its hat layer on top: 64 opaque ARGB ints, row-major. Accepts 64x64 and legacy
 * 64x32 skins and their HD multiples up to 1024 wide; anything else gives null.
 */
internal fun faceFromSkin(skin: BufferedImage): List<Int>? {
    val width = skin.width
    if (!isSkinSize(width, skin.height)) return null
    val scale = width / 64
    fun pixel(x: Int, y: Int) = skin.getRGB(x * scale, y * scale)

    val base = List(64) { pixel(8 + it % 8, 8 + it / 8) or OPAQUE }
    val hat = List(64) { pixel(40 + it % 8, 8 + it / 8) }
    // Like Minecraft, ignore a legacy skin's hat layer when it's fully opaque: old skins often filled it with black.
    val legacy = skin.height == width / 2
    if (legacy && hat.none { it ushr 24 < 128 }) return base
    return List(64) { blend(base[it], hat[it]) }
}

private const val OPAQUE = 0xFF000000.toInt()

/** [top] drawn over the opaque [bottom] by its alpha. */
private fun blend(bottom: Int, top: Int): Int {
    val a = top ushr 24
    fun channel(shift: Int) = ((bottom shr shift and 0xFF) * (255 - a) + (top shr shift and 0xFF) * a) / 255
    return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
