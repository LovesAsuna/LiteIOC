package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.annotation.MergedAnnotation.Adapt
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.SearchStrategy
import com.hyosakura.liteioc.util.MultiValueMap
import java.lang.reflect.AnnotatedElement

object AnnotatedElementUtil {

    fun getMergedAnnotationAttributes(
        element: AnnotatedElement,
        annotationName: String, classValuesAsString: Boolean, nestedAnnotationsAsMap: Boolean
    ): AnnotationAttributes? {
        val mergedAnnotation =
            getAnnotations(element)[annotationName, null, MergedAnnotationSelectors.firstDirectlyDeclared()]
        return getAnnotationAttributes(
            mergedAnnotation,
            classValuesAsString,
            nestedAnnotationsAsMap
        )
    }

    fun getAllAnnotationAttributes(
        element: AnnotatedElement,
        annotationName: String, classValuesAsString: Boolean, nestedAnnotationsAsMap: Boolean
    ): MultiValueMap<String, Any>? {
        val adaptations = Adapt.values(classValuesAsString, nestedAnnotationsAsMap)
        return getAnnotations(element).stream<Annotation>(annotationName)
            .filter(MergedAnnotationPredicates.unique(MergedAnnotation<Annotation>::getMetaTypes))
            .map(MergedAnnotation<Annotation>::withNonMergedAttributes)
            .collect(MergedAnnotationCollectors.toMultiValueMap(::nullIfEmpty, *adaptations))
    }

    private fun getAnnotations(element: AnnotatedElement): MergedAnnotations {
        return MergedAnnotations.from(element, SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
    }

    private fun getAnnotationAttributes(
        annotation: MergedAnnotation<*>,
        classValuesAsString: Boolean, nestedAnnotationsAsMap: Boolean
    ): AnnotationAttributes? {
        return if (!annotation.isPresent()) {
            null
        } else annotation.asAnnotationAttributes(
            *Adapt.values(classValuesAsString, nestedAnnotationsAsMap)
        )
    }

    private fun nullIfEmpty(map: MultiValueMap<String, Any>): MultiValueMap<String, Any>? {
        return if (map.isEmpty()) null else map
    }

    fun isAnnotated(element: AnnotatedElement, annotationName: String): Boolean {
        return getAnnotations(element).isPresent(annotationName)
    }

}