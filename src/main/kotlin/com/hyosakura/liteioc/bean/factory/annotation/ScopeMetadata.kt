package com.hyosakura.liteioc.bean.factory.annotation

import com.hyosakura.liteioc.bean.BeanDefinition

/**
 * @author LovesAsuna
 **/
class ScopeMetadata {

    private var scopeName: String = BeanDefinition.SCOPE_SINGLETON

    private var scopedProxyMode: ScopedProxyMode = ScopedProxyMode.NO

    fun setScopeName(scopeName: String) {
        this.scopeName = scopeName
    }

    fun getScopeName(): String {
        return scopeName
    }

    fun setScopedProxyMode(scopedProxyMode: ScopedProxyMode) {
        this.scopedProxyMode = scopedProxyMode
    }

    fun getScopedProxyMode(): ScopedProxyMode {
        return scopedProxyMode
    }

}
