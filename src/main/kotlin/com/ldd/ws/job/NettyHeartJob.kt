package com.ldd.ws.job

import com.ldd.ws.netty.NettyChannelHandlerPool
import io.netty.channel.ChannelId
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class NettyHeartJob {

    @Scheduled(fixedRate = 30 * 1000)
    @Async
    fun getKlineHistoryDataOf1m() {
        //每分钟判断当前连接中是否有超过1分钟为发送心跳的连接,如果有则主动释放
        //此频率可以主动调整
        val closeChannelIdList = mutableListOf<ChannelId>()
        NettyChannelHandlerPool.CHANNEL_ID_AND_LAST_TIME_MAP.forEach { (channelId, lastTime) ->
            if ((System.currentTimeMillis() - lastTime) / 1000 > 60) {
                closeChannelIdList.add(channelId)
            }
        }
        closeChannelIdList.forEach {
            try {
                NettyChannelHandlerPool.CHANNEL_GROUP_APP.find(it).close()
            } catch (e: Exception) {
                //忽略此异常,从下次循环中处理
            }
        }
    }
}