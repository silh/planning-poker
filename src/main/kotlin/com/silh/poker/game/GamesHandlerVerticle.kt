package com.silh.poker.game

import com.silh.poker.eventBus
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.CoroutineVerticle
import org.hashids.Hashids
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

const val START_GAME_EVENT = "start-game"
const val GET_GAME_EVENT = "get-game"
const val UPDATE_GAME_EVENT = "update-game"
const val STOP_GAME_EVENT = "stop-game"
const val GAME_UPDATED_EVENT = "game-updated"
const val GAME_STOPPED_EVENT = "game-stopped"

/**
 * GamesContainer is responsible for starting new games, storing all running games and stopping games.
 * Following consumers are present:
 * @see START_GAME_EVENT - expects Buffer of [StartGameRequest], returns Buffer of Game.
 * @see UPDATE_GAME_EVENT - expects Buffer of [UpdateGameRequest].
 * @see STOP_GAME_EVENT - expects Long which represents ID of the game that should be deleted,
 * returns "true" if game was successfully deleted, "false" if there was no game with such ID.
 * Following events are sent:
 * @see GAME_UPDATED_EVENT - sent when there were any updates to the game state.
 * @see GAME_STOPPED_EVENT - sent when the game was stopped by request.
 */
class GamesHandlerVerticle : CoroutineVerticle() {

  private val log = LoggerFactory.getLogger(GamesHandlerVerticle::class.java)

  private val games = ConcurrentHashMap<String, Game>()
  private val idGenerator = Hashids("add some salt, per favore")

  override suspend fun start() {
    val eb = eventBus()
    eb.consumer(START_GAME_EVENT, this::startGame)
    eb.consumer(GET_GAME_EVENT, this::getGame)
    eb.consumer(UPDATE_GAME_EVENT, this::updateGame)
    eb.consumer(STOP_GAME_EVENT, this::stopGame)
    log.info("${this.javaClass.simpleName} deployed.")
  }

  /**
   * Handles start game requests.
   * @param msg - expected to contain [StartGameRequest].
   */
  private fun startGame(msg: Message<Buffer>) {
    val startGameRequest = Json.decodeValue(msg.body(), StartGameRequest::class.java)
    var game: Game
    // Try generating random ids and then inserting. The chacnes of generating 2 similar IDs are low
    // so most of the time first insertion will be successful.
    do {
      game = getNewGame(startGameRequest)
      val oldGame = games.putIfAbsent(game.id, game)
    } while (oldGame != null)
    log.info("New game was started with id=${game.id} by ${startGameRequest.userName}")
    msg.reply(Json.encodeToBuffer(game))
  }

  /**
   * Handles update game requests
   * @param msg - expected to contain [UpdateGameRequest]
   */
  private fun updateGame(msg: Message<Buffer>) {
    val req = try {
      Json.decodeValue(msg.body(), UpdateGameRequest::class.java)
    } catch (e: Exception) {
      log.warn(e.message)
      return msg.fail(1, e.message)
    }
    val game = when (req.actionType) {
      RequestUpdateGameActionType.ADD -> addPlayer(req.gameId, req.player)
      RequestUpdateGameActionType.DELETE -> removePlayer(req.gameId, req.player)
    }
    if (game == null) {
      msg.reply(Json.encodeToBuffer(req.toResponse(actionType = ResponseUpdateGameActionType.NOT_FOUND)))
    } else {
      msg.reply(Json.encodeToBuffer(req.toResponse()))
      notifyGameUpdated(game)
    }
  }

  private fun getGame(msg: Message<String>) {
    val gameId = msg.body()
    log.trace("Handling get game: $gameId")
    val game = games[gameId]
    if (game == null) {
      msg.reply(Json.encodeToBuffer(null))
    } else {
      msg.reply(Json.encodeToBuffer(game))
    }
  }

  private fun stopGame(msg: Message<String>) {
    val gameId = msg.body()
    val game = games.remove(gameId)
    if (game != null) {
      notifyGameStopped(game.id)
      log.info("Game $gameId was stopped.")
    }
    msg.reply(game != null)
  }

  private fun addPlayer(gameId: String, player: Player): Game? {
    log.debug("Received add player message for $gameId.")
    val game = games[gameId]
    if (game == null) {
      log.debug("Game with ID $gameId was not found.")
      return null
    }
    game.participants.add(player)
    log.info("Player $player added to the game ${game.id}.")
    return game
  }

  private fun removePlayer(gameId: String, player: Player): Game? {
    log.info("Received remove player message for $gameId.")
    val game = games[gameId]
    if (game == null) {
      log.debug("Game with ID $gameId was not found.")
      return null
    }
    game.participants.remove(player)
    log.info("Player $player removed from the game ${game.id}.")
    return game
  }

  private fun notifyGameUpdated(game: Game) = eventBus().send(GAME_UPDATED_EVENT, Json.encodeToBuffer(game))

  private fun notifyGameStopped(id: String) = eventBus().send(GAME_STOPPED_EVENT, id)

  private fun getNewGame(startGameRequest: StartGameRequest): Game {
    val random = ThreadLocalRandom.current()
    return Game(
      idGenerator.encode(random.nextLong(Hashids.MAX_NUMBER), random.nextLong(Hashids.MAX_NUMBER)),
      Player("1", startGameRequest.userName)
    )
  }
}
