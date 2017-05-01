package eu.mikroskeem.moarsms

import eu.mikroskeem.utils.text.MinecraftText
import fi.iki.elonen.NanoHTTPD
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
    private lateinit var platform : BukkitPlatform
    private val fortumoUtils = FortumoUtils()

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
        httpServerThread.interrupt()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name == "moarsms") {
            reloadConfig()
            if (config.getString("config.http.host") != oldHost ||
                    config.getInt("config.http.port") != oldPort) {
                /* Restart http server */
                sender.sendMessage("Restarting HTTP server on new host and port...")
                httpServerThread.interrupt()
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
        private var httpServer : HTTPServer? = null

        override fun run() {
            logger.info("Initializing HTTP server")
            oldHost = config.getString("config.http.host")
            oldPort = config.getInt("config.http.port")
            httpServer = HTTPServer(oldHost!!, oldPort!!)
            logger.info("Starting HTTP server")
            try {
                httpServer?.start()
            } catch (e: IOException) {
                logger.severe("Failed to start HTTP server!")
                return
            }

            while (true) {
                try {
                    synchronized(this) {
                        lock.wait()
                    }
                } catch (e: InterruptedException) {
                    break
                }
            }
            logger.info("Shutting down HTTP server")
            httpServer?.closeAllConnections()
            httpServer?.stop()
        }
    }

    private inner class BukkitPlatform {
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

    private inner class FortumoUtils {
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

    private inner class HTTPServer(host: String, port: Int) : NanoHTTPD(host, port) {
        override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            if (session.uri != "/") {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
            logger.info("Beep-boop, got request")
            val params = HashMap<String, String>()
            session.parameters.forEach { key, values -> params.put(key, values[0]) }

            var originIP: String? = session.headers["x-real-ip"]
            if (originIP != null) {
                val nginxProxy = session.headers["x-nginx-proxy"]
                if (nginxProxy == null || nginxProxy != "true") {
                    logger.info("X-Forwaded-For was present, but X-Nginx-Proxy not, bailing out!")
                    return sendFailResponse(platform.getMessage("badconfig.reverseProxy"))
                }
            } else {
                originIP = session.headers["remote-addr"]
            }

            /* Get basic headers */
            val useragent = session.headers["user-agent"]

            /* Get Fortumo headers */
            val sender = params["sender"]
            val keyword = params["keyword"]
            val message = params["message"]
            val serviceId = params["service_id"]
            val signature = params["sig"]
            val test = params["test"]

            /* Log them */
            logger.info("Origin IP: $originIP")
            logger.info("Useragent: $useragent")
            logger.info("Sender: $sender")
            logger.info("Keyword: $keyword")
            logger.info("Message: $message")
            logger.info("Test: $test")

            /* Check parameters */
            if (signature != null && serviceId != null && keyword != null && message != null) {
                logger.info("Got valid Fortumo request!")
                /* Check for IP */
                if (!fortumoUtils.checkIP(originIP)) {
                    logger.info("Request was from non-whitelisted IP '$originIP'!")
                    return sendFailResponse(platform.getMessage("validation.forbiddenIP"))
                }
                /* Check for service id */
                val checkSignature = platform.getServiceSecrets()[serviceId]
                if (checkSignature == null) {
                    logger.info("Service '$serviceId' was requested, but it is not defined!")
                    logger.info("Keyword was '$keyword', maybe this helps")
                    return sendFailResponse(platform.getMessage("validation.undefinedService"))
                }

                /* Check for signature */
                if (!fortumoUtils.checkSignature(params, checkSignature)) {
                    logger.info("Signature seems incorrect, correct is '$checkSignature', but $signature' was provided")
                    return sendFailResponse(platform.getMessage("validation.signatureIncorrect"))
                }

                /* Check if message it's test message and if they're allowed */
                if (test != null && test == "true" && !platform.allowTest()) {
                    logger.info("Test messages are disabled from config, bailing out")
                    return sendResponse(platform.getMessage("test.notallowed"))
                }

                logger.info("Message is valid")
                return sendResponse(platform.invokeService(serviceId, message))
            }

            logger.info("Sending fake response")
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html",
                    "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "    <head>\n" +
                    "        <meta charset=\"utf-8\">\n" +
                    "        <title>Nothing to see here</title>\n" +
                    "    </head>\n" +
                    "    <body>\n" +
                    "        <img src=\"https://i.imgflip.com/19e9u5.jpg\" />\n" +
                    "    </body>\n" +
                    "</html>"
            )
        }

        private fun sendFailResponse(resp: String): NanoHTTPD.Response {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, "text/plain", resp)
        }

        private fun sendResponse(resp: String): NanoHTTPD.Response {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", resp)
        }
    }
}