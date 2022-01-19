package com.ldd.ws.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.ResourceLeakDetector
import org.slf4j.LoggerFactory

/**
 * 用于向终端推送现货行情数据
 *
 * @author ldd
 */
class NettyServer(
    private var path: String,
    private var port: Int
) {

    private val log = LoggerFactory.getLogger(NettyServer::class.java)

    fun start() {
        val bossGroup: EventLoopGroup = NioEventLoopGroup()
        val group: EventLoopGroup = NioEventLoopGroup()
        try {
            val serverBootstrap = ServerBootstrap()
            serverBootstrap.option(ChannelOption.SO_BACKLOG, 5000)
            // 绑定线程池
            serverBootstrap.group(group, bossGroup) // 指定使用的channel
                .channel(NioServerSocketChannel::class.java) // 绑定监听端口
                .localAddress(port)
                .handler(LoggingHandler(LogLevel.DEBUG)) // 绑定客户端连接时候触发操作
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    @Throws(Exception::class)
                    override fun initChannel(ch: SocketChannel) {
                        log.info("SpotQuoteNettyServer 收到新连接")
                        //websocket协议本身是基于http协议的，所以这边也要使用http解编码器
                        val pipeline = ch.pipeline()
                        pipeline.addLast(HttpServerCodec())
                        //以块的方式来写的处理器
                        pipeline.addLast(ChunkedWriteHandler())
                        pipeline.addLast(HttpObjectAggregator(65536))
                        //手动维护心跳/数据
                        pipeline.addLast(NettyWebSocketHandler(path))
                    }
                }
                )
            // 服务器异步创建绑定
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
            val cf = serverBootstrap.bind().sync()
            log.info(
                NettyServer::class.java.toString() + " 启动正在监听: " + cf.channel().localAddress()
            )
            // 关闭服务器通道
            cf.channel().closeFuture().sync()
        } catch (e: Exception) {
            log.error(e.message)
        } finally {
            // 释放线程池资源
            group.shutdownGracefully().sync()
            bossGroup.shutdownGracefully().sync()
        }
    }
}