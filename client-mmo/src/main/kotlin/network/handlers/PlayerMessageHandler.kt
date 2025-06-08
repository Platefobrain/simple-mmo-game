/*
 * This file is part of [GreenVale]
 *
 * [GreenVale] is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * [GreenVale] is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with [GreenVale].  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.decodesoft.network.handlers

import pl.decodesoft.MMOGame
import pl.decodesoft.network.BaseMessageHandler

// Handler obsługujący wiadomości związane z graczami
class PlayerMessageHandler(game: MMOGame) : BaseMessageHandler(game) {
    override val supportedMessageTypes = setOf("JOIN", "MOVE", "MOVE_FAILED", "LEAVE", "XP_GAINED")

    override fun handleMessage(parts: List<String>) {
        when (parts[0]) {
            "JOIN" -> handleJoinMessage(parts)
            "MOVE" -> handleMoveMessage(parts)
            "MOVE_FAILED" -> handleMoveFailedMessage(parts)
            "LEAVE" -> handleLeaveMessage(parts)
            "XP_GAINED" -> handleXpGained(parts)
        }
    }

    private fun handleJoinMessage(parts: List<String>) {
        if (parts.size >= 7) {
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()
            val id = parts[3]
            val playerUsername = if (parts.size >= 5) parts[4] else "Unknown"
            val characterClass = if (parts.size >= 6) parts[5].toIntOrNull() ?: 2 else 2
            val currentHealth = if (parts.size >= 7) parts[6].toIntOrNull() ?: 100 else 100
            val maxHealth = if (parts.size >= 8) parts[7].toIntOrNull() ?: 100 else 100
            val level = parts[8].toIntOrNull() ?: 1
            val experience = parts[9].toIntOrNull() ?: 0

            // Używamy metody z MMOGame
            game.addPlayer(id, x, y, playerUsername, characterClass, currentHealth, maxHealth, level, experience)
        }
    }

    private fun handleMoveMessage(parts: List<String>) {
        if (parts.size >= 4) {
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()
            val id = parts[3]

            // Używamy metody z MMOGame
            game.updatePlayerPosition(id, x, y)
        }
    }

    private fun handleMoveFailedMessage(parts: List<String>) {
        if (parts.size >= 3) {
            val failedPlayerId = parts[1]
            val reason = parts[2]

            // Używamy metody z MMOGame
            game.handleMoveFailed(failedPlayerId, reason)
        }
    }

    private fun handleLeaveMessage(parts: List<String>) {
        if (parts.size >= 2) {
            val id = parts[1]

            // Używamy metody z MMOGame
            game.removePlayer(id)
        }
    }

    private fun handleXpGained(parts: List<String>) {
        if (parts.size >= 5) {
            val playerId = parts[1]
            val gained = parts[2].toIntOrNull() ?: return
            val currentXp = parts[3].toIntOrNull() ?: return
            val currentLevel = parts[4].toIntOrNull() ?: return

            val player = game.getPlayer(playerId) ?: return
            player.experience = currentXp
            player.level = currentLevel

            println("Gracz $playerId zdobył $gained XP (lvl: $currentLevel)")
        }
    }
}