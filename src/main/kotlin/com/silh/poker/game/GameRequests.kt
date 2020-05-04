package com.silh.poker.game

data class StartGameRequest(val userName: String)
data class AddPlayerRequest(val player: Player)
data class RemovePlayerPlayerRequest(val player: Player)
