package com.silh.poker.server

import com.silh.poker.game.Game
import com.silh.poker.game.Player

data class UpdateWsMessage constructor(
  val gameId: Long,
  val actionType: ActionType,
  val player: Player
)

enum class ActionType {
  ADD,
  DELETE
}

data class GameUpdatedWsMessage(
  val gameId: Long,
  val game: Game
)
