package com.hyosakura.liteioc.aop

import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory

/**
 * @author LovesAsuna
 **/
class ProxyFactory(beanFactory: AutowireCapableBeanFactory) : ProxyCreatorSupport(beanFactory) {

    fun getProxy(): Any {
        return createAopProxy().getProxy()
    }

    fun getProxy(classLoader: ClassLoader?): Any {
        return createAopProxy().getProxy(classLoader)
    }

}