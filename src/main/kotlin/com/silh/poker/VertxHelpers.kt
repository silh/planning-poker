package com.silh.poker

import io.vertx.core.eventbus.EventBus
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle

/**
 * Simplifies getting eventBus with inside of a Verticle.
 */
fun CoroutineVerticle.eventBus(): EventBus = this.vertx.eventBus()
