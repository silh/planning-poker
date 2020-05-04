package com.silh.poker

import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.runBlocking

/**
 * Simplifies running verify for VertxTestContext with runBlocking
 */
fun VertxTestContext.verifyBlocking(block: suspend () -> Unit): VertxTestContext {
  return this.verify {
    runBlocking {
      block()
    }
  }
}
