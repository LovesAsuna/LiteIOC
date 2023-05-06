package com.hyosakura.liteioc.bean.factory.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.context.annotation.Scope
import com.hyosakura.liteioc.context.annotation.ScopeMetadataResolver
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil

/**
 * @author LovesAsuna
 **/
class AnnotationScopeMetadataResolver : ScopeMetadataResolver {

    private var defaultProxyMode: ScopedProxyMode? = null

    var scopeAnnotationType: Class<out Annotation> = Scope::class.java

    constructor() {
        defaultProxyMode = ScopedProxyMode.NO
    }

    constructor(defaultProxyMode: ScopedProxyMode) {
        this.defaultProxyMode = defaultProxyMode
    }

    constructor(scopeAnnotationType: Class<out Annotation>) {
        this.scopeAnnotationType = scopeAnnotationType
    }

    override fun resolveScopeMetadata(definition: BeanDefinition): ScopeMetadata {
        val metadata = ScopeMetadata()
        if (definition is AnnotatedBeanDefinition) {
            val attributes = AnnotationConfigUtil.attributesFor(definition.getMetadata(), scopeAnnotationType)
            if (attributes != null) {
                metadata.setScopeName(attributes.getString("value"))
                var proxyMode: ScopedProxyMode = attributes.getEnum("proxyMode")
                if (proxyMode == ScopedProxyMode.DEFAULT) {
                    proxyMode = defaultProxyMode!!
                }
                metadata.setScopedProxyMode(proxyMode)
            }
        }
        return metadata
    }

}