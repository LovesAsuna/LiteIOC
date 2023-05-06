package com.hyosakura.liteioc.bean.factory.support

import org.jetbrains.annotations.Nullable
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArraySet

/**
 * @author LovesAsuna
 **/
class MethodOverrides {

    private val overrides: MutableSet<MethodOverride> = CopyOnWriteArraySet()

    constructor()

    fun MethodOverrides(other: MethodOverrides?) {
        addOverrides(other)
    }

    fun addOverrides(other: MethodOverrides?) {
        if (other != null) {
            overrides.addAll(other.overrides)
        }
    }

    fun addOverride(override: MethodOverride) {
        overrides.add(override)
    }

    fun getOverrides(): Set<MethodOverride> {
        return overrides
    }

    fun isEmpty(): Boolean {
        return overrides.isEmpty()
    }

    @Nullable
    fun getOverride(method: Method): MethodOverride? {
        var match: MethodOverride? = null
        for (candidate in overrides) {
            if (candidate.matches(method)) {
                match = candidate
            }
        }
        return match
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is MethodOverrides) {
            return false
        }
        return this.overrides == other.overrides
    }

    override fun hashCode(): Int {
        return overrides.hashCode()
    }

}