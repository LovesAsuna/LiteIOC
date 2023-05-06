package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.annotation.ScopedProxyMode
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import com.hyosakura.liteioc.core.annotation.AnnotationAttributes
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.io.ResourceLoader
import com.hyosakura.liteioc.util.BeanUtil
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.StringUtil
import java.util.*

/**
 * @author LovesAsuna
 **/
class ComponentScanAnnotationParser(
    private val environment: Environment,
    private val resourceLoader: ResourceLoader,
    private val beanNameGenerator: BeanNameGenerator,
    private val registry: BeanDefinitionRegistry,
) {

    fun parse(componentScan: AnnotationAttributes, declaringClass: String): Set<BeanDefinitionHolder> {
        val scanner = ClassPathBeanDefinitionScanner(
            this.registry,
            this.environment,
        )
        val generatorClass: Class<out BeanNameGenerator> = componentScan.getClass("nameGenerator")
        val useInheritedGenerator = BeanNameGenerator::class.java == generatorClass
        // 设置BeanNameGenerator
        scanner.setBeanNameGenerator(
            if (useInheritedGenerator) this.beanNameGenerator else BeanUtil.instantiateClass(
                generatorClass
            )
        )
        val scopedProxyMode: ScopedProxyMode = componentScan.getEnum("scopedProxy")
        // 设置scopedProxyMode
        if (scopedProxyMode != ScopedProxyMode.DEFAULT) {
            scanner.setScopedProxyMode(scopedProxyMode)
        } else {
            val resolverClass: Class<out ScopeMetadataResolver> = componentScan.getClass("scopeResolver")
            scanner.setScopeMetadataResolver(BeanUtil.instantiateClass(resolverClass))
        }

        val lazyInit: Boolean = componentScan.getBoolean("lazyInit")
        if (lazyInit) {
            scanner.getBeanDefinitionDefaults().lazyInit = true
        }
        val basePackages: MutableSet<String> = LinkedHashSet()
        val basePackagesArray: Array<String> = componentScan.getStringArray("basePackages")
        for (pkg in basePackagesArray) {
            val tokenized = pkg.split(",")
            Collections.addAll(basePackages, *tokenized.toTypedArray())
        }
        for (clazz in componentScan.getClassArray("basePackageClasses")) {
            basePackages.add(ClassUtil.getPackageName(clazz))
        }
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtil.getPackageName(declaringClass))
        }
        // 扫描包（重点）
        return scanner.doScan(*StringUtil.toStringArray(basePackages))
    }

}