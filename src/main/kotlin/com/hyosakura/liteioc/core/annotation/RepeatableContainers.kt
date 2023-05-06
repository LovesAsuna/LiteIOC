package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import java.lang.annotation.Repeatable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
abstract class RepeatableContainers private constructor(private var parent: RepeatableContainers?) {

    companion object {

        fun standardRepeatables(): RepeatableContainers {
            return StandardRepeatableContainers.INSTANCE
        }

        fun none(): RepeatableContainers {
            return NoRepeatableContainers.INSTANCE
        }

    }

    open fun and(
        container: Class<out Annotation>, repeatable: Class<out Annotation>
    ): RepeatableContainers? {
        return ExplicitRepeatableContainer(
            this, repeatable, container
        )
    }

    open fun findRepeatedAnnotations(annotation: Annotation): Array<Annotation>? {
        return if (parent == null) {
            null
        } else parent!!.findRepeatedAnnotations(annotation)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        return if (other == null || javaClass != other.javaClass) {
            false
        } else ObjectUtil.nullSafeEquals(parent, (other as RepeatableContainers).parent)
    }

    override fun hashCode(): Int = ObjectUtil.nullSafeHashCode(parent)

    open fun of(
        repeatable: Class<out Annotation>, container: Class<out Annotation>?
    ): RepeatableContainers? {
        return ExplicitRepeatableContainer(
            null, repeatable, container
        )
    }

    private class StandardRepeatableContainers : RepeatableContainers(null) {

        companion object {

            private val cache: MutableMap<Class<out Annotation>, Any> = ConcurrentHashMap()

            private val NONE = Any()

            val INSTANCE = StandardRepeatableContainers()

            private fun getRepeatedAnnotationsMethod(annotationType: Class<out Annotation>): Method? {
                val result = cache.computeIfAbsent(
                    annotationType
                ) { type: Class<out Annotation> ->
                    computeRepeatedAnnotationsMethod(
                        type
                    )
                }
                return if (result !== NONE) result as Method else null
            }

            private fun computeRepeatedAnnotationsMethod(annotationType: Class<out Annotation>): Any {
                val methods = AttributeMethods.forAnnotationType(annotationType)
                if (methods.hasOnlyValueAttribute()) {
                    val method: Method = methods[0]
                    val returnType = method.returnType
                    if (returnType.isArray) {
                        val componentType = returnType.componentType
                        if (Annotation::class.java.isAssignableFrom(componentType) && componentType.isAnnotationPresent(
                                Repeatable::class.java
                            )
                        ) {
                            return method
                        }
                    }
                }
                return NONE
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun findRepeatedAnnotations(annotation: Annotation): Array<Annotation>? {
            val method = getRepeatedAnnotationsMethod(annotation.javaClass)
            return if (method != null) {
                ReflectionUtil.invokeMethod(method, annotation) as Array<Annotation>
            } else super.findRepeatedAnnotations(annotation)
        }

    }

    private class ExplicitRepeatableContainer(
        parent: RepeatableContainers?, repeatable: Class<out Annotation>, container: Class<out Annotation>?
    ) : RepeatableContainers(parent) {
        private val repeatable: Class<out Annotation>
        private val container: Class<out Annotation>?
        private val valueMethod: Method

        init {
            var container = container
            if (container == null) {
                container = deduceContainer(repeatable)
            }
            val valueMethod = AttributeMethods.forAnnotationType(container)[MergedAnnotation.VALUE]
            try {
                if (valueMethod == null) {
                    throw NoSuchMethodException("No value method found")
                }
                val returnType = valueMethod.returnType
                if (!returnType.isArray || returnType.componentType != repeatable) {
                    throw AnnotationConfigurationException(
                        "Container type [" + container.name + "] must declare a 'value' attribute for an array of type [" + repeatable.name + "]"
                    )
                }
            } catch (ex: AnnotationConfigurationException) {
                throw ex
            } catch (ex: Throwable) {
                throw AnnotationConfigurationException(
                    ("Invalid declaration of container type [" + container.name + "] for repeatable annotation [" + repeatable.name + "]"),
                    ex
                )
            }
            this.repeatable = repeatable
            this.container = container
            this.valueMethod = valueMethod
        }

        private fun deduceContainer(repeatable: Class<out Annotation>): Class<out Annotation> {
            val annotation = repeatable.getAnnotation(
                Repeatable::class.java
            )
            return annotation.javaClass
        }

        @Suppress("UNCHECKED_CAST")
        override fun findRepeatedAnnotations(annotation: Annotation): Array<Annotation>? {
            return if (container!!.isAssignableFrom(annotation.javaClass)) {
                ReflectionUtil.invokeMethod(valueMethod, annotation) as Array<Annotation>
            } else super.findRepeatedAnnotations(annotation)
        }

        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) {
                return false
            }
            val otherErc = other as ExplicitRepeatableContainer
            return ((container == otherErc.container) && (repeatable == otherErc.repeatable))
        }

        override fun hashCode(): Int {
            var hashCode = super.hashCode()
            hashCode = 31 * hashCode + container.hashCode()
            hashCode = 31 * hashCode + repeatable.hashCode()
            return hashCode
        }
    }

    private class NoRepeatableContainers : RepeatableContainers(null) {

        companion object {

            val INSTANCE: NoRepeatableContainers = NoRepeatableContainers()

        }

    }

}