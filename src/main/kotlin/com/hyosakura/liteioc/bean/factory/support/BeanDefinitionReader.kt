package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.core.io.Resource

/**
 * 解析配置文件
 *
 * @author LovesAsuna
 */
interface BeanDefinitionReader {
    /**
     * 获取注册表对象
     *
     * @return 注册表
     */
    fun getRegistry(): BeanDefinitionRegistry


    /**
     * 加载配置文件并在注册表中注册
     *
     * @param configLocation 配置文件路径
     * @return 找到的 BeanDefinition 数
     */
    @Throws(BeanDefinitionStoreException::class)
    fun loadBeanDefinitions(location: String): Int

    /**
     * 加载配置文件并在注册表中注册
     * @param locations 配置文件路径
     * @return 找到的 BeanDefinition 数
     */
    @Throws(BeanDefinitionStoreException::class)
    fun loadBeanDefinitions(vararg locations: String): Int

    /**
     * 从指定的Resource装入 BeanDefinition
     * @param resource 资源描述符
     * @return 找到的 BeanDefinition 数
     */
    @Throws(BeanDefinitionStoreException::class)
    fun loadBeanDefinitions(resource: Resource): Int

    /**
     * 从指定的Resource装入 BeanDefinition
     * @param resources 资源描述符
     * @return 找到的 BeanDefinition 数
     */
    @Throws(BeanDefinitionStoreException::class)
    fun loadBeanDefinitions(vararg resources: Resource): Int

}