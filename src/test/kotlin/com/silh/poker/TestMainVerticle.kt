package com.silh.poker

import com.silh.poker.game.Game
import com.silh.poker.game.Player
import com.silh.poker.game.StartGameRequest
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.sendJsonAwait
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  @BeforeEach
  fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
    vertx.deployVerticle(MainVerticle(), testContext.succeeding { testContext.completeNow() })
  }

  @Test
  fun verticleDeployed(vertx: Vertx, testContext: VertxTestContext) {
    testContext.completeNow()
  }

  @Test
  fun startGameRoute(vertx: Vertx, testContext: VertxTestContext) {
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
  fun stopGameRoute(vertx: Vertx, testContext: VertxTestContext) {
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
  fun stopGameRouteGameNotFound(vertx: Vertx, testContext: VertxTestContext) {
    testContext.verifyBlocking {
      // Now delete the game
      val deleteResponse = getWebClient(vertx)
        .delete("/api/game/0")
        .sendAwait()
      assertThat(deleteResponse.statusCode()).isEqualTo(404)
      testContext.completeNow()
    }
  }

  private fun getWebClient(vertx: Vertx): WebClient =
    WebClient.create(vertx, webClientOptionsOf(defaultPort = 8080, defaultHost = "localhost"))

}
