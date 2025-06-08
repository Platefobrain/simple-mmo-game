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

package pl.decodesoft.player.combat

import io.ktor.websocket.*
import pl.decodesoft.enemy.EnemyLevelManager
import pl.decodesoft.enemy.manager.EnemyManager
import pl.decodesoft.level.LevelManager
import pl.decodesoft.player.manager.UserManager
import pl.decodesoft.player.model.PlayerData
import java.util.concurrent.ConcurrentHashMap


class PlayerCombatManager(
    private val connections: ConcurrentHashMap<String, DefaultWebSocketSession>,
    private val playerPositions: ConcurrentHashMap<String, PlayerData>,
    private val userManager: UserManager,
    private val enemyManager: EnemyManager
) {
    // Oblicza obrażenia na podstawie typu ataku
    private fun calculateDamage(attackType: String): Int {
        return when (attackType) {
            "ARROW" -> 12
            "FIREBALL" -> 8
            "MELEE" -> 250
            else -> 5
        }
    }

    // Główna funkcja obsługująca trafienia
    suspend fun processHitMessage(targetId: String, attackerId: String, attackType: String) {
        // Sprawdź, czy gracz nie atakuje sam siebie
        if (targetId == attackerId) {
            return
        }

        // Oblicz obrażenia na podstawie typu ataku
        val damage = calculateDamage(attackType)

        // Sprawdź czy cel to przeciwnik czy gracz
        if (targetId.startsWith("enemy_")) {
            processEnemyHit(targetId, attackerId, attackType, damage)
        } else {
            processPlayerHit(targetId, attackerId, attackType, damage)
        }
    }

    // Obsługa trafienia przeciwnika
    private suspend fun processEnemyHit(targetId: String, attackerId: String, attackType: String, damage: Int) {
        val enemyId = targetId.substringAfter("enemy_")
        enemyManager.getEnemies().find { it.id == enemyId }?.let { enemy ->
            // Zadaj obrażenia przeciwnikowi
            val died = enemyManager.damageEnemy(enemy.id, damage)

            // Wyślij informację o trafieniu do wszystkich graczy
            val broadcastHitMessage = "HIT|$targetId|$attackerId|$attackType|${enemy.currentHealth}|${enemy.maxHealth}"
            broadcastToAll(broadcastHitMessage)

            // Szczegółowa wiadomość dla atakującego
            val detailedHitMessage = "HIT_DETAILED|$targetId|$attackerId|$attackType|${enemy.currentHealth}|${enemy.maxHealth}|$damage"
            sendToSpecificPlayers(detailedHitMessage)

            // Obsługa śmierci przeciwnika
            if (died) {
                broadcastToAll("ENEMY_DIED|$enemyId")

                // Przyznanie XP graczowi
                playerPositions[attackerId]?.let { attacker ->
                    val xpGain = EnemyLevelManager.calculateExperienceReward(enemy.type, enemy.level)
                    LevelManager.addExperience(attacker, xpGain)

                    // Aktualizacja wybranej postaci użytkownika w bazie
                    userManager.getUserById(attackerId)?.let { user ->
                        user.getSelectedCharacter()?.let { character ->
                            character.level = attacker.level
                            character.experience = attacker.experience
                            character.maxHealth = attacker.maxHealth
                            character.currentHealth = attacker.currentHealth
                            userManager.updateUser(user)
                        }
                    }

                    // Informacja zwrotna
                    val xpMsg = "XP_GAINED|${attacker.id}|$xpGain|${attacker.experience}|${attacker.level}"
                    broadcastToAll(xpMsg)
                }
            }
        }
    }

    // Obsługa trafienia gracza
    private suspend fun processPlayerHit(targetId: String, attackerId: String, attackType: String, damage: Int) {
        playerPositions[targetId]?.let { targetPlayer ->
            // Zadaj obrażenia graczowi
            targetPlayer.takeDamage(damage)

            // Upewnij się, że zdrowie nie spadnie poniżej zera
            if (targetPlayer.currentHealth < 0) targetPlayer.currentHealth = 0

            // Aktualizuj zdrowie gracza w wybranej postaci
            userManager.getUserById(targetId)?.let { user ->
                user.getSelectedCharacter()?.let { character ->
                    character.currentHealth = targetPlayer.currentHealth
                    userManager.updateUser(user)
                }
            }

            // Wyślij informację o trafieniu do wszystkich graczy
            val broadcastHitMessage = "HIT|$targetId|$attackerId|$attackType|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
            broadcastToAll(broadcastHitMessage)

            // Wyślij szczegółową informację o trafieniu do atakującego i celu
            val detailedHitMessage = "HIT_DETAILED|$targetId|$attackerId|$attackType|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}|$damage"
            sendToSpecificPlayers(detailedHitMessage)

            println("Gracz $targetId został trafiony przez $attackerId atakiem: $attackType, pozostałe zdrowie: ${targetPlayer.currentHealth}/${targetPlayer.maxHealth}")

            // Obsługa śmierci gracza jeśli potrzebna
            if (targetPlayer.currentHealth <= 0) {
                val deathMessage = "PLAYER_DIED|$targetId"
                broadcastToAll(deathMessage)
                println("Gracz $targetId zginął.")
            }
        }
    }

    // Wysyła wiadomość do wszystkich
    private suspend fun broadcastToAll(message: String) {
        connections.forEach { (_, session) ->
            session.send(message)
        }
    }

    // Wysyła wiadomość do określonych graczy
    private suspend fun sendToSpecificPlayers(message: String) {
        connections.forEach { (_, session) ->
            session.send(message)
        }
    }
}