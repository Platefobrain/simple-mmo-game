package pl.decodesoft.network.handlers

import pl.decodesoft.MMOGame
import pl.decodesoft.network.BaseMessageHandler

// Handler obsługujący wiadomości związane z pathfindingiem
class PathfindingMessageHandler(game: MMOGame) : BaseMessageHandler(game) {
    override val supportedMessageTypes = setOf("PATH")

    override fun handleMessage(parts: List<String>) {
        when (parts[0]) {
            "PATH" -> handlePathMessage(parts)
        }
    }

    private fun handlePathMessage(parts: List<String>) {
        if (parts.size >= 2) {
            // Konwertuje string z formatu "x1:y1,x2:y2,..." na listę par (x,y)
            val pathData = parts[1].split(",").mapNotNull {
                val coords = it.split(":")
                if (coords.size == 2) {
                    coords[0].toIntOrNull()?.let { x ->
                        coords[1].toIntOrNull()?.let { y ->
                            x to y
                        }
                    }
                } else null
            }

            // Przekazuje dane ścieżki do MMOGame
            game.updatePathTiles(pathData)
        }
    }
}