package com.silh.poker

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.silh.poker.game.GamesContainerVerticle
import com.silh.poker.server.ServerVerticle
import io.vertx.core.Launcher
import io.vertx.core.json.Json
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
    vertx.deployVerticleAwait(GamesContainerVerticle())
    vertx.deployVerticleAwait(ServerVerticle())
  }

  private fun addJsonKotlinModule() {
    DatabindCodec.mapper().registerModule(KotlinModule())
  }

}
