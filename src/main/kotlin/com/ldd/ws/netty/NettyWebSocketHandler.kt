package com.ldd.ws.netty

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.linktech.dealing.market.netty.SubTypeConstants
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelId
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.websocketx.*
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

/**
 * QuoteNettyWebSocketHandler WebSocket处理器，处理websocket连接相关
 *
 * @author ldd
 */
class NettyWebSocketHandler(
    /**
     * ws path
     */
    private val path: String
) : SimpleChannelInboundHandler<TextWebSocketFrame?>() {

    /**
     * webSocketServerHandshaker
     */
    private var webSocketServerHandshaker: WebSocketServerHandshaker? = null

    private val log = LoggerFactory.getLogger(NettyWebSocketHandler::class.java)

    override fun channelActive(ctx: ChannelHandlerContext) {
        log.info("与客户端准备建立连接，通道开启！" + ctx.channel().id())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        val channelId = channel.id()
        log.info("与客户端断开连接，通道关闭！$channelId")
        //移除channelGroup 通道组,
        NettyChannelHandlerPool.CHANNEL_GROUP_APP.remove(channel)
        NettyChannelHandlerPool.CHANNEL_ID_AND_LAST_TIME_MAP.remove(channelId)
    }

    /**
     * 主逻辑
     *
     * @param ctx ctx
     * @param msg msg
     */
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is HttpRequest -> {
                val factory = WebSocketServerHandshakerFactory(
                    "ws://"
                            + path + "/ws" + "", null, false
                )
                webSocketServerHandshaker = factory.newHandshaker(msg)
                webSocketServerHandshaker?.handshake(ctx.channel(), msg)
                NettyChannelHandlerPool.CHANNEL_GROUP_APP.add(ctx.channel())
                log.info("与客户端连接成功！channelId=${ctx.channel().id()}")
                //记录初始连接时间
                NettyChannelHandlerPool.CHANNEL_ID_AND_LAST_TIME_MAP[ctx.channel().id()] = System.currentTimeMillis()
            }
            is CloseWebSocketFrame -> {
                webSocketServerHandshaker?.close(ctx.channel(), msg.retain())
                NettyChannelHandlerPool.CHANNEL_GROUP_APP.remove(ctx.channel())
            }
            is PingWebSocketFrame -> {
                try {
                    ctx.channel().writeAndFlush(PongWebSocketFrame(msg.content().retain()))
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }
            is TextWebSocketFrame -> {
                try {
                    handleData(ctx, msg)
                } catch (e: Exception) {
                    log.error("客户端消息解析异常!msg=${e.message}")
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        super.exceptionCaught(ctx, cause)
        val channel = ctx.channel()
        if (channel.isActive) {
            ctx.close()
        }
        ReferenceCountUtil.release(cause)
    }

    /**
     * 客户端数据处理
     *
     * @param ctx ctx
     * @param msg 消息
     */
    private fun handleData(ctx: ChannelHandlerContext, msg: TextWebSocketFrame?) {
        if (msg != null) {
            val text: String = msg.text()
            //数据解析
            val dataJson = JSONObject.parseObject(text)
            if (dataJson != null && dataJson.size > 0) {
                val currentChannelId = ctx.channel().id() ?: return
                //二次判断防止连接已经断开了
                if (NettyChannelHandlerPool.CHANNEL_GROUP_APP.contains(ctx.channel())) {
                    val wsType = dataJson.getString("ws_type")
                    //1订阅0取消订阅
                    val sub = dataJson.getString("sub")
                    if (SubTypeConstants.PING == wsType) {
                        //心跳处理
                        handlePingDataMsg(currentChannelId)
                    } else if (SubTypeConstants.ORDER == wsType) {
                        //订阅订单推送
                        handleOrderDataMsg(currentChannelId, sub)
                    }
                }
            }
        }
    }


    private fun handleOrderDataMsg(currentChannelId: ChannelId, sub: String) {
        val isSub =
            NettyChannelHandlerPool.CHANNEL_AND_ORDER_MAP[currentChannelId]
        when (sub) {
            SubTypeConstants.SUB -> {
                NettyChannelHandlerPool.CHANNEL_AND_ORDER_MAP[currentChannelId] = sub
                sendSubReceipt(currentChannelId, "order")
            }
            SubTypeConstants.UNSUB -> {
                if (isSub != null) {
                    NettyChannelHandlerPool.CHANNEL_AND_ORDER_MAP.remove(currentChannelId)
                    sendSubReceipt(currentChannelId, "order")
                }
            }
            else -> {
                log.error("订阅类型错误sub=$sub")
            }
        }
    }

    private fun sendSubReceipt(currentChannelId: ChannelId, wsType: String) {
        val map: MutableMap<String, Any> = HashMap(1)
        map["ws_type"] = wsType
        map["sub_result"] = "1"
        NettyChannelHandlerPool.CHANNEL_GROUP_APP.find(currentChannelId).writeAndFlush(
            TextWebSocketFrame(JSON.toJSONString(map))
        )
    }

    private fun handlePingDataMsg(currentChannelId: ChannelId) {
        //记录上次收到心跳的时间戳
        NettyChannelHandlerPool.CHANNEL_ID_AND_LAST_TIME_MAP[currentChannelId] = System.currentTimeMillis()
        val map: MutableMap<String, Any> = HashMap(1)
        map[SubTypeConstants.PONG] = System.currentTimeMillis()
        map["channelId"] = currentChannelId.asShortText()
        NettyChannelHandlerPool.CHANNEL_GROUP_APP.find(currentChannelId).writeAndFlush(
            TextWebSocketFrame(JSON.toJSONString(map))
        )
    }

    override fun messageReceived(ctx: ChannelHandlerContext?, msg: TextWebSocketFrame?) {
        log.info("messageReceived,ctx=${ctx},msg=${msg}")
    }

}