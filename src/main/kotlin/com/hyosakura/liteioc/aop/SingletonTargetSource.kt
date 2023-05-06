package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
class SingletonTargetSource(private var target: Any) : TargetSource {

    override fun getTargetClass(): Class<*> {
        return target.javaClass
    }

    override fun getTarget(): Any {
        return target
    }

    override fun releaseTarget(target: Any) {
        // nothing to do
    }

    override fun isStatic(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other !is SingletonTargetSource) {
            false
        } else target == other.target
    }

    override fun hashCode(): Int {
        return target.hashCode()
    }

    override fun toString(): String {
        return "SingletonTargetSource for target object [$target]"
    }

}