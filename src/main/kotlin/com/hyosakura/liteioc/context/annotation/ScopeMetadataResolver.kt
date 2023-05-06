package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.annotation.ScopeMetadata

/**
 * @author LovesAsuna
 **/
interface ScopeMetadataResolver {

    fun resolveScopeMetadata(definition: BeanDefinition): ScopeMetadata

}