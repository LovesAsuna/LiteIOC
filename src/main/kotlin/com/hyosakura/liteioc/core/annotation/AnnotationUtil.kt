package com.hyosakura.liteioc.core.annotation

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.AnnotatedElement

object AnnotationUtil {

    private val logger: Logger = LoggerFactory.getLogger(MergedAnnotation::class.java)

    fun isCandidateClass(clazz: Class<*>, annotationTypes: Collection<Class<out Annotation>>): Boolean {
        for (annotationType in annotationTypes) {
            if (isCandidateClass(clazz, annotationType)) {
                return true
            }
        }
        return false
    }

    fun isCandidateClass(clazz: Class<*>?, annotationType: Class<out Annotation>): Boolean {
        return isCandidateClass(clazz, annotationType.name)
    }

    fun isCandidateClass(clazz: Class<*>?, annotationName: String): Boolean {
        if (annotationName.startsWith("java.")) {
            return true
        }
        return !AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz)
    }

    fun handleIntrospectionFailure(element: AnnotatedElement?, ex: Throwable) {
        rethrowAnnotationConfigurationException(ex)
        var meta = false
        if (element is Class<*> && Annotation::class.java.isAssignableFrom(element)) {
            // Meta-annotation or (default) value lookup on an annotation type
            meta = true
        }
        if (logger.isDebugEnabled) {
            val message = if (meta) "Failed to meta-introspect annotation " else "Failed to introspect annotations on "
            logger.debug("$message$element: $ex")
        }
    }


    fun rethrowAnnotationConfigurationException(ex: Throwable) {
        if (ex is AnnotationConfigurationException) {
            throw ex
        }
    }
}