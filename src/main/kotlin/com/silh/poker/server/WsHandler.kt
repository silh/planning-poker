package com.silh.poker.server

import com.silh.poker.game.*
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Handles Events for the ongoing games.
 */
class WsHandler(private val vertx: Vertx) : Handler<ServerWebSocket> {

  private val log = LoggerFactory.getLogger(WsHandler::class.java)

  init {
    eventBus().consumer(GAME_UPDATED_EVENT, this::handleGameUpdateNotification)
    eventBus().consumer(GAME_STOPPED_EVENT, this::handleGameStoppedNotification)
  }

  // List of observers for each game
  // TODO each observer can have multiple sockets - need to change to a map with session id
  private val observers = ConcurrentHashMap<String, MutableMap<String, ServerWebSocket>>()

  private val pingPeriod = 10L

  override fun handle(ws: ServerWebSocket) {
    ws.textMessageHandler { msg: String ->
      eventBus().request<Buffer>(UPDATE_GAME_EVENT, Buffer.buffer(msg)) { resp ->
        this.handleGameUpdatedResponse(ws, resp)
      }
    }
    ws.pongHandler {
      log.trace("Received pong from ${ws.localAddress()}.")
      vertx.setTimer(TimeUnit.SECONDS.toMillis(pingPeriod)) {
        ws.writePing(Buffer.buffer("ping"))
      }
    }
    ws.closeHandler {
      log.trace("WS closed ${ws.localAddress()}.")
      // Delete this ws from observing all of the games
      observers.values.forEach { gameObservers ->
        val iterator = gameObservers.iterator()
        while (iterator.hasNext()) {
          val observer = iterator.next()
          if (observer.value == ws) {
            iterator.remove()
          }
        }
      }
    }

    // Do a ping right away to start ping-pong cycle
    ws.writePing(Buffer.buffer("ping"))
  }

  private fun handleGameUpdateNotification(msg: Message<Buffer>) {
    log.info("Received game updated message ${msg.body()}.")
    val game = Json.decodeValue(msg.body(), Game::class.java)
    observers[game.id]?.values?.forEach { ws ->
      ws.writeTextMessage(Json.encode(GameUpdatedMessage(game.id, game)))
    }
  }

  private fun handleGameUpdatedResponse(ws: ServerWebSocket, resp: AsyncResult<Message<Buffer>>) {
    if (resp.succeeded()) {
      val updateResult = Json.decodeValue(resp.result().body(), UpdateGameResponse::class.java)
      when (updateResult.actionType) {
        ResponseUpdateGameActionType.ADDED -> {
          log.debug("Adding new WS listener for game ${updateResult.gameId} with addr=${ws.uri()}.")
          observers.computeIfAbsent(updateResult.gameId) {
            ConcurrentHashMap()
          }[updateResult.player.id] = ws
        }
        ResponseUpdateGameActionType.DELETED -> {
          log.debug("Removing WS listener for game ${updateResult.gameId} with addr=${ws.uri()}.")
          observers[updateResult.gameId]?.remove(updateResult.player.id)
        }
        ResponseUpdateGameActionType.NOT_FOUND -> {
          log.warn("Received an action for non-existent game: ${updateResult.gameId}")
        }
      }
    } else {
      log.debug("Update game failed: ${resp.cause()}.")
    }
  }

  private fun handleGameStoppedNotification(msg: Message<String>) {
    val gameId: String = msg.body()
    val game = observers.remove(gameId)
    if (game != null) {
      log.info("Stopped sending notifications for everyone in game $gameId")
    }
  }

  private fun eventBus(): EventBus = vertx.eventBus()
}
