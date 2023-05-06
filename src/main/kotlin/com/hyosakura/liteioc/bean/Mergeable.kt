package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
interface Mergeable {

    fun isMergeEnabled(): Boolean

    fun merge(parent: Any?): Any

}