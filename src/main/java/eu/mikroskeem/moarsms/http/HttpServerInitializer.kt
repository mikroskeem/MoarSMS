package eu.mikroskeem.moarsms.http

import eu.mikroskeem.moarsms.MoarSMSPlugin
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder

/**
 * @author Mark Vainomaa
 */
class HttpServerInitializer(private val plugin: MoarSMSPlugin) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val p = ch.pipeline()
        p.addLast(HttpRequestDecoder())
        p.addLast(HttpObjectAggregator(1048576))
        p.addLast(HttpResponseEncoder())
        p.addLast(HttpContentCompressor())
        p.addLast(HttpServerHandler(plugin))
    }
}