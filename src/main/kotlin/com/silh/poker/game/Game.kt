package com.silh.poker.game

import java.util.concurrent.CopyOnWriteArrayList

data class Game(
  val id: String,
  val creator: Player,
  val participants: MutableList<Player> = CopyOnWriteArrayList()
)
