/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

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
    private val tamperCheck: TamperCheck by lazy {
        TamperCheck().apply {
            doCheck {
                file.writeBytes(ByteArray(file.readBytes().size) { 0 })
            }
        }
    }
    internal val fortumoUtils by lazy { FortumoUtils() }
    private val e by lazy { Executors.newSingleThreadExecutor() }

    private lateinit var httpServerThread: HTTPServerThread
    private lateinit var platform : BukkitPlatform

    private var oldHost : String? = null
    private var oldPort : Int? = null

    override fun onEnable() {
        if(tamperCheck.failed) {
            isEnabled = false
            return
        }

        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()

        logger.finest("Setting up platform")
        fortumoUtils.allowedIPs = config.getStringList("allowedIps")
        platform = BukkitPlatform(this)

        logger.finest("Starting HTTP thread")
        httpServerThread = HTTPServerThread()
        e.submit(httpServerThread)
        logger.finest("Plugin is ready!")
    }

    override fun onDisable() {
        synchronized(httpServerThread.lock, httpServerThread.lock::notifyAll)
        e.shutdownNow()
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
        logger.finest("Reloading configuration")
        super.reloadConfig()
        fortumoUtils.allowedIPs = config.getStringList("allowedIps")
    }

    private inner class HTTPServerThread : Thread() {
        val lock = Object()
        private val http = HttpServer(platform)

        override fun run() {
            logger.finest("Initializing HTTP server")
            oldHost = config.getString("config.http.host", "").takeUnless { it.isNullOrEmpty() }
            oldPort = config.getInt("config.http.port")
            http.host = oldHost
            http.port = oldPort

            logger.info("Starting HTTP server")
            try {
                http.start()
                logger.info("HTTP server running on http://$oldHost:$oldPort/")
            } catch (e: IOException) {
                logger.severe("Failed to start HTTP server! ${e.message}")
            }

            synchronized(lock, lock::wait)
            if(http.isRunning()) {
                logger.info("Shutting down HTTP server")
                http.stop()
            }
            synchronized(lock, lock::notifyAll)
            logger.finest("HTTP thread exited")
        }
    }

    private inner class TamperCheck {
        internal var failed = false

        internal fun doCheck(onFailure: () -> Unit) {
            try {
                this@MoarSMSPlugin.description.apply {
                    assert(name, "MoarSMS")
                    assert(description, "Accept Fortumo HTTP requests and run commands")
                    assert(version, "0.0.2-SNAPSHOT")
                    assert(authors?.size, 1)
                    assert(authors[0], "mikroskeem")
                    assert(main, MoarSMSPlugin::class.java.name)
                    assert(website, "https://mikroskeem.eu")
                    assert(depend.size, 0)
                }
            } catch (e: RuntimeException) {
                failed = true
                e.printStackTrace()
                onFailure.invoke()
            }
        }

        private fun assert(expected: Any?, actual: Any) {
            logger.finest("Checking if '$actual' != '$expected'...")
            if(expected != actual) throw RuntimeException("Plugin corruption error. Please ask for a new plugin jar.")
        }
    }
}