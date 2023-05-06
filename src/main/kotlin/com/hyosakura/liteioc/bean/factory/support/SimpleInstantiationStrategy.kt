package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanInstantiationException
import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConfigurableBeanFactory
import com.hyosakura.liteioc.util.BeanUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import org.jetbrains.annotations.Nullable
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
open class SimpleInstantiationStrategy : InstantiationStrategy {

    companion object {

        private val currentlyInvokedFactoryMethod = ThreadLocal<Method>()

    }

    fun getCurrentlyInvokedFactoryMethod(): Method? {
        return currentlyInvokedFactoryMethod.get()
    }

    override fun instantiate(bd: RootBeanDefinition, beanName: String?, owner: BeanFactory): Any {
        if (!bd.hasMethodOverrides()) {
            var constructorToUse: Constructor<*>
            synchronized(bd.constructorArgumentLock) {
                val clazz: Class<*> = bd.getBeanClass()
                if (clazz.isInterface) {
                    throw BeanInstantiationException(clazz, "Specified class is an interface")
                }
                try {
                    constructorToUse = clazz.getDeclaredConstructor()
                } catch (ex: Throwable) {
                    throw BeanInstantiationException(clazz, "No default constructor found", ex)
                }
            }
            return BeanUtil.instantiateClass(constructorToUse)
        } else {
            // Must generate CGLIB subclass.
            return instantiateWithMethodInjection(bd, beanName, owner)
        }
    }

    override fun instantiate(
        bd: RootBeanDefinition, beanName: String?, owner: BeanFactory,
        factoryBean: Any?, factoryMethod: Method, vararg args: Any?
    ): Any {
        try {
            ReflectionUtil.makeAccessible(factoryMethod)
            val priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get()
            try {
                currentlyInvokedFactoryMethod.set(factoryMethod)
                var result = factoryMethod.invoke(factoryBean, *args)
                if (result == null) {
                    result = NullBean()
                }
                return result
            } finally {
                if (priorInvokedFactoryMethod != null) {
                    currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod)
                } else {
                    currentlyInvokedFactoryMethod.remove()
                }
            }
        } catch (ex: IllegalArgumentException) {
            throw BeanInstantiationException(
                factoryMethod,
                "Illegal arguments to factory method '" + factoryMethod.name + "'; " +
                        "args: " + args.joinToString(","), ex
            )
        } catch (ex: IllegalAccessException) {
            throw BeanInstantiationException(
                factoryMethod,
                "Cannot access factory method '" + factoryMethod.name + "'; is it public?", ex
            )
        } catch (ex: InvocationTargetException) {
            var msg = "Factory method '" + factoryMethod.name + "' threw exception"
            if (((bd.getFactoryBeanName() != null) && owner is ConfigurableBeanFactory &&
                        owner.isCurrentlyInCreation(bd.getFactoryBeanName()!!))
            ) {
                msg =
                    (("Circular reference involving containing bean '" + bd.getFactoryBeanName()).toString() + "' - consider " +
                            "declaring the factory method as static for independence from its containing instance. " + msg)
            }
            throw BeanInstantiationException(factoryMethod, msg, ex.targetException)
        }
    }

    open fun instantiateWithMethodInjection(
        bd: RootBeanDefinition, beanName: String?, owner: BeanFactory
    ): Any {
        throw UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy")
    }

    override fun instantiate(
        bd: RootBeanDefinition, beanName: String?, owner: BeanFactory, ctor: Constructor<*>, vararg args: Any?
    ): Any {
        return if (!bd.hasMethodOverrides()) {
            BeanUtil.instantiateClass(ctor, *args)
        } else {
            instantiateWithMethodInjection(bd, beanName, owner, ctor, *args)
        }
    }

    open fun instantiateWithMethodInjection(
        bd: RootBeanDefinition, beanName: String?, owner: BeanFactory, ctor: Constructor<*>?, vararg args: Any?
    ): Any {
        throw UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy")
    }

}