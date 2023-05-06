package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
interface BeanClassLoaderAware : Aware {

    fun setBeanClassLoader(classLoader: ClassLoader)

}