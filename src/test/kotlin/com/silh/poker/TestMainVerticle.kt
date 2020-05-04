package com.silh.poker

import com.silh.poker.game.Game
import com.silh.poker.game.Player
import com.silh.poker.game.START_GAME_EVENT
import com.silh.poker.game.StartGameRequest
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
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
  fun startGameRouteWorks(vertx: Vertx, testContext: VertxTestContext) {
    val creatorName = "someone"
    val expectedCreator = Player("1", creatorName)
    val expectedGame = Game(0L, expectedCreator)

    //Prepare the consumer of events
    vertx.eventBus()
      .localConsumer<StartGameRequest>(START_GAME_EVENT) { msg -> msg.reply(Json.encodeToBuffer(expectedGame)) }

    WebClient.create(vertx, webClientOptionsOf(defaultPort = 8080, defaultHost = "localhost"))
      .post("/api/game/start")
      .sendJson(StartGameRequest(creatorName)) { response ->
        if (response.failed()) {
          testContext.failNow(response.cause())
        } else {
          testContext.verify {
            val result = response.result()
            assertThat(result.statusCode()).isEqualTo(200)

            val body = result.body()
            assertThat(Json.decodeValue(body, Game::class.java)).isEqualTo(expectedGame)
            testContext.completeNow()
          }
        }
      }
  }
}
