package com.silh.poker.game

import java.util.concurrent.ConcurrentHashMap

data class Round(
  val id: String
) {
  val participantsVotes: Map<Player, Vote> = ConcurrentHashMap<Player, Vote>()
  var finished: Boolean = false
}

data class Vote(
  val value: String = ""
)
