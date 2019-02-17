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

package eu.mikroskeem.moarsms.http

import eu.mikroskeem.moarsms.Platform
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

/**
 * HTTP server manager
 *
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