package com.heretek.dorado_hd.cloud

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the detached RS256 signature on a Dorado Cloud update manifest,
 * matching `DoradoCloud.Shared/Contracts/UpdateManifestCrypto.cs` byte-for-byte.
 *
 * The signed bytes are the canonical JSON produced by `System.Text.Json` with
 * `JsonSerializerDefaults.Web`: compact output, properties in declaration order
 * (`app, channel, version, url, sha256, publishedAt, notes`), and the default
 * `JavaScriptEncoder` escaping. [canonicalManifestJson] reproduces that
 * escaping exactly (verified against generated .NET vectors):
 *
 *  - `"` `&` `'` `+` `<` `>` `` ` `` are emitted as uppercase `\uXXXX`;
 *  - `\` is `\\`; `\b` `\f` `\n` `\r` `\t` use their short escapes;
 *  - all other control characters and every non-ASCII unit are `\uXXXX`
 *    (uppercase), including surrogate halves.
 */
object CloudUpdateVerifier {

    fun verify(manifest: CloudUpdateManifest, signatureBase64: String, publicKeyPem: String): Boolean {
        return try {
            val keyBytes = decodePemPublicKey(publicKeyPem)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(canonicalManifestJson(manifest).toByteArray(Charsets.UTF_8))
            signature.verify(Base64.getDecoder().decode(signatureBase64))
        } catch (_: Exception) {
            false
        }
    }

    fun canonicalManifestJson(manifest: CloudUpdateManifest): String {
        val builder = StringBuilder()
        builder.append('{')
        appendField(builder, "app", manifest.app)
        builder.append(',')
        appendField(builder, "channel", manifest.channel)
        builder.append(',')
        appendField(builder, "version", manifest.version)
        builder.append(',')
        appendField(builder, "url", manifest.url)
        builder.append(',')
        appendField(builder, "sha256", manifest.sha256)
        builder.append(',')
        // DateTimeOffset is written by System.Text.Json's built-in converter,
        // which emits the ISO 8601 text verbatim (the timezone '+') without
        // routing through JavaScriptEncoder. Quote it without escaping.
        builder.append("\"publishedAt\":\"").append(manifest.publishedAt).append('"')
        builder.append(',')
        appendField(builder, "notes", manifest.notes)
        builder.append('}')
        return builder.toString()
    }

    private fun appendField(builder: StringBuilder, name: String, value: String) {
        builder.append('"').append(name).append('"').append(':')
        appendEscaped(builder, value)
    }

    private fun appendEscaped(builder: StringBuilder, value: String) {
        builder.append('"')
        for (character in value) {
            when (character) {
                '"' -> builder.append("\\u0022")
                '&' -> builder.append("\\u0026")
                '\'' -> builder.append("\\u0027")
                '+' -> builder.append("\\u002B")
                '<' -> builder.append("\\u003C")
                '>' -> builder.append("\\u003E")
                '`' -> builder.append("\\u0060")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else ->
                    if (character < ' ' || character == '\u007F' || character > '\u007E') {
                        builder.append("\\u")
                        builder.append(character.code.toString(16).uppercase().padStart(4, '0'))
                    } else {
                        builder.append(character)
                    }
            }
        }
        builder.append('"')
    }

    private fun decodePemPublicKey(pem: String): ByteArray {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filterNot { it.isWhitespace() }
        return Base64.getDecoder().decode(body)
    }
}
