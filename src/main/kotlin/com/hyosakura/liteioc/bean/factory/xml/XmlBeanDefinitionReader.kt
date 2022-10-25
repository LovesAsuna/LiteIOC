package com.hyosakura.liteioc.bean.factory.xml

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.MutablePropertyValues
import com.hyosakura.liteioc.bean.PropertyValue
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionReader
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.SimpleBeanDefinitionRegistry
import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.SAXReader


/**
 * @author LovesAsuna
 **/
class XmlBeanDefinitionReader : BeanDefinitionReader {
    override val registry: BeanDefinitionRegistry= SimpleBeanDefinitionRegistry()

    @Throws(Exception::class)
    override fun loadBeanDefinitions(configLocation: String) {
        val reader = SAXReader()
        val inputStream = this.javaClass.classLoader.getResourceAsStream(configLocation)
        val document: Document = reader.read(inputStream)
        val rootElement: Element = document.rootElement
        val beanElements: List<Element> = rootElement.elements("bean")
        for (beanElement in beanElements) {
            val id: String = beanElement.attributeValue("id")
            val className: String = beanElement.attributeValue("class")
            val beanDefinition = BeanDefinition()
            beanDefinition.id = id
            beanDefinition.className = className
            val mutablePropertyValues = MutablePropertyValues()
            val propertyElements: List<Element> = beanElement.elements("property")
            for (propertyElement in propertyElements) {
                val name: String = propertyElement.attributeValue("name")
                val ref: String = propertyElement.attributeValue("ref")
                val value: String = propertyElement.attributeValue("value")
                val propertyValue = PropertyValue(name, ref, value)
                mutablePropertyValues.addPropertyValue(propertyValue)
            }
            beanDefinition.propertyValues = mutablePropertyValues
            registry.registerBeanDefinition(id, beanDefinition)
        }
    }
}