package com.hyosakura.liteioc.core.type

import com.hyosakura.liteioc.core.annotation.*
import com.hyosakura.liteioc.core.annotation.MergedAnnotation.Adapt
import com.hyosakura.liteioc.util.MultiValueMap

/**
 * @author LovesAsuna
 **/
interface AnnotatedTypeMetadata {

    fun getAnnotations(): MergedAnnotations

    fun isAnnotated(annotationName: String): Boolean {
        return getAnnotations().isPresent(annotationName)
    }

    fun getAnnotationAttributes(annotationName: String): Map<String, Any>? {
        return getAnnotationAttributes(annotationName, false)
    }

    fun getAnnotationAttributes(
        annotationName: String,
        classValuesAsString: Boolean
    ): Map<String, Any>? {
        val annotation = getAnnotations()[annotationName, null, MergedAnnotationSelectors.firstDirectlyDeclared()]
        return if (!annotation.isPresent()) {
            null
        } else annotation.asAnnotationAttributes(*Adapt.values(classValuesAsString, true))
    }

    fun getAllAnnotationAttributes(annotationName: String): MultiValueMap<String, Any>? {
        return getAllAnnotationAttributes(annotationName, false)
    }

    fun getAllAnnotationAttributes(
        annotationName: String, classValuesAsString: Boolean
    ): MultiValueMap<String, Any>? {
        val adaptations = Adapt.values(classValuesAsString, true)
        return getAnnotations().stream<Annotation>(annotationName)
            .filter(MergedAnnotationPredicates.unique(MergedAnnotation<Annotation>::getMetaTypes))
            .map(MergedAnnotation<Annotation>::withNonMergedAttributes)
            .collect(
                MergedAnnotationCollectors.toMultiValueMap(
                    { map: MultiValueMap<String, Any> ->
                        if (map.isEmpty()) null else map
                    },
                    *adaptations
                )
            )
    }

}