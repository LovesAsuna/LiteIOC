package com.hyosakura.liteioc.core.convert

import com.hyosakura.liteioc.core.convert.converter.GenericConverter
import com.hyosakura.liteioc.core.convert.converter.GenericConverter.ConvertiblePair
import com.hyosakura.liteioc.core.convert.support.ConversionUtil
import com.hyosakura.liteioc.util.ClassUtil
import org.jetbrains.annotations.Nullable
import java.lang.reflect.Array
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * @author LovesAsuna
 **/
open class GenericConversionService : ConversionService {

    companion object {

        private val NO_OP_CONVERTER: GenericConverter = NoOpConverter("NO_OP")

        private val NO_MATCH: GenericConverter = NoOpConverter("NO_MATCH")

    }

    private val converterCache: MutableMap<ConverterCacheKey, GenericConverter> = ConcurrentHashMap(64)

    private val converters = Converters()

    override fun canConvert(sourceType: Class<*>?, targetType: Class<*>): Boolean {
        if (sourceType == null) {
            return true
        }
        val converter = getConverter(TypeDescriptor.valueOf(sourceType), TypeDescriptor.valueOf(targetType))
        return converter != null
    }

    override fun canConvert(sourceType: TypeDescriptor?, targetType: TypeDescriptor): Boolean {
        if (sourceType == null) {
            return true
        }
        val converter = getConverter(sourceType, targetType)
        return converter != null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> convert(source: Any?, targetType: Class<T>): T? {
        return convert(source, TypeDescriptor.forObject(source), TypeDescriptor.valueOf(targetType)) as T?
    }

    override fun convert(
        source: Any?,
        sourceType: TypeDescriptor?,
        targetType: TypeDescriptor
    ): Any? {
        if (sourceType == null) {
            requireNotNull(source) { "Source must be [null] if source type == [null]" }
            return handleResult(null, targetType, convertNullSource(null, targetType))
        }
        if (source != null && !sourceType.getObjectType().isInstance(source)) {
            throw IllegalArgumentException(
                "Source to convert from must be an instance of [" +
                        sourceType + "]; instead it was a [" + source.javaClass.name + "]"
            )
        }
        val converter = getConverter(sourceType, (targetType))
        if (converter != null) {
            val result = ConversionUtil.invokeConverter(converter, source, sourceType, targetType)
            return handleResult(sourceType, targetType, result)
        }
        return handleConverterNotFound(source, sourceType, targetType)
    }

    protected open fun convertNullSource(sourceType: TypeDescriptor?, targetType: TypeDescriptor): Any? {
        return if (targetType.getObjectType() == Optional::class.java) {
            Optional.empty<Any>()
        } else null
    }

    private fun getConverter(sourceType: TypeDescriptor, targetType: TypeDescriptor): GenericConverter? {
        val key = ConverterCacheKey(sourceType, targetType)
        var converter = this.converterCache[key]
        if (converter != null) {
            return if (converter != NO_MATCH) converter else null
        }
        converter = this.converters.find(sourceType, targetType)
        if (converter == null) {
            converter = getDefaultConverter(sourceType, targetType)
        }
        if (converter != null) {
            this.converterCache[key] = converter
            return converter
        }
        this.converterCache[key] = NO_MATCH
        return null
    }

    private fun getDefaultConverter(sourceType: TypeDescriptor, targetType: TypeDescriptor): GenericConverter? {
        return if (sourceType.isAssignableTo(targetType)) NO_OP_CONVERTER else null
    }

    private class ConverterCacheKey(private val sourceType: TypeDescriptor, private val targetType: TypeDescriptor) :
        Comparable<ConverterCacheKey> {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other !is ConverterCacheKey) {
                false
            } else sourceType == other.sourceType && targetType == other.targetType
        }

        override fun hashCode(): Int {
            return sourceType.hashCode() * 29 + targetType.hashCode()
        }

        override fun toString(): String {
            return "ConverterCacheKey [sourceType = $sourceType, targetType = $targetType]"
        }

