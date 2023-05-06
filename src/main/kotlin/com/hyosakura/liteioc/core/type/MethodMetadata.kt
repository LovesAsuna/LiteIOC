package com.hyosakura.liteioc.core.type

/**
 * @author LovesAsuna
 **/
interface MethodMetadata : AnnotatedTypeMetadata {

    fun getMethodName(): String

    fun getDeclaringClassName(): String

    fun getReturnTypeName(): String

    fun isAbstract(): Boolean

    fun isStatic(): Boolean

    fun isFinal(): Boolean

    fun isOverridable(): Boolean

}