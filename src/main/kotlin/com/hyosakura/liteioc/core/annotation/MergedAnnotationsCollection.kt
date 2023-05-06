package com.hyosakura.liteioc.core.annotation

import org.slf4j.LoggerFactory
import java.util.function.Predicate
import java.util.stream.Stream

/**
 * @author LovesAsuna
 **/
class MergedAnnotationsCollection : MergedAnnotations {

    companion object {

        fun of(annotations: Collection<MergedAnnotation<*>>): MergedAnnotations {
            return if (annotations.isEmpty()) {
                TypeMappedAnnotations.NONE
            } else MergedAnnotationsCollection(annotations)
        }

        private fun isMappingForType(mapping: AnnotationTypeMapping, requiredType: Any?): Boolean {
            if (requiredType == null) {
                return true
            }
            val actualType = mapping.getAnnotationType()
            return actualType == requiredType || actualType.name == requiredType
        }

    }

    private val annotations: Array<MergedAnnotation<*>>

    private val mappings: Array<AnnotationTypeMappings>

    private constructor(annotations: Collection<MergedAnnotation<*>>) {
        this.annotations = annotations.toTypedArray()
        this.mappings = Array(annotations.size) {
            val annotation = this.annotations[it]
            require(annotation.isDirectlyPresent()) { "Annotation must be directly present" }
            require(annotation.getAggregateIndex() == 0) { "Annotation must have aggregate index of zero" }
            AnnotationTypeMappings.forAnnotationType(annotation.getType())
        }
    }

    override fun <A : Annotation> isPresent(annotationType: Class<A>): Boolean {
        return isPresent(annotationType, false)
    }

    override fun isPresent(annotationType: String): Boolean {
        return isPresent(annotationType, false)
    }

    override fun <A : Annotation> isDirectlyPresent(annotationType: Class<A>): Boolean {
        return isPresent(annotationType, true)
    }

    override fun isDirectlyPresent(annotationType: String): Boolean {
        return isPresent(annotationType, true)
    }

    private fun isPresent(requiredType: Any, directOnly: Boolean): Boolean {
        for (annotation in annotations) {
            val type = annotation.getType()
            if (type == requiredType || type.name == requiredType) {
                return true
            }
        }
        if (!directOnly) {
            for (mappings in mappings) {
                for (i in 1 until mappings.size()) {
                    val mapping = mappings[i]
                    if (isMappingForType(mapping, requiredType)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun <A : Annotation> get(annotationType: Class<A>): MergedAnnotation<A> {
        return get(annotationType, null, null)
    }

    override fun <A : Annotation> get(
        annotationType: Class<A>,
        predicate: Predicate<in MergedAnnotation<A>>?
    ): MergedAnnotation<A> {
        return get(annotationType, predicate, null)
    }

    override fun <A : Annotation> get(
        annotationType: Class<A>,
        predicate: Predicate<in MergedAnnotation<A>>?,
        selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A> {
        val result = find(annotationType, predicate, selector)
        return result ?: MergedAnnotation.missing()
    }

    override fun <A : Annotation> get(annotationType: String): MergedAnnotation<A> {
        return get(annotationType, null, null)
    }

    override fun <A : Annotation> get(
        annotationType: String,
        predicate: Predicate<in MergedAnnotation<A>>?
    ): MergedAnnotation<A> {
        return get(annotationType, predicate, null)
    }

    override fun <A : Annotation> get(
        annotationType: String,
        predicate: Predicate<in MergedAnnotation<A>>?,
        selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A> {
        val result = find(annotationType, predicate, selector)
        return result ?: MergedAnnotation.missing()
    }

    private fun <A : Annotation> find(
        requiredType: Any,
        predicate: Predicate<in MergedAnnotation<A>>?,
        selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A>? {
        var selector = selector
        if (selector == null) {
            selector = MergedAnnotationSelectors.nearest()
        }
        var result: MergedAnnotation<A>? = null
        for (i in annotations.indices) {
            val root = annotations[i]
            val mappings = mappings[i]
            for (mappingIndex in 0 until mappings.size()) {
                val mapping = mappings[mappingIndex]
                if (!isMappingForType(mapping, requiredType)) {
                    continue
                }
                val candidate =
                    if (mappingIndex == 0) root as MergedAnnotation<A> else TypeMappedAnnotation.createIfPossible(
                        mapping,
                        root,
                        LoggerFactory.getLogger(MergedAnnotation::class.java)
                    )
                if (candidate != null && (predicate == null || predicate.test(candidate))) {
                    if (selector.isBestCandidate(candidate)) {
                        return candidate
                    }
                    result = if (result != null) selector.select(result, candidate) else candidate
                }
            }
        }
        return result
    }

    override fun <A : Annotation> stream(annotationType: Class<A>): Stream<MergedAnnotation<A>> {
        TODO("Not yet implemented")
    }

    override fun <A : Annotation> stream(annotationType: String): Stream<MergedAnnotation<A>> {
        TODO("Not yet implemented")
    }

    override fun stream(): Stream<MergedAnnotation<Annotation>> {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<MergedAnnotation<Annotation>> {
        TODO("Not yet implemented")
    }

}