        override operator fun compareTo(other: ConverterCacheKey): Int {
            var result: Int = sourceType.toString().compareTo(
                other.sourceType.toString()
            )
            if (result == 0) {
                result = targetType.toString().compareTo(
                    other.targetType.toString()
                )
            }
            return result
        }

    }

    private fun handleConverterNotFound(
        source: Any?, sourceType: TypeDescriptor?, targetType: TypeDescriptor
    ): Any? {
        if (source == null) {
            assertNotPrimitiveTargetType(sourceType, targetType)
            return null
        }
        if ((sourceType == null || sourceType.isAssignableTo(targetType)) &&
            targetType.getObjectType().isInstance(source)
        ) {
            return source
        }
        throw ConverterNotFoundException(sourceType, targetType)
    }

    private fun handleResult(
        sourceType: TypeDescriptor?,
        targetType: TypeDescriptor,
        result: Any?
    ): Any? {
        if (result == null) {
            assertNotPrimitiveTargetType(sourceType, targetType)
        }
        return result
    }

    private fun assertNotPrimitiveTargetType(sourceType: TypeDescriptor?, targetType: TypeDescriptor) {
        if (targetType.isPrimitive()) {
            throw ConversionFailedException(
                sourceType, targetType, null,
                IllegalArgumentException("A null value cannot be assigned to a primitive type")
            )
        }
    }

    private class ConvertersForPair {

        private val converters: Deque<GenericConverter> = ConcurrentLinkedDeque()

        fun add(converter: GenericConverter) {
            converters.addFirst(converter)
        }

        fun getConverter(sourceType: TypeDescriptor, targetType: TypeDescriptor): GenericConverter? {
            return converters.random()
        }

        override fun toString(): String {
            return converters.joinToString(",")
        }

    }

    private class Converters {

        private val converters: MutableMap<ConvertiblePair, ConvertersForPair> = ConcurrentHashMap(
            256
        )

        fun add(converter: GenericConverter) {
            val convertibleTypes = converter.getConvertibleTypes()
            if (convertibleTypes != null) {
                for (convertiblePair in convertibleTypes) {
                    getMatchableConverters(convertiblePair).add(converter)
                }
            }
        }

        private fun getMatchableConverters(convertiblePair: ConvertiblePair): ConvertersForPair {
            return converters.computeIfAbsent(
                convertiblePair
            ) { ConvertersForPair() }
        }

        fun remove(sourceType: Class<*>?, targetType: Class<*>?) {
            converters.remove(ConvertiblePair(sourceType!!, targetType!!))
        }

        fun find(sourceType: TypeDescriptor, targetType: TypeDescriptor): GenericConverter? {
            // Search the full type hierarchy
            val sourceCandidates = getClassHierarchy(sourceType.type)
            val targetCandidates = getClassHierarchy(targetType.type)
            for (sourceCandidate in sourceCandidates) {
                for (targetCandidate in targetCandidates) {
                    val convertiblePair = ConvertiblePair(sourceCandidate, targetCandidate)
                    val converter = getRegisteredConverter(sourceType, targetType, convertiblePair)
                    if (converter != null) {
                        return converter
                    }
                }
            }
            return null
        }

        private fun getRegisteredConverter(
            sourceType: TypeDescriptor, targetType: TypeDescriptor, convertiblePair: ConvertiblePair
        ): GenericConverter? {
            // Check specifically registered converters
            val convertersForPair = converters[convertiblePair]
            if (convertersForPair != null) {
                val converter = convertersForPair.getConverter(sourceType, targetType)
                if (converter != null) {
                    return converter
                }
            }
            return null
        }

        private fun getClassHierarchy(type: Class<*>): List<Class<*>> {
            val hierarchy: MutableList<Class<*>> = ArrayList(20)
            val visited: MutableSet<Class<*>> = HashSet(20)
            addToClassHierarchy(0, ClassUtil.resolvePrimitiveIfNecessary(type), false, hierarchy, visited)
            val array = type.isArray
            var i = 0
            while (i < hierarchy.size) {
                var candidate = hierarchy[i]
                candidate = if (array) candidate.componentType else ClassUtil.resolvePrimitiveIfNecessary(candidate)
                val superclass = candidate.superclass
                if (superclass != null && superclass != Any::class.java && superclass != Enum::class.java) {
                    addToClassHierarchy(i + 1, candidate.superclass, array, hierarchy, visited)
                }
                addInterfacesToClassHierarchy(candidate, array, hierarchy, visited)
                i++
            }
            if (Enum::class.java.isAssignableFrom(type)) {
                addToClassHierarchy(hierarchy.size, Enum::class.java, array, hierarchy, visited)
                addToClassHierarchy(hierarchy.size, Enum::class.java, false, hierarchy, visited)
                addInterfacesToClassHierarchy(Enum::class.java, array, hierarchy, visited)
            }
            addToClassHierarchy(hierarchy.size, Any::class.java, array, hierarchy, visited)
            addToClassHierarchy(hierarchy.size, Any::class.java, false, hierarchy, visited)
            return hierarchy
        }

        private fun addInterfacesToClassHierarchy(
            type: Class<*>, asArray: Boolean, hierarchy: MutableList<Class<*>>, visited: MutableSet<Class<*>>
        ) {
            for (implementedInterface in type.interfaces) {
                addToClassHierarchy(hierarchy.size, implementedInterface, asArray, hierarchy, visited)
            }
        }

        private fun addToClassHierarchy(
            index: Int,
            type: Class<*>,
            asArray: Boolean,
            hierarchy: MutableList<Class<*>>,
            visited: MutableSet<Class<*>>
        ) {
            var type = type
            if (asArray) {
                type = Array.newInstance(type, 0).javaClass
            }
            if (visited.add(type)) {
                hierarchy.add(index, type)
            }
        }

        override fun toString(): String {
            val builder = StringBuilder()
            builder.append("ConversionService converters =\n")
            for (converterString in converterStrings) {
                builder.append('\t').append(converterString).append('\n')
            }
            return builder.toString()
        }

        private val converterStrings: List<String>
            get() {
                val converterStrings: MutableList<String> = ArrayList()
                for (convertersForPair in converters.values) {
                    converterStrings.add(convertersForPair.toString())
                }
                converterStrings.sort()
                return converterStrings
            }
    }

    private class NoOpConverter(private val name: String) : GenericConverter {

        override fun getConvertibleTypes(): MutableSet<ConvertiblePair>? {
            return null
        }

        override fun convert(source: Any?, sourceType: TypeDescriptor, targetType: TypeDescriptor): Any? {
            return source
        }

        override fun toString(): String {
            return name
        }

    }

}