package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
interface DisposableBean {

    @Throws(Exception::class)
    fun destroy()

}