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
        bootstrap = ServerBootstrap().apply {
            this.group(NioEventLoopGroup(1).apply(this@HttpServer::bossGroup::set),
                    NioEventLoopGroup().apply(this@HttpServer::workerGroup::set))
                    .channel(NioServerSocketChannel::class.java)
                    .handler(LoggingHandler(LogLevel.INFO))
                    .childHandler(HttpServerInitializer(platform))
            channel = bind(host, port!!).sync().channel()
        }
    }

    internal fun stop() {
        channel?.close()
        bossGroup?.shutdownGracefully()
        workerGroup?.shutdownGracefully()
    }

    internal fun isRunning(): Boolean = channel != null && bootstrap != null && bossGroup != null && workerGroup != null
}