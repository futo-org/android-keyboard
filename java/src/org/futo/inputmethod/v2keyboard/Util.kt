package org.futo.inputmethod.v2keyboard

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlPath
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


open class ClassOrScalarsSerializer<T>(
    private val serializer: KSerializer<T>,
    private val stringFactory: (List<String>) -> T,
) : KSerializer<T> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor(
            this::class.java.name,
            SerialKind.CONTEXTUAL
        )

    override fun deserialize(decoder: Decoder): T {
        val valueDecoder = decoder.beginStructure(descriptor)
        return when(val element = (valueDecoder as YamlInput).node) {
            is YamlScalar -> stringFactory(listOf(element.content))
            is YamlList ->
                stringFactory(element.items.map {
                    valueDecoder.yaml.decodeFromYamlNode(String.serializer(), it)
                })
            is YamlMap -> valueDecoder.yaml.decodeFromYamlNode(serializer, element)

            else -> throw SerializationException("Unexpected YAML element type: ${element::class.simpleName}")
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeSerializableValue(serializer, value)
    }
}

open class PathDependentModifier<T>(
    private val baseSerializer: KSerializer<T>,
    private val editor: (YamlPath, T) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = baseSerializer.descriptor

    override fun deserialize(decoder: Decoder): T {
        val valueDecoder = decoder.beginStructure(descriptor)
        val element = (valueDecoder as YamlInput).node

        return valueDecoder.yaml.decodeFromYamlNode(baseSerializer, element).let {
            editor(element.path, it)
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeSerializableValue(baseSerializer, value)
    }
}


open class SpacedListSerializer<T>(
    private val serializer: KSerializer<T>,
    private val splitter: (String) -> List<String> = { it.split(' ') }
) : KSerializer<List<T>> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor(
            this::class.java.name,
            SerialKind.CONTEXTUAL
        )

    override fun deserialize(decoder: Decoder): List<T> {
        val valueDecoder = decoder.beginStructure(descriptor)
        val elements = when(val element = (valueDecoder as YamlInput).node) {
            is YamlScalar -> splitter(element.content).map { YamlScalar(it, element.path) }
            is YamlList -> element.items
            else -> throw SerializationException("Unexpected YAML element type: ${element::class.simpleName}")
        }

        return elements.map { element ->
            valueDecoder.yaml.decodeFromYamlNode(serializer, element)
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) {
        TODO()
    }
}