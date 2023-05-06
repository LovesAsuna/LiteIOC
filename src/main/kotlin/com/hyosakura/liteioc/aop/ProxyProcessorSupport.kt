package com.hyosakura.liteioc.aop

import com.hyosakura.liteioc.bean.factory.Aware
import com.hyosakura.liteioc.bean.factory.DisposableBean
import com.hyosakura.liteioc.bean.factory.InitializingBean
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import java.io.Closeable

/**
 * @author LovesAsuna
 **/
open class ProxyProcessorSupport : ProxyConfig() {

    var proxyClassLoader: ClassLoader? = ClassUtil.getDefaultClassLoader()

    fun evaluateProxyInterfaces(beanClass: Class<*>, proxyFactory: ProxyFactory) {
        val targetInterfaces = ClassUtil.getAllInterfacesForClass(beanClass, proxyClassLoader)
        var hasReasonableProxyInterface = false
        for (ifc in targetInterfaces) {
            if (!isConfigurationCallbackInterface(ifc) && !isInternalLanguageInterface(ifc) && ifc.methods.isNotEmpty()) {
                hasReasonableProxyInterface = true
                break
            }
        }
        if (hasReasonableProxyInterface) {
            for (ifc in targetInterfaces) {
                proxyFactory.addInterface(ifc)
            }
        } else {
            proxyFactory.setProxyTargetClass(true)
        }
    }

    open fun isConfigurationCallbackInterface(ifc: Class<*>): Boolean {
        return InitializingBean::class.java == ifc || DisposableBean::class.java == ifc || Closeable::class.java == ifc || AutoCloseable::class.java == ifc || ObjectUtil.containsElement(
            arrayOf(*ifc.interfaces),
            Aware::class.java
        )
    }

    open fun isInternalLanguageInterface(ifc: Class<*>): Boolean {
        return ifc.name == "groovy.lang.GroovyObject" ||
                ifc.name.endsWith(".cglib.proxy.Factory") ||
                ifc.name.endsWith(".bytebuddy.MockAccess")
    }

}