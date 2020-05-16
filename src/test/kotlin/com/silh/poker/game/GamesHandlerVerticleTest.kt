package com.silh.poker.game

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.silh.poker.verifyBlocking
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.eventbus.requestAwait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(VertxExtension::class)
internal class GamesHandlerVerticleTest {

  @BeforeEach
  internal fun setUp(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      vertx.deployVerticleAwait(GamesHandlerVerticle())
      testContext.completeNow()
    }
    DatabindCodec.mapper().registerModule(KotlinModule())
  }

  @Test
  internal fun startUpdateStopGame(vertx: Vertx, testContext: VertxTestContext) {
    val updateCheckpoint = testContext.checkpoint(4)
    val deleteGameCheckpoint = testContext.checkpoint()
    val deletedGameCheckpoint = testContext.checkpoint()

    val eb = vertx.eventBus()
    val creatorName = "someone"
    val creator = Player("1", creatorName)
    val startGameRequest = StartGameRequest(creatorName)

    testContext.verifyBlocking {
      // Check that game with ID 0 doesn't exist yet
      val zeroGameResult = Json.decodeValue(
        eb.requestAwait<Buffer>(GET_GAME_EVENT, 0L).body(),
        Game::class.java
      )
      assertThat(zeroGameResult).isNull()

      // Start game
      val expectedStartedGame = Game(0L, creator)
      val createGameMsg = eb.requestAwait<Buffer>(START_GAME_EVENT, Json.encodeToBuffer(startGameRequest))
      val startedGame = Json.decodeValue(createGameMsg.body(), Game::class.java)
      assertThat(expectedStartedGame).isEqualTo(startedGame)

      // Verify get request
      val getGameResult = Json.decodeValue(
        eb.requestAwait<Buffer>(GET_GAME_EVENT, startedGame.id).body(),
        Game::class.java
      )
      assertThat(getGameResult).isEqualTo(startedGame)

      // Update game
      val firstPlayer = Player("first", "1")
      val secondPlayer = Player("second", "2")
      val updateCounter = AtomicInteger()
      eb.consumer<Buffer>(GAME_UPDATED_EVENT) { updateMsg ->
        testContext.verifyBlocking {
          val updatedGame = Json.decodeValue(updateMsg.body(), Game::class.java)
          val counterValue = updateCounter.incrementAndGet()
          when (counterValue) {
            1 -> assertThat(updatedGame.participants)
              .hasSize(1)
              .contains(firstPlayer)
            2 -> assertThat(updatedGame.participants)
              .hasSize(2)
              .containsAll(listOf(firstPlayer, secondPlayer))
            3 -> assertThat(updatedGame.participants)
              .hasSize(1)
              .contains(secondPlayer)
            4 -> assertThat(updatedGame.participants).isEmpty()
            else -> testContext.failNow(RuntimeException("Unexpected update"))
          }
          updateCheckpoint.flag()

          // Delete the game after 4 updates.
          if (counterValue == 4) {
            eb.request<Boolean>(STOP_GAME_EVENT, startedGame.id) {
              deleteGameCheckpoint.flag()
            }
          }
        }
      }
      eb.consumer<Any>(GAME_STOPPED_EVENT) { msg ->
        testContext.verify {
          assertThat(msg.body()).isEqualTo(startedGame.id)
          deletedGameCheckpoint.flag()
        }
      }
      val addPlayerReq1 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.ADD, firstPlayer)
      val addPlayerReq2 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.ADD, secondPlayer)
      eb.send(UPDATE_GAME_EVENT, Json.encodeToBuffer(addPlayerReq1))
      eb.send(UPDATE_GAME_EVENT, Json.encodeToBuffer(addPlayerReq2))
      val deletePlayerReq1 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.DELETE, firstPlayer)
      val deletePlayerReq2 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.DELETE, secondPlayer)
      eb.send(UPDATE_GAME_EVENT, Json.encodeToBuffer(deletePlayerReq1))
      eb.send(UPDATE_GAME_EVENT, Json.encodeToBuffer(deletePlayerReq2))
    }
  }
}
