package com.tuti.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * gRPC-Gateway (protobuf JSON mapping) represents int64 fields as JSON strings.
 * This serializer encodes Kotlin [Long] values as JSON strings and can decode from either a JSON
 * string or number to be tolerant of non-conforming clients/servers.
 */
object LongAsStringSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LongAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Long {
        // JSON: accept both "123" and 123.
        if (decoder is JsonDecoder) {
            val prim = decoder.decodeJsonElement().jsonPrimitive
            return prim.longOrNull ?: prim.content.toLong()
        }

        // Fallback for other formats.
        return try {
            decoder.decodeLong()
        } catch (_: Exception) {
            decoder.decodeString().toLong()
        }
    }
}

