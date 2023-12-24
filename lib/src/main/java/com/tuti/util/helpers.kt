package com.tuti.util

import com.tuti.model.KYC
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.Date

@Serializer(forClass = Date::class)
object DateSerializer : KSerializer<Date> {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") // Define your date format

    override fun serialize(encoder: Encoder, value: Date) {
        val str = dateFormat.format(value)
        encoder.encodeString(str)
    }

    override fun deserialize(decoder: Decoder): Date {
        val str = decoder.decodeString()
        return dateFormat.parse(str)
    }
}

object GenderSerializer : KSerializer<KYC.Gender> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Gender", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: KYC.Gender) {
        val serialName = when (value) {
            is KYC.Gender.Male -> "male"
            is KYC.Gender.Female -> "female"
        }
        encoder.encodeString(serialName)
    }

    override fun deserialize(decoder: Decoder): KYC.Gender {
        return when (val string = decoder.decodeString()) {
            "male" -> KYC.Gender.Male
            "female" -> KYC.Gender.Female
            else -> throw IllegalArgumentException("$string is not a valid gender")
        }
    }
}
