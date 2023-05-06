package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedBeanDefinition
import com.hyosakura.liteioc.bean.factory.support.AbstractBeanDefinition
import com.hyosakura.liteioc.core.type.AnnotationMetadata
import com.hyosakura.liteioc.core.type.StandardAnnotationMetadata
import org.slf4j.LoggerFactory

object ConfigurationClassUtil {

    private val candidateIndicators: MutableSet<String> = HashSet(8)

    private val logger = LoggerFactory.getLogger(ConfigurationClassUtil::class.java)

    private const val CONFIGURATION_CLASS_LITE = "lite"

    private val CONFIGURATION_CLASS_ATTRIBUTE = "${ConfigurationClassProcessor::class.java}.configurationClass"

    init {

        candidateIndicators.add(Component::class.java.name)
        candidateIndicators.add(ComponentScan::class.java.name)

    }

    fun checkConfigurationClassCandidate(
        beanDef: BeanDefinition
    ): Boolean {
        val className = beanDef.getBeanClassName() ?: return false
        val metadata: AnnotationMetadata =
            if (beanDef is AnnotatedBeanDefinition && className == beanDef.getMetadata().getClassName()) {
                // Can reuse the pre-parsed metadata from the given BeanDefinition...
                beanDef.getMetadata()
            } else if (beanDef is AbstractBeanDefinition && beanDef.hasBeanClass()) {
                // Check already loaded Class if present...
                // since we possibly can't even load the class file for this Class.
                val beanClass: Class<*> = beanDef.getBeanClass()
                AnnotationMetadata.introspect(beanClass)
            } else {
                try {
                    StandardAnnotationMetadata(Class.forName(className))
                } catch (ex: Exception) {
                    if (logger.isDebugEnabled) {
                        logger.debug(
                            "Could not find class file for introspecting configuration annotations: " +
                                    className, ex
                        )
                    }
                    return false
                }
            }
        val config = metadata.getAnnotationAttributes(Configuration::class.java.name)
        if (config != null || isConfigurationCandidate(metadata)
        ) {
            beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE)
        } else {
            return false
        }

        return true
    }

    fun isConfigurationCandidate(metadata: AnnotationMetadata): Boolean {
        // Do not consider an interface or an annotation...
        if (metadata.isInterface()) {
            return false
        }

        // Any of the typical annotations found?
        for (indicator in candidateIndicators) {
            if (metadata.isAnnotated(indicator)) {
                return true
            }
        }

        // Finally, let's look for @Bean methods...
        return hasBeanMethods(metadata)
    }

    fun hasBeanMethods(metadata: AnnotationMetadata): Boolean {
        return try {
            metadata.hasAnnotatedMethods(Bean::class.java.name)
        } catch (ex: Throwable) {
            if (logger.isDebugEnabled) {
                logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex)
            }
            false
        }
    }

}