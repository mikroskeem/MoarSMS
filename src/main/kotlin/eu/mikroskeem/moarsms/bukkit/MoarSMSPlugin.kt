/*
 * This file is part of project MoarSMS, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017-2019 Mark Vainomaa <mikroskeem@mikroskeem.eu>
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

import eu.mikroskeem.moarsms.allowedFortumoIPs
import eu.mikroskeem.moarsms.http.HttpServer
import org.bstats.bukkit.Metrics
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
    private var e = Executors.newSingleThreadExecutor()

    private lateinit var httpServerThread: HTTPServerThread
    private lateinit var platform : BukkitPlatform

    private var oldHost : String? = null
    private var oldPort : Int? = null

    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()

        logger.finest("Setting up platform")
        allowedFortumoIPs = config.getStringList("allowedIps")
        platform = BukkitPlatform(this)

        // Do metrics
        Thread({
            val metrics = Metrics(this@MoarSMSPlugin)

            metrics.addCustomChart(Metrics.SimplePie("test_sms_allowed") {
                if(platform.allowTest) "allowed" else "disallowed"
            })

            metrics.addCustomChart(Metrics.SimplePie("defined_services_amount") {
                "${platform.serviceSecrets.size}"
            })

        }, "bStats thread").start()

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
        allowedFortumoIPs = config.getStringList("allowedIps")
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
}