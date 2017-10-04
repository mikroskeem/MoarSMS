/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

package eu.mikroskeem.moarsms.http

import eu.mikroskeem.moarsms.Platform
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder

/**
 * HTTP server channel initializer
 *
 * @author Mark Vainomaa
 */
internal class HttpServerInitializer(private val platform: Platform) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) = ch.pipeline().run {
        platform.logger.finest("HttpServerInitializer initChannel($ch)")
        addLast(HttpRequestDecoder())
        addLast(HttpObjectAggregator(1048576))
        addLast(HttpResponseEncoder())
        addLast(HttpContentCompressor())
        addLast(HttpServerHandler(platform))

        Unit
    }
}