package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable
import java.beans.IntrospectionException
import java.beans.PropertyDescriptor
import java.lang.reflect.Method

object PropertyDescriptorUtil {

    fun copyNonMethodProperties(source: PropertyDescriptor, target: PropertyDescriptor) {
        target.isExpert = source.isExpert
        target.isHidden = source.isHidden
        target.isPreferred = source.isPreferred
        target.name = source.name
        target.shortDescription = source.shortDescription
        target.displayName = source.displayName

        // Copy all attributes (emulating behavior of private FeatureDescriptor#addTable)
        val keys = source.attributeNames()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement()
            target.setValue(key, source.getValue(key))
        }

        // See java.beans.PropertyDescriptor#PropertyDescriptor(PropertyDescriptor)
        target.propertyEditorClass = source.propertyEditorClass
        target.isBound = source.isBound
        target.isConstrained = source.isConstrained
    }

    @Throws(IntrospectionException::class)
    fun findPropertyType(readMethod: Method?, writeMethod: Method?): Class<*>? {
        var propertyType: Class<*>? = null
        if (readMethod != null) {
            if (readMethod.parameterCount != 0) {
                throw IntrospectionException("Bad read method arg count: $readMethod")
            }
            propertyType = readMethod.returnType
            if (propertyType == Void.TYPE) {
                throw IntrospectionException("Read method returns void: $readMethod")
            }
        }
        if (writeMethod != null) {
            val params = writeMethod.parameterTypes
            if (params.size != 1) {
                throw IntrospectionException("Bad write method arg count: $writeMethod")
            }
            if (propertyType != null) {
                if (propertyType.isAssignableFrom(params[0])) {
                    // Write method's property type potentially more specific
                    propertyType = params[0]
                } else if (params[0].isAssignableFrom(propertyType)) {
                    // Proceed with read method's property type
                } else {
                    throw IntrospectionException(
                        "Type mismatch between read and write methods: $readMethod - $writeMethod"
                    )
                }
            } else {
                propertyType = params[0]
            }
        }
        return propertyType
    }

    @Throws(IntrospectionException::class)
    fun findIndexedPropertyType(
        name: String?, @Nullable propertyType: Class<*>?,
        @Nullable indexedReadMethod: Method?, @Nullable indexedWriteMethod: Method?
    ): Class<*>? {
        var indexedPropertyType: Class<*>? = null
        if (indexedReadMethod != null) {
            val params = indexedReadMethod.parameterTypes
            if (params.size != 1) {
                throw IntrospectionException("Bad indexed read method arg count: $indexedReadMethod")
            }
            if (params[0] != Integer.TYPE) {
                throw IntrospectionException("Non int index to indexed read method: $indexedReadMethod")
            }
            indexedPropertyType = indexedReadMethod.returnType
            if (indexedPropertyType == Void.TYPE) {
                throw IntrospectionException("Indexed read method returns void: $indexedReadMethod")
            }
        }
        if (indexedWriteMethod != null) {
            val params = indexedWriteMethod.parameterTypes
            if (params.size != 2) {
                throw IntrospectionException("Bad indexed write method arg count: $indexedWriteMethod")
            }
            if (params[0] != Integer.TYPE) {
                throw IntrospectionException("Non int index to indexed write method: $indexedWriteMethod")
            }
            if (indexedPropertyType != null) {
                if (indexedPropertyType.isAssignableFrom(params[1])) {
                    // Write method's property type potentially more specific
                    indexedPropertyType = params[1]
                } else if (params[1].isAssignableFrom(indexedPropertyType)) {
                    // Proceed with read method's property type
                } else {
                    throw IntrospectionException(
                        "Type mismatch between indexed read and write methods: " +
                                indexedReadMethod + " - " + indexedWriteMethod
                    )
                }
            } else {
                indexedPropertyType = params[1]
            }
        }
        if (propertyType != null && (!propertyType.isArray ||
                    propertyType.componentType != indexedPropertyType)
        ) {
            throw IntrospectionException(
                ("Type mismatch between indexed and non-indexed methods: " +
                        indexedReadMethod + " - " + indexedWriteMethod)
            )
        }
        return indexedPropertyType
    }

    fun equals(pd: PropertyDescriptor, otherPd: PropertyDescriptor): Boolean {
        return ObjectUtil.nullSafeEquals(pd.readMethod, otherPd.readMethod) &&
                ObjectUtil.nullSafeEquals(pd.writeMethod, otherPd.writeMethod) &&
                ObjectUtil.nullSafeEquals(pd.propertyType, otherPd.propertyType) &&
                ObjectUtil.nullSafeEquals(
                    pd.propertyEditorClass,
                    otherPd.propertyEditorClass
                ) && pd.isBound == otherPd.isBound && pd.isConstrained == otherPd.isConstrained
    }

}