package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeanMetadataElement
import com.hyosakura.liteioc.util.ClassUtil

/**
 * @author LovesAsuna
 **/
class TypedStringValue : BeanMetadataElement {

    private var value: String? = null

    @Volatile
    private var targetType: Any? = null

    private var source: Any? = null

    private var specifiedTypeName: String? = null

    @Volatile
    private var dynamic = false

    fun getTargetTypeName(): String? {
        val targetTypeValue = this.targetType
        return if (targetTypeValue is Class<*>) {
            targetTypeValue.name
        } else {
            targetTypeValue as String
        }
    }

    fun hasTargetType(): Boolean {
        return this.targetType is Class<*>
    }

    @Throws(ClassNotFoundException::class)
    fun resolveTargetType(classLoader: ClassLoader?): Class<*>? {
        val typeName = getTargetTypeName() ?: return null
        val resolvedClass = ClassUtil.forName(typeName, classLoader)
        this.targetType = resolvedClass
        return resolvedClass
    }

    fun getValue(): String? {
        return this.value
    }

    fun getTargetType(): Class<*> {
        val targetTypeValue = this.targetType
        check(targetTypeValue is Class<*>) { "Typed String value does not carry a resolved target type" }
        return targetTypeValue
    }

}