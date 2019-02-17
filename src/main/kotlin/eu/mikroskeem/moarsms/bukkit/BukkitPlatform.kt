/*
 * This file is part of project MoarSMS, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Mark Vainomaa <mikroskeem@mikroskeem.eu>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
        if(username.length !in 1..32 || !username.matches(usernamePattern)) {
            return this.getMessage("badmessage.badUsername")
        }

        val commands = ArrayList(plugin.config.getStringList("services.$serviceId.commands"))
        plugin.server.scheduler.runTask(plugin) {
            commands.map { it.replace("%user%", username) }.forEach {
                plugin.server.dispatchCommand(plugin.server.consoleSender, it)
            }
        }

        return this.getMessage("success.thanks")
    }
}