package com.hyosakura.liteioc.core

/**
 * @author LovesAsuna
 **/
interface Ordered {

    companion object {

        const val HIGHEST_PRECEDENCE = Int.MIN_VALUE

        const val LOWEST_PRECEDENCE = Int.MAX_VALUE

    }

    fun getOrder(): Int

}