package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.factory.ObjectFactory

/**
 * @author LovesAsuna
 **/
interface Scope {

    fun get(name: String, objectFactory: ObjectFactory<*>): Any

    fun remove(name: String?): Any?

    fun registerDestructionCallback(name: String, callback: Runnable)

    fun resolveContextualObject(key: String): Any?

}