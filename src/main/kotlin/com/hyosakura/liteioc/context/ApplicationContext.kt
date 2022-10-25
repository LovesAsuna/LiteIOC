package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.bean.factory.BeanFactory

/**
 * @author LovesAsuna
 **/
interface ApplicationContext : BeanFactory {
    @Throws(Exception::class)
    fun refresh()
}