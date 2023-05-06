package com.hyosakura.liteioc.core

/**
 * @author LovesAsuna
 **/
class DefaultParameterNameDiscoverer : PrioritizedParameterNameDiscoverer() {

    init {
        if (KotlinDetector.isKotlinReflectPresent) {
            addDiscoverer(KotlinReflectionParameterNameDiscoverer())
        }
        addDiscoverer(StandardReflectionParameterNameDiscoverer())
    }

}