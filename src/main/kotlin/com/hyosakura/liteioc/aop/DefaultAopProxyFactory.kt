package com.hyosakura.liteioc.aop

import java.lang.reflect.Proxy

/**
 * @author LovesAsuna
 **/
class DefaultAopProxyFactory : AopProxyFactory {

    override fun createAopProxy(config: HookSupport): AopProxy {
        return if (config.isProxyTargetClass() && hasNoUserSuppliedProxyInterfaces(config)) {
            val targetClass = config.getTargetClass()
                ?: throw AopConfigException(
                    "TargetSource cannot determine target class: " +
                            "Either an interface or a target is required for proxy creation."
                )
            if (targetClass.isInterface || Proxy.isProxyClass(targetClass)) {
                JdkDynamicAopProxy(config)
            } else {
                ByteBuddyAopProxy(config)
            }
        } else {
            JdkDynamicAopProxy(config)
        }
    }

    private fun hasNoUserSuppliedProxyInterfaces(config: HookSupport): Boolean {
        val ifcs = config.getProxiedInterfaces()
        return (ifcs.isEmpty() || (ifcs.size == 1 && LiteIocProxy::class.java.isAssignableFrom(ifcs[0])))
    }

}