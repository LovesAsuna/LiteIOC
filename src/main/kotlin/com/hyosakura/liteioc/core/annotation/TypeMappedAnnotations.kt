package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.annotation.MergedAnnotations.Search
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.SearchStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.AnnotatedElement
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * @author LovesAsuna
 **/
class TypeMappedAnnotations : MergedAnnotations {

    companion object {
        val NONE: MergedAnnotations = TypeMappedAnnotations(
            null, emptyArray(), RepeatableContainers.none(), AnnotationFilter.ALL
        )

        fun isMappingForType(
            mapping: AnnotationTypeMapping,
            annotationFilter: AnnotationFilter, requiredType: Any?
        ): Boolean {
            val actualType = mapping.getAnnotationType()
            return !annotationFilter.matches(actualType) &&
                    (requiredType == null || actualType == requiredType || actualType.name == requiredType)
        }

        fun from(
            element: AnnotatedElement, searchStrategy: SearchStrategy,
            searchEnclosingClass: Predicate<Class<*>>, repeatableContainers: RepeatableContainers,
            annotationFilter: AnnotationFilter
        ): MergedAnnotations {
            return if (AnnotationsScanner.isKnownEmpty(element, searchStrategy, searchEnclosingClass)) {
                NONE
            } else TypeMappedAnnotations(
                element,
                searchStrategy,
                searchEnclosingClass,
                repeatableContainers,
                annotationFilter
            )
        }

        fun from(
            source: Any?, annotations: Array<out Annotation>,
            repeatableContainers: RepeatableContainers, annotationFilter: AnnotationFilter
        ): MergedAnnotations {
            return if (annotations.isEmpty()) {
                NONE
            } else TypeMappedAnnotations(source, annotations, repeatableContainers, annotationFilter)
        }

    }

    private var source: Any? = null

    private var element: AnnotatedElement? = null

    private var searchStrategy: SearchStrategy? = null

    private val searchEnclosingClass: Predicate<Class<*>>

    private var annotations: Array<out Annotation>? = null

    private val repeatableContainers: RepeatableContainers

    private val annotationFilter: AnnotationFilter

    @Volatile
    private var aggregates: List<Aggregate>? = null

    private constructor(
        element: AnnotatedElement, searchStrategy: SearchStrategy,
        searchEnclosingClass: Predicate<Class<*>>, repeatableContainers: RepeatableContainers,
        annotationFilter: AnnotationFilter
    ) {
        this.source = element
        this.element = element
        this.searchStrategy = searchStrategy
        this.searchEnclosingClass = searchEnclosingClass
        this.annotations = null
        this.repeatableContainers = repeatableContainers
        this.annotationFilter = annotationFilter
    }

    private constructor(
        source: Any?, annotations: Array<out Annotation>,
        repeatableContainers: RepeatableContainers, annotationFilter: AnnotationFilter
    ) {
        this.source = source
        this.element = null
        this.searchStrategy = null
        this.searchEnclosingClass = Search.never
        this.annotations = annotations
        this.repeatableContainers = repeatableContainers
        this.annotationFilter = annotationFilter
    }

    override fun <A : Annotation> isPresent(annotationType: Class<A>): Boolean {
        return if (annotationFilter.matches(annotationType)) {
            false
        } else java.lang.Boolean.TRUE == scan(
            annotationType,
            IsPresent[repeatableContainers, annotationFilter, false]
        )
    }

    override fun isPresent(annotationType: String): Boolean {
        return if (annotationFilter.matches(annotationType)) {
            false
        } else true == scan(
            annotationType,
            IsPresent[repeatableContainers, annotationFilter, false]
        )
    }

    override fun <A : Annotation> isDirectlyPresent(annotationType: Class<A>): Boolean {
        return if (annotationFilter.matches(annotationType)) {
            false
        } else java.lang.Boolean.TRUE == scan(
            annotationType,
            IsPresent[repeatableContainers, annotationFilter, true]
        )
    }

    override fun isDirectlyPresent(annotationType: String): Boolean {
        return if (annotationFilter.matches(annotationType)) {
            false
        } else java.lang.Boolean.TRUE == scan(
            annotationType,
            IsPresent[repeatableContainers, annotationFilter, true]
        )
    }

    override operator fun <A : Annotation> get(annotationType: Class<A>): MergedAnnotation<A> {
        return get(annotationType, null, null)
    }

    override operator fun <A : Annotation> get(
        annotationType: Class<A>,
        predicate: Predicate<in MergedAnnotation<A>>?
    ): MergedAnnotation<A> {
        return get(annotationType, predicate, null)
    }

