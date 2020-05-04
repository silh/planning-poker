package com.silh.poker

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.silh.poker.game.GamesContainer
import com.silh.poker.game.START_GAME_EVENT
import com.silh.poker.game.STOP_GAME_EVENT
import io.vertx.core.Launcher
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch

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
    val serverOptions = httpServerOptionsOf(
      port = 8080
    )
    vertx.createHttpServer(serverOptions)
      .requestHandler(getRouter())
      .listenAwait()
    log.info("HTTP server started on port ${serverOptions.port}")
  }

  private suspend fun deployVerticles() {
    vertx.deployVerticleAwait(GamesContainer())
  }

  private suspend fun handleStartGame(ctx: RoutingContext) {
    val msg = eventBus().requestAwait<Buffer>(START_GAME_EVENT, ctx.body)
    ctx.response()
      .putHeader("Content-Type", APPLICATION_JSON)
      .end(msg.body())
  }

  private suspend fun handleStopGame(ctx: RoutingContext) {
    val msg = eventBus().requestAwait<Boolean>(STOP_GAME_EVENT, ctx.pathParamLong("id"))
    val responseCode = if (msg.body()) {
      204
    } else {
      404
    }
    ctx.response()
      .setStatusCode(responseCode)
      .end()
  }

  private fun Route.coroutineHandler(handler: suspend (RoutingContext) -> Unit): Route {
    return this.handler { ctx ->
      launch {
        try {
          handler(ctx)
        } catch (e: Exception) {
          ctx.fail(e)
        }
      }
    }
  }

  private fun getRouter(): Router {
    val apiRouter = Router.router(vertx)

    apiRouter.post("/game")
      .consumes(APPLICATION_JSON)
      .produces(APPLICATION_JSON)
      .handler(BodyHandler.create())
      .coroutineHandler(this::handleStartGame)

    apiRouter.delete("/game/:id")
      .handler(BodyHandler.create())
      .coroutineHandler(this::handleStopGame)

    return Router
      .router(vertx)
      .mountSubRouter("/api", apiRouter)
  }

  @SuppressWarnings("deprecation")
  private fun addJsonKotlinModule() {
    Json.mapper.registerModule(KotlinModule())
  }
}
