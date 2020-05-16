package com.silh.poker.server

import com.silh.poker.game.*
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit

/**
 * Handles Events for the ongoing games.
 */
class WsHandler(
  private val vertx: Vertx
) : Handler<ServerWebSocket> {

  private val log = LoggerFactory.getLogger(WsHandler::class.java)

  // List of observers for each game
  // TODO each observer can have multiple sockets - need to change to a map with session id
  private val observers = ConcurrentHashMap<Long, MutableSet<ServerWebSocket>>()
  private val gameEventConsumers = ConcurrentHashMap<String, MessageConsumer<Buffer>>()

  private val pingPeriod = 10L

  override fun handle(ws: ServerWebSocket) {
    ws.textMessageHandler { msg ->
      handleUpdateMessage(msg, ws)
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
        gameObservers.remove(ws)
      }
    }

    // Do a ping right away to start ping-pong cycle
    ws.writePing(Buffer.buffer("ping"))
  }

  private fun handleUpdateMessage(msg: String, ws: ServerWebSocket) {
    val wsMessage = try {
      Json.decodeValue(msg, UpdateWsMessage::class.java)
    } catch (e: Exception) {
      log.warn("Failed to decode message from ${ws.uri()}: ${e.message}")
      return
    }
    when (wsMessage.actionType) {
      ActionType.ADD -> {
        log.info("Received request to add new player ${wsMessage.player} to the game ${wsMessage.gameId}")
        // if the game didn't have observers before - create EB consumer for game update events and game stop events
        observers.computeIfAbsent(wsMessage.gameId) {
          addGameUpdatedConsumer(wsMessage.gameId)
          addGameStoppedConsumer(wsMessage.gameId)
          return@computeIfAbsent CopyOnWriteArraySet()
        }.add(ws)
        eventBus().send(ADD_PLAYER_EVENT + wsMessage.gameId, Json.encodeToBuffer(wsMessage.player))
      }
      ActionType.DELETE -> {
        log.info("Received request to remove player ${wsMessage.player} from the game ${wsMessage.gameId}")
        observers[wsMessage.gameId]?.remove(ws)
        eventBus().send(REMOVE_PLAYER_EVENT + wsMessage.gameId, Json.encodeToBuffer(wsMessage.player))
      }
    }
  }

  private fun addGameUpdatedConsumer(gameId: Long) {
    val subscription = GAME_UPDATED_EVENT + gameId
    gameEventConsumers[subscription] = eventBus().consumer(subscription, this::handleGameUpdate)
    log.info("Added game events consumer $subscription.")
  }

  private fun addGameStoppedConsumer(gameId: Long) {
    val subscription = GAME_STOPPED_EVENT + gameId
    gameEventConsumers[subscription] = eventBus().consumer(subscription) {
      removeGameUpdatedConsumer(gameId)
      removeGameStoppedConsumer(gameId)
      observers.remove(gameId)
    }
    log.info("Added game events consumer $subscription.")
  }

  private fun removeGameUpdatedConsumer(gameId: Long) = removeConsumer(GAME_UPDATED_EVENT + gameId)

  private fun removeGameStoppedConsumer(gameId: Long) = removeConsumer(GAME_STOPPED_EVENT + gameId)

  private fun removeConsumer(subscription: String) {
    gameEventConsumers.remove(subscription)?.unregister()
    log.debug("Removed consumer for $subscription.")
  }

  private fun handleGameUpdate(msg: Message<Buffer>) {
    log.info("Received game updated message ${msg.body()}.")
    val game = Json.decodeValue(msg.body(), Game::class.java)
    observers[game.id]?.forEach { ws ->
      ws.writeTextMessage(Json.encode(GameUpdatedWsMessage(game.id, game)))
    }
  }

  private fun eventBus(): EventBus = vertx.eventBus()
}
