package com.nousresearch.hermesmobile.data

import java.net.URI

/**
 * Single source of truth for Hermes gateway URLs.
 *
 * Android's Network Security Config cannot express "any RFC1918/ULA host" for dynamically entered
 * addresses. The manifest therefore permits cleartext transport, while this policy is enforced at
 * every HTTP/WebSocket client boundary: public gateways must use HTTPS; HTTP is accepted only for
 * loopback, link-local and private LAN destinations.
 */
object GatewayUrlPolicy {
    fun normalize(input: String): String {
        var raw = input.trim()
        require(raw.isNotBlank()) { "Introduce la dirección del gateway Hermes" }

        // Be forgiving when a URL is pasted from rich text/Markdown.
        Regex("^\\[([^]]+)]\\((https?://[^)]+)\\)$").matchEntire(raw)?.let { raw = it.groupValues[2] }
        raw = raw.trim().trimEnd('/')

        if (!raw.contains("://")) {
            val authority = raw.substringBefore('/')
            val host = when {
                authority.startsWith("[") -> authority.substringAfter('[').substringBefore(']')
                else -> authority.substringBefore(':')
            }
            raw = (if (isPrivateOrLocalHost(host)) "http://" else "https://") + raw
        }

        val parsed = parse(raw)
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val httpScheme = when (scheme) {
            "ws" -> "http"
            "wss" -> "https"
            else -> scheme
        }
        if (httpScheme != scheme) {
            raw = URI(
                httpScheme,
                parsed.userInfo,
                parsed.host,
                parsed.port,
                parsed.path,
                parsed.query,
                parsed.fragment
            ).toString()
        }
        return raw.trimEnd('/')
    }

    fun validate(baseUrl: String) {
        val raw = baseUrl.trim()
        val uri = parse(raw)
        val scheme = uri.scheme?.lowercase().orEmpty()
        require(raw.isNotBlank() && scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "Introduce una URL válida del gateway Hermes"
        }
        require(uri.userInfo.isNullOrBlank()) { "No incluyas usuario o contraseña dentro de la URL del gateway" }
        require(uri.fragment.isNullOrBlank() && uri.query.isNullOrBlank()) {
            "La URL del gateway no debe incluir query ni fragmento"
        }
        require(uri.path.isNullOrBlank() || uri.path == "/") {
            "Introduce la URL base del gateway, sin una ruta adicional"
        }
        if (scheme == "http" && !isPrivateOrLocalHost(uri.host)) {
            throw IllegalArgumentException(
                "HTTP sin cifrar solo está permitido para gateways de red local/privada. Usa HTTPS para hosts públicos."
            )
        }
    }

    fun canonical(input: String): String = normalize(input).also(::validate)

    fun defaultLabel(baseUrl: String): String = defaultLabel(parse(baseUrl))

    fun defaultLabel(uri: URI): String {
        val host = uri.host.orEmpty().removePrefix("[").removeSuffix("]")
        return when {
            host.equals("localhost", ignoreCase = true) -> "Local Hermes"
            host.isNotBlank() -> host
            else -> "Hermes"
        }
    }

    fun isPrivateOrLocalHost(hostValue: String?): Boolean {
        val host = hostValue.orEmpty().trim().removePrefix("[").removeSuffix("]").lowercase()
        if (host.isBlank()) return false
        if (host == "localhost" || host.endsWith(".local")) return true

        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.matches(Regex("[0-9]{1,3}")) }) {
            val v4 = parts.map { it.toInt() }
            if (v4.any { it !in 0..255 }) return false
            return v4[0] == 10 || v4[0] == 127 ||
                (v4[0] == 169 && v4[1] == 254) ||
                (v4[0] == 172 && v4[1] in 16..31) ||
                (v4[0] == 192 && v4[1] == 168)
        }
        // Validate a literal IPv6 address; a public hostname beginning with 'fc' is not a ULA.
        if (':' in host && host.matches(Regex("[0-9a-f:]+"))) {
            val address = runCatching { java.net.InetAddress.getByName(host) }.getOrNull()
            if (address is java.net.Inet6Address) {
                val bytes = address.address
                return address.isLoopbackAddress || (bytes[0].toInt() and 0xfe) == 0xfc ||
                    ((bytes[0].toInt() and 0xff) == 0xfe && (bytes[1].toInt() and 0xc0) == 0x80)
            }
        }
        return false
    }

    private fun parse(raw: String): URI = runCatching { URI(raw) }
        .getOrElse { throw IllegalArgumentException("Introduce una URL válida del gateway Hermes") }
}
