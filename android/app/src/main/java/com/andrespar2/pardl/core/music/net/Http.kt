package com.andrespar2.pardl.core.music.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Cliente HTTP mínimo para proveedores musicales.
 *
 * Reglas de seguridad:
 *  - Solo HTTPS en el módulo de música.
 *  - Se rechazan hosts locales, privados, de enlace local y metadatos de nube.
 *  - Las redirecciones se siguen a mano (máximo 5) validando cada destino.
 *  - No se registran cabeceras de autenticación ni URLs con tokens.
 */
object Http {

    const val USER_AGENT =
        "ParDL/0.2.0 (Android; +https://github.com/andrespar2/ParDL) music-module"

    init {
        // Algunos proveedores (MusicBrainz) exigen identificar la aplicación.
        System.setProperty("http.agent", USER_AGENT)
    }

    data class HeadInfo(
        val ok: Boolean,
        val code: Int,
        val contentType: String?,
        val contentLength: Long?,
        val finalUrl: String,
        val acceptsRanges: Boolean,
        val error: String? = null
    )

    class Body(
        val bytes: ByteArray,
        val contentType: String?,
        val finalUrl: String,
        val code: Int
    ) {
        val text: String get() = bytes.toString(Charsets.UTF_8)
    }

    class Stream(
        val connection: HttpURLConnection,
        val input: InputStream,
        val contentType: String?,
        val finalUrl: String,
        val contentLength: Long,
        val totalLength: Long,
        val resumed: Boolean,
        val code: Int
    ) {
        fun close() {
            runCatching { input.close() }
            connection.disconnect()
        }
    }

    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20_000
    ): String = withContext(Dispatchers.IO) {
        fetch(url, headers, timeoutMs).text
    }

    suspend fun fetch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20_000
    ): Body = withContext(Dispatchers.IO) {
        val response = execute(url, "GET", headers, timeoutMs)
        if (response.responseCode !in 200..299) {
            val code = response.responseCode
            response.disconnect()
            throw IOException("HTTP $code al consultar el proveedor.")
        }
        val bytes = response.inputStream.use { it.readBytes() }
        val type = response.contentType
        val finalUrl = response.url.toString()
        val code = response.responseCode
        response.disconnect()
        Body(bytes, type, finalUrl, code)
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000
    ): HeadInfo = withContext(Dispatchers.IO) {
        try {
            val response = execute(url, "HEAD", headers, timeoutMs)
            var code = response.responseCode
            var type = response.contentType
            var length = headerLong(response, "Content-Length")
            var ranges = response.getHeaderField("Accept-Ranges")
                ?.contains("bytes", ignoreCase = true) == true
            val finalUrl = response.url.toString()
            response.disconnect()

            // Algunos CDN no implementan HEAD: se prueba un GET de 1 byte.
            if (code == 405 || code == 403 || code == 400) {
                val probe = execute(url, "GET", headers + ("Range" to "bytes=0-0"), timeoutMs)
                code = probe.responseCode
                type = probe.contentType ?: type
                val contentRange = probe.getHeaderField("Content-Range")
                if (contentRange != null) {
                    ranges = true
                    length = contentRange.substringAfterLast('/', "")
                        .toLongOrNull() ?: length
                }
                probe.disconnect()
            }
            HeadInfo(
                ok = code in 200..299 || code == 206,
                code = code,
                contentType = type,
                contentLength = length,
                finalUrl = finalUrl,
                acceptsRanges = ranges
            )
        } catch (t: Throwable) {
            HeadInfo(false, 0, null, null, url, false, t.message ?: "Error de red")
        }
    }

    /**
     * Abre una descarga. Si [rangeStart] > 0 se solicita `Range` y se informa
     * si el servidor lo respetó (206) o reinició el archivo (200).
     */
    suspend fun openStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        rangeStart: Long = 0,
        timeoutMs: Int = 25_000
    ): Stream = withContext(Dispatchers.IO) {
        val merged = headers.toMutableMap()
        if (rangeStart > 0) merged["Range"] = "bytes=$rangeStart-"
        val response = execute(url, "GET", merged, timeoutMs)
        val code = response.responseCode
        if (code !in 200..299) {
            response.disconnect()
            throw IOException("HTTP $code al descargar el archivo.")
        }
        val contentLength = headerLong(response, "Content-Length") ?: -1
        val contentRange = response.getHeaderField("Content-Range")
        val resumed = rangeStart > 0 && code == 206
        val total = if (resumed && contentRange != null) {
            contentRange.substringAfterLast('/', "").toLongOrNull() ?: (rangeStart + contentLength)
        } else {
            contentLength
        }
        Stream(
            connection = response,
            input = response.inputStream,
            contentType = response.contentType,
            finalUrl = response.url.toString(),
            contentLength = contentLength,
            totalLength = total,
            resumed = resumed,
            code = code
        )
    }

    /** Comprueba que una URL sea HTTPS y no apunte a una red privada. */
    fun validateUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "La dirección está vacía."
        val uri = try { URI(value) } catch (_: Exception) { return "La dirección no es válida." }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return "Por seguridad, el módulo de música solo admite HTTPS."
        }
        val host = uri.host ?: return "El dominio de la dirección no es válido."
        if (host.isBlank() || !host.contains('.')) return "El dominio de la dirección no es válido."
        if (isPrivateHost(host)) return "El destino apunta a una red privada o local."
        return null
    }

    fun isPrivateHost(host: String): Boolean {
        val addresses = try { InetAddress.getAllByName(host) } catch (_: Exception) { return true }
        return addresses.any { address ->
            val bytes = address.address
            val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
            val cgnat = bytes.size == 4 && bytes[0].toInt() and 0xFF == 100 &&
                (bytes[1].toInt() and 0xFF) in 64..127
            address.isAnyLocalAddress || address.isLoopbackAddress ||
                address.isLinkLocalAddress || address.isSiteLocalAddress ||
                address.isMulticastAddress || uniqueLocalV6 || cgnat
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Codificación válida para segmentos de ruta (el + de URLEncoder es literal). */
    fun encodePath(value: String): String = encode(value).replace("+", "%20")

    private fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        timeoutMs: Int
    ): HttpURLConnection {
        var current = url
        repeat(5) {
            val scheme = URI(current).scheme?.lowercase(Locale.ROOT)
            if (scheme != "https") throw IOException("Solo se admite HTTPS.")
            val host = URI(current).host ?: throw IOException("Dominio no válido.")
            if (isPrivateHost(host)) throw IOException("Destino de red privada bloqueado.")

            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.6")
            conn.setRequestProperty("Accept", "*/*")
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }

            val code = conn.responseCode
            if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirección sin destino.")
                current = URI(current).resolve(location).toString()
                return@repeat
            }
            return conn
        }
        throw IOException("Demasiadas redirecciones.")
    }

    private fun headerLong(conn: HttpURLConnection, name: String): Long? =
        conn.getHeaderField(name)?.trim()?.toLongOrNull()
}
