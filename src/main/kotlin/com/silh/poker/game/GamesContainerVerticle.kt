package com.silh.poker.game

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.CoroutineVerticle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

const val START_GAME_EVENT = "start-game" // Expects Buffer
const val STOP_GAME_EVENT = "stop-game"

/**
 * GamesContainer is responsible for starting new games, storing all running games and stopping games.
 * Following consumers are present:
 * @see START_GAME_EVENT - expects Buffer of StartGameRequest, returns Buffer of Game
 * @see STOP_GAME_EVENT - expects Long which represents ID of the game that should be deleted,
 * returns "true" if game was successfully deleted, "false" if there was no game with such ID.
 */
class GamesContainerVerticle : CoroutineVerticle() {

  private val log = LoggerFactory.getLogger(GamesContainerVerticle::class.java)

  private val games = ConcurrentHashMap<Long, Game>()
  private val idGenerator = AtomicLong()

  override suspend fun start() {
    val eventBus = vertx.eventBus()
    eventBus.consumer(START_GAME_EVENT, this::startGame)
    eventBus.consumer(STOP_GAME_EVENT, this::stopGame)
    log.info("${this.javaClass.simpleName} deployed.")
  }

  private fun startGame(msg: Message<Buffer>) {
    val startGameRequest = Json.decodeValue(msg.body(), StartGameRequest::class.java)
    val game = Game(
      idGenerator.getAndIncrement(),
      Player("1", startGameRequest.userName)
    )
    games[game.id] = game
    log.info("New game was started with id=${game.id} by ${startGameRequest.userName}")
    msg.reply(Json.encodeToBuffer(game))
  }

  private fun stopGame(msg: Message<Long>) {
    val gameId = msg.body()
    val removedElement = games.remove(gameId) != null
    if (removedElement) {
      log.info("Game $gameId was stopped.")
    }
    msg.reply(removedElement)
  }
}
