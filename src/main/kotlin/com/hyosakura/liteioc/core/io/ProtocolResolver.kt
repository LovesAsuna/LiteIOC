package com.hyosakura.liteioc.core.io

/**
 * @author LovesAsuna
 **/
fun interface ProtocolResolver {

    fun resolve(location: String, resourceLoader: ResourceLoader): Resource?

}