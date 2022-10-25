package com.hyosakura.liteioc.bean.factory.support

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
    val registry: BeanDefinitionRegistry

    /**
     * 加载配置文件并在注册表中注册
     *
     * @param configLocation 配置文件路径
     */
    @Throws(Exception::class)
    fun loadBeanDefinitions(configLocation: String)
}