    override operator fun <A : Annotation> get(
        annotationType: Class<A>,
        predicate: Predicate<in MergedAnnotation<A>>?,
        selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A> {
        if (annotationFilter.matches(annotationType)) {
            return MergedAnnotation.missing()
        }
        val result = scan(
            annotationType,
            MergedAnnotationFinder(annotationType, predicate, selector)
        )
        return result ?: MergedAnnotation.missing()
    }

    override operator fun <A : Annotation> get(annotationType: String): MergedAnnotation<A> {
        return get(annotationType, null, null)
    }

    override operator fun <A : Annotation> get(
        annotationType: String,
        predicate: Predicate<in MergedAnnotation<A>>?
    ): MergedAnnotation<A> {
        return get(annotationType, predicate, null)
    }

    override operator fun <A : Annotation> get(
        annotationType: String,
        predicate: Predicate<in MergedAnnotation<A>>?,
        selector: MergedAnnotationSelector<A>?
    ): MergedAnnotation<A> {
        if (this.annotationFilter.matches(annotationType)) {
            return MergedAnnotation.missing()
        }
        val result = scan(
            annotationType,
            MergedAnnotationFinder(annotationType, predicate, selector)
        )
        return result ?: MergedAnnotation.missing()
    }

    override fun <A : Annotation> stream(annotationType: Class<A>): Stream<MergedAnnotation<A>> {
        return if (annotationFilter === AnnotationFilter.ALL) {
            Stream.empty()
        } else StreamSupport.stream(spliterator(annotationType), false)
    }

    override fun <A : Annotation> stream(annotationType: String): Stream<MergedAnnotation<A>> {
        return if (annotationFilter === AnnotationFilter.ALL) {
            Stream.empty()
        } else StreamSupport.stream(spliterator(annotationType), false)
    }

    override fun stream(): Stream<MergedAnnotation<Annotation>> {
        return if (annotationFilter == AnnotationFilter.ALL) {
            Stream.empty()
        } else StreamSupport.stream(spliterator(), false)
    }

    override fun iterator(): Iterator<MergedAnnotation<Annotation>> {
        return if (annotationFilter === AnnotationFilter.ALL) {
            Collections.emptyIterator()
        } else Spliterators.iterator(spliterator())
    }

    fun spliterator(): Spliterator<MergedAnnotation<Annotation>> {
        return if (annotationFilter == AnnotationFilter.ALL) {
            Spliterators.emptySpliterator()
        } else spliterator(null)
    }

    private fun <A : Annotation> spliterator(annotationType: Any?): Spliterator<MergedAnnotation<A>> {
        return AggregatesSpliterator(annotationType, getAggregates())
    }

    private fun getAggregates(): List<Aggregate> {
        var aggregates = aggregates
        if (aggregates == null) {
            aggregates = scan(this, AggregatesCollector())
            if (aggregates == null || aggregates.isEmpty()) {
                aggregates = emptyList()
            }
            this.aggregates = aggregates
        }
        return aggregates
    }

    private fun <C, R> scan(criteria: C, processor: AnnotationsProcessor<C, R>): R? {
        if (annotations != null) {
            val result = processor.doWithAnnotations(criteria, 0, source, annotations!!)
            return processor.finish(result)
        }
        return if (element != null && searchStrategy != null) {
            AnnotationsScanner.scan(
                criteria, element!!, searchStrategy!!,
                searchEnclosingClass, processor
            )
        } else null
    }


    private class IsPresent private constructor(
        private val repeatableContainers: RepeatableContainers?,
        private val annotationFilter: AnnotationFilter?, private val directOnly: Boolean
    ) : AnnotationsProcessor<Any, Boolean> {
        override fun doWithAnnotations(
            requiredType: Any, aggregateIndex: Int,
            source: Any?, annotations: Array<out Annotation>
        ): Boolean? {
            for (annotation in annotations) {
                val type = annotation.annotationClass.java
                if (!annotationFilter!!.matches(type)) {
                    if (type == requiredType || type.name == requiredType) {
                        return true
                    }
                    val repeatedAnnotations = repeatableContainers!!.findRepeatedAnnotations(annotation)
                    if (repeatedAnnotations != null) {
                        val result = doWithAnnotations(
                            requiredType, aggregateIndex, source, repeatedAnnotations
                        )
                        if (result != null) {
                            return result
                        }
                    }
                    if (!directOnly) {
                        val mappings = AnnotationTypeMappings.forAnnotationType(type)
                        for (i in 0 until mappings.size()) {
                            val mapping: AnnotationTypeMapping = mappings[i]
                            if (isMappingForType(mapping, annotationFilter, requiredType)) {
                                return true
                            }
                        }
                    }
                }
            }
            return null
        }

        companion object {

            private val SHARED: Array<IsPresent> = arrayOf(
                IsPresent(RepeatableContainers.none(), AnnotationFilter.PLAIN, true),
                IsPresent(RepeatableContainers.none(), AnnotationFilter.PLAIN, false),
                IsPresent(RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, true),
                IsPresent(RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, false)
            )

            operator fun get(
                repeatableContainers: RepeatableContainers?,
                annotationFilter: AnnotationFilter?, directOnly: Boolean
            ): IsPresent {

                // Use a single shared instance for common combinations
                if (annotationFilter == AnnotationFilter.PLAIN) {
                    if (repeatableContainers == RepeatableContainers.none()) {
                        return SHARED[if (directOnly) 0 else 1]
                    }
                    if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
                        return SHARED[if (directOnly) 2 else 3]
                    }
                }
                return IsPresent(repeatableContainers, annotationFilter, directOnly)
            }
        }
    }

