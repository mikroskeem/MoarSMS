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
 * The MoarSMS plugin
 *
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
    private var e = Executors.newSingleThreadExecutor()

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
        FortumoUtils.allowedIPs = config.getStringList("allowedIps")
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
                    try {
                        httpServerThread.lock.wait(5000)
                    } catch (e: InterruptedException) {
                        logger.warning("Forcefully killed thread executor with ${this@MoarSMSPlugin.e.shutdownNow().size} tasks")
                        this@MoarSMSPlugin.e = Executors.newSingleThreadExecutor()
                        e.printStackTrace()
                        sender.sendMessage("Failed to stop HTTP old server! See logs. ${e.message}")
                    }
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
        FortumoUtils.allowedIPs = config.getStringList("allowedIps")
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
                    name mustBe "MoarSMS"
                    description mustBe "Accept Fortumo HTTP requests and run commands"
                    version mustBe "0.0.3-SNAPSHOT"
                    authors?.size mustBe 1
                    authors[0] mustBe "mikroskeem"
                    main mustBe MoarSMSPlugin::class.java.name
                    website mustBe "https://mikroskeem.eu"
                    depend.size mustBe 0
                }
            } catch (e: RuntimeException) {
                failed = true
                e.printStackTrace()
                onFailure.invoke()
            }
        }

        private infix fun Any?.mustBe(actual: Any) {
            logger.finest("Checking if '$actual' != '$this'...")
            if(this != actual) throw RuntimeException("Plugin corruption error. Please ask for a new plugin jar.")
        }
    }
}