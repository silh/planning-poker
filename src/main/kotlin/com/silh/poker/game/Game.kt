package com.silh.poker.game

data class Game(
  val id: Long,
  val creator: Player
) {
  val participants = ArrayList<Player>()
}
