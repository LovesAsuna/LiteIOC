package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.BeansException
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * @author LovesAsuna
 **/
interface ObjectProvider<T> : ObjectFactory<T> {

    @Throws(BeansException::class)
    fun getObject(vararg args: Any?): T

    @Throws(BeansException::class)
    fun getIfAvailable(): T?

    @Throws(BeansException::class)
    fun getIfAvailable(defaultSupplier: Supplier<T>): T {
        val dependency = getIfAvailable()
        return dependency ?: defaultSupplier.get()
    }

    @Throws(BeansException::class)
    fun ifAvailable(dependencyConsumer: Consumer<T>) {
        val dependency = getIfAvailable()
        if (dependency != null) {
            dependencyConsumer.accept(dependency)
        }
    }

    @Throws(BeansException::class)
    fun getIfUnique(): T?

    @Throws(BeansException::class)
    fun getIfUnique(defaultSupplier: Supplier<T>): T {
        val dependency = getIfUnique()
        return dependency ?: defaultSupplier.get()
    }

    @Throws(BeansException::class)
    fun ifUnique(dependencyConsumer: Consumer<T>) {
        val dependency = getIfUnique()
        if (dependency != null) {
            dependencyConsumer.accept(dependency)
        }
    }

}