    private inner class MergedAnnotationFinder<A : Annotation>(
        private val requiredType: Any,
        private val predicate: Predicate<in MergedAnnotation<A>>?,
        selector: MergedAnnotationSelector<A>?
    ) : AnnotationsProcessor<Any, MergedAnnotation<A>> {

        private val selector: MergedAnnotationSelector<A>

        private var result: MergedAnnotation<A>? = null

        init {
            this.selector = selector ?: MergedAnnotationSelectors.nearest()
        }

        fun doWithAggregate(context: Any?, aggregateIndex: Int): MergedAnnotation<A>? {
            return result
        }

        override fun doWithAnnotations(
            type: Any, aggregateIndex: Int,
            source: Any?, annotations: Array<out Annotation>
        ): MergedAnnotation<A>? {
            for (annotation in annotations) {
                if (!annotationFilter.matches(annotation.annotationClass.java)) {
                    val result = process(type, aggregateIndex, source, annotation)
                    if (result != null) {
                        return result
                    }
                }
            }
            return null
        }

        private fun process(
            type: Any, aggregateIndex: Int, source: Any?, annotation: Annotation
        ): MergedAnnotation<A>? {
            val repeatedAnnotations = repeatableContainers.findRepeatedAnnotations(annotation)
            if (repeatedAnnotations != null) {
                return doWithAnnotations(type, aggregateIndex, source, repeatedAnnotations)
            }
            val mappings = AnnotationTypeMappings.forAnnotationType(
                annotation.annotationClass.java, repeatableContainers, annotationFilter
            )
            for (i in 0 until mappings.size()) {
                val mapping = mappings[i]
                if (isMappingForType(mapping, annotationFilter, requiredType)) {
                    val candidate = TypeMappedAnnotation.createIfPossible<A>(
                        mapping,
                        source,
                        annotation,
                        aggregateIndex,
                        LoggerFactory.getLogger(MergedAnnotation::class.java)
                    )
                    if (candidate != null && (predicate == null || predicate.test(candidate))) {
                        if (selector.isBestCandidate(candidate)) {
                            return candidate
                        }
                        updateLastResult(candidate)
                    }
                }
            }
            return null
        }

        private fun updateLastResult(candidate: MergedAnnotation<A>) {
            val lastResult = result
            result = if (lastResult != null) selector.select(lastResult, candidate) else candidate
        }

        override fun finish(result: MergedAnnotation<A>?): MergedAnnotation<A>? {
            return result ?: this.result
        }
    }


    private inner class AggregatesCollector : AnnotationsProcessor<Any, List<Aggregate>> {

        private val aggregates: MutableList<Aggregate> = ArrayList()

        override fun doWithAnnotations(
            criteria: Any, aggregateIndex: Int,
            source: Any?, annotations: Array<out Annotation>
        ): List<Aggregate>? {
            aggregates.add(createAggregate(aggregateIndex, source, annotations))
            return null
        }

        private fun createAggregate(
            aggregateIndex: Int,
            source: Any?,
            annotations: Array<out Annotation>
        ): Aggregate {
            val aggregateAnnotations = getAggregateAnnotations(annotations)
            return Aggregate(aggregateIndex, source, aggregateAnnotations)
        }

        private fun getAggregateAnnotations(annotations: Array<out Annotation>): List<Annotation> {
            val result = ArrayList<Annotation>(annotations.size)
            addAggregateAnnotations(result, annotations)
            return result
        }

        private fun addAggregateAnnotations(
            aggregateAnnotations: MutableList<Annotation>,
            annotations: Array<out Annotation>
        ) {
            for (annotation in annotations) {
                if (!annotationFilter.matches(annotation)) {
                    val repeatedAnnotations = repeatableContainers.findRepeatedAnnotations(annotation)
                    repeatedAnnotations?.let { addAggregateAnnotations(aggregateAnnotations, it) }
                        ?: aggregateAnnotations.add(annotation)
                }
            }
        }

        override fun finish(processResult: List<Aggregate>?): List<Aggregate> {
            return aggregates
        }
    }

