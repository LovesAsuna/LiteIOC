package com.hyosakura.liteioc.core.type

import com.hyosakura.liteioc.core.annotation.AnnotatedElementUtil
import com.hyosakura.liteioc.core.annotation.AnnotationUtil
import com.hyosakura.liteioc.core.annotation.MergedAnnotations
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.SearchStrategy
import com.hyosakura.liteioc.core.annotation.RepeatableContainers
import com.hyosakura.liteioc.util.MultiValueMap
import com.hyosakura.liteioc.util.ReflectionUtil
import java.lang.reflect.Method
import java.util.*

/**
 * @author LovesAsuna
 **/
class StandardAnnotationMetadata : StandardClassMetadata, AnnotationMetadata {

    companion object {

        fun from(introspectedClass: Class<*>): AnnotationMetadata {
            return StandardAnnotationMetadata(introspectedClass, true)
        }

    }

    private val mergedAnnotations: MergedAnnotations

    private val nestedAnnotationsAsMap: Boolean

    private var annotationTypes: Set<String>? = null

    constructor(introspectedClass: Class<*>) : this(introspectedClass, false)

    constructor(introspectedClass: Class<*>, nestedAnnotationsAsMap: Boolean) : super(introspectedClass) {

        mergedAnnotations = MergedAnnotations.from(
            introspectedClass,
            SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none()
        )
        this.nestedAnnotationsAsMap = nestedAnnotationsAsMap
    }

    override fun getAnnotations(): MergedAnnotations = mergedAnnotations

    override fun getAnnotationTypes(): Set<String> {
        var annotationTypes = annotationTypes
        if (annotationTypes == null) {
            annotationTypes = Collections.unmodifiableSet(super.getAnnotationTypes())
            this.annotationTypes = annotationTypes
        }
        return annotationTypes!!
    }

    override fun getAnnotationAttributes(annotationName: String, classValuesAsString: Boolean): Map<String, Any>? {
        return if (nestedAnnotationsAsMap) {
            super.getAnnotationAttributes(annotationName, classValuesAsString)
        } else AnnotatedElementUtil.getMergedAnnotationAttributes(
            getIntrospectedClass(), annotationName, classValuesAsString, false
        )
    }

    override fun getAllAnnotationAttributes(
        annotationName: String,
        classValuesAsString: Boolean
    ): MultiValueMap<String, Any>? {
        return if (nestedAnnotationsAsMap) {
            super.getAllAnnotationAttributes(annotationName, classValuesAsString)
        } else AnnotatedElementUtil.getAllAnnotationAttributes(
            getIntrospectedClass(), annotationName, classValuesAsString, false
        )
    }

    override fun hasAnnotatedMethods(annotationName: String): Boolean {
        if (AnnotationUtil.isCandidateClass(getIntrospectedClass(), annotationName)) {
            try {
                val methods: Array<Method> = ReflectionUtil.getDeclaredMethods(getIntrospectedClass())
                for (method in methods) {
                    if (isAnnotatedMethod(method, annotationName)) {
                        return true
                    }
                }
            } catch (ex: Throwable) {
                throw IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex)
            }
        }
        return false
    }

    override fun getAnnotatedMethods(annotationName: String): Set<MethodMetadata> {
        val result = LinkedHashSet<MethodMetadata>(4)
        if (AnnotationUtil.isCandidateClass(getIntrospectedClass(), annotationName)) {
            ReflectionUtil.doWithLocalMethods(getIntrospectedClass()) { method ->
                if (isAnnotatedMethod(method, annotationName)) {
                    result.add(StandardMethodMetadata(method, nestedAnnotationsAsMap))
                }
            }
        }
        return result
    }

    fun getDeclaredMethods(): Set<MethodMetadata> {
        val result = LinkedHashSet<MethodMetadata>(16)
        ReflectionUtil.doWithLocalMethods(getIntrospectedClass()) { method ->
            result.add(
                StandardMethodMetadata(
                    method,
                    nestedAnnotationsAsMap
                )
            )
        }
        return result
    }

    private fun isAnnotatedMethod(method: Method, annotationName: String): Boolean {
        return !method.isBridge && method.annotations.isNotEmpty() &&
                AnnotatedElementUtil.isAnnotated(method, annotationName)
    }

    fun from(introspectedClass: Class<*>): AnnotationMetadata {
        return StandardAnnotationMetadata(introspectedClass, true)
    }

}