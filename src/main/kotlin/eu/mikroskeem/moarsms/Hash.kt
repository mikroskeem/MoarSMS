/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

package eu.mikroskeem.moarsms

import java.security.MessageDigest
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * @author Mark Vainomaa
 */
private const val zero_x_ff: Byte = (0xFF).toByte()
private const val zero_x_hundred: Byte = (0x100).toByte()

internal object Hash {
    private val md = MessageDigest.getInstance("MD5")

    internal fun md5(text: String): String = StringBuilder().run {
        md.digest(text.toByteArray()).forEach {
            append(Integer.toHexString(((it and zero_x_ff) or zero_x_hundred).toInt()).subSequence(1, 3))
        }
        toString()
    }
}