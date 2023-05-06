package com.hyosakura.liteioc.core.io

/**
 * @author LovesAsuna
 **/
interface ResourceLoader {

    fun getResource(location: String): Resource

    fun getClassLoader(): ClassLoader?

}