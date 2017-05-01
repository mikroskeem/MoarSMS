package eu.mikroskeem.moarsms.http

/**
 * @author Mark Vainomaa
 */

import eu.mikroskeem.moarsms.MoarSMSPlugin
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders.Names.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.util.CharsetUtil
import java.net.InetSocketAddress
import java.util.*

class HttpServerHandler(private val plugin: MoarSMSPlugin) : SimpleChannelInboundHandler<Any>() {
    private val defaultResponse =
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "    <head>\n" +
            "        <meta charset=\"utf-8\">\n" +
            "        <title>Nothing to see here</title>\n" +
            "    </head>\n" +
            "    <body>\n" +
            "        <img src=\"https://i.imgflip.com/19e9u5.jpg\" />\n" +
            "    </body>\n" +
            "</html>"

    private var request: HttpRequest? = null
    private val buf = StringBuilder()

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
        var successful = true
        if (msg is HttpRequest) {
            plugin.logger.info("Beep-boop, got request!")
            this.request = msg
            val request = msg

            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx)
            }
            val queryStringDecoder = QueryStringDecoder(request.uri)

            buf.setLength(0)
            if(queryStringDecoder.path() == "/" && msg.method == HttpMethod.GET) {
                val ipAddr = (ctx.channel().remoteAddress() as InetSocketAddress).address.hostAddress
                val headers = HashMap<String, String>()
                request.headers().forEach { entry -> headers.put(entry.key.toLowerCase(Locale.ENGLISH), entry.value) }
                val params = HashMap<String, String>()
                queryStringDecoder.parameters().forEach { entry -> params.put(entry.key, entry.value[0]) }

                var originIP: String? = headers["x-real-ip"]
                if (originIP != null) {
                    val nginxProxy = headers["x-nginx-proxy"]
                    if (nginxProxy == null || nginxProxy != "true") {
                        plugin.logger.info("X-Forwaded-For was present, but X-Nginx-Proxy not, bailing out!")
                        buf.append(plugin.platform.getMessage("badconfig.reverseProxy"))
                        successful = false
                    }
                } else {
                    originIP = ipAddr
                }

                /* Get basic headers */
                val useragent = headers["user-agent"]

                /* Get Fortumo headers */
                val sender = params["sender"]
                val keyword = params["keyword"]
                val message = params["message"]
                val serviceId = params["service_id"]
                val signature = params["sig"]
                val test = params["test"]

                /* Log them */
                plugin.logger.info("Origin IP: $originIP")
                plugin.logger.info("Useragent: $useragent")
                plugin.logger.info("Sender: $sender")
                plugin.logger.info("Keyword: $keyword")
                plugin.logger.info("Message: $message")
                plugin.logger.info("Test: $test")

                /* Check parameters */
                if (signature != null && serviceId != null && keyword != null && message != null) {
                    plugin.logger.info("Got valid Fortumo request!")
                    /* Check for IP */
                    if (successful && !plugin.fortumoUtils.checkIP(originIP)) {
                        plugin.logger.info("Request was from non-whitelisted IP '$originIP'!")
                        buf.append(plugin.platform.getMessage("validation.forbiddenIP"))
                        successful = false
                    }
                    /* Check for service id */
                    val checkSignature = plugin.platform.getServiceSecrets()[serviceId]
                    if (successful && checkSignature == null) {
                        plugin.logger.info("Service '$serviceId' was requested, but it is not defined!")
                        plugin.logger.info("Keyword was '$keyword', maybe this helps")
                        buf.append(plugin.platform.getMessage("validation.undefinedService"))
                        successful = false
                    }

                    /* Check for signature */
                    if (successful && !plugin.fortumoUtils.checkSignature(params, checkSignature!!)) {
                        plugin.logger.info("Signature seems incorrect, correct is '$checkSignature', " +
                                "but $signature' was provided")
                        buf.append(plugin.platform.getMessage("validation.signatureIncorrect"))
                        successful = false
                    }

                    /* Check if message it's test message and if they're allowed */
                    if (successful && test != null && test == "true" && !plugin.platform.allowTest()) {
                        plugin.logger.info("Test messages are disabled from config, bailing out")
                        buf.append(plugin.platform.getMessage("test.notallowed"))
                        successful = false
                    }
                } else {
                    successful = false
                }

                if(successful) {
                    plugin.logger.info("Message is valid")
                    buf.append(plugin.platform.invokeService(serviceId!!, message!!))
                } else {
                    buf.append(defaultResponse)
                }
            }
        }

        if (msg is LastHttpContent) {
            val keepAlive = HttpHeaders.isKeepAlive(request)
            val response = DefaultFullHttpResponse(HTTP_1_1, if(successful) OK else BAD_REQUEST,
                    Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8))

            response.headers().set(CONTENT_TYPE, "${if(successful) "text/plain" else "text/html"}; charset=utf-8")
            response.headers().set(SERVER, "das rite bitches, first i hax netty then i eat y'all spaghetti")

            if(keepAlive) {
                response.headers().set(CONTENT_LENGTH, response.content().readableBytes())
                response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
            }
            ctx.write(response)
            if(!keepAlive) {
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }

    private fun send100Continue(ctx: ChannelHandlerContext) {
        val response = DefaultFullHttpResponse(HTTP_1_1, CONTINUE)
        ctx.write(response)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }
}