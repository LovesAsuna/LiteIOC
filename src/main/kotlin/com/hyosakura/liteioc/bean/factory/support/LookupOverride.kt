package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.util.ObjectUtil
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * @author LovesAsuna
 **/
class LookupOverride : MethodOverride {

    private val beanName: String?

    private var method: Method? = null

    constructor(methodName: String, beanName: String?) : super(methodName) {
        this.beanName = beanName
    }

    constructor(method: Method, beanName: String?) : super(method.name) {
        this.method = method
        this.beanName = beanName
    }

    fun getBeanName(): String? {
        return beanName
    }

    override fun matches(method: Method): Boolean {
        return if (this.method != null) {
            method == this.method
        } else {
            method.name == getMethodName() && (!isOverloaded() ||
                    Modifier.isAbstract(method.modifiers) || method.parameterCount == 0)
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other !is LookupOverride || !super.equals(other)) {
            false
        } else ObjectUtil.nullSafeEquals(method, other.method) &&
                ObjectUtil.nullSafeEquals(beanName, other.beanName)
    }

    override fun hashCode(): Int {
        return 29 * super.hashCode() + ObjectUtil.nullSafeHashCode(beanName)
    }

    override fun toString(): String {
        return "LookupOverride for method '" + getMethodName() + "'"
    }

}