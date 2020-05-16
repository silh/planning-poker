package com.silh.poker.game

data class StartGameRequest(
  val userName: String
)

data class UpdateGameRequest(
  val gameId: Long,
  val actionType: RequestUpdateGameActionType,
  val player: Player
) {

  fun toResponse(actionType: ResponseUpdateGameActionType = this.actionType.toResponseType()) =
    UpdateGameResponse(this.gameId, actionType, player)
}

enum class RequestUpdateGameActionType {
  ADD,
  DELETE;

  fun toResponseType(): ResponseUpdateGameActionType {
    return when (this) {
      ADD -> ResponseUpdateGameActionType.ADDED
      DELETE -> ResponseUpdateGameActionType.DELETED
    }
  }
}

data class UpdateGameResponse(
  val gameId: Long,
  val actionType: ResponseUpdateGameActionType,
  val player: Player
)

enum class ResponseUpdateGameActionType {
  ADDED,
  DELETED,
  NOT_FOUND
}

data class GameUpdatedMessage(
  val gameId: Long,
  val game: Game
)
