package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.core.type.MethodMetadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author LovesAsuna
 **/
abstract class ConfigurationMethod(
    @JvmField
    val metadata: MethodMetadata,
    @JvmField
    val configurationClass: ConfigurationClass
) {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    open fun getMetadata(): MethodMetadata {
        return this.metadata
    }

    open fun getConfigurationClass(): ConfigurationClass {
        return this.configurationClass
    }

    open fun validate() {}

    override fun toString(): String {
        return java.lang.String.format(
            "[%s:name=%s,declaringClass=%s]",
            javaClass.simpleName, getMetadata().getMethodName(), getMetadata().getDeclaringClassName()
        )
    }

}