package com.silh.poker.game

import com.silh.poker.eventBus
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.CoroutineVerticle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

const val START_GAME_EVENT = "start-game"
const val GET_GAME_EVENT = "get-game"
const val STOP_GAME_EVENT = "stop-game"
const val ADD_PLAYER_EVENT = "add-player-game-"
const val REMOVE_PLAYER_EVENT = "remove-player-game-"
const val GAME_UPDATED_EVENT = "game-updated-"
const val GAME_STOPPED_EVENT = "game-stoped-"

/**
 * GamesContainer is responsible for starting new games, storing all running games and stopping games.
 * Following consumers are present:
 * @see START_GAME_EVENT - expects Buffer of StartGameRequest, returns Buffer of Game
 * @see STOP_GAME_EVENT - expects Long which represents ID of the game that should be deleted,
 * returns "true" if game was successfully deleted, "false" if there was no game with such ID.
 * @see ADD_PLAYER_EVENT{id} - expects AddPlayerRequest messages regarding the particular game.
 * @see REMOVE_PLAYER_EVENT{id} - expects RemovePlayerPlayerRequest messages regarding the particular game.
 * Following events are sent:
 * @see GAME_UPDATED_EVENT{id} - sent when there were any updates to the game state.
 * @see GAME_STOPPED_EVENT{id} - sent when the game was stopped by request.
 */
class GamesHandlerVerticle : CoroutineVerticle() {

  private val log = LoggerFactory.getLogger(GamesHandlerVerticle::class.java)

  private val games = ConcurrentHashMap<Long, Game>()
  private val idGenerator = AtomicLong()
  private val consumers = ConcurrentHashMap<String, MessageConsumer<Buffer>>()

  override suspend fun start() {
    val eb = eventBus()
    eb.consumer(START_GAME_EVENT, this::startGame)
    eb.consumer(GET_GAME_EVENT, this::getGame)
    eb.consumer(STOP_GAME_EVENT, this::stopGame)
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
    addConsumer(ADD_PLAYER_EVENT + game.id, game, this::addPlayer)
    addConsumer(REMOVE_PLAYER_EVENT + game.id, game, this::removePlayer)
    msg.reply(Json.encodeToBuffer(game))
  }

  private fun getGame(msg: Message<Long>) {
    val game = games[msg.body()]
    if (game != null) {
      msg.reply(Json.encodeToBuffer(game))
    } else {
      msg.reply(null)
    }
  }

  private fun stopGame(msg: Message<Long>) {
    val gameId = msg.body()
    val game = games.remove(gameId)
    if (game != null) {
      removeConsumer(ADD_PLAYER_EVENT + game.id)
      removeConsumer(REMOVE_PLAYER_EVENT + game.id)
      notifyGameStopped(game.id)
      log.info("Game $gameId was stopped.")
    }
    msg.reply(game != null)
  }

  private fun addConsumer(subId: String, game: Game, handler: (msg: Message<Buffer>, game: Game) -> Unit) {
    val addConsumer = eventBus().consumer(subId) { msg: Message<Buffer> -> handler(msg, game) }
    this.consumers[subId] = addConsumer
    log.debug("Created consumer for $subId.")
  }

  private fun removeConsumer(subdId: String) {
    val consumer = consumers.remove(subdId)
    if (consumer == null) {
      log.debug("No consumer for $subdId.")
    } else {
      consumer.unregister()
      log.debug("unregistered consumer for $subdId.")
    }
  }

  private fun addPlayer(msg: Message<Buffer>, game: Game) {
    log.info("Received add player message for ${game.id}.")
    val player = Json.decodeValue(msg.body(), Player::class.java)
    game.participants.add(player)
    notifyGameUpdated(game)
    log.info("Player $player added to the game ${game.id}.")
  }

  private fun removePlayer(msg: Message<Buffer>, game: Game) {
    log.info("Received remove player message for ${game.id}.")
    val player = Json.decodeValue(msg.body(), Player::class.java)
    game.participants.remove(player)
    notifyGameUpdated(game)
    log.info("Player $player removed from the game ${game.id}.")
  }

  private fun notifyGameUpdated(game: Game) = eventBus().send(GAME_UPDATED_EVENT + game.id, Json.encodeToBuffer(game))

  private fun notifyGameStopped(id: Long) = eventBus().send(GAME_STOPPED_EVENT + id, null)
}
