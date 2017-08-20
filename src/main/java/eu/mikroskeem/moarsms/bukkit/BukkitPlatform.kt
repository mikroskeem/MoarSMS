package eu.mikroskeem.moarsms.bukkit

import eu.mikroskeem.moarsms.Platform

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

    override val fortumoUtils get() = plugin.fortumoUtils

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