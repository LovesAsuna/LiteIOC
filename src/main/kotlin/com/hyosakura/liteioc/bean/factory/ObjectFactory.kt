package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.BeansException

/**
 * @author LovesAsuna
 **/
fun interface ObjectFactory<T> {

    @Throws(BeansException::class)
    fun getObject(): T

}