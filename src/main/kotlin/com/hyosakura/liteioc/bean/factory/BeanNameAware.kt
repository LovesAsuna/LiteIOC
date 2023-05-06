package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
interface BeanNameAware : Aware {

    fun setBeanName(name: String)

}