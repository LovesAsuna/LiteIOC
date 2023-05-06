package com.hyosakura.liteioc.aop

import com.hyosakura.liteioc.util.ObjectUtil

/**
 * @author LovesAsuna
 **/
class EmptyTargetSource : TargetSource {

    companion object {

        val INSTANCE: EmptyTargetSource = EmptyTargetSource(null, true)

    }

    fun forClass(targetClass: Class<*>?): EmptyTargetSource {
        return forClass(targetClass, true)
    }

    fun forClass(targetClass: Class<*>?, isStatic: Boolean): EmptyTargetSource {
        return if (targetClass == null && isStatic) INSTANCE else EmptyTargetSource(targetClass, isStatic)
    }

    private val targetClass: Class<*>?

    private val isStatic: Boolean

    private constructor(targetClass: Class<*>?, isStatic: Boolean) {
        this.targetClass = targetClass
        this.isStatic = isStatic
    }

    override fun getTargetClass(): Class<*>? {
        return targetClass
    }

    override fun isStatic(): Boolean {
        return isStatic
    }

    override fun getTarget(): Any? {
        return null
    }

    override fun releaseTarget(target: Any) {}

    private fun readResolve(): Any {
        return if (targetClass == null && isStatic) INSTANCE else this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other !is EmptyTargetSource) {
            false
        } else ObjectUtil.nullSafeEquals(targetClass, other.targetClass) && isStatic == other.isStatic
    }

    override fun hashCode(): Int {
        return EmptyTargetSource::class.java.hashCode() * 13 + ObjectUtil.nullSafeHashCode(targetClass)
    }

    override fun toString(): String {
        return "EmptyTargetSource: " +
                (if (targetClass != null) "target class [" + targetClass.name + "]" else "no target class") +
                ", " + if (isStatic) "static" else "dynamic"
    }

}