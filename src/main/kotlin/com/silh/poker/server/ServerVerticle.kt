package com.silh.poker.server

import com.silh.poker.APPLICATION_JSON
import com.silh.poker.eventBus
import com.silh.poker.game.START_GAME_EVENT
import com.silh.poker.game.STOP_GAME_EVENT
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.Router.router
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

  private lateinit var wsHandler: Handler<ServerWebSocket>

  override suspend fun start() {
    wsHandler = WsHandler(vertx)
    val serverOptions = httpServerOptionsOf(
      port = 8080
    )
    vertx.createHttpServer(serverOptions)
      .requestHandler(apiRouter())
      .listenAwait()
    log.info("HTTP server started on port ${serverOptions.port}")
  }

  private fun apiRouter(): Router {
    val apiRouter = router(vertx)

    apiRouter.post("/game")
      .consumes(APPLICATION_JSON)
      .produces(APPLICATION_JSON)
      .handler(BodyHandler.create())
      .coroutineHandler(this::handleStartGame)

    apiRouter.delete("/game/:id")
      .handler(BodyHandler.create())
      .coroutineHandler(this::handleStopGame)

    val wsRouter = router(vertx)
    wsRouter
      .get()
      .safeHandler { ctx: RoutingContext ->
        val upgraded = ctx.request().upgrade()
        wsHandler.handle(upgraded)
      }

    return router(vertx)
      .mountSubRouter("/api", apiRouter)
      .mountSubRouter("/events", wsRouter)
  }

  private suspend fun handleStartGame(ctx: RoutingContext) {
    val msg = eventBus().requestAwait<Buffer>(START_GAME_EVENT, ctx.body)
    ctx.response()
      .putHeader("Content-Type", APPLICATION_JSON)
      .end(msg.body())
  }

  private suspend fun handleStopGame(ctx: RoutingContext) {
    val idString = ctx.pathParam("id")
    val id = idString.toLongOrNull()
    if (id == null) {
      ctx.fail(400)
      return
    }
    val msg = eventBus().requestAwait<Boolean>(STOP_GAME_EVENT, id)
    val responseCode = if (msg.body()) 204 else 404
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

  private fun Route.safeHandler(handler: (RoutingContext) -> Unit): Route {
    return this.handler { ctx ->
      try {
        handler(ctx)
      } catch (e: Exception) {
        ctx.fail(e)
      }
    }
  }
}