    private class Aggregate(
        private val aggregateIndex: Int,
        private val source: Any?,
        private val annotations: List<Annotation>
    ) {

        private val mappings: Array<AnnotationTypeMappings> = Array(annotations.size) { i ->
            AnnotationTypeMappings.forAnnotationType(annotations[i].javaClass)
        }

        fun size(): Int {
            return annotations.size
        }

        fun getMapping(annotationIndex: Int, mappingIndex: Int): AnnotationTypeMapping? {
            val mappings = getMappings(annotationIndex)
            return if (mappingIndex < mappings.size()) mappings[mappingIndex] else null
        }

        fun getMappings(annotationIndex: Int): AnnotationTypeMappings {
            return mappings[annotationIndex]
        }

        fun <A : Annotation> createMergedAnnotationIfPossible(
            annotationIndex: Int, mappingIndex: Int, logger: Logger
        ): MergedAnnotation<A>? {
            return TypeMappedAnnotation.createIfPossible(
                mappings[annotationIndex][mappingIndex], source,
                annotations[annotationIndex], aggregateIndex, logger
            )
        }
    }

    private inner class AggregatesSpliterator<A : Annotation>(
        private val requiredType: Any?,
        private val aggregates: List<Aggregate>?
    ) : Spliterator<MergedAnnotation<A>> {

        private var aggregateCursor = 0

        private var mappingCursors: IntArray? = null

        override fun tryAdvance(action: Consumer<in MergedAnnotation<A>>): Boolean {
            while (aggregateCursor < aggregates!!.size) {
                val aggregate = aggregates[aggregateCursor]
                if (tryAdvance(aggregate, action)) {
                    return true
                }
                aggregateCursor++
                mappingCursors = null
            }
            return false
        }

        private fun tryAdvance(aggregate: Aggregate, action: Consumer<in MergedAnnotation<A>>): Boolean {
            if (mappingCursors == null) {
                mappingCursors = IntArray(aggregate.size())
            }
            var lowestDistance = Int.MAX_VALUE
            var annotationResult = -1
            for (annotationIndex in 0 until aggregate.size()) {
                val mapping = getNextSuitableMapping(aggregate, annotationIndex)
                if (mapping != null && mapping.getDistance() < lowestDistance) {
                    annotationResult = annotationIndex
                    lowestDistance = mapping.getDistance()
                }
                if (lowestDistance == 0) {
                    break
                }
            }
            if (annotationResult != -1) {
                val mergedAnnotation = aggregate.createMergedAnnotationIfPossible<A>(
                    annotationResult, mappingCursors!![annotationResult],
                    LoggerFactory.getLogger(MergedAnnotation::class.java)
                )
                mappingCursors!![annotationResult]++
                if (mergedAnnotation == null) {
                    return tryAdvance(aggregate, action)
                }
                action.accept(mergedAnnotation)
                return true
            }
            return false
        }

        private fun getNextSuitableMapping(aggregate: Aggregate, annotationIndex: Int): AnnotationTypeMapping? {
            val cursors = mappingCursors
            if (cursors != null) {
                var mapping: AnnotationTypeMapping?
                do {
                    mapping = aggregate.getMapping(annotationIndex, cursors[annotationIndex])
                    if (mapping != null && isMappingForType(mapping, annotationFilter, requiredType)) {
                        return mapping
                    }
                    cursors[annotationIndex]++
                } while (mapping != null)
            }
            return null
        }

        override fun trySplit(): Spliterator<MergedAnnotation<A>>? = null

        override fun estimateSize(): Long {
            var size = 0
            for (aggregateIndex in aggregateCursor until aggregates!!.size) {
                val aggregate = aggregates[aggregateIndex]
                for (annotationIndex in 0 until aggregate.size()) {
                    val mappings = aggregate.getMappings(annotationIndex)
                    var numberOfMappings = mappings.size()
                    if (aggregateIndex == aggregateCursor && mappingCursors != null) {
                        numberOfMappings -= mappingCursors!![annotationIndex].coerceAtMost(mappings.size())
                    }
                    size += numberOfMappings
                }
            }
            return size.toLong()
        }

        override fun characteristics(): Int {
            return Spliterator.NONNULL or Spliterator.IMMUTABLE
        }
    }

}