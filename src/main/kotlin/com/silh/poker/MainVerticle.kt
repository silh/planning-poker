package com.silh.poker

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.silh.poker.game.GamesHandlerVerticle
import com.silh.poker.server.ServerVerticle
import io.netty.handler.logging.LogLevel
import io.vertx.core.Launcher
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle

const val APPLICATION_JSON = "application/json;"

/**
 * Run app from and IDE.
 */
fun main() {
  Launcher.executeCommand("run", MainVerticle::class.java.name)
}

class MainVerticle : CoroutineVerticle() {

  private val log = LoggerFactory.getLogger(MainVerticle::class.java)

  override suspend fun start() {
    addJsonKotlinModule()
    deployVerticles()
    log.info("${this.javaClass.simpleName} deployed.")
  }

  private suspend fun deployVerticles() {
    vertx.deployVerticleAwait(GamesHandlerVerticle())
    vertx.deployVerticleAwait(ServerVerticle())
  }

  private fun addJsonKotlinModule() {
    DatabindCodec.mapper().registerModule(KotlinModule())
  }

}
