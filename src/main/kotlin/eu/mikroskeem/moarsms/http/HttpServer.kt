/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

package eu.mikroskeem.moarsms.http

import eu.mikroskeem.moarsms.Platform
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

/**
 * @author Mark Vainomaa
 */
internal class HttpServer(private val platform: Platform) {
    internal var host: String? = null
    internal var port: Int? = null

    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    private var bootstrap: ServerBootstrap? = null
    private var channel: Channel? = null

    internal fun start() {
        platform.logger.finest("HTTPServer start($host:$port)")
        if(isRunning()) {
            IllegalStateException("Tried to start HTTP server before stopping it!").printStackTrace()
            stop()
        }
        bootstrap = ServerBootstrap().apply {
            group(NioEventLoopGroup(1).apply(this@HttpServer::bossGroup::set),
                    NioEventLoopGroup().apply(this@HttpServer::workerGroup::set))
                    .channel(NioServerSocketChannel::class.java)
                    .handler(LoggingHandler(HttpServer::class.java, LogLevel.valueOf(platform.nettyLoggingLevel)))
                    .childHandler(HttpServerInitializer(platform))
            channel = bind(host, port!!).sync().channel()
        }
    }

    internal fun stop() {
        platform.logger.finest("HTTPServer stop()")
        channel?.close().apply { channel = null }
        bootstrap = null
        bossGroup?.shutdownGracefully().apply { bossGroup = null }
        workerGroup?.shutdownGracefully().apply { workerGroup = null }
    }

    internal fun isRunning(): Boolean = channel != null && bootstrap != null && bossGroup != null && workerGroup != null
}