package eu.mikroskeem.moarsms.bukkit

import eu.mikroskeem.moarsms.FortumoUtils
import eu.mikroskeem.moarsms.http.HttpServer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.util.concurrent.Executors

/**
 * @author Mark Vainomaa
 */

class MoarSMSPlugin : JavaPlugin() {
    private val e = Executors.newSingleThreadExecutor()
    private lateinit var httpServerThread: HTTPServerThread
    private lateinit var platform : BukkitPlatform

    internal val fortumoUtils = FortumoUtils()

    private var oldHost : String? = null
    private var oldPort : Int? = null

    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()

        logger.info("Setting up platform")
        fortumoUtils.allowedIPs = config.getStringList("allowedIps")
        platform = BukkitPlatform(this)

        logger.info("Starting HTTP thread")
        httpServerThread = HTTPServerThread()
        e.submit(httpServerThread)
        logger.info("Plugin is ready!")
    }

    override fun onDisable() {
        synchronized(httpServerThread.lock, httpServerThread.lock::notifyAll)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if(command.name == "moarsms") {
            reloadConfig()
            if(config.getString("config.http.host") != oldHost || config.getInt("config.http.port") != oldPort) {
                /* Restart http server */
                sender.sendMessage("Restarting HTTP server on new host and port...")
                synchronized(httpServerThread.lock) {
                    httpServerThread.lock.notifyAll()
                    httpServerThread.lock.wait() // TODO: timeout?
                }
                e.submit(HTTPServerThread().apply(this::httpServerThread::set))
                sender.sendMessage("Done")
            }
            sender.sendMessage("Configuration reloaded")
            return true
        }
        return false
    }

    override fun reloadConfig() {
        super.reloadConfig()
        fortumoUtils.allowedIPs = config.getStringList("allowedIps")
    }

    private inner class HTTPServerThread : Thread() {
        val lock = Object()
        private val http = HttpServer(platform)

        override fun run() {
            logger.info("Initializing HTTP server")
            oldHost = config.getString("config.http.host", "").takeUnless { it.isNullOrEmpty() }
            oldPort = config.getInt("config.http.port")
            http.host = oldHost
            http.port = oldPort

            logger.info("Starting HTTP server")
            try {
                http.start()
                logger.info("HTTP server running")
            } catch (e: IOException) {
                logger.severe("Failed to start HTTP server!")
            }

            synchronized(lock, lock::wait)
            if(http.isRunning()) {
                logger.info("Shutting down HTTP server")
                http.stop()
            }
            synchronized(lock, lock::notifyAll)
        }
    }
}