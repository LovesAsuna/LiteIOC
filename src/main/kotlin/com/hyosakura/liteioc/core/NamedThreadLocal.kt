package com.hyosakura.liteioc.core

/**
 * @author LovesAsuna
 **/
open class NamedThreadLocal<T>(val name: String) : ThreadLocal<T>() {

    override fun toString(): String = name

}