package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.core.io.DescriptiveResource
import com.hyosakura.liteioc.core.io.Resource
import com.hyosakura.liteioc.core.type.AnnotationMetadata
import com.hyosakura.liteioc.util.ClassUtil

/**
 * @author LovesAsuna
 **/
class ConfigurationClass {

    private var metadata: AnnotationMetadata

    private var resource: Resource

    private var beanName: String? = null

    private val importedBy: MutableSet<ConfigurationClass?> = LinkedHashSet(1)

    private val beanMethods = LinkedHashSet<BeanMethod>()

    constructor(clazz: Class<*>, importedBy: ConfigurationClass?) {
        metadata = AnnotationMetadata.introspect(clazz)
        resource = DescriptiveResource(clazz.name)
        this.importedBy.add(importedBy)
    }

    constructor(metadata: AnnotationMetadata, beanName: String) {
        this.metadata = metadata
        this.resource = DescriptiveResource(metadata.getClassName())
        this.beanName = beanName
    }

    constructor(clazz: Class<*>, beanName: String) {
        this.metadata = AnnotationMetadata.introspect(clazz)
        this.resource = DescriptiveResource(metadata.getClassName())
        this.beanName = beanName
    }

    fun isImported(): Boolean {
        return importedBy.isNotEmpty()
    }

    fun mergeImportedBy(otherConfigClass: ConfigurationClass) {
        importedBy.addAll(otherConfigClass.importedBy)
    }

    fun getResource(): Resource {
        return resource
    }

    fun getMetadata(): AnnotationMetadata {
        return metadata
    }

    fun getBeanName(): String? {
        return beanName
    }

    fun getBeanMethods(): Set<BeanMethod> {
        return beanMethods
    }

    fun addBeanMethod(method: BeanMethod) {
        beanMethods.add(method)
    }

    fun getSimpleName(): String {
        return ClassUtil.getShortName(getMetadata().getClassName())
    }

    fun setBeanName(beanName: String) {
        this.beanName = beanName
    }

}