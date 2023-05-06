package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
interface BeanFactoryAware : Aware {
    fun setBeanFactory(beanFactory: BeanFactory)
}