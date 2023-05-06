package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.TypeConverter
import com.hyosakura.liteioc.bean.factory.BeanFactory

/**
 * @author LovesAsuna
 **/
interface AutowireCapableBeanFactory : BeanFactory {

    companion object {

        const val AUTOWIRE_NO = 0

        const val AUTOWIRE_BY_NAME = 1

        const val AUTOWIRE_BY_TYPE = 2

        const val AUTOWIRE_CONSTRUCTOR = 3

        @Deprecated("If you are using mixed autowiring strategies, prefer annotation-based autowiring for clearer demarcation of autowiring needs.")
        const val AUTOWIRE_AUTODETECT = 4

    }

    @Throws(BeansException::class)
    fun resolveDependency(
        descriptor: DependencyDescriptor,
        requestingBeanName: String?,
        autowiredBeanNames: MutableSet<String>?,
        typeConverter: TypeConverter?
    ): Any?

}