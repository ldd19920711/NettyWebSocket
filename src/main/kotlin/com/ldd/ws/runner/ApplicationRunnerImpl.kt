package com.ldd.ws.runner

import com.ldd.ws.netty.NettyServer
import io.netty.util.concurrent.DefaultThreadFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.util.CollectionUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 应用启动监听
 *
 * @author ldd
 */
@Component
class ApplicationRunnerImpl : ApplicationRunner {

    private val log = LoggerFactory.getLogger(ApplicationRunnerImpl::class.java)

    @Value("\${server.servlet.context-path}")
    private lateinit var projectPath: String

    @Value("\${server.ws.port}")
    private var serverSpotWsPort: Int = 0

    override fun run(args: ApplicationArguments) {
        //启动行情ws服务
        poolExecutor.submit {
            try {
                NettyServer(projectPath, serverSpotWsPort).start()
            } catch (e: Exception) {
                log.error("NettyServer start error!msg={}", e.message)
            }
        }
    }


    companion object {

        val poolExecutor = ThreadPoolExecutor(
            30, 300, 60,
            TimeUnit.SECONDS, LinkedBlockingQueue(),
            DefaultThreadFactory("ApplicationRunnerImpl-")
        )
    }
}