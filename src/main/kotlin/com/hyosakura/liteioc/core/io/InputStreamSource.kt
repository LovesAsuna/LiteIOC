package com.hyosakura.liteioc.core.io

import java.io.IOException
import java.io.InputStream

/**
 * @author LovesAsuna
 **/
interface InputStreamSource {

    @Throws(IOException::class)
    fun getInputStream(): InputStream

}