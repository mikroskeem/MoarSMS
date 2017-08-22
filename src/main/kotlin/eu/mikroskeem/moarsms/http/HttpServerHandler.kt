/*
 * Copyright (c) 2017 Mark Vainomaa
 *
 * This source code is proprietary software and must not be distributed and/or copied without the express permission of Mark Vainomaa
 */

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
import io.netty.handler.codec.http.HttpHeaderNames.*
import io.netty.handler.codec.http.HttpResponseStatus.*
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.util.CharsetUtil
import java.net.InetSocketAddress
import java.util.*

internal class HttpServerHandler(private val platform: Platform) : SimpleChannelInboundHandler<Any>() {
    private var request: HttpRequest? = null
    private val buf = StringBuilder()

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
        var successful = true
        if (msg is HttpRequest) {
            platform.logger.finest("Beep-boop, got request!")
            this.request = msg
            val request = msg

            if (HttpUtil.is100ContinueExpected(request)) {
                ctx.write(DefaultFullHttpResponse(HTTP_1_1, CONTINUE))
            }
            val queryStringDecoder = QueryStringDecoder(request.uri())

            buf.setLength(0)
            if(queryStringDecoder.path() == "/" && msg.method() == HttpMethod.GET) {
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
                        platform.logger.finest("X-Forwaded-For was present, but X-Nginx-Proxy not, bailing out!")
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
                platform.logger.finest("Origin IP: $originIP")
                platform.logger.finest("Useragent: $useragent")
                platform.logger.finest("Sender: $sender")
                platform.logger.finest("Keyword: $keyword")
                platform.logger.finest("Message: $message")
                platform.logger.finest("Test: $test")

                /* Check parameters */
                if (signature != null && serviceId != null && keyword != null && message != null) {
                    platform.logger.finest("Got valid Fortumo request!")
                    /* Check for IP */
                    if (successful && !platform.fortumoUtils.checkIP(originIP)) {
                        platform.logger.finest("Request was from non-whitelisted IP '$originIP'!")
                        buf.append(platform.getMessage("validation.forbiddenIP"))
                        successful = false
                    }
                    /* Check for service id */
                    val checkSignature = platform.serviceSecrets[serviceId]
                    if (successful && checkSignature == null) {
                        platform.logger.finest("Service '$serviceId' was requested, but it is not defined!")
                        platform.logger.finest("Keyword was '$keyword', maybe this helps")
                        buf.append(platform.getMessage("validation.undefinedService"))
                        successful = false
                    }

                    /* Check for signature */
                    if (successful && !platform.fortumoUtils.checkSignature(params, checkSignature!!)) {
                        platform.logger.finest("Signature seems incorrect, correct is '$checkSignature', " +
                                "but $signature' was provided")
                        buf.append(platform.getMessage("validation.signatureIncorrect"))
                        successful = false
                    }

                    /* Check if message it's test message and if they're allowed */
                    if (successful && test != null && test == "true" && !platform.allowTest) {
                        platform.logger.finest("Test messages are disabled from config, bailing out")
                        buf.append(platform.getMessage("test.notallowed"))
                        successful = false
                    }
                } else {
                    successful = false
                }

                if(successful) {
                    platform.logger.finest("Message is valid")
                    buf.append(platform.invokeService(serviceId!!, message!!))
                } else {
                    buf.append(platform.defaultResponse)
                }
            }
        }

        if (msg is LastHttpContent) {
            val keepAlive = HttpUtil.isKeepAlive(request)
            val response = DefaultFullHttpResponse(HTTP_1_1, if(successful) OK else BAD_REQUEST,
                    Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8))

            response.headers().set(CONTENT_TYPE, "${if(successful) "text/plain" else "text/html"}; charset=utf-8")
            response.headers().set(SERVER, "das rite bitches, first i hax netty then i eat y'all spaghetti")

            if(keepAlive) {
                response.headers().set(CONTENT_LENGTH, response.content().readableBytes())
                response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            }
            ctx.write(response)
            if(!keepAlive) {
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }
}