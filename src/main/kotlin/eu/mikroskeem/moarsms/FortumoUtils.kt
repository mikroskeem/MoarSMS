/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

package eu.mikroskeem.moarsms

/**
 * Common Fortumo utility
 *
 * @author Mark Vainomaa
 */
internal object FortumoUtils {
    internal lateinit var allowedIPs: List<String>

    fun checkSignature(params: Map<String, String>, secret: String): Boolean = StringBuilder().run {
        params.keys.toSortedSet().forEach { if(it != "sig") append("$it=${params[it]}") }
        append(secret)
        return@run Hash.md5(this.toString()) == params["sig"]
    }

    fun checkIP(ip: String?): Boolean = allowedIPs.contains(ip)
}