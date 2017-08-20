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
    fun getMessage(path: String): String
    fun invokeService(serviceId: String, message: String): String
}