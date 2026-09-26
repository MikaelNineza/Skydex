package com.skydex.shared.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A list of enum constants, encoded by name, that drops names it doesn't know when decoding, so a peer on a newer
 * version (with more constants) doesn't fail the whole payload.
 */
abstract class LenientEnumListSerializer<E : Enum<E>>(private val values: List<E>) : KSerializer<List<E>> {
    private val names = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = names.descriptor

    override fun serialize(encoder: Encoder, value: List<E>) = names.serialize(encoder, value.map { it.name })

    override fun deserialize(decoder: Decoder): List<E> =
        names.deserialize(decoder).mapNotNull { name -> values.find { it.name == name } }
}

/** Like [LenientEnumListSerializer], for a set. */
abstract class LenientEnumSetSerializer<E : Enum<E>>(values: List<E>) : KSerializer<Set<E>> {
    private val list = object : LenientEnumListSerializer<E>(values) {}
    override val descriptor: SerialDescriptor = list.descriptor

    override fun serialize(encoder: Encoder, value: Set<E>) = list.serialize(encoder, value.toList())

    override fun deserialize(decoder: Decoder): Set<E> = list.deserialize(decoder).toSet()
}

object LenientCropList : LenientEnumListSerializer<Crop>(Crop.entries)

object LenientCropSet : LenientEnumSetSerializer<Crop>(Crop.entries)

object LenientEventTypeSet : LenientEnumSetSerializer<EventType>(EventType.entries)
