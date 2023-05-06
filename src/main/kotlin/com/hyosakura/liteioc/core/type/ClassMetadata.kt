package com.hyosakura.liteioc.core.type

/**
 * @author LovesAsuna
 **/
interface ClassMetadata {

    fun getClassName(): String

    fun isInterface(): Boolean

    fun isAnnotation(): Boolean

    fun isAbstract(): Boolean

    fun isConcrete(): Boolean {
        return !(isInterface() || isAbstract())
    }

    fun isFinal(): Boolean

    fun isIndependent(): Boolean

    fun hasEnclosingClass(): Boolean {
        return getEnclosingClassName() != null
    }

    fun getEnclosingClassName(): String?

    fun hasSuperClass(): Boolean {
        return getSuperClassName() != null
    }

    fun getSuperClassName(): String?

    fun getInterfaceNames(): Array<String>

    fun getMemberClassNames(): Array<String>

}