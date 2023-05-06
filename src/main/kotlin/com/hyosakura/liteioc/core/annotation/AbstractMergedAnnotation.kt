package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.annotation.MergedAnnotation.Adapt
import org.jetbrains.annotations.Nullable
import java.util.*
import java.util.function.Predicate

/**
 * @author LovesAsuna
 **/
abstract class AbstractMergedAnnotation<A : Annotation> : MergedAnnotation<A> {

    @Nullable
    @Volatile
    private var synthesizedAnnotation: A? = null


    override fun isDirectlyPresent(): Boolean {
        return isPresent() && getDistance() === 0
    }

    override fun isMetaPresent(): Boolean {
        return isPresent() && getDistance() > 0
    }

    override fun hasNonDefaultValue(attributeName: String): Boolean {
        return !hasDefaultValue(attributeName)
    }

    override fun getByte(attributeName: String): Byte {
        return getRequiredAttributeValue(attributeName, Byte::class.java)
    }

    override fun getByteArray(attributeName: String): ByteArray {
        return getRequiredAttributeValue(attributeName, ByteArray::class.java)
    }

    override fun getBoolean(attributeName: String): Boolean {
        return getRequiredAttributeValue(attributeName, Boolean::class.java)
    }

    override fun getBooleanArray(attributeName: String): BooleanArray {
        return getRequiredAttributeValue(attributeName, BooleanArray::class.java)
    }

    override fun getChar(attributeName: String): Char {
        return getRequiredAttributeValue(attributeName, Char::class.java)
    }

    override fun getCharArray(attributeName: String): CharArray {
        return getRequiredAttributeValue(attributeName, CharArray::class.java)
    }

    override fun getShort(attributeName: String): Short {
        return getRequiredAttributeValue(attributeName, Short::class.java)
    }

    override fun getShortArray(attributeName: String): ShortArray {
        return getRequiredAttributeValue(attributeName, ShortArray::class.java)
    }

    override fun getInt(attributeName: String): Int {
        return getRequiredAttributeValue(attributeName, Int::class.java)
    }

    override fun getIntArray(attributeName: String): IntArray {
        return getRequiredAttributeValue(attributeName, IntArray::class.java)
    }

    override fun getLong(attributeName: String): Long {
        return getRequiredAttributeValue(attributeName, Long::class.java)
    }

    override fun getLongArray(attributeName: String): LongArray {
        return getRequiredAttributeValue(attributeName, LongArray::class.java)
    }

    override fun getDouble(attributeName: String): Double {
        return getRequiredAttributeValue(attributeName, Double::class.java)
    }

    override fun getDoubleArray(attributeName: String): DoubleArray {
        return getRequiredAttributeValue(attributeName, DoubleArray::class.java)
    }

    override fun getFloat(attributeName: String): Float {
        return getRequiredAttributeValue(attributeName, Float::class.java)
    }

    override fun getFloatArray(attributeName: String): FloatArray {
        return getRequiredAttributeValue(attributeName, FloatArray::class.java)
    }

    override fun getString(attributeName: String): String {
        return getRequiredAttributeValue(attributeName, String::class.java)
    }

    override fun getStringArray(attributeName: String): Array<String> {
        return getRequiredAttributeValue(attributeName, Array<String>::class.java)
    }

    override fun getClass(attributeName: String): Class<*> {
        return getRequiredAttributeValue(attributeName, Class::class.java)
    }

    override fun getClassArray(attributeName: String): Array<Class<*>> {
        return getRequiredAttributeValue(attributeName, emptyArray<Class<*>>()::class.java)
    }

    override fun <E : Enum<E>> getEnum(attributeName: String, type: Class<E>): E {
        return getRequiredAttributeValue(attributeName, type)
    }

    override fun <E : Enum<E>> getEnumArray(attributeName: String, type: Class<E>): Array<E> {
        val arrayType: Class<*> = java.lang.reflect.Array.newInstance(type, 0).javaClass
        return getRequiredAttributeValue(attributeName, arrayType) as Array<E>
    }

    override fun getValue(attributeName: String): Any? {
        return getValue(attributeName, Any::class.java)
    }

    override fun <T> getValue(attributeName: String, type: Class<T>): T? {
        return getAttributeValue(attributeName, type)
    }

    override fun getDefaultValue(attributeName: String): Any? {
        return getDefaultValue(attributeName, Any::class.java)
    }

    override fun filterDefaultValues(): MergedAnnotation<A> {
        return filterAttributes { attributeName: String -> hasNonDefaultValue(attributeName) }
    }

    override fun asAnnotationAttributes(vararg adaptations: Adapt): AnnotationAttributes {
        return asMap({ mergedAnnotation -> AnnotationAttributes(mergedAnnotation.getType()) }, *adaptations)
    }

    @Throws(NoSuchElementException::class)
    override fun synthesize(condition: Predicate<in MergedAnnotation<A>>): A? {
        return if (condition.test(this)) synthesize() else null
    }

    override fun synthesize(): A {
        if (!isPresent()) {
            throw NoSuchElementException("Unable to synthesize missing annotation")
        }
        var synthesized = synthesizedAnnotation
        if (synthesized == null) {
            synthesized = createSynthesized()
            synthesizedAnnotation = synthesized
        }
        return synthesized
    }

    private fun <T> getRequiredAttributeValue(attributeName: String, type: Class<T>): T {
        return getAttributeValue(attributeName, type)
            ?: throw NoSuchElementException(
                "No attribute named '" + attributeName +
                        "' present in merged annotation " + getType().name
            )
    }

    protected abstract fun <T> getAttributeValue(attributeName: String, type: Class<T>): T?

    protected abstract fun createSynthesized(): A

}