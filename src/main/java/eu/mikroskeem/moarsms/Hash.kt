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
    internal fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val array = md.digest(text.toByteArray())
        val sb = StringBuilder()

        array.forEach {
            sb.append(Integer.toHexString(((it and zero_x_ff) or zero_x_hundred).toInt()).subSequence(1, 3))
        }
        return sb.toString()
    }
}