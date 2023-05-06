package com.hyosakura.liteioc.core.type

import com.hyosakura.liteioc.core.annotation.MergedAnnotation
import com.hyosakura.liteioc.core.annotation.MergedAnnotations
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.SearchStrategy
import java.util.stream.Collectors

/**
 * @author LovesAsuna
 **/
interface AnnotationMetadata : ClassMetadata, AnnotatedTypeMetadata {

    companion object {
        fun introspect(type: Class<*>): AnnotationMetadata {
            return StandardAnnotationMetadata.from(type)
        }

    }

    fun getAnnotationTypes(): Set<String> {
        return getAnnotations().stream().filter(MergedAnnotation<Annotation>::isDirectlyPresent)
            .map { annotation -> annotation.getType().name }.collect(Collectors.toCollection { LinkedHashSet() })
    }

    fun getMetaAnnotationTypes(annotationName: String): Set<String> {
        val annotation = getAnnotations()[annotationName, MergedAnnotation<Annotation>::isDirectlyPresent]
        return if (!annotation.isPresent()) {
            emptySet<String>()
        } else MergedAnnotations.from(annotation.getType(), SearchStrategy.INHERITED_ANNOTATIONS).stream()
            .map { mergedAnnotation -> mergedAnnotation.getType().name }
            .collect(Collectors.toCollection { LinkedHashSet() })
    }

    fun hasAnnotatedMethods(annotationName: String): Boolean {
        return getAnnotatedMethods(annotationName).isNotEmpty()
    }

    fun getAnnotatedMethods(annotationName: String): Set<MethodMetadata>

}