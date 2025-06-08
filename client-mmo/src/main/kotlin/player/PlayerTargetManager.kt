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

package pl.decodesoft.player

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import pl.decodesoft.enemy.EnemyClient
import pl.decodesoft.klasy.CharacterClass

/**
 * Klasa zarządzająca celami gracza
 */
class PlayerTargetManager(
    private val camera: OrthographicCamera,
    private val localPlayer: Player,
    private val players: Map<String, Player>,
    private val enemies: Map<String, EnemyClient>,
    private val characterClass: CharacterClass,
    private val onTargetChanged: (Any?, String?) -> Unit
) {
    // Właściwości z prywatnym setterem - bardziej idiomatyczne w Kotlin
    private var selectedEntityId: String? = null
    private var selectedEntityType: String? = null
    private var lastPlayerTarget: Player? = null
    private var lastEnemyTarget: EnemyClient? = null

    fun findEntityUnderCursor(): Pair<Any, String>? {
        val mouseX = Gdx.input.x
        val mouseY = Gdx.input.y

        // Konwersja współrzędnych ekranu na współrzędne świata gry
        val worldCoords = camera.unproject(Vector3(mouseX.toFloat(), mouseY.toFloat(), 0f))

        // Ograniczenie zasięgu przeszukiwania (max dystans od gracza)
        val maxSearchRadius = 500f
        val searchRadiusSquared = maxSearchRadius * maxSearchRadius

        // Najpierw sprawdź, czy kursor jest nad którymś z graczy
        val nearbyPlayers = players.values.filter { player ->
            player.id != localPlayer.id &&
                    Vector2.dst2(localPlayer.x, localPlayer.y, player.x, player.y) <= searchRadiusSquared
        }

        // Szukaj tylko wśród przefiltrowanych graczy
        nearbyPlayers.find { player ->
            val distance = Vector2.dst(worldCoords.x, worldCoords.y, player.x, player.y)
            distance <= 15f
        }?.let {
            return Pair(it, "player")
        }

        val nearbyEnemies = enemies.values.filter { enemy ->
            val distSquared = Vector2.dst2(localPlayer.x, localPlayer.y, enemy.x, enemy.y)
            distSquared <= searchRadiusSquared
        }

        nearbyEnemies.find { enemy ->
            val distance = Vector2.dst(worldCoords.x, worldCoords.y, enemy.x, enemy.y)
            distance <= 15f
        }?.let {
            return Pair(it, "enemy")
        }

        return null
    }

    fun setTarget(entity: Any?, entityType: String?) {
        // Odznacz poprzedni cel
        clearSelectionMarkers()

        // Ustaw nowy cel
        if (entity != null && entityType != null) {
            when (entityType) {
                "player" -> {
                    val player = entity as Player
                    player.isSelected = true
                    selectedEntityId = player.id
                    selectedEntityType = "player"
                    lastPlayerTarget = player
                    lastEnemyTarget = null
                }
                "enemy" -> {
                    val enemy = entity as EnemyClient
                    enemy.isSelected = true
                    selectedEntityId = enemy.id
                    selectedEntityType = "enemy"
                    lastPlayerTarget = null
                    lastEnemyTarget = enemy
                }
            }
        } else {
            selectedEntityId = null
            selectedEntityType = null
        }

        onTargetChanged(entity, entityType)
    }

    fun clearTarget() {
        clearSelectionMarkers()
        selectedEntityId = null
        selectedEntityType = null
        onTargetChanged(null, null)
    }

    fun requestAttackOnTarget(): Boolean {
        return when (selectedEntityType) {
            "player" -> lastPlayerTarget?.let {
                characterClass.handleTargetClick(it)
            } ?: false
            "enemy" -> lastEnemyTarget?.let {
                characterClass.handleEnemyClick(it)
            } ?: false
            else -> false
        }
    }

    private fun clearSelectionMarkers() {
        selectedEntityId?.let { prevId ->
            if (selectedEntityType == "player") {
                players[prevId]?.isSelected = false
            } else if (selectedEntityType == "enemy") {
                enemies[prevId]?.isSelected = false
            }
        }
    }
}