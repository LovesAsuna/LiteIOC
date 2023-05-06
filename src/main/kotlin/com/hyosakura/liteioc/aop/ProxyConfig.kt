package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
open class ProxyConfig {

    private var proxyTargetClass = false

    private var frozen = false

    open fun setProxyTargetClass(proxyTargetClass: Boolean) {
        this.proxyTargetClass = proxyTargetClass
    }

    open fun isProxyTargetClass(): Boolean {
        return proxyTargetClass
    }

    open fun setFrozen(frozen: Boolean) {
        this.frozen = frozen
    }

    open fun isFrozen(): Boolean {
        return this.frozen
    }

    open fun copyFrom(other: ProxyConfig) {
        this.proxyTargetClass = other.proxyTargetClass
        this.frozen = other.frozen
    }

}