package com.hyosakura.liteioc.core.annotation

import java.util.*
import java.util.function.Function
import java.util.function.Predicate

/**
 * @author LovesAsuna
 **/
class MissingMergedAnnotation<A : Annotation> private constructor() : AbstractMergedAnnotation<A>() {

    companion object {

        private val INSTANCE: MissingMergedAnnotation<*> = MissingMergedAnnotation<Annotation>()

        @Suppress("UNCHECKED_CAST")
        fun <A : Annotation> getInstance(): MergedAnnotation<A> {
            return INSTANCE as MergedAnnotation<A>
        }

    }

    override fun getType(): Class<A> {
        throw NoSuchElementException("Unable to get type for missing annotation")
    }

    override fun isPresent(): Boolean {
        return false
    }

    override fun getSource(): Any? {
        return null
    }

    override fun getMetaSource(): MergedAnnotation<*>? {
        return null
    }

    override fun getRoot(): MergedAnnotation<*> {
        return this
    }

    override fun getMetaTypes(): List<Class<out Annotation>> {
        return emptyList<Class<out Annotation?>>()
    }

    override fun getDistance(): Int {
        return -1
    }

    override fun getAggregateIndex(): Int {
        return -1
    }

    override fun hasNonDefaultValue(attributeName: String): Boolean {
        throw NoSuchElementException(
            "Unable to check non-default value for missing annotation"
        )
    }

    override fun hasDefaultValue(attributeName: String): Boolean {
        throw NoSuchElementException(
            "Unable to check default value for missing annotation"
        )
    }

    override fun <T> getValue(attributeName: String, type: Class<T>): T? {
        return null
    }

    override fun <T> getDefaultValue(attributeName: String, type: Class<T>): T? {
        return null
    }

    override fun filterAttributes(predicate: Predicate<String>): MergedAnnotation<A> {
        return this
    }

    override fun withNonMergedAttributes(): MergedAnnotation<A> {
        return this
    }

    override fun asAnnotationAttributes(vararg adaptations: MergedAnnotation.Adapt): AnnotationAttributes {
        return AnnotationAttributes()
    }

    override fun asMap(vararg adaptations: MergedAnnotation.Adapt): Map<String, Any> {
        return emptyMap()
    }

    override fun <T : MutableMap<String, Any>> asMap(
        factory: Function<MergedAnnotation<*>, T>,
        vararg adaptations: MergedAnnotation.Adapt
    ): T {
        return factory.apply(this)
    }

    override fun toString(): String {
        return "(missing)"
    }

    @Throws(NoSuchElementException::class)
    override fun <T : Annotation> getAnnotation(
        attributeName: String,
        type: Class<T>
    ): MergedAnnotation<T> {
        throw NoSuchElementException(
            "Unable to get attribute value for missing annotation"
        )
    }

    @Throws(NoSuchElementException::class)
    override fun <T : Annotation> getAnnotationArray(
        attributeName: String, type: Class<T>
    ): Array<MergedAnnotation<T>> {
        throw NoSuchElementException(
            "Unable to get attribute value for missing annotation"
        )
    }

    override fun <T> getAttributeValue(attributeName: String, type: Class<T>): T {
        throw NoSuchElementException(
            "Unable to get attribute value for missing annotation"
        )
    }

    override fun createSynthesized(): A {
        throw NoSuchElementException("Unable to synthesize missing annotation")
    }

}