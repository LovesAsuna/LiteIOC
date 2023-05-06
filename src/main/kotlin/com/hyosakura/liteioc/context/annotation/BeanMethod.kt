package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.core.type.MethodMetadata

/**
 * @author LovesAsuna
 **/
class BeanMethod(
    metadata: MethodMetadata,
    configurationClass: ConfigurationClass
) : ConfigurationMethod(metadata, configurationClass) {

    override fun validate() {
        if (getMetadata().isStatic()) {
            // static @Bean methods have no constraints to validate -> return immediately
            return
        }
        if (configurationClass.getMetadata().isAnnotated(Configuration::class.java.name)) {
            if (!getMetadata().isOverridable()) {
                // instance @Bean methods within @Configuration classes must be overridable to accommodate CGLIB
                logger.error(
                    String.format(
                        "@Bean method '%s' must not be private or final; change the method's modifiers to continue",
                        getMetadata().getMethodName()
                    )
                )
            }
        }
    }

    override fun equals(obj: Any?): Boolean {
        return this === obj || obj is BeanMethod && metadata == obj.metadata
    }

    override fun hashCode(): Int {
        return metadata.hashCode()
    }

    override fun toString(): String {
        return "BeanMethod: $metadata"
    }

}