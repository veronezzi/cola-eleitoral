package com.veronezzi.colaeleitoral.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement

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

/**
 * Like [LenientListSerializer], but reads the array one item at a time: only one item's JSON tree
 * exists at once instead of the whole list's. Used for the largest response, the E4 candidate
 * list (~1.400 items of ~85 keys, a peak near 30 MB as a full tree on small API 26 heaps). Items
 * that fail to decode are still dropped one by one; a value that is not an array is a format error.
 */
open class StreamingLenientListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)
    private val items = ListSerializer(JsonElement.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val json = (decoder as? JsonDecoder)?.json ?: return delegate.deserialize(decoder)
        val result = ArrayList<T>()
        val list = decoder.beginStructure(items.descriptor)
        if (list.decodeSequentially()) {
            repeat(list.decodeCollectionSize(items.descriptor)) { index -> decodeItem(json, list, index)?.let(result::add) }
        } else {
            while (true) {
                val index = list.decodeElementIndex(items.descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                decodeItem(json, list, index)?.let(result::add)
            }
        }
        list.endStructure(items.descriptor)
        return result
    }

    private fun decodeItem(json: Json, list: CompositeDecoder, index: Int): T? {
        val item = list.decodeSerializableElement(items.descriptor, index, JsonElement.serializer())
        return try {
            json.decodeFromJsonElement(element, item)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

object CandidatoListSerializer : StreamingLenientListSerializer<CandidatoDto>(CandidatoDto.serializer())

object ViceListSerializer : LenientListSerializer<ViceDto>(ViceDto.serializer())

object EleicaoAnteriorListSerializer : LenientListSerializer<EleicaoAnteriorDto>(EleicaoAnteriorDto.serializer())
