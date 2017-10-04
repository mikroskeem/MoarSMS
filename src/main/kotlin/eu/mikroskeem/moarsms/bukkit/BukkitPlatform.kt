/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

package eu.mikroskeem.moarsms.bukkit

import eu.mikroskeem.moarsms.Platform
import io.netty.handler.logging.LogLevel
import java.io.ByteArrayOutputStream

/**
 * @author Mark Vainomaa
 */
internal class BukkitPlatform(private val plugin: MoarSMSPlugin): Platform {
    private val usernamePattern = "^[A-Za-z0-9_]+$".toRegex()

    override val serviceSecrets get() = HashMap<String, String>().apply {
        plugin.config.getConfigurationSection("services").getValues(false).forEach { cServiceId, _ ->
            put(cServiceId, plugin.config.getString("services.$cServiceId.secret"))
        }
    }

    override val allowTest get() = plugin.config.getBoolean("config.allowTest", false)

    override val logger get() = plugin.logger!!

    override val defaultResponse by lazy {
        String(ByteArrayOutputStream().apply {
            use { plugin.getResource("assets/moarsms/index.html").copyTo(this) }
        }.toByteArray())
    }

    override val nettyLoggingLevel: String get() {
        val configLevel = plugin.config.getString("config.http.loggingLevel", "DEBUG")
        // Validate
        return LogLevel.values().find { it.name == configLevel }?.name ?: "DEBUG"
    }

    override fun getMessage(path: String): String = plugin.config.getString("messages.$path", "")

    override fun invokeService(serviceId: String, message: String): String {
        val username = message.trim { it <= ' ' }
        if(username.length !in 3..16 || !username.matches(usernamePattern)) {
            return this.getMessage("badmessage.badUsername")
        }
        plugin.config.getStringList("services.$serviceId.commands").forEach {
            plugin.server.scheduler.runTask(plugin) {
                plugin.server.dispatchCommand(plugin.server.consoleSender, it.replace("%user%", username))
            }
        }
        return this.getMessage("success.thanks")
    }
}