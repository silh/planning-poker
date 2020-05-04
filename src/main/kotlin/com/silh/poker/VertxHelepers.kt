package com.silh.poker

import io.vertx.core.buffer.Buffer

fun String.toBuffer(): Buffer = Buffer.buffer(this)
