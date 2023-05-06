package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
class BeanIsAbstractException(beanName: String) : BeanCreationException(beanName, "Bean definition is abstract")