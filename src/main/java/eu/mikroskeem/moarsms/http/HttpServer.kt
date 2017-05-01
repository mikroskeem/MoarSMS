package eu.mikroskeem.moarsms.http

import eu.mikroskeem.moarsms.MoarSMSPlugin
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

/**
 * @author Mark Vainomaa
 */
class HttpServer(private val plugin: MoarSMSPlugin) {
    var host: String? = null
    var port: Int? = null

    private var bossGroup : NioEventLoopGroup? = null
    private var workerGroup : NioEventLoopGroup? = null
    private var bootstrap : ServerBootstrap? = null
    private var channel : Channel? = null

    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()
        bootstrap = ServerBootstrap()

        bootstrap!!.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .handler(LoggingHandler(LogLevel.INFO))
                .childHandler(HttpServerInitializer(plugin))

        channel = bootstrap!!.bind(host, port!!).sync().channel()
    }

    fun stop() {
        channel!!.close()
        bossGroup!!.shutdownGracefully()
        workerGroup!!.shutdownGracefully()
    }
}