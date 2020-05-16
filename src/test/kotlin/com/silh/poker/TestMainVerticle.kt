package com.silh.poker

import com.silh.poker.game.*
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.http.httpClientOptionsOf
import io.vertx.kotlin.core.http.webSocketAwait
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.sendJsonAwait
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  @BeforeEach
  fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
    vertx.deployVerticle(MainVerticle(), testContext.succeeding { testContext.completeNow() })
  }

  @Test
  fun verticleDeployed(testContext: VertxTestContext) {
    testContext.completeNow()
  }

  @Test
  fun startGame(vertx: Vertx, testContext: VertxTestContext) {
    val creatorName = "someone"
    val expectedCreator = Player("1", creatorName)
    val expectedGame = Game(0L, expectedCreator)
    testContext.verifyBlocking {
      val response = getWebClient(vertx)
        .post("/api/game")
        .sendJsonAwait(StartGameRequest(creatorName))
      assertThat(response.statusCode()).isEqualTo(200)

      assertThat(Json.decodeValue(response.body(), Game::class.java)).isEqualTo(expectedGame)
      testContext.completeNow()
    }
  }

  @Test
  fun stopGame(vertx: Vertx, testContext: VertxTestContext) {
    val creatorName = "someone"
    val expectedCreator = Player("1", creatorName)
    val expectedGame = Game(0L, expectedCreator)
    val webClient = getWebClient(vertx)
    testContext.verifyBlocking {
      // First - create a game
      val createResponse = webClient
        .post("/api/game")
        .sendJsonAwait(StartGameRequest(creatorName))
      assertThat(createResponse.statusCode()).isEqualTo(200)

      val body = createResponse.body()
      val game = Json.decodeValue(body, Game::class.java)
      assertThat(game).isEqualTo(expectedGame)

      // Now delete the game
      val deleteResponse = webClient
        .delete("/api/game/${game?.id}")
        .sendAwait()
      assertThat(deleteResponse.statusCode()).isEqualTo(204)
      testContext.completeNow()
    }
  }

  @Test
  fun stopGameGameNotFound(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      // Now delete the game
      val deleteResponse = getWebClient(vertx)
        .delete("/api/game/0")
        .sendAwait()
      assertThat(deleteResponse.statusCode()).isEqualTo(404)
      testContext.completeNow()
    }
  }

  @Test
  fun stopGameIdIsNotLong(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      // Now delete the game
      val deleteResponse = getWebClient(vertx)
        .delete("/api/game/aaaa")
        .sendAwait()
      assertThat(deleteResponse.statusCode()).isEqualTo(400)
      testContext.completeNow()
    }
  }

  @Test
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  fun canReceiveGameUpdatesAfterConnecting(vertx: Vertx, testContext: VertxTestContext) {
    val receivedGameUpdate = testContext.checkpoint(3)

    val creatorName = "someperson"
    testContext.verifyBlocking {
      // Create a game
      val webClient = getWebClient(vertx)
      val response = webClient
        .post("/api/game")
        .sendJsonAwait(StartGameRequest(creatorName))
      assertThat(response.statusCode()).isEqualTo(200)

      val game = Json.decodeValue(response.body(), Game::class.java)
      val httpClient = vertx.createHttpClient(httpClientOptionsOf(defaultPort = 8080, defaultHost = "localhost"))

      //Set-up ws connection and handler
      val firstPlayer = Player("first", "firstPlayer")
      val secondPlayer = Player("second", "secondPlayer")
      val ws = httpClient.webSocketAwait("/events")
      val counter = AtomicInteger()
      ws.textMessageHandler { msg ->
        // Check updates
        testContext.verify {
          when (counter.getAndIncrement()) {
            // First should be an "add player"
            0 -> {
              val expectedUpdatedGame = Game(game.id, game.creator, mutableListOf(firstPlayer))

              val updatedGame = Json.decodeValue(msg, GameUpdatedMessage::class.java)
              assertThat(updatedGame.game).isEqualTo(expectedUpdatedGame)
              receivedGameUpdate.flag()
            }
            // Then another "add player"
            1 -> {
              val expectedUpdatedGame = Game(game.id, game.creator, mutableListOf(firstPlayer, secondPlayer))

              val updatedGame = Json.decodeValue(msg, GameUpdatedMessage::class.java)
              assertThat(updatedGame.game).isEqualTo(expectedUpdatedGame)
              receivedGameUpdate.flag()
            }
            // Third should be "remove player"
            2 -> {
              val expectedUpdatedGame = Game(game.id, game.creator, mutableListOf(firstPlayer))

              val updatedGame = Json.decodeValue(msg, GameUpdatedMessage::class.java)
              assertThat(updatedGame.game).isEqualTo(expectedUpdatedGame)
              receivedGameUpdate.flag()
            }
            else -> throw RuntimeException("unexpected update")
          }
        }
      }
      //Try to add a player
      var updateGameWsMessage = UpdateGameRequest(game.id, RequestUpdateGameActionType.ADD, firstPlayer)
      ws.writeTextMessage(Json.encode(updateGameWsMessage))

      //Try to add second player with another WS connection
      val ws2 = httpClient.webSocketAwait("/events")
      updateGameWsMessage = UpdateGameRequest(game.id, RequestUpdateGameActionType.ADD, secondPlayer)
      ws2.writeTextMessage(Json.encode(updateGameWsMessage))
      // Try to remove second player with second WS connection
      updateGameWsMessage = UpdateGameRequest(game.id, RequestUpdateGameActionType.DELETE, secondPlayer)
      ws2.writeTextMessage(Json.encode(updateGameWsMessage))
    }
  }

  private fun getWebClient(vertx: Vertx): WebClient =
    WebClient.create(vertx, webClientOptionsOf(defaultPort = 8080, defaultHost = "localhost"))

}
