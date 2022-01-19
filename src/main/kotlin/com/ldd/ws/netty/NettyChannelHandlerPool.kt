package com.ldd.ws.netty

import io.netty.channel.ChannelId
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor

/**
 * QuoteNettyChannelHandlerPool 通道组池，管理所有websocket连接
 *
 * @author ldd
 */
object NettyChannelHandlerPool {
    /**
     * 维护连接 key:channel
     */
    val CHANNEL_GROUP_APP: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    val CHANNEL_AND_ORDER_MAP: MutableMap<ChannelId, String> =
        mutableMapOf()

    /**
     * 记录channel最后一次通信时间
     */
    val CHANNEL_ID_AND_LAST_TIME_MAP: MutableMap<ChannelId, Long> =
        mutableMapOf()
}