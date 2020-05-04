package com.silh.poker.server

import com.silh.poker.APPLICATION_JSON
import com.silh.poker.eventBus
import com.silh.poker.game.START_GAME_EVENT
import com.silh.poker.game.STOP_GAME_EVENT
import com.silh.poker.pathParamLong
import io.vertx.core.buffer.Buffer
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.eventbus.requestAwait
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch

/**
 * Handles HTTP requests to start/stop the game and add participants to running games.
 */
class ServerVerticle : CoroutineVerticle() {

  private val log = LoggerFactory.getLogger(ServerVerticle::class.java)

  override suspend fun start() {
    val serverOptions = httpServerOptionsOf(
      port = 8080
    )
    vertx.createHttpServer(serverOptions)
      .requestHandler(getRouter())
      .listenAwait()
    log.info("HTTP server started on port ${serverOptions.port}")
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
}
