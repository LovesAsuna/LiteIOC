package com.hyosakura.liteioc.bean

import java.beans.BeanInfo
import java.beans.IntrospectionException

/**
 * @author LovesAsuna
 **/
interface BeanInfoFactory {

    @Throws(IntrospectionException::class)
    fun getBeanInfo(beanClass: Class<*>): BeanInfo?

}