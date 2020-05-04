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
import java.util.concurrent.CountDownLatch
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

    val eb = vertx.eventBus()
    val creatorName = "someone"
    val creator = Player("1", creatorName)
    val startGameRequest = StartGameRequest(creatorName)

    testContext.verifyBlocking {
      // Start game
      val expectedStartedGame = Game(0L, creator)
      val createGameMsg = eb.requestAwait<Buffer>(START_GAME_EVENT, Json.encodeToBuffer(startGameRequest))
      val startedGame = Json.decodeValue(createGameMsg.body(), Game::class.java)
      assertThat(expectedStartedGame).isEqualTo(startedGame)

      // Update game
      val firstPlayer = Player("first", "1")
      val secondPlayer = Player("second", "2")
      val updateCounter = AtomicInteger()
      eb.consumer<Buffer>(GAME_UPDATED_EVENT + startedGame.id) { updateMsg ->
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
      val addPlayerPath = ADD_PLAYER_EVENT + startedGame.id
      eb.send(addPlayerPath, Json.encodeToBuffer(AddPlayerRequest(firstPlayer)))
      eb.send(addPlayerPath, Json.encodeToBuffer(AddPlayerRequest(secondPlayer)))
      val removePlayerPath = REMOVE_PLAYER_EVENT + startedGame.id
      eb.send(removePlayerPath, Json.encodeToBuffer(AddPlayerRequest(firstPlayer)))
      eb.send(removePlayerPath, Json.encodeToBuffer(AddPlayerRequest(secondPlayer)))
    }
  }
}