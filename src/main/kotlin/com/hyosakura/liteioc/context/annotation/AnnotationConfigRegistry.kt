package com.hyosakura.liteioc.context.annotation

/**
 * @author LovesAsuna
 **/
interface AnnotationConfigRegistry {

    /**
     * 注册一个或多个要处理的组件类
     *
     * 对register的调用是幂等的
     * 多次添加同一个组件类不会产生额外的效果
     * @param componentClasses 一个或多个组件类,
     * 例如 [@Configuration][Configuration] 类
     */
    fun register(vararg componentClasses: Class<*>)

    /**
     * 用给定的包路径启动一次扫描包过程
     * @param basePackages 需要扫描组件类的扫描包路径the packages to scan for component classes
     */
    fun scan(vararg basePackages: String)

}