package eu.mikroskeem.moarsms.http

/**
 * @author Mark Vainomaa
 */

import eu.mikroskeem.moarsms.Platform
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders.Names.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.util.CharsetUtil
import org.intellij.lang.annotations.Language
import java.net.InetSocketAddress
import java.util.*

internal class HttpServerHandler(private val platform: Platform) : SimpleChannelInboundHandler<Any>() {
    @Language("HTML")
    private val defaultResponse = """
    <!DOCTYPE html>
    <html>
        <head>
            <meta charset="utf-8">
            <title>Nothing to see here</title>
        </head>
        <body>
            <img src="https://i.imgflip.com/19e9u5.jpg" />
        </body>
    </html>
    """

    private var request: HttpRequest? = null
    private val buf = StringBuilder()

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
        var successful = true
        if (msg is HttpRequest) {
            platform.logger.info("Beep-boop, got request!")
            this.request = msg
            val request = msg

            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx)
            }
            val queryStringDecoder = QueryStringDecoder(request.uri)

            buf.setLength(0)
            if(queryStringDecoder.path() == "/" && msg.method == HttpMethod.GET) {
                val ipAddr = (ctx.channel().remoteAddress() as InetSocketAddress).address.hostAddress
                val headers = HashMap<String, String>().apply {
                    request.headers().forEach { put(it.key.toLowerCase(Locale.ENGLISH), it.value) }
                }
                val params = HashMap<String, String>().apply {
                    queryStringDecoder.parameters().forEach { put(it.key, it.value[0]) }
                }

                var originIP: String? = headers["x-real-ip"]
                if (originIP != null) {
                    val nginxProxy = headers["x-nginx-proxy"]
                    if (nginxProxy == null || nginxProxy != "true") {
                        platform.logger.info("X-Forwaded-For was present, but X-Nginx-Proxy not, bailing out!")
                        buf.append(platform.getMessage("badconfig.reverseProxy"))
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
                platform.logger.info("Origin IP: $originIP")
                platform.logger.info("Useragent: $useragent")
                platform.logger.info("Sender: $sender")
                platform.logger.info("Keyword: $keyword")
                platform.logger.info("Message: $message")
                platform.logger.info("Test: $test")

                /* Check parameters */
                if (signature != null && serviceId != null && keyword != null && message != null) {
                    platform.logger.info("Got valid Fortumo request!")
                    /* Check for IP */
                    if (successful && !platform.fortumoUtils.checkIP(originIP)) {
                        platform.logger.info("Request was from non-whitelisted IP '$originIP'!")
                        buf.append(platform.getMessage("validation.forbiddenIP"))
                        successful = false
                    }
                    /* Check for service id */
                    val checkSignature = platform.serviceSecrets[serviceId]
                    if (successful && checkSignature == null) {
                        platform.logger.info("Service '$serviceId' was requested, but it is not defined!")
                        platform.logger.info("Keyword was '$keyword', maybe this helps")
                        buf.append(platform.getMessage("validation.undefinedService"))
                        successful = false
                    }

                    /* Check for signature */
                    if (successful && !platform.fortumoUtils.checkSignature(params, checkSignature!!)) {
                        platform.logger.info("Signature seems incorrect, correct is '$checkSignature', " +
                                "but $signature' was provided")
                        buf.append(platform.getMessage("validation.signatureIncorrect"))
                        successful = false
                    }

                    /* Check if message it's test message and if they're allowed */
                    if (successful && test != null && test == "true" && !platform.allowTest) {
                        platform.logger.info("Test messages are disabled from config, bailing out")
                        buf.append(platform.getMessage("test.notallowed"))
                        successful = false
                    }
                } else {
                    successful = false
                }

                if(successful) {
                    platform.logger.info("Message is valid")
                    buf.append(platform.invokeService(serviceId!!, message!!))
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
        ctx.write(DefaultFullHttpResponse(HTTP_1_1, CONTINUE))
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }
}