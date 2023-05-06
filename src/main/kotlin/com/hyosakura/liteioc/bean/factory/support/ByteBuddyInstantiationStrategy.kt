package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanInstantiationException
import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.bean.factory.annotation.Lookup
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.util.BeanUtil
import net.bytebuddy.ByteBuddy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
class ByteBuddyInstantiationStrategy : SimpleInstantiationStrategy() {

    override fun instantiateWithMethodInjection(bd: RootBeanDefinition, beanName: String?, owner: BeanFactory): Any {
        return instantiateWithMethodInjection(bd, beanName, owner, null)
    }

    override fun instantiateWithMethodInjection(
        bd: RootBeanDefinition, beanName: String?, owner: BeanFactory, ctor: Constructor<*>?, vararg args: Any?
    ): Any {

        // Must generate CGLIB subclass...
        return ByteBuddySubClassCreator(
            bd, owner
        ).instantiate(ctor, *args)
    }

    private class ByteBuddySubClassCreator(val beanDefinition: RootBeanDefinition, val owner: BeanFactory) {

        fun instantiate(ctor: Constructor<*>?, vararg args: Any?): Any {
            val subclass = createEnhancedSubclass(beanDefinition)
            val instance: Any = if (ctor == null) {
                BeanUtil.instantiateClass(subclass)
            } else {
                try {
                    val enhancedSubclassConstructor = subclass.getConstructor(*ctor.parameterTypes)
                    enhancedSubclassConstructor.newInstance(*args)
                } catch (ex: Exception) {
                    throw BeanInstantiationException(
                        beanDefinition.getBeanClass(),
                        "Failed to invoke constructor for CGLIB enhanced subclass [" + subclass.name + "]",
                        ex
                    )
                }
            }
            return instance
        }

        private fun createEnhancedSubclass(beanDefinition: RootBeanDefinition): Class<*> {
            val enhancer = ByteBuddy().subclass(beanDefinition.getBeanClass())
            return enhancer.method(ElementMatchers.isAnnotatedWith(Lookup::class.java))
                .intercept(InvocationHandlerAdapter.of(LookupOverrideMethodInvocationHandler(beanDefinition, owner)))
                .make().load(this.javaClass.classLoader).loaded
        }

    }

    private class LookupOverrideMethodInvocationHandler(
        val beanDefinition: RootBeanDefinition, val owner: BeanFactory
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any? {
            val lo = beanDefinition.getMethodOverrides().getOverride(method) as LookupOverride?
            requireNotNull(lo) { "LookupOverride not found" }
            val argsToUse = if (args.isNotEmpty()) args else null  // if no-arg, don't insist on args at all
            if (!lo.getBeanName().isNullOrEmpty()) {
                val bean = if (argsToUse != null) {
                    this.owner.getBean(lo.getBeanName()!!, argsToUse)
                } else {
                    this.owner.getBean(lo.getBeanName()!!)
                    // Detect package-protected NullBean instance through equals(null) check
                }
                return if (bean.equals(null)) {
                    null
                } else {
                    bean
                }
            } else {
                // Find target bean matching the (potentially generic) method return type
                val genericReturnType = ResolvableType.forMethodReturnType(method)
                return if (argsToUse != null) {
                    this.owner.getBeanProvider<Any>(genericReturnType).getObject(argsToUse)
                } else {
                    this.owner.getBeanProvider<Any>(genericReturnType).getObject()
                }
            }
        }

    }

}