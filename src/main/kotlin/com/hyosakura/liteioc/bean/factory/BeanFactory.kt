package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
interface BeanFactory {
    /**
     * 返回对应name的Bean对象
     *
     * @param name Bean的名字
     * @return 对应的Bean对象
     */
    @Throws(Exception::class)
    fun getBean(name: String): Any?

    /**
     * 返回具有确切类型的对应name的Bean对象
     *
     * @param name Bean的名字
     * @param clazz 对应Bean对象的class
     * @return 对应的Bean对象
     */
    @Throws(Exception::class)
    fun <T> getBean(name: String, clazz: Class<T>): T?
}