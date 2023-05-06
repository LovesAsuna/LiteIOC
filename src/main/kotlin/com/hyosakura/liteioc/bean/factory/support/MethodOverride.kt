package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.util.ObjectUtil
import java.lang.reflect.Method

abstract class MethodOverride {

    private val methodName: String

    private var overloaded: Boolean = true

    private var source: Any? = null

    protected constructor(methodName: String) {
        this.methodName = methodName
    }

    fun getMethodName(): String = this.methodName

    fun setOverloaded(overloaded: Boolean) {
        this.overloaded = overloaded
    }

    protected fun isOverloaded(): Boolean = this.overloaded

    fun setSource(source: Any?) {
        this.source = source
    }

    fun getSource(): Any? = this.source

    abstract fun matches(method: Method): Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is MethodOverride) {
            return false
        }
        return (ObjectUtil.nullSafeEquals(this.methodName, other.methodName) &&
                ObjectUtil.nullSafeEquals(this.source, other.source))
    }

    override fun hashCode(): Int {
        var hashCode = ObjectUtil.nullSafeHashCode(this.methodName)
        hashCode = 29 * hashCode + ObjectUtil.nullSafeHashCode(this.source)
        return hashCode
    }


}
