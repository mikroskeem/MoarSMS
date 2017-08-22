/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

package eu.mikroskeem.moarsms

import java.util.logging.Logger

/**
 * @author Mark Vainomaa
 */
internal interface Platform {
    val fortumoUtils: FortumoUtils
    val serviceSecrets: Map<String, String>
    val allowTest: Boolean
    val logger: Logger
    val defaultResponse: String
    val nettyLoggingLevel: String
    fun getMessage(path: String): String
    fun invokeService(serviceId: String, message: String): String
}