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

package pl.decodesoft.klasy.skile

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.player.Player
import pl.decodesoft.klasy.projectiles.Arrow
import pl.decodesoft.klasy.projectiles.Fireball
import pl.decodesoft.klasy.projectiles.Sword
import io.ktor.websocket.Frame
import pl.decodesoft.enemy.EnemyClient

/**
 * Klasa zarządzająca wszystkimi umiejętnościami (pociskami) w grze
 */
class SkileManager(
    private val localPlayerId: String,
    private val players: Map<String, Player>,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?,
    private val enemies: Map<String, EnemyClient> = emptyMap()
) {
    // Lista wszystkich aktywnych umiejętności
    private val activeSkills = mutableListOf<Skile>()

    // Mapa przechowująca informacje o trafionych przeciwnikach i odpowiadających im pociskach
    private val hitEnemies = mutableMapOf<String, MutableList<String>>()

    // Dodaj nową umiejętność do listy
    fun addSkill(skill: Skile) {
        activeSkills.add(skill)
    }

    // Aktualizacja wszystkich umiejętności
    fun update(delta: Float) {
        val skillsToRemove = mutableListOf<Skile>()

        activeSkills.forEach { skill ->
            val isActive = skill.update(delta)
            if (!isActive) {
                skillsToRemove.add(skill)
            } else {
                // Sprawdź kolizje z graczami
                players.values.forEach { player ->
                    // Pomijamy kolizje z casterem
                    if (player.id != skill.casterId && player.isSelected && skill.checkCollision(player)) {
                        skillsToRemove.add(skill)
                        // Wysyłanie informacji o trafieniu
                        if (skill.casterId == localPlayerId) {
                            sendHitMessage(player.id, skill)
                        }
                    }
                }

                // Sprawdź kolizje z przeciwnikami
                enemies.values.forEach { enemy ->
                    if (enemy.isSelected && skill.checkCollision(enemy) && !isEnemyHitBySkill(enemy.id, skill.id)) {
                        skillsToRemove.add(skill)
                        // Nie musimy wysyłać informacji o trafieniu, to robi serwer
                    }
                }
            }
        }

        // Usuń umiejętności, które wyszły poza zasięg lub trafiły w cel
        activeSkills.removeAll(skillsToRemove)
    }

    // Sprawdź czy dany przeciwnik został już trafiony przez konkretną umiejętność
    private fun isEnemyHitBySkill(enemyId: String, skillId: String): Boolean {
        return hitEnemies[enemyId]?.contains(skillId) ?: false
    }

    // Renderowanie wszystkich umiejętności
    fun render(shapeRenderer: ShapeRenderer) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        activeSkills.forEach { skill ->
            skill.render(shapeRenderer)
        }
        shapeRenderer.end()
    }

    // Wysyłanie wiadomości o trafieniu
    private fun sendHitMessage(targetId: String, skill: Skile) {
        networkScope.launch {
            try {
                val hitType = when (skill) {
                    is Arrow -> "ARROW"
                    is Fireball -> "FIREBALL"
                    is Sword -> "SWORD"
                    else -> "UNKNOWN"
                }
                val message = "HIT|$targetId|${skill.casterId}|$hitType"
                session()?.send(Frame.Text(message))
            } catch (e: Exception) {
                Gdx.app.error("SkileManager", "Błąd wysyłania informacji o trafieniu: ${e.message}")
            }
        }
    }

    // Obsługa trafiania w przeciwników
    private fun handleEnemyHit(enemyId: String, attackerId: String) {
        // Znajdź umiejętność, która mogła trafić w przeciwnika
        activeSkills.filter { it.casterId == attackerId }.forEach { skill ->
            enemies[enemyId]?.let { enemy ->
                if (skill.checkCollision(enemy)) {
                    // Zarejestruj trafienie, aby nie usuwać tej samej umiejętności wielokrotnie
                    val skillsForEnemy = hitEnemies.getOrPut(enemyId) { mutableListOf() }
                    skillsForEnemy.add(skill.id)

                    // Usuń umiejętność przy następnej aktualizacji
                    skill.markForRemoval()
                }
            }
        }
    }

    fun handleSkillMessage(msgType: String, parts: List<String>) {
        when (msgType) {
            "RANGED_ATTACK" -> {
                if (parts.size >= 7) {
                    val startX = parts[1].toFloat()
                    val startY = parts[2].toFloat()
                    val dirX = parts[3].toFloat()
                    val dirY = parts[4].toFloat()
                    val casterId = parts[5]
                    val targetId = parts[6]

                    // Nie tworzymy strzały dla lokalnego gracza (już została utworzona)
                    if (casterId != localPlayerId) {
                        addSkill(Arrow(startX, startY, dirX, dirY, casterId))
                    }
                }
            }

            "SPELL_ATTACK" -> {
                if (parts.size >= 7) {
                    val startX = parts[1].toFloat()
                    val startY = parts[2].toFloat()
                    val dirX = parts[3].toFloat()
                    val dirY = parts[4].toFloat()
                    val casterId = parts[5]
                    val targetId = parts[6]

                    // Nie tworzymy kuli ognia dla lokalnego gracza
                    if (casterId != localPlayerId) {
                        addSkill(Fireball(startX, startY, dirX, dirY, casterId))
                    }
                }
            }

            "MELEE_ATTACK" -> {
                if (parts.size >= 7) {
                    val startX = parts[1].toFloat()
                    val startY = parts[2].toFloat()
                    val dirX = parts[3].toFloat()
                    val dirY = parts[4].toFloat()
                    val casterId = parts[5]
                    val targetId = parts[6]

                    // Nie tworzymy ataku mieczem dla lokalnego gracza
                    if (casterId != localPlayerId) {
                        addSkill(Sword(startX, startY, dirX, dirY, casterId))
                    }
                }
            }

            // Obsługa wiadomości o trafieniu przeciwnika
            "HIT", "HIT_DETAILED" -> {
                if (parts.size >= 3 && parts[1].startsWith("enemy_")) {
                    val enemyId = parts[1].substring(6)
                    val attackerId = parts[2]

                    // Zatrzymaj pocisk trafiony w przeciwnika
                    handleEnemyHit(enemyId, attackerId)
                }
            }
        }
    }
}