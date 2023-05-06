package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedBeanDefinition
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil
import com.hyosakura.liteioc.util.ClassUtil
import java.beans.Introspector
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 */
open class AnnotationBeanNameGenerator : BeanNameGenerator {

    private val metaAnnotationTypesCache: MutableMap<String, Set<String>> = ConcurrentHashMap<String, Set<String>>()
    override fun generateBeanName(definition: BeanDefinition, registry: BeanDefinitionRegistry): String {
        if (definition is AnnotatedBeanDefinition) {
            val beanName = determineBeanNameFromAnnotation(definition)
            if (!beanName.isNullOrEmpty()) {
                // Explicit bean name found.
                return beanName
            }
        }
        // Fallback: generate a unique default bean name.
        return buildDefaultBeanName(definition, registry)
    }

    fun determineBeanNameFromAnnotation(annotatedDef: AnnotatedBeanDefinition): String? {
        val amd = annotatedDef.getMetadata()
        val types = amd.getAnnotationTypes()
        var beanName: String? = null
        for (type in types) {
            val attributes = AnnotationConfigUtil.attributesFor(amd, type)
            if (attributes != null) {
                val metaTypes = metaAnnotationTypesCache.computeIfAbsent(type) { key ->
                    val result = amd.getMetaAnnotationTypes(key)
                    result.ifEmpty { emptySet() }
                }
                if (isStereotypeWithNameValue(type, metaTypes, attributes)) {
                    val value = attributes["value"]
                    if (value is String) {
                        if (value.isNotEmpty()) {
                            check(!(beanName != null && value != beanName)) {
                                "Stereotype annotations suggest inconsistent " + "component names: '" + beanName + "' versus '" + value + "'"
                            }
                            beanName = value
                        }
                    }
                }
            }
        }
        return beanName
    }

    fun isStereotypeWithNameValue(
        annotationType: String, metaAnnotationTypes: Set<String>, attributes: Map<String, Any>?
    ): Boolean {
        val isStereotype = annotationType == COMPONENT_ANNOTATION_CLASSNAME || metaAnnotationTypes.contains(
            COMPONENT_ANNOTATION_CLASSNAME
        ) || annotationType == "jakarta.annotation.ManagedBean" || annotationType == "jakarta.inject.Named"
        return isStereotype && attributes != null && attributes.containsKey("value")
    }

    fun buildDefaultBeanName(definition: BeanDefinition, registry: BeanDefinitionRegistry): String {
        return buildDefaultBeanName(definition)
    }

    open fun buildDefaultBeanName(definition: BeanDefinition): String {
        val beanClassName = definition.getBeanClassName()
        requireNotNull(beanClassName) { "No bean class name set" }
        val shortClassName = ClassUtil.getShortName(beanClassName)
        return Introspector.decapitalize(shortClassName)
    }

    companion object {
        val INSTANCE = AnnotationBeanNameGenerator()
        private const val COMPONENT_ANNOTATION_CLASSNAME = "org.springframework.stereotype.Component"
    }
}