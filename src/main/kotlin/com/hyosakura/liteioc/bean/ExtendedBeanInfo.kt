package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Image
import java.beans.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**
 * @author LovesAsuna
 **/
class ExtendedBeanInfo : BeanInfo {

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(ExtendedBeanInfo::class.java)
        fun isCandidateWriteMethod(method: Method): Boolean {
            val methodName = method.name
            val nParams = method.parameterCount
            return methodName.length > 3 && methodName.startsWith("set") && Modifier.isPublic(method.modifiers) && (!Void.TYPE.isAssignableFrom(
                method.returnType
            ) || Modifier.isStatic(method.modifiers)) && (nParams == 1 || nParams == 2 && Int::class.javaPrimitiveType == method.parameterTypes[0])
        }

    }

    private val delegate: BeanInfo

    private val propertyDescriptors: MutableSet<PropertyDescriptor> =
        TreeSet<PropertyDescriptor>(PropertyDescriptorComparator())

    constructor(delegate: BeanInfo) {
        this.delegate = delegate
        for (pd in delegate.propertyDescriptors) {
            try {
                this.propertyDescriptors.add(
                    if (pd is IndexedPropertyDescriptor) SimpleIndexedPropertyDescriptor(
                        pd
                    ) else SimplePropertyDescriptor(pd)
                )
            } catch (ex: IntrospectionException) {
                // Probably simply a method that wasn't meant to follow the JavaBeans pattern...
                if (logger.isDebugEnabled) {
                    logger.debug("Ignoring invalid bean property '" + pd.name + "': " + ex.message)
                }
            }
        }
        val methodDescriptors = delegate.methodDescriptors
        if (methodDescriptors != null) {
            for (method in findCandidateWriteMethods(methodDescriptors)) {
                try {
                    handleCandidateWriteMethod(method)
                } catch (ex: IntrospectionException) {
                    // We're only trying to find candidates, can easily ignore extra ones here...
                    if (logger.isDebugEnabled) {
                        logger.debug("Ignoring candidate write method [" + method + "]: " + ex.message)
                    }
                }
            }
        }
    }

    @Throws(IntrospectionException::class)
    private fun handleCandidateWriteMethod(method: Method) {
        val nParams = method.parameterCount
        val propertyName = propertyNameFor(method)
        val propertyType = method.parameterTypes[nParams - 1]
        val existingPd = findExistingPropertyDescriptor(propertyName, propertyType)
        if (nParams == 1) {
            if (existingPd == null) {
                propertyDescriptors.add(SimplePropertyDescriptor(propertyName, null, method))
            } else {
                existingPd.writeMethod = method
            }
        } else if (nParams == 2) {
            if (existingPd == null) {
                propertyDescriptors.add(
                    SimpleIndexedPropertyDescriptor(propertyName, null, null, null, method)
                )
            } else if (existingPd is IndexedPropertyDescriptor) {
                existingPd.indexedWriteMethod = method
            } else {
                propertyDescriptors.remove(existingPd)
                propertyDescriptors.add(
                    SimpleIndexedPropertyDescriptor(
                        propertyName, existingPd.readMethod, existingPd.writeMethod, null, method
                    )
                )
            }
        } else {
            throw IllegalArgumentException("Write method must have exactly 1 or 2 parameters: $method")
        }
    }

    fun findExistingPropertyDescriptor(propertyName: String?, propertyType: Class<*>): PropertyDescriptor? {
        for (pd in this.propertyDescriptors) {
            val candidateType: Class<*>
            val candidateName = pd.name
            if (pd is IndexedPropertyDescriptor) {
                candidateType = pd.indexedPropertyType
                if (candidateName.equals(propertyName) && (candidateType == propertyType || candidateType == propertyType.componentType)) {
                    return pd
                }
            } else {
                candidateType = pd.propertyType
                if (candidateName.equals(propertyName) && (candidateType == propertyType || propertyType == candidateType.componentType)) {
                    return pd
                }
            }
        }
        return null
    }


    private fun propertyNameFor(method: Method): String? {
        return Introspector.decapitalize(method.name.substring(3))
    }

    private fun findCandidateWriteMethods(methodDescriptors: Array<MethodDescriptor>): List<Method> {
        val matches: MutableList<Method> = ArrayList()
        for (methodDescriptor in methodDescriptors) {
            val method = methodDescriptor.method
            if (isCandidateWriteMethod(method)) {
                matches.add(method)
            }
        }
        // Sort non-void returning write methods to guard against the ill effects of
        // non-deterministic sorting of methods returned from Class#getDeclaredMethods
        matches.sortWith(Comparator.comparing { obj: Method -> obj.toString() }.reversed())
        return matches
    }

    override fun getBeanDescriptor(): BeanDescriptor {
        return delegate.beanDescriptor
    }

    override fun getEventSetDescriptors(): Array<EventSetDescriptor> {
        return delegate.eventSetDescriptors
    }

    override fun getDefaultEventIndex(): Int {
        return delegate.defaultEventIndex
    }

    override fun getPropertyDescriptors(): Array<PropertyDescriptor> {
        return propertyDescriptors.toTypedArray()
    }

    override fun getDefaultPropertyIndex(): Int {
        return delegate.defaultPropertyIndex
    }

    override fun getMethodDescriptors(): Array<MethodDescriptor> {
        return delegate.methodDescriptors
    }

    override fun getAdditionalBeanInfo(): Array<BeanInfo> {
        return delegate.additionalBeanInfo
    }

    override fun getIcon(iconKind: Int): Image {
        return delegate.getIcon(iconKind)
    }

    class SimplePropertyDescriptor : PropertyDescriptor {

        private var readMethod: Method?

        private var writeMethod: Method?

        private var propertyType: Class<*>?

        private var propertyEditorClass: Class<*>? = null

        constructor(original: PropertyDescriptor) : this(original.name, original.readMethod, original.writeMethod) {
            PropertyDescriptorUtil.copyNonMethodProperties(original, this)
        }

        @Throws(IntrospectionException::class)
        constructor(propertyName: String?, readMethod: Method?, writeMethod: Method?) : super(
            propertyName, null, null
        ) {
            this.readMethod = readMethod
            this.writeMethod = writeMethod
            propertyType = PropertyDescriptorUtil.findPropertyType(readMethod, writeMethod)
        }


        @Nullable
        override fun getReadMethod(): Method? {
            return readMethod
        }

        override fun setReadMethod(@Nullable readMethod: Method) {
            this.readMethod = readMethod
        }

        @Nullable
        override fun getWriteMethod(): Method? {
            return writeMethod
        }

        override fun setWriteMethod(@Nullable writeMethod: Method) {
            this.writeMethod = writeMethod
        }

        @Nullable
        override fun getPropertyType(): Class<*>? {
            if (propertyType == null) {
                try {
                    propertyType = PropertyDescriptorUtil.findPropertyType(readMethod, writeMethod)
                } catch (ex: IntrospectionException) {
                    // Ignore, as does PropertyDescriptor#getPropertyType
                }
            }
            return propertyType
        }

        @Nullable
        override fun getPropertyEditorClass(): Class<*>? {
            return propertyEditorClass
        }

        override fun setPropertyEditorClass(@Nullable propertyEditorClass: Class<*>?) {
            this.propertyEditorClass = propertyEditorClass
        }

        override fun equals(other: Any?): Boolean {
            return this === other || other is PropertyDescriptor && PropertyDescriptorUtil.equals(
                this, other
            )
        }

        override fun hashCode(): Int {
            return ObjectUtil.nullSafeHashCode(getReadMethod()) * 29 + ObjectUtil.nullSafeHashCode(getWriteMethod())
        }

        override fun toString(): String {
            return String.format(
                "%s[name=%s, propertyType=%s, readMethod=%s, writeMethod=%s]",
                javaClass.simpleName,
                name,
                getPropertyType(),
                readMethod,
                writeMethod
            )
        }
    }

    class SimpleIndexedPropertyDescriptor : IndexedPropertyDescriptor {

        private var readMethod: Method? = null

        private var writeMethod: Method? = null

        private var propertyType: Class<*>? = null

        private var indexedReadMethod: Method? = null

        private var indexedWriteMethod: Method? = null

        private var indexedPropertyType: Class<*>? = null

        private var propertyEditorClass: Class<*>? = null

        constructor(original: IndexedPropertyDescriptor) : this(
            original.name,
            original.readMethod,
            original.writeMethod,
            original.indexedReadMethod,
            original.indexedWriteMethod
        ) {
            PropertyDescriptorUtil.copyNonMethodProperties(original, this)
        }

        constructor(
            propertyName: String?,
            readMethod: Method?,
            writeMethod: Method?,
            indexedReadMethod: Method?,
            indexedWriteMethod: Method
        ) : super(propertyName, null, null, null, null) {
            this.readMethod = readMethod
            this.writeMethod = writeMethod
            this.propertyType = PropertyDescriptorUtil.findPropertyType(readMethod, writeMethod)
            this.indexedReadMethod = indexedReadMethod
            this.indexedWriteMethod = indexedWriteMethod
            this.indexedPropertyType = PropertyDescriptorUtil.findIndexedPropertyType(
                propertyName, this.propertyType, indexedReadMethod, indexedWriteMethod
            )
        }

        override fun getReadMethod(): Method? {
            return readMethod
        }

        override fun setReadMethod(readMethod: Method?) {
            this.readMethod = readMethod
        }

        override fun getWriteMethod(): Method? {
            return writeMethod
        }

        override fun setWriteMethod(writeMethod: Method?) {
            this.writeMethod = writeMethod
        }

        override fun getPropertyType(): Class<*>? {
            if (this.propertyType == null) {
                try {
                    this.propertyType = PropertyDescriptorUtil.findPropertyType(this.readMethod, this.writeMethod)
                } catch (ex: IntrospectionException) {
                    // Ignore, as does IndexedPropertyDescriptor#getPropertyType
                }
            }
            return this.propertyType
        }

        override fun getIndexedReadMethod(): Method? {
            return this.indexedReadMethod
        }

        @Throws(IntrospectionException::class)
        override fun setIndexedReadMethod(indexedReadMethod: Method?) {
            this.indexedReadMethod = indexedReadMethod
        }

        override fun getIndexedWriteMethod(): Method? {
            return indexedWriteMethod
        }

        @Throws(IntrospectionException::class)
        override fun setIndexedWriteMethod(indexedWriteMethod: Method?) {
            this.indexedWriteMethod = indexedWriteMethod
        }

        override fun getIndexedPropertyType(): Class<*>? {
            if (this.indexedPropertyType == null) {
                try {
                    this.indexedPropertyType = PropertyDescriptorUtil.findIndexedPropertyType(
                        name, getPropertyType(), this.indexedReadMethod, this.indexedWriteMethod
                    )
                } catch (ex: IntrospectionException) {
                    // Ignore, as does IndexedPropertyDescriptor#getIndexedPropertyType
                }
            }
            return this.indexedPropertyType
        }

        override fun getPropertyEditorClass(): Class<*>? {
            return propertyEditorClass
        }

        override fun setPropertyEditorClass(propertyEditorClass: Class<*>?) {
            this.propertyEditorClass = propertyEditorClass
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is IndexedPropertyDescriptor) {
                return false
            }
            return (ObjectUtil.nullSafeEquals(
                getIndexedReadMethod(), other.indexedReadMethod
            ) && ObjectUtil.nullSafeEquals(
                getIndexedWriteMethod(), other.indexedWriteMethod
            ) && ObjectUtil.nullSafeEquals(
                getIndexedPropertyType(), other.indexedPropertyType
            ) && PropertyDescriptorUtil.equals(this, other))
        }

        override fun hashCode(): Int {
            var hashCode = ObjectUtil.nullSafeHashCode(getReadMethod())
            hashCode = 29 * hashCode + ObjectUtil.nullSafeHashCode(getWriteMethod())
            hashCode = 29 * hashCode + ObjectUtil.nullSafeHashCode(getIndexedReadMethod())
            hashCode = 29 * hashCode + ObjectUtil.nullSafeHashCode(getIndexedWriteMethod())
            return hashCode
        }

        override fun toString(): String {
            return String.format(
                "%s[name=%s, propertyType=%s, indexedPropertyType=%s, " + "readMethod=%s, writeMethod=%s, indexedReadMethod=%s, indexedWriteMethod=%s]",
                javaClass.simpleName,
                name,
                getPropertyType(),
                getIndexedPropertyType(),
                this.readMethod,
                this.writeMethod,
                this.indexedReadMethod,
                this.indexedWriteMethod
            )
        }
    }

    class PropertyDescriptorComparator : Comparator<PropertyDescriptor> {
        override fun compare(desc1: PropertyDescriptor, desc2: PropertyDescriptor): Int {
            val left = desc1.name
            val right = desc2.name
            val leftBytes = left.toByteArray()
            val rightBytes = right.toByteArray()
            for (i in left.indices) {
                if (right.length == i) {
                    return 1
                }
                val result = leftBytes[i] - rightBytes[i]
                if (result != 0) {
                    return result
                }
            }
            return left.length - right.length
        }
    }

}