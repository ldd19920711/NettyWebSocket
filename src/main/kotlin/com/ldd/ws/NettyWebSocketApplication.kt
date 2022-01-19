package com.ldd.ws

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableAsync

@ComponentScan(basePackages = ["com.ldd"])
@SpringBootApplication
@EnableAsync
class NettyWebSocketApplication

fun main(args: Array<String>) {
    runApplication<NettyWebSocketApplication>(*args)
}
