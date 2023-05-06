package com.hyosakura.liteioc.core.annotation

import java.util.*
import java.util.function.Function
import java.util.function.Predicate

/**
 * @author LovesAsuna
 **/
interface MergedAnnotation<A : Annotation> {
    companion object {

        const val VALUE = "value"

        fun <A : Annotation> missing(): MergedAnnotation<A> {
            return MissingMergedAnnotation.getInstance()
        }

    }

    fun getType(): Class<A>

    fun isPresent(): Boolean

    fun isDirectlyPresent(): Boolean

    fun isMetaPresent(): Boolean

    fun getDistance(): Int

    fun getAggregateIndex(): Int

    fun getSource(): Any?

    fun getMetaSource(): MergedAnnotation<*>?

    fun getRoot(): MergedAnnotation<*>?

    fun getMetaTypes(): List<Class<out Annotation>>

    fun hasNonDefaultValue(attributeName: String): Boolean

    @Throws(NoSuchElementException::class)
    fun hasDefaultValue(attributeName: String): Boolean

    @Throws(NoSuchElementException::class)
    fun getByte(attributeName: String): Byte

    @Throws(NoSuchElementException::class)
    fun getByteArray(attributeName: String): ByteArray

    @Throws(NoSuchElementException::class)
    fun getBoolean(attributeName: String): Boolean

    @Throws(NoSuchElementException::class)
    fun getBooleanArray(attributeName: String): BooleanArray


    @Throws(NoSuchElementException::class)
    fun getChar(attributeName: String): Char

    @Throws(NoSuchElementException::class)
    fun getCharArray(attributeName: String): CharArray

    @Throws(NoSuchElementException::class)
    fun getShort(attributeName: String): Short

    @Throws(NoSuchElementException::class)
    fun getShortArray(attributeName: String): ShortArray

    @Throws(NoSuchElementException::class)
    fun getInt(attributeName: String): Int

    @Throws(NoSuchElementException::class)
    fun getIntArray(attributeName: String): IntArray

    @Throws(NoSuchElementException::class)
    fun getLong(attributeName: String): Long

    @Throws(NoSuchElementException::class)
    fun getLongArray(attributeName: String): LongArray

    @Throws(NoSuchElementException::class)
    fun getDouble(attributeName: String): Double

    @Throws(NoSuchElementException::class)
    fun getDoubleArray(attributeName: String): DoubleArray

    @Throws(NoSuchElementException::class)
    fun getFloat(attributeName: String): Float

    @Throws(NoSuchElementException::class)
    fun getFloatArray(attributeName: String): FloatArray

    @Throws(NoSuchElementException::class)
    fun getString(attributeName: String): String

    @Throws(NoSuchElementException::class)
    fun getStringArray(attributeName: String): Array<String>

    @Throws(NoSuchElementException::class)
    fun getClass(attributeName: String): Class<*>

    @Throws(NoSuchElementException::class)
    fun getClassArray(attributeName: String): Array<Class<*>>

    @Throws(NoSuchElementException::class)
    fun <E : Enum<E>> getEnum(attributeName: String, type: Class<E>): E

    @Throws(NoSuchElementException::class)
    fun <E : Enum<E>> getEnumArray(attributeName: String, type: Class<E>): Array<E>

    @Throws(NoSuchElementException::class)
    fun <T : Annotation> getAnnotation(attributeName: String, type: Class<T>): MergedAnnotation<T>

    @Throws(NoSuchElementException::class)
    fun <T : Annotation> getAnnotationArray(attributeName: String, type: Class<T>): Array<MergedAnnotation<T>>

    fun getValue(attributeName: String): Any?

    fun <T> getValue(attributeName: String, type: Class<T>): T?

    fun getDefaultValue(attributeName: String): Any?

    fun <T> getDefaultValue(attributeName: String, type: Class<T>): T?

    fun filterDefaultValues(): MergedAnnotation<A>

    fun filterAttributes(predicate: Predicate<String>): MergedAnnotation<A>

    fun withNonMergedAttributes(): MergedAnnotation<A>

    fun asAnnotationAttributes(vararg adaptations: Adapt): AnnotationAttributes

    fun asMap(vararg adaptations: Adapt): Map<String, Any>

    fun <T : MutableMap<String, Any>> asMap(
        factory: Function<MergedAnnotation<*>, T>,
        vararg adaptations: Adapt
    ): T

    @Throws(NoSuchElementException::class)
    fun synthesize(): A

    @Throws(NoSuchElementException::class)
    fun synthesize(condition: Predicate<in MergedAnnotation<A>>): A?

    enum class Adapt {

        CLASS_TO_STRING,

        ANNOTATION_TO_MAP;

        fun isIn(vararg adaptations: Adapt): Boolean {
            for (candidate in adaptations) {
                if (candidate == this) {
                    return true
                }
            }
            return false
        }

        companion object {

            fun values(classToString: Boolean, annotationsToMap: Boolean): Array<Adapt> {
                val result = EnumSet.noneOf(Adapt::class.java)
                addIfTrue(result, CLASS_TO_STRING, classToString)
                addIfTrue(result, ANNOTATION_TO_MAP, annotationsToMap)
                return result.toTypedArray()
            }

            private fun <T> addIfTrue(result: MutableSet<T>, value: T, test: Boolean) {
                if (test) {
                    result.add(value)
                }
            }

        }

    }

}