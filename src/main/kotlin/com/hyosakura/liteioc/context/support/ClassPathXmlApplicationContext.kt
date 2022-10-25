package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.factory.xml.XmlBeanDefinitionReader
import com.hyosakura.liteioc.util.StringUtil
import java.lang.reflect.Method


/**
 * 记载类路径下的XML格式的配置文件
 *
 * @author LovesAsuna
 */
class ClassPathXmlApplicationContext(configLocation: String) : AbstractApplicationContext() {
    init {
        this.configLocation = configLocation
        beanDefinitionReader = XmlBeanDefinitionReader()
        try {
            refresh()
        } catch (ignore: Exception) {
        }
    }

    @Throws(Exception::class)
    override fun getBean(name: String): Any? {
        val obj = singletonObjects[name]
        if (obj != null) {
            return obj
        }
        val registry = beanDefinitionReader.registry
        val beanDefinition = registry.getBeanDefinition(name)
        val className = beanDefinition!!.className
        val clazz = Class.forName(className)
        val beanObj = clazz.getDeclaredConstructor().newInstance()
        val propertyValues = beanDefinition.propertyValues
        for (propertyValue in propertyValues) {
            val propertyName = propertyValue.name
            val value= propertyValue.value
            val ref = propertyValue.ref
            if (ref != null && "" != ref) {
                val bean = getBean(ref)
                val methodName: String = StringUtil.getSetterMethodByFieldName(propertyName)
                val methods = clazz.methods
                for (method in methods) {
                    if (methodName == method.getName()) {
                        method.isAccessible = true
                        method.invoke(beanObj, bean)
                    }
                }
            }
            if (value != null && "" != value) {
                val methodName: String = StringUtil.getSetterMethodByFieldName(value)
                val method: Method = clazz.getDeclaredMethod(methodName, String::class.java)
                method.setAccessible(true)
                method.invoke(beanObj, value)
            }
        }
        singletonObjects[name] = beanObj
        return beanObj
    }

    @Throws(Exception::class)
    override fun <T> getBean(name: String, clazz: Class<T>): T? {
        val bean = getBean(name)
        return if (bean == null) {
            null
        } else {
            clazz.cast(bean)
        }
    }
}