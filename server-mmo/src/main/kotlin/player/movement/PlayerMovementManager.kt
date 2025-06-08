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

package pl.decodesoft.player.movement

import io.ktor.websocket.*
import pl.decodesoft.map.GameMap
import pl.decodesoft.pathfinding.findPath
import pl.decodesoft.player.model.PlayerData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.sqrt

class PlayerMovementManager(
    private val connections: ConcurrentHashMap<String, DefaultWebSocketSession>,
    private val playerPositions: ConcurrentHashMap<String, PlayerData>,
    private val playerMovementTargets: ConcurrentHashMap<String, MovementTarget>
) {
    // Funkcja pomocnicza do obliczania odległości
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }

    // Główna funkcja aktualizacji pozycji graczy
    suspend fun updatePlayerPositions(gameMap: GameMap, deltaTimeSeconds: Float, speed: Float) {
        playerMovementTargets.forEach { (playerId, target) ->
            if (!target.isMoving || target.path.isEmpty()) return@forEach

            playerPositions[playerId]?.let { player ->
                // Jeśli doszliśmy do końca ścieżki
                if (target.currentPathIndex >= target.path.size) {
                    handleEndOfPathMovement(playerId, player, target, gameMap, deltaTimeSeconds, speed)
                } else {
                    // Poruszanie się do następnego punktu ścieżki
                    moveAlongPath(playerId, player, target, deltaTimeSeconds, speed)
                }
            }
        }

        // Usuń zakończone cele ruchu
        playerMovementTargets.entries.removeIf { !it.value.isMoving }
    }

    // Obsługa ruchu na końcu ścieżki
    private suspend fun handleEndOfPathMovement(
        playerId: String,
        player: PlayerData,
        target: MovementTarget,
        gameMap: GameMap,
        deltaTimeSeconds: Float,
        speed: Float
    ) {
        // Oblicz odległość do końcowego celu
        val distToFinalTarget = calculateDistance(player.x, player.y, target.targetX, target.targetY)

        // Sprawdź warunki zatrzymania ruchu
        if (shouldStopMoving(target, distToFinalTarget)) {
            target.isMoving = false
            return
        }

        // Próba bezpośredniego ruchu do celu
        moveDirectlyToTarget(playerId, player, target, gameMap, distToFinalTarget, deltaTimeSeconds, speed)
    }

    // Sprawdź warunki zatrzymania ruchu
    private fun shouldStopMoving(target: MovementTarget, distToTarget: Float): Boolean {
        // Jeśli osiągnęliśmy wymagany zasięg do celu dla ataków
        if (target.moveToRange > 0f && distToTarget <= target.moveToRange) {
            return true
        }

        // Jeśli jesteśmy wystarczająco blisko końcowego celu
        if (distToTarget < 5f) {
            return true
        }

        return false
    }

    // Bezpośredni ruch do celu
    private suspend fun moveDirectlyToTarget(
        playerId: String,
        player: PlayerData,
        target: MovementTarget,
        gameMap: GameMap,
        distToTarget: Float,
        deltaTimeSeconds: Float,
        speed: Float
    ) {
        // Oblicz kierunek ruchu
        val dirX = (target.targetX - player.x) / distToTarget
        val dirY = (target.targetY - player.y) / distToTarget
        val moveDistance = speed * deltaTimeSeconds

        // Oblicz nową pozycję
        val newX = player.x + dirX * moveDistance
        val newY = player.y + dirY * moveDistance
        val tileX = (newX / gameMap.tileSize).toInt()
        val tileY = (newY / gameMap.tileSize).toInt()

        if (gameMap.isWalkable(tileX, tileY)) {
            // Wykonaj ruch
            movePlayer(playerId, player, newX, newY)
        } else {
            // Jeśli ruch niemożliwy, znajdź nową ścieżkę
            findNewPath(playerId, player, target, gameMap)
        }
    }

    // Ruch wzdłuż ścieżki
    private suspend fun moveAlongPath(
        playerId: String,
        player: PlayerData,
        target: MovementTarget,
        deltaTimeSeconds: Float,
        speed: Float
    ) {
        // Pobierz następny punkt ścieżki
        val nextPathPoint = target.path[target.currentPathIndex]
        val nextX = (nextPathPoint.first * 16) + (16 / 2f) // Używamy stałego rozmiaru płytki 16
        val nextY = (nextPathPoint.second * 16) + (16 / 2f)

        // Oblicz odległość do następnego punktu
        val distToNextPoint = calculateDistance(player.x, player.y, nextX, nextY)

        // Jeśli osiągnęliśmy punkt, przejdź do następnego
        if (distToNextPoint < 5f) {
            target.currentPathIndex++
            return
        }

        // Oblicz kierunek i wykonaj ruch
        val dirX = (nextX - player.x) / distToNextPoint
        val dirY = (nextY - player.y) / distToNextPoint
        val moveDistance = speed * deltaTimeSeconds

        // Aktualizuj pozycję
        val newX = player.x + dirX * moveDistance
        val newY = player.y + dirY * moveDistance

        movePlayer(playerId, player, newX, newY)
    }

    // Aktualizacja pozycji gracza i rozgłoszenie
    private suspend fun movePlayer(playerId: String, player: PlayerData, newX: Float, newY: Float) {
        player.x = newX
        player.y = newY
        val moveMessage = "MOVE|${player.x}|${player.y}|$playerId"

        // Wysyłamy wiadomość do wszystkich klientów
        connections.forEach { (_, session) ->
            session.send(moveMessage)
        }
    }

    // Znajdź nową ścieżkę, gdy bezpośredni ruch jest niemożliwy
    private suspend fun findNewPath(
        playerId: String,
        player: PlayerData,
        target: MovementTarget,
        gameMap: GameMap
    ) {
        val startTileX = (player.x / gameMap.tileSize).toInt()
        val startTileY = (player.y / gameMap.tileSize).toInt()
        val endTileX = (target.targetX / gameMap.tileSize).toInt()
        val endTileY = (target.targetY / gameMap.tileSize).toInt()

        val newPath = findPath(gameMap, startTileX, startTileY, endTileX, endTileY)
        if (newPath.isNotEmpty()) {
            // Aktualizuj ścieżkę
            target.path.clear()
            target.path.addAll(newPath)
            target.currentPathIndex = 0

            // Poinformuj klienta o nowej ścieżce
            val pathString = newPath.joinToString(",") { "${it.first}:${it.second}" }
            connections[playerId]?.send("PATH|$pathString")
        } else {
            // Jeśli nadal nie można znaleźć ścieżki, zatrzymaj ruch
            target.isMoving = false
            connections[playerId]?.send("MOVE_FAILED|$playerId|no_path")
        }
    }
}