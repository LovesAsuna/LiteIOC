package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedBeanDefinition
import com.hyosakura.liteioc.context.annotation.DependsOn
import com.hyosakura.liteioc.context.annotation.Description
import com.hyosakura.liteioc.context.annotation.Lazy
import com.hyosakura.liteioc.core.type.AnnotatedTypeMetadata
import com.hyosakura.liteioc.core.type.AnnotationMetadata
import java.util.*

object AnnotationConfigUtil {

    const val CONFIGURATION_BEAN_NAME_GENERATOR =
        "com.hyosakura.liteioc.context.annotation.internalConfigurationBeanNameGenerator"

    fun attributesFor(metadata: AnnotatedTypeMetadata, annotationClass: Class<*>): AnnotationAttributes? {
        return attributesFor(metadata, annotationClass.name)
    }

    fun attributesFor(metadata: AnnotatedTypeMetadata, annotationClassName: String): AnnotationAttributes? {
        return AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(annotationClassName))
    }

    fun processCommonDefinitionAnnotations(abd: AnnotatedBeanDefinition) {
        processCommonDefinitionAnnotations(abd, abd.getMetadata())
    }

    fun processCommonDefinitionAnnotations(abd: AnnotatedBeanDefinition, metadata: AnnotatedTypeMetadata) {
        var lazy = attributesFor(metadata, Lazy::class.java)
        if (lazy != null) {
            abd.setLazyInit(lazy.getBoolean("value"))
        } else if (abd.getMetadata() != metadata) {
            lazy = attributesFor(abd.getMetadata(), Lazy::class.java)
            if (lazy != null) {
                abd.setLazyInit(lazy.getBoolean("value"))
            }
        }
        val dependsOn = attributesFor(metadata, DependsOn::class.java)
        if (dependsOn != null) {
            abd.setDependsOn(*dependsOn.getStringArray("value"))
        }
        val description = attributesFor(metadata, Description::class.java)
        if (description != null) {
            abd.setDescription(description.getString("value"))
        }
    }

    fun attributesForRepeatable(
        metadata: AnnotationMetadata,
        containerClass: Class<*>,
        annotationClass: Class<*>
    ): Set<AnnotationAttributes> {
        return attributesForRepeatable(
            metadata,
            containerClass.name,
            annotationClass.name
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun attributesForRepeatable(
        metadata: AnnotationMetadata, containerClassName: String, annotationClassName: String
    ): Set<AnnotationAttributes> {
        val result = LinkedHashSet<AnnotationAttributes>()

        // Direct annotation present?
        addAttributesIfNotNull(result, metadata.getAnnotationAttributes(annotationClassName))

        // Container annotation present?
        val container = metadata.getAnnotationAttributes(containerClassName)
        if (container != null && container.containsKey("value")) {
            for (containedAttributes in (container["value"] as Array<Map<String, Any>>)) {
                addAttributesIfNotNull(result, containedAttributes)
            }
        }

        // Return merged result
        return Collections.unmodifiableSet(result)
    }

    private fun addAttributesIfNotNull(
        result: MutableSet<AnnotationAttributes>, attributes: Map<String, Any>?
    ) {
        if (attributes != null) {
            result.add(AnnotationAttributes.fromMap(attributes)!!)
        }
    }

}