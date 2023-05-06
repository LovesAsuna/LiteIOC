package com.hyosakura.liteioc.core.type

import com.hyosakura.liteioc.core.annotation.AnnotatedElementUtil
import com.hyosakura.liteioc.core.annotation.MergedAnnotations
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.SearchStrategy
import com.hyosakura.liteioc.core.annotation.RepeatableContainers
import com.hyosakura.liteioc.util.MultiValueMap
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * @author LovesAsuna
 **/
class StandardMethodMetadata : MethodMetadata {

    private val introspectedMethod: Method

    private val nestedAnnotationsAsMap: Boolean

    private var mergedAnnotations: MergedAnnotations

    @Deprecated("in favor of obtaining instances via {@link AnnotationMetadata}")
    constructor(introspectedMethod: Method) : this(introspectedMethod, false)

    constructor(introspectedMethod: Method, nestedAnnotationsAsMap: Boolean) {
        this.introspectedMethod = introspectedMethod
        this.nestedAnnotationsAsMap = nestedAnnotationsAsMap
        this.mergedAnnotations = MergedAnnotations.from(
            introspectedMethod, SearchStrategy.DIRECT, RepeatableContainers.none()
        )
    }

    override fun getAnnotations(): MergedAnnotations {
        return this.mergedAnnotations
    }

    fun getIntrospectedMethod(): Method {
        return this.introspectedMethod
    }

    override fun getMethodName(): String {
        return this.introspectedMethod.name
    }

    override fun getDeclaringClassName(): String {
        return this.introspectedMethod.declaringClass.name
    }

    override fun getReturnTypeName(): String {
        return this.introspectedMethod.returnType.name
    }

    override fun isAbstract(): Boolean {
        return Modifier.isAbstract(this.introspectedMethod.modifiers)
    }

    override fun isStatic(): Boolean {
        return Modifier.isStatic(this.introspectedMethod.modifiers)
    }

    override fun isFinal(): Boolean {
        return Modifier.isFinal(this.introspectedMethod.modifiers)
    }

    override fun isOverridable(): Boolean {
        return !isStatic() && !isFinal() && !isPrivate()
    }

    private fun isPrivate(): Boolean {
        return Modifier.isPrivate(this.introspectedMethod.modifiers)
    }

    override fun getAnnotationAttributes(annotationName: String, classValuesAsString: Boolean): Map<String, Any>? {
        return if (this.nestedAnnotationsAsMap) {
            super.getAnnotationAttributes(annotationName, classValuesAsString)
        } else AnnotatedElementUtil.getMergedAnnotationAttributes(
            this.introspectedMethod,
            annotationName, classValuesAsString, false
        )
    }

    override fun getAllAnnotationAttributes(
        annotationName: String,
        classValuesAsString: Boolean
    ): MultiValueMap<String, Any>? {
        return if (this.nestedAnnotationsAsMap) {
            super.getAllAnnotationAttributes(annotationName, classValuesAsString)
        } else AnnotatedElementUtil.getAllAnnotationAttributes(
            this.introspectedMethod,
            annotationName, classValuesAsString, false
        )
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is StandardMethodMetadata && this.introspectedMethod == other.introspectedMethod
    }

    override fun hashCode(): Int {
        return introspectedMethod.hashCode()
    }

    override fun toString(): String {
        return introspectedMethod.toString()
    }

}