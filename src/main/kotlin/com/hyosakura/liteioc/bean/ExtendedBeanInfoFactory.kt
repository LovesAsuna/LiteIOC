package com.hyosakura.liteioc.bean

import java.beans.BeanInfo
import java.beans.Introspector

/**
 * @author LovesAsuna
 **/
class ExtendedBeanInfoFactory : BeanInfoFactory {

    override fun getBeanInfo(beanClass: Class<*>): BeanInfo? {
        return if (supports(beanClass)) ExtendedBeanInfo(Introspector.getBeanInfo(beanClass)) else null
    }

    private fun supports(beanClass: Class<*>): Boolean {
        for (method in beanClass.methods) {
            if (ExtendedBeanInfo.isCandidateWriteMethod(method)) {
                return true
            }
        }
        return false
    }

}