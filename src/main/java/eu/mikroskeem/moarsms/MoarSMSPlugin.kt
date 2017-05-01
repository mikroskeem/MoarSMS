package eu.mikroskeem.moarsms

import eu.mikroskeem.moarsms.http.HttpServer
import eu.mikroskeem.utils.text.MinecraftText
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap

/**
 * @author Mark Vainomaa
 */

class MoarSMSPlugin : JavaPlugin() {
    private val e = Executors.newSingleThreadExecutor()
    private lateinit var httpServerThread: HTTPServerThread
    lateinit var platform : BukkitPlatform
    val fortumoUtils = FortumoUtils()

    private var oldHost : String? = null
    private var oldPort : Int? = null

    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()

        logger.info("Setting up platform")
        platform = BukkitPlatform()

        logger.info("Starting HTTP thread")
        httpServerThread = HTTPServerThread()
        e.submit(httpServerThread)
        logger.info("Plugin is ready!")
    }

    override fun onDisable() {
        synchronized(httpServerThread.lock) {
            httpServerThread.lock.notifyAll()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name == "moarsms") {
            reloadConfig()
            if (config.getString("config.http.host") != oldHost ||
                    config.getInt("config.http.port") != oldPort) {
                /* Restart http server */
                sender.sendMessage("Restarting HTTP server on new host and port...")
                synchronized(httpServerThread.lock) {
                    httpServerThread.lock.notifyAll()
                    httpServerThread.lock.wait()
                }
                httpServerThread = HTTPServerThread()
                e.submit(httpServerThread)
                sender.sendMessage("Done")
            }
            sender.sendMessage("Configuration reloaded")
            return true
        }
        return false
    }

    private inner class HTTPServerThread : Thread() {
        val lock = java.lang.Object()
        private val http : HttpServer = HttpServer(this@MoarSMSPlugin)

        override fun run() {
            logger.info("Initializing HTTP server")
            oldHost = config.getString("config.http.host")
            oldPort = config.getInt("config.http.port")
            http.host = oldHost
            http.port = oldPort

            logger.info("Starting HTTP server")
            try {
                http.start()
                logger.info("HTTP server running")
            } catch (e: IOException) {
                logger.severe("Failed to start HTTP server!")
                return
            }

            synchronized(lock) {
                lock.wait()
            }
            logger.info("Shutting down HTTP server")
            http.stop()
            synchronized(lock) {
                lock.notifyAll()
            }
        }
    }

    inner class BukkitPlatform {
        fun getServiceSecrets(): Map<String, String> {
            val serviceSecrets = HashMap<String, String>()
            val sect = config.getConfigurationSection("services")
            sect.getValues(false).forEach { cServiceId, _ ->
                serviceSecrets.put(cServiceId, config.getString("services.$cServiceId.secret"))
            }
            return serviceSecrets
        }

        fun invokeService(serviceId: String, message: String): String {
            val username = message.trim { it <= ' ' }
            if (!MinecraftText.validateUsername(username)) {
                return this.getMessage("badmessage.badUsername")
            }
            val commands = config.getStringList("services.$serviceId.commands")
            commands.forEach { command ->
                server.scheduler.runTask(this@MoarSMSPlugin) {
                    server.dispatchCommand(server.consoleSender, command.replace("%user%", username))
                }
            }
            return this.getMessage("success.thanks")
        }

        fun allowTest(): Boolean {
            return config.getBoolean("config.allowTest", false)
        }

        fun getMessage(path: String): String {
            return config.getString("messages.$path", "")
        }
    }

    inner class FortumoUtils {
        private val allowedIPs = listOf(
            // TODO: use configuration instead of hardcoding
            "79.125.125.1",
            "79.125.5.205",
            "79.125.5.95",
            "54.72.6.126",
            "54.72.6.27",
            "54.72.6.17",
            "54.72.6.23",
            "127.0.0.1")

        /**
         * Check if signature matches

         * @param params Parameters to generate signature from
         * *
         * @param secret Secret key
         * *
         * @return Whether signature was correct
         */
        fun checkSignature(params: Map<String, String>, secret: String): Boolean {
            val toHash = StringBuilder()
            val keys = TreeSet(params.keys)
            keys.forEach { key ->
                if (key != "sig") toHash.append("$key=${params[key]}")
            }
            toHash.append(secret)
            return Hash.md5(toHash.toString()) == params["sig"]
        }

        /**
         * Check if IP address is in whitelist
         * @param ip IP to check
         * *
         * @return Whether IP was allowed or not
         */
        fun checkIP(ip: String?): Boolean {
            return allowedIPs.contains(ip)
            //return allowedIPs.stream().filter { i -> i == ip }.count() > 0
        }
    }
}