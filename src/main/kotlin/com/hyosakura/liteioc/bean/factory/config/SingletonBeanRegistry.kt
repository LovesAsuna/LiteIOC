package com.hyosakura.liteioc.bean.factory.config

/**
 * @author LovesAsuna
 **/
interface SingletonBeanRegistry {

    fun registerSingleton(beanName: String, singletonObject: Any)

    fun getSingleton(beanName: String): Any?

    fun containsSingleton(beanName: String): Boolean

    fun getSingletonNames(): Array<String>

    fun getSingletonCount(): Int

}