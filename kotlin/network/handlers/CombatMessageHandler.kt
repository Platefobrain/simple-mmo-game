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

import com.badlogic.gdx.graphics.Color
import pl.decodesoft.MMOGame
import pl.decodesoft.network.BaseMessageHandler

// Handler obsługujący wiadomości związane z walką
class CombatMessageHandler(game: MMOGame) : BaseMessageHandler(game) {
    override val supportedMessageTypes = setOf(
        "HIT", "HIT_DETAILED", "HEALTH_UPDATE", "PLAYER_DIED", "RESPAWN",
        "RANGED_ATTACK", "SPELL_ATTACK", "MELEE_ATTACK"
    )

    override fun handleMessage(parts: List<String>) {
        when (parts[0]) {
            "HIT" -> handleHitMessage(parts)
            "HIT_DETAILED" -> handleHitDetailedMessage(parts)
            "HEALTH_UPDATE" -> handleHealthUpdateMessage(parts)
            "PLAYER_DIED" -> handlePlayerDiedMessage(parts)
            "RESPAWN" -> handleRespawnMessage(parts)
            "RANGED_ATTACK", "SPELL_ATTACK", "MELEE_ATTACK" -> handleAttackMessage(parts)
        }
    }

    private fun handleHitMessage(parts: List<String>) {
        if (parts.size >= 5) {
            val targetId = parts[1]
            val attackerId = parts[2]
            val attackType = parts[3]
            val currentHealth = parts[4].toIntOrNull() ?: 0
            val maxHealth = if (parts.size >= 6) parts[5].toIntOrNull() ?: 100 else 100

            // Dodaj metody do MMOGame do obsługi trafień
            if (targetId.startsWith("enemy_")) {
                val enemyId = targetId.substringAfter("enemy_")
                game.updateEnemyHealthExplicit(enemyId, currentHealth, maxHealth)
            } else {
                game.updatePlayerHealth(targetId, currentHealth, maxHealth)
            }
        }
    }

    private fun handleHitDetailedMessage(parts: List<String>) {
        if (parts.size >= 7) {
            val targetId = parts[1]
            val attackerId = parts[2]
            val attackType = parts[3]
            val currentHealth = parts[4].toIntOrNull() ?: 0
            val maxHealth = parts[5].toIntOrNull() ?: 100
            val damage = parts[6].toIntOrNull() ?: 0

            if (targetId.startsWith("enemy_")) {
                val enemyId = targetId.substringAfter("enemy_")
                game.updateEnemyHealthExplicit(enemyId, currentHealth, maxHealth)

                // Pokazujemy tekstowy wskaźnik obrażeń tylko jeśli jesteśmy atakującym
                if (attackerId == game.localPlayerId) {
                    val enemy = game.getEnemy(enemyId)
                    enemy?.let {
                        game.addDamageText(it.x, it.y + 20f, "-$damage", Color.WHITE)
                    }
                }
            } else {
                game.updatePlayerHealth(targetId, currentHealth, maxHealth)

                // Pokazujemy tekstowy wskaźnik obrażeń tylko jeśli jesteśmy atakującym lub atakowanym
                if (targetId == game.localPlayerId || attackerId == game.localPlayerId) {
                    val player = game.getPlayer(targetId)
                    player?.let {
                        game.addDamageText(it.x, it.y + 20f, "-$damage", Color.WHITE)
                    }
                }
            }
        }
    }

    private fun handleHealthUpdateMessage(parts: List<String>) {
        if (parts.size >= 4) {
            val playerId = parts[1]
            val currentHealth = parts[2].toIntOrNull() ?: 0
            val maxHealth = parts[3].toIntOrNull() ?: 100

            game.updatePlayerHealth(playerId, currentHealth, maxHealth)
        }
    }

    private fun handlePlayerDiedMessage(parts: List<String>) {
        if (parts.size >= 2) {
            val playerId = parts[1]
            game.handlePlayerDeath(playerId)
        }
    }

    private fun handleRespawnMessage(parts: List<String>) {
        if (parts.size >= 4) {
            val playerId = parts[1]
            val currentHealth = parts[2].toIntOrNull() ?: 100
            val maxHealth = parts[3].toIntOrNull() ?: 100

            game.respawnPlayer(playerId, currentHealth, maxHealth)
        }
    }

    private fun handleAttackMessage(parts: List<String>) {
        game.handleAttackMessage(parts[0], parts)
    }
}