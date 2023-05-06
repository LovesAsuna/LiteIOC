package com.hyosakura.liteioc.core.convert

import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.util.ClassUtil
import java.io.Serializable
import java.lang.reflect.AnnotatedElement
import java.util.*
import java.util.stream.Stream

/**
 * @author LovesAsuna
 **/
class TypeDescriptor {

    companion object {

        private val EMPTY_ANNOTATION_ARRAY = emptyArray<Annotation>()

        private val commonTypesCache: MutableMap<Class<*>, TypeDescriptor> = HashMap(32)

        private val CACHED_COMMON_TYPES = arrayOf(
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.java,
            Byte::class.javaPrimitiveType!!,
            Byte::class.java,
            Char::class.javaPrimitiveType!!,
            Char::class.java,
            Double::class.javaPrimitiveType!!,
            Double::class.java,
            Float::class.javaPrimitiveType!!,
            Float::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.java,
            Long::class.javaPrimitiveType!!,
            Long::class.java,
            Short::class.javaPrimitiveType!!,
            Short::class.java,
            String::class.java,
            Any::class.java
        )

        init {
            for (preCachedClass in CACHED_COMMON_TYPES) {
                commonTypesCache[preCachedClass] = valueOf(preCachedClass)
            }
        }

        fun valueOf(type: Class<*>?): TypeDescriptor {
            var type = type
            if (type == null) {
                type = Any::class.java
            }
            return commonTypesCache[type]
                ?: TypeDescriptor(
                    ResolvableType.forClass(
                        type
                    ), null, null
                )
        }

        fun forObject(source: Any?): TypeDescriptor? {
            return if (source != null) valueOf(source.javaClass) else null
        }

    }

    var type: Class<*>

    var resolvableType: ResolvableType

    var annotatedElement: AnnotatedElementAdapter

    constructor(
        resolvableType: ResolvableType,
        type: Class<*>?,
        annotations: Array<Annotation>?
    ) {
        this.resolvableType = resolvableType
        this.type = type ?: resolvableType.toClass()
        this.annotatedElement = AnnotatedElementAdapter(annotations)
    }

    constructor(property: Property) {
        this.resolvableType = ResolvableType.forMethodParameter(property.getMethodParameter())
        this.type = resolvableType.resolve(property.getType())
        this.annotatedElement = AnnotatedElementAdapter(property.getAnnotations())
    }

    fun isPrimitive(): Boolean {
        return type.isPrimitive
    }

    private fun isNestedAssignable(
        nestedTypeDescriptor: TypeDescriptor?,
        otherNestedTypeDescriptor: TypeDescriptor?
    ): Boolean {
        return nestedTypeDescriptor == null || otherNestedTypeDescriptor == null ||
                nestedTypeDescriptor.isAssignableTo(otherNestedTypeDescriptor)
    }

    fun isArray(): Boolean {
        return type.isArray
    }

    fun isCollection(): Boolean {
        return Collection::class.java.isAssignableFrom(type)
    }

    fun isMap(): Boolean {
        return Map::class.java.isAssignableFrom(type)
    }

    fun getObjectType(): Class<*> {
        return ClassUtil.resolvePrimitiveIfNecessary(type)
    }

    fun getAnnotations(): Array<Annotation> {
        return annotatedElement.annotations
    }

    fun getElementTypeDescriptor(): TypeDescriptor? {
        if (resolvableType.isArray()) {
            return TypeDescriptor(
                resolvableType.getComponentType(),
                null,
                getAnnotations()
            )
        }
        return if (Stream::class.java.isAssignableFrom(type)) {
            getRelatedIfResolvable(
                this, resolvableType.`as`(
                    Stream::class.java
                ).getGeneric(0)
            )
        } else getRelatedIfResolvable(
            this,
            resolvableType.asCollection().getGeneric(0)
        )
    }

    private fun getRelatedIfResolvable(
        source: TypeDescriptor,
        type: ResolvableType
    ): TypeDescriptor? {
        return if (type.resolve() == null) {
            null
        } else TypeDescriptor(type, null, source.getAnnotations())
    }

    fun getMapKeyTypeDescriptor(): TypeDescriptor? {
        require(isMap()) { "Not a [java.util.Map]" }
        return getRelatedIfResolvable(
            this,
            resolvableType.asMap().getGeneric(0)
        )
    }

    fun getMapValueTypeDescriptor(): TypeDescriptor? {
        require(isMap()) {"Not a [java.util.Map]"}
        return getRelatedIfResolvable(
            this,
            resolvableType.asMap().getGeneric(1)
        )
    }

    fun isAssignableTo(typeDescriptor: TypeDescriptor): Boolean {
        val typesAssignable = typeDescriptor.getObjectType().isAssignableFrom(getObjectType())
        if (!typesAssignable) {
            return false
        }
        return if (isArray() && typeDescriptor.isArray()) {
            isNestedAssignable(getElementTypeDescriptor(), typeDescriptor.getElementTypeDescriptor())
        } else if (isCollection() && typeDescriptor.isCollection()) {
            isNestedAssignable(getElementTypeDescriptor(), typeDescriptor.getElementTypeDescriptor())
        } else if (isMap() && typeDescriptor.isMap()) {
            isNestedAssignable(getMapKeyTypeDescriptor(), typeDescriptor.getMapKeyTypeDescriptor()) &&
                    isNestedAssignable(getMapValueTypeDescriptor(), typeDescriptor.getMapValueTypeDescriptor())
        } else {
            true
        }
    }

    inner class AnnotatedElementAdapter(
        private val annotations: Array<Annotation>?
    ) : AnnotatedElement, Serializable {
        override fun isAnnotationPresent(annotationClass: Class<out Annotation>): Boolean {
            for (annotation in getAnnotations()) {
                if (annotation.annotationClass.java == annotationClass) {
                    return true
                }
            }
            return false
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>): T? {
            for (annotation in getAnnotations()) {
                if (annotation.annotationClass.java == annotationClass) {
                    return annotation as T
                }
            }
            return null
        }

        override fun getAnnotations(): Array<Annotation> {
            return annotations?.clone() ?: EMPTY_ANNOTATION_ARRAY
        }

        override fun getDeclaredAnnotations(): Array<Annotation> {
            return getAnnotations()
        }

        fun isEmpty(): Boolean {
            return this.annotations.isNullOrEmpty()
        }

        override fun equals(other: Any?): Boolean {
            return this === other || other is AnnotatedElementAdapter && Arrays.equals(annotations, other.annotations)
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(annotations)
        }

        override fun toString(): String {
            return this@TypeDescriptor.toString()
        }

    }

}