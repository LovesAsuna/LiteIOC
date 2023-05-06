package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
interface InitializingBean {

    @Throws(Exception::class)
    fun afterPropertiesSet()

}