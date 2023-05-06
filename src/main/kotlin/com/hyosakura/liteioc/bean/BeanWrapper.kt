package com.hyosakura.liteioc.bean

import java.beans.PropertyDescriptor

/**
 * @author LovesAsuna
 **/
interface BeanWrapper : ConfigurablePropertyAccessor {

    fun getWrappedInstance(): Any

    fun getWrappedClass(): Class<*>

    fun getPropertyDescriptors(): Array<PropertyDescriptor>

    fun getPropertyDescriptor(propertyName: String): PropertyDescriptor

}