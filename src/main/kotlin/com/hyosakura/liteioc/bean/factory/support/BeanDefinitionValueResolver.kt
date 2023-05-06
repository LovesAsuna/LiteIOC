package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.TypeConverter
import java.util.*

/**
 * @author LovesAsuna
 **/
class BeanDefinitionValueResolver {

    private val beanFactory: AbstractAutowireCapableBeanFactory

    private val beanName: String

    private val beanDefinition: BeanDefinition

    private val typeConverter: TypeConverter

    constructor(
        beanFactory: AbstractAutowireCapableBeanFactory,
        beanName: String,
        beanDefinition: BeanDefinition,
        typeConverter: TypeConverter
    ) {
        this.beanFactory = beanFactory
        this.beanName = beanName
        this.beanDefinition = beanDefinition
        this.typeConverter = typeConverter
    }

    fun resolveValueIfNecessary(argName: Any, value: Any?): Any? {
        return evaluate(value)
    }

    @Suppress("UNCHECKED_CAST")
    open fun evaluate(value: Any?): Any? {
        return (value as? String?)?.let { doEvaluate(it) }
            ?: if (value is Array<*> && value.isArrayOf<String>()) {
                val values = value as Array<String>
                var actuallyResolved = false
                val resolvedValues = arrayOfNulls<Any>(value.size)
                for (i in value.indices) {
                    val originalValue = value[i]
                    val resolvedValue = doEvaluate(originalValue)
                    if (resolvedValue != originalValue) {
                        actuallyResolved = true
                    }
                    resolvedValues[i] = resolvedValue
                }
                if (actuallyResolved) resolvedValues else value
            } else {
                value
            }
    }

    private fun doEvaluate(value: String?): Any? {
        TODO("Not implemented")
    }

}