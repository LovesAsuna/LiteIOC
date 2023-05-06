package com.hyosakura.liteioc.core.convert.converter

import com.hyosakura.liteioc.core.convert.TypeDescriptor

/**
 * @author LovesAsuna
 **/
interface GenericConverter {

    fun getConvertibleTypes(): MutableSet<ConvertiblePair>?

    fun convert(source: Any?, sourceType: TypeDescriptor, targetType: TypeDescriptor): Any?

    class ConvertiblePair(val sourceType: Class<*>, val targetType: Class<*>) {

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || other.javaClass != ConvertiblePair::class.java) {
                return false
            }
            val otherPair = other as ConvertiblePair
            return sourceType == otherPair.sourceType && targetType == otherPair.targetType
        }

        override fun hashCode(): Int {
            return sourceType.hashCode() * 31 + targetType.hashCode()
        }

        override fun toString(): String {
            return sourceType.name + " -> " + targetType.name
        }
    }

}
