package com.silh.poker.game

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.silh.poker.verifyBlocking
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.ReplyException
import io.vertx.core.json.Json
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.eventbus.requestAwait
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
      // Start game
      val expectedStartedGame = Game("can't know the id before hand", creator)
      val createGameMsg = eb.requestAwait<Buffer>(START_GAME_EVENT, Json.encodeToBuffer(startGameRequest))
      val startedGame = Json.decodeValue(createGameMsg.body(), Game::class.java)
      assertThat(expectedStartedGame.creator).isEqualTo(startedGame.creator)

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
      var updatedGame = eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Json.encodeToBuffer(addPlayerReq1))
      assertThat(addPlayerReq1.toResponse()).isEqualTo(updatedGame.body().toUpdateGameResponse())

      val addPlayerReq2 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.ADD, secondPlayer)
      updatedGame = eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Json.encodeToBuffer(addPlayerReq2))
      assertThat(addPlayerReq2.toResponse()).isEqualTo(updatedGame.body().toUpdateGameResponse())

      val deletePlayerReq1 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.DELETE, firstPlayer)
      updatedGame = eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Json.encodeToBuffer(deletePlayerReq1))
      assertThat(deletePlayerReq1.toResponse()).isEqualTo(updatedGame.body().toUpdateGameResponse())

      val deletePlayerReq2 = UpdateGameRequest(startedGame.id, RequestUpdateGameActionType.DELETE, secondPlayer)
      updatedGame = eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Json.encodeToBuffer(deletePlayerReq2))
      assertThat(deletePlayerReq2.toResponse()).isEqualTo(updatedGame.body().toUpdateGameResponse())
    }
  }

  @Test
  internal fun `get returns null if game doesn't exist`(vertx: Vertx, testContext: VertxTestContext) {
    val eb = vertx.eventBus()
    testContext.verifyBlocking {
      // Check that game with ID 0 doesn't exist yet
      val respBody = eb.requestAwait<Buffer>(GET_GAME_EVENT, "some string").body()
      val zeroGameResult = Json.decodeValue(respBody, Game::class.java)
      assertThat(zeroGameResult).isNull()
      testContext.completeNow()
    }
  }

  @Test
  internal fun `can't add player to non-existent game`(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      val eb = vertx.eventBus()
      val addPlayerReq = UpdateGameRequest("some id", RequestUpdateGameActionType.ADD, Player("1", "1"))
      val expectedResponse = addPlayerReq.toResponse(ResponseUpdateGameActionType.NOT_FOUND)
      val response = eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Json.encodeToBuffer(addPlayerReq))
      assertThat(expectedResponse).isEqualTo(response.body().toUpdateGameResponse())
      testContext.completeNow()
    }
  }

  @Test
  internal fun `can't delete player from non-existent game`(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      val eb = vertx.eventBus()
      val deletePlayerReq = UpdateGameRequest("some id", RequestUpdateGameActionType.DELETE, Player("1", "1"))
      val expectedResponse = deletePlayerReq.toResponse(ResponseUpdateGameActionType.NOT_FOUND)
      val response = eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Json.encodeToBuffer(deletePlayerReq))
      assertThat(expectedResponse).isEqualTo(response.body().toUpdateGameResponse())
      testContext.completeNow()
    }
  }

  @Test
  internal fun `incorrect body fails the message`(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      val eb = vertx.eventBus()
      val exception = assertThrows<ReplyException> {
        runBlocking { eb.requestAwait<Buffer>(UPDATE_GAME_EVENT, Buffer.buffer("something")) }
      }
      assertThat(exception.failureCode()).isEqualTo(400)
      testContext.completeNow()
    }
  }

  private fun Buffer.toUpdateGameResponse(): UpdateGameResponse = this.toPojo(UpdateGameResponse::class.java)

  private fun <T : Any> Buffer.toPojo(clazz: Class<T>): T = Json.decodeValue(this, clazz)
}
