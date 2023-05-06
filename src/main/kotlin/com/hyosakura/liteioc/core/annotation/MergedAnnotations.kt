package com.hyosakura.liteioc.core.annotation

import java.lang.reflect.AnnotatedElement
import java.util.function.Predicate
import java.util.stream.Stream

/**
 * @author LovesAsuna
 **/
interface MergedAnnotations : Iterable<MergedAnnotation<Annotation>> {

    companion object {
        fun from(element: AnnotatedElement): MergedAnnotations {
            return from(element, SearchStrategy.DIRECT)
        }

        fun from(
            element: AnnotatedElement, searchStrategy: SearchStrategy
        ): MergedAnnotations {
            return from(element, searchStrategy, RepeatableContainers.standardRepeatables())
        }

        fun from(
            element: AnnotatedElement, searchStrategy: SearchStrategy, repeatableContainers: RepeatableContainers
        ): MergedAnnotations {
            return from(element, searchStrategy, repeatableContainers, AnnotationFilter.PLAIN)
        }

        fun from(
            element: AnnotatedElement,
            searchStrategy: SearchStrategy,
            repeatableContainers: RepeatableContainers,
            annotationFilter: AnnotationFilter
        ): MergedAnnotations {
            return from(element, searchStrategy, Search.never, repeatableContainers, annotationFilter)
        }

        private fun from(
            element: AnnotatedElement,
            searchStrategy: SearchStrategy,
            searchEnclosingClass: Predicate<Class<*>>,
            repeatableContainers: RepeatableContainers,
            annotationFilter: AnnotationFilter
        ): MergedAnnotations {
            return TypeMappedAnnotations.from(
                element, searchStrategy, searchEnclosingClass, repeatableContainers, annotationFilter
            )
        }

        fun from(vararg annotations: Annotation): MergedAnnotations {
            return from(annotations, *annotations)
        }

        fun from(source: Any, vararg annotations: Annotation): MergedAnnotations {
            return from(source, annotations, RepeatableContainers.standardRepeatables())
        }

        fun from(
            source: Any, annotations: Array<out Annotation>, repeatableContainers: RepeatableContainers
        ): MergedAnnotations {
            return from(source, annotations, repeatableContainers, AnnotationFilter.PLAIN)
        }

        fun from(
            source: Any,
            annotations: Array<out Annotation>,
            repeatableContainers: RepeatableContainers,
            annotationFilter: AnnotationFilter
        ): MergedAnnotations {
            return TypeMappedAnnotations.from(source, annotations, repeatableContainers, annotationFilter)
        }

    }

    fun <A : Annotation> isPresent(annotationType: Class<A>): Boolean

    fun isPresent(annotationType: String): Boolean

    fun <A : Annotation> isDirectlyPresent(annotationType: Class<A>): Boolean

    fun isDirectlyPresent(annotationType: String): Boolean

    operator fun <A : Annotation> get(annotationType: Class<A>): MergedAnnotation<A>

    operator fun <A : Annotation> get(
        annotationType: Class<A>, predicate: Predicate<in MergedAnnotation<A>>?
    ): MergedAnnotation<A>

    operator fun <A : Annotation> get(
        annotationType: Class<A>, predicate: Predicate<in MergedAnnotation<A>>?, selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A>

    operator fun <A : Annotation> get(annotationType: String): MergedAnnotation<A>

    operator fun <A : Annotation> get(
        annotationType: String, predicate: Predicate<in MergedAnnotation<A>>?
    ): MergedAnnotation<A>

    operator fun <A : Annotation> get(
        annotationType: String, predicate: Predicate<in MergedAnnotation<A>>?, selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A>

    fun <A : Annotation> stream(annotationType: Class<A>): Stream<MergedAnnotation<A>>

    fun <A : Annotation> stream(annotationType: String): Stream<MergedAnnotation<A>>

    fun stream(): Stream<MergedAnnotation<Annotation>>

    fun of(annotations: Collection<MergedAnnotation<*>>): MergedAnnotations {
        return MergedAnnotationsCollection.of(annotations)
    }

    fun search(searchStrategy: SearchStrategy): Search {
        return Search(searchStrategy)
    }

    class Search constructor(searchStrategy: SearchStrategy) {

        private val searchStrategy: SearchStrategy

        private var searchEnclosingClass = never

        private var repeatableContainers: RepeatableContainers = RepeatableContainers.standardRepeatables()

        private var annotationFilter: AnnotationFilter = AnnotationFilter.PLAIN

        init {
            this.searchStrategy = searchStrategy
        }

        fun withEnclosingClasses(searchEnclosingClass: Predicate<Class<*>>): Search {
            require(
                searchStrategy == SearchStrategy.TYPE_HIERARCHY,
            ) { "A custom 'searchEnclosingClass' predicate can only be combined with SearchStrategy.TYPE_HIERARCHY" }
            this.searchEnclosingClass = searchEnclosingClass
            return this
        }

        fun withRepeatableContainers(repeatableContainers: RepeatableContainers): Search {
            this.repeatableContainers = repeatableContainers
            return this
        }

        fun withAnnotationFilter(annotationFilter: AnnotationFilter): Search {
            this.annotationFilter = annotationFilter
            return this
        }

        fun from(element: AnnotatedElement): MergedAnnotations {
            return from(
                element, searchStrategy, searchEnclosingClass, repeatableContainers, annotationFilter
            )
        }

        companion object {

            val always = Predicate { _: Class<*> -> true }

            val never = Predicate { _: Class<*> -> false }

        }
    }

    enum class SearchStrategy {

        DIRECT,

        INHERITED_ANNOTATIONS,

        SUPERCLASS,

        TYPE_HIERARCHY

    }

}