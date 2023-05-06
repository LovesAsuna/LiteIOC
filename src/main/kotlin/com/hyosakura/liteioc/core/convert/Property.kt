package com.hyosakura.liteioc.core.convert

import com.hyosakura.liteioc.core.MethodParameter
import com.hyosakura.liteioc.util.ReflectionUtil
import com.hyosakura.liteioc.util.StringUtil
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
class Property {

    companion object {

        private val annotationCache: MutableMap<Property, Array<Annotation>> = ConcurrentHashMap()

    }
    private val objectType: Class<*>

    private val readMethod: Method?

    private val writeMethod: Method?

    private val name: String

    private val methodParameter: MethodParameter

    private var annotations: Array<Annotation>? = null

    constructor(
        objectType: Class<*>, readMethod: Method?, writeMethod: Method?, name: String?
    ) {
        this.objectType = objectType
        this.readMethod = readMethod
        this.writeMethod = writeMethod
        this.methodParameter = resolveMethodParameter()
        this.name = name ?: resolveName()
    }

    fun getMethodParameter(): MethodParameter {
        return this.methodParameter
}

    fun getType(): Class<*> {
        return this.methodParameter.getParameterType()
    }

    fun getObjectType(): Class<*> {
        return this.objectType
    }

    fun getName(): String {
        return this.name
    }

    private fun getField(): Field? {
        val name= getName()
        if (name.isEmpty()) {
            return null
        }
        var field: Field? = null
        val declaringClass = declaringClass()
        if (declaringClass != null) {
            field = ReflectionUtil.findField(declaringClass, name)
            if (field == null) {
                // Same lenient fallback checking as in CachedIntrospectionResults...
                field = ReflectionUtil.findField(declaringClass, StringUtil.uncapitalize(name))
                if (field == null) {
                    field = ReflectionUtil.findField(declaringClass, StringUtil.capitalize(name))
                }
            }
        }
        return field
    }

    fun getReadMethod(): Method? {
        return this.readMethod
    }

    fun getWriteMethod(): Method? {
        return this.writeMethod
    }

    fun getAnnotations(): Array<Annotation>? {
        if (this.annotations == null) {
            this.annotations = resolveAnnotations()
        }
        return annotations
    }

    private fun resolveName(): String {
        return if (readMethod != null) {
            var index = readMethod.name.indexOf("get")
            if (index != -1) {
                index += 3
            } else {
                index = readMethod.name.indexOf("is")
                if (index != -1) {
                    index += 2
                } else {
                    // Record-style plain accessor method, e.g. name()
                    index = 0
                }
            }
            StringUtil.uncapitalize(readMethod.name.substring(index))
        } else if (writeMethod != null) {
            var index = writeMethod.name.indexOf("set")
            require(index != -1) { "Not a setter method" }
            index += 3
            StringUtil.uncapitalize(writeMethod.name.substring(index))
        } else {
            throw IllegalStateException("Property is neither readable nor writeable")
        }
    }

    private fun resolveMethodParameter(): MethodParameter {
        val read = resolveReadMethodParameter()
        val write = resolveWriteMethodParameter()
        if (write == null) {
            checkNotNull(read) { "Property is neither readable nor writeable" }
            return read
        }
        if (read != null) {
            val readType = read.getParameterType()
            val writeType = write.getParameterType()
            if (writeType != readType && writeType.isAssignableFrom(readType)) {
                return read
            }
        }
        return write
    }

    private fun resolveReadMethodParameter(): MethodParameter? {
        return if (getReadMethod() == null) {
            null
        } else MethodParameter(getReadMethod()!!, -1).withContainingClass(getObjectType())
    }

    private fun resolveWriteMethodParameter(): MethodParameter? {
        return if (getWriteMethod() == null) {
            null
        } else MethodParameter(getWriteMethod()!!, 0).withContainingClass(getObjectType())
    }

    private fun resolveAnnotations(): Array<Annotation>? {
        var annotations = annotationCache[this]
        if (annotations == null) {
            val annotationMap = LinkedHashMap<Class<out Annotation>, Annotation>()
            addAnnotationsToMap(annotationMap, getReadMethod())
            addAnnotationsToMap(annotationMap, getWriteMethod())
            addAnnotationsToMap(annotationMap, getField())
            annotations = annotationMap.values.toTypedArray()
            annotationCache[this] = annotations
        }
        return annotations
    }

    private fun addAnnotationsToMap(
        annotationMap: MutableMap<Class<out Annotation>, Annotation>, `object`: AnnotatedElement?
    ) {
        if (`object` != null) {
            for (annotation in `object`.annotations) {
                annotationMap[annotation.annotationClass.java] = annotation
            }
        }
    }

    private fun declaringClass(): Class<*>? {
        return if (getReadMethod() != null) {
            getReadMethod()!!.declaringClass
        } else if (getWriteMethod() != null) {
            getWriteMethod()!!.declaringClass
        } else {
            null
        }
    }

}