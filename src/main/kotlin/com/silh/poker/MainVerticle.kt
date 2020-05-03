package com.silh.poker

import io.vertx.kotlin.core.http.HttpServerOptions
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle

class MainVerticle : CoroutineVerticle() {

  override suspend fun start() {
    val serverOptions = httpServerOptionsOf(
      port = 8080
    )
    vertx
      .createHttpServer(serverOptions)
      .requestHandler { req ->
        req.response()
          .putHeader("content-type", "text/plain")
          .end("Hello from Vert.x!")
      }
      .listenAwait(8888)
    println("HTTP server started on port 8888")
  }
}
