package com.veronezzi.colaeleitoral.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * Decodes a JSON array and drops the items that fail to decode, so one malformed candidacy never
 * hides the others (ARCHITECTURE.md 2.3). A value that is not an array decodes as an empty list.
 */
open class LenientListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            try {
                jsonDecoder.json.decodeFromJsonElement(element, item)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

object EleicaoListSerializer : LenientListSerializer<EleicaoDto>(EleicaoDto.serializer())

object UeListSerializer : LenientListSerializer<UeDto>(UeDto.serializer())

object CargoListSerializer : LenientListSerializer<CargoDto>(CargoDto.serializer())

object CandidatoListSerializer : LenientListSerializer<CandidatoDto>(CandidatoDto.serializer())

object ViceListSerializer : LenientListSerializer<ViceDto>(ViceDto.serializer())

object EleicaoAnteriorListSerializer : LenientListSerializer<EleicaoAnteriorDto>(EleicaoAnteriorDto.serializer())
