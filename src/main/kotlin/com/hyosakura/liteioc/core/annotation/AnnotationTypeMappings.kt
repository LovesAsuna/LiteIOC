package com.hyosakura.liteioc.core.annotation

import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
class AnnotationTypeMappings {

    companion object {

        private val standardRepeatablesCache: MutableMap<AnnotationFilter, Cache> = ConcurrentHashMap()

        private val noRepeatablesCache: MutableMap<AnnotationFilter, Cache> = ConcurrentHashMap()

        fun forAnnotationType(annotationType: Class<out Annotation>): AnnotationTypeMappings {
            return forAnnotationType(annotationType, HashSet<Class<out Annotation>>())
        }

        fun forAnnotationType(
            annotationType: Class<out Annotation>,
            visitedAnnotationTypes: MutableSet<Class<out Annotation>>
        ): AnnotationTypeMappings {
            return forAnnotationType(
                annotationType, RepeatableContainers.standardRepeatables(),
                AnnotationFilter.PLAIN, visitedAnnotationTypes
            )
        }

        fun forAnnotationType(
            annotationType: Class<out Annotation>,
            repeatableContainers: RepeatableContainers,
            annotationFilter: AnnotationFilter
        ): AnnotationTypeMappings {
            return forAnnotationType(
                annotationType, repeatableContainers, annotationFilter, HashSet()
            )
        }

        private fun forAnnotationType(
            annotationType: Class<out Annotation>,
            repeatableContainers: RepeatableContainers,
            annotationFilter: AnnotationFilter,
            visitedAnnotationTypes: MutableSet<Class<out Annotation>>
        ): AnnotationTypeMappings {
            if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
                return standardRepeatablesCache.computeIfAbsent(
                    annotationFilter
                ) { key: AnnotationFilter ->
                    Cache(
                        repeatableContainers, key
                    )
                }[annotationType, visitedAnnotationTypes]
            }
            return if (repeatableContainers == RepeatableContainers.none()) {
                noRepeatablesCache.computeIfAbsent(
                    annotationFilter
                ) { key: AnnotationFilter ->
                    Cache(
                        repeatableContainers, key
                    )
                }[annotationType, visitedAnnotationTypes]
            } else AnnotationTypeMappings(
                repeatableContainers, annotationFilter, annotationType, visitedAnnotationTypes
            )
        }

    }

    private val repeatableContainers: RepeatableContainers

    private val filter: AnnotationFilter

    private val mappings: MutableList<AnnotationTypeMapping>

    private constructor(
        repeatableContainers: RepeatableContainers,
        filter: AnnotationFilter,
        annotationType: Class<out Annotation>,
        visitedAnnotationTypes: MutableSet<Class<out Annotation>>
    ) {
        this.repeatableContainers = repeatableContainers
        this.filter = filter
        this.mappings = ArrayList()
        addAllMappings(annotationType, visitedAnnotationTypes)
        this.mappings.forEach(AnnotationTypeMapping::afterAllMappingsSet)
    }

    fun size(): Int = mappings.size

    private fun addAllMappings(
        annotationType: Class<out Annotation>, visitedAnnotationTypes: MutableSet<Class<out Annotation>>
    ) {
        val queue: Deque<AnnotationTypeMapping> = ArrayDeque()
        addIfPossible(queue, null, annotationType, null, visitedAnnotationTypes)
        while (!queue.isEmpty()) {
            val mapping = queue.removeFirst()
            mappings.add(mapping)
            addMetaAnnotationsToQueue(queue, mapping)
        }
    }

    private fun addMetaAnnotationsToQueue(queue: Deque<AnnotationTypeMapping>, source: AnnotationTypeMapping) {
        val metaAnnotations = AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false)
        for (metaAnnotation in metaAnnotations) {
            if (!isMappable(source, metaAnnotation)) {
                continue
            }
            val repeatedAnnotations = repeatableContainers.findRepeatedAnnotations(metaAnnotation)
            if (repeatedAnnotations != null) {
                for (repeatedAnnotation in repeatedAnnotations) {
                    if (!isMappable(source, repeatedAnnotation)) {
                        continue
                    }
                    addIfPossible(queue, source, repeatedAnnotation)
                }
            } else {
                addIfPossible(queue, source, metaAnnotation)
            }
        }
    }

    private fun isMappable(source: AnnotationTypeMapping, metaAnnotation: Annotation?): Boolean {
        return metaAnnotation != null && !filter.matches(metaAnnotation) && !AnnotationFilter.PLAIN.matches(source.getAnnotationType()) && !isAlreadyMapped(
            source,
            metaAnnotation
        )
    }

    private fun isAlreadyMapped(source: AnnotationTypeMapping, metaAnnotation: Annotation): Boolean {
        val annotationType = metaAnnotation.annotationClass.java
        var mapping: AnnotationTypeMapping? = source
        while (mapping != null) {
            if (mapping.getAnnotationType() == annotationType) {
                return true
            }
            mapping = mapping.getSource()
        }
        return false
    }

    operator fun get(index: Int): AnnotationTypeMapping {
        return mappings[index]
    }

    private fun addIfPossible(queue: Deque<AnnotationTypeMapping>, source: AnnotationTypeMapping, ann: Annotation) {
        addIfPossible(queue, source, ann.annotationClass.java, ann, HashSet())
    }

    private fun addIfPossible(
        queue: Deque<AnnotationTypeMapping>,
        source: AnnotationTypeMapping?,
        annotationType: Class<out Annotation>,
        ann: Annotation?,
        visitedAnnotationTypes: MutableSet<Class<out Annotation>>
    ) {
        try {
            queue.addLast(AnnotationTypeMapping(source, annotationType, ann, visitedAnnotationTypes))
        } catch (ex: Exception) {
            LoggerFactory.getLogger(MergedAnnotation::class.java).debug(ex.message)
        }
    }

    private class Cache(
        private val repeatableContainers: RepeatableContainers, private val filter: AnnotationFilter
    ) {

        private val mappings: MutableMap<Class<out Annotation>, AnnotationTypeMappings> = ConcurrentHashMap()

        operator fun get(
            annotationType: Class<out Annotation>, visitedAnnotationTypes: MutableSet<Class<out Annotation>>
        ): AnnotationTypeMappings {
            return mappings.computeIfAbsent(
                annotationType
            ) { key: Class<out Annotation> ->
                createMappings(
                    key, visitedAnnotationTypes
                )
            }
        }

        private fun createMappings(
            annotationType: Class<out Annotation>, visitedAnnotationTypes: MutableSet<Class<out Annotation>>
        ): AnnotationTypeMappings {
            return AnnotationTypeMappings(repeatableContainers, filter, annotationType, visitedAnnotationTypes)
        }
    }

}