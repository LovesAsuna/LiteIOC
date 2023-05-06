package com.hyosakura.liteioc.core

import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class DecoratingClassLoader : ClassLoader {

    companion object {

        init {
            registerAsParallelCapable()
        }

    }

    private val excludedPackages = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(8))

    private val excludedClasses = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(8))

    constructor()

    constructor(parent: ClassLoader?) : super(parent)

    fun excludePackage(packageName: String) {
        excludedPackages.add(packageName)
    }

    fun excludeClass(className: String) {
        excludedClasses.add(className)
    }

    protected fun isExcluded(className: String): Boolean {
        if (excludedClasses.contains(className)) {
            return true
        }
        for (packageName in excludedPackages) {
            if (className.startsWith(packageName!!)) {
                return true
            }
        }
        return false
    }

}