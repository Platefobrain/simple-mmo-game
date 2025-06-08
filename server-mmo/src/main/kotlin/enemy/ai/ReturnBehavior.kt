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

package pl.decodesoft.enemy.ai

import pl.decodesoft.enemy.model.Enemy
import pl.decodesoft.enemy.model.EnemyMovementTarget
import pl.decodesoft.enemy.model.EnemyState
import pl.decodesoft.map.GameMap
import pl.decodesoft.pathfinding.findPath
import pl.decodesoft.player.model.PlayerData
import kotlin.math.pow
import kotlin.math.sqrt

class ReturnBehavior {
    fun updateReturningEnemy(
        enemy: Enemy,
        players: Map<String, PlayerData>,
        gameMap: GameMap,
        deltaTime: Float,
        detectionRange: Float,
        enemyTargets: MutableMap<String, EnemyMovementTarget>,
        enemyStates: MutableMap<String, EnemyState>,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        idleChangeTargetTimers: MutableMap<String, Float>
    ) {
        val target = enemyTargets[enemy.id] ?: return
        val homePos = enemyHomePositions[enemy.id] ?: return

        // Aktualizuj ścieżkę powrotu
        updateReturnPath(enemy, target, homePos, gameMap, deltaTime, enemyStates, idleChangeTargetTimers)

        // Sprawdź, czy przeciwnik wrócił do domu
        checkIfReachedHome(enemy, homePos, enemyStates, idleChangeTargetTimers)

        // Sprawdź, czy jakiś gracz pojawił się w zasięgu
        checkForPlayersInDetectionRange(enemy, target, players, detectionRange, enemyStates)
    }

    private fun updateReturnPath(
        enemy: Enemy,
        target: EnemyMovementTarget,
        homePos: Pair<Float, Float>,
        gameMap: GameMap,
        deltaTime: Float,
        enemyStates: MutableMap<String, EnemyState>,
        idleChangeTargetTimers: MutableMap<String, Float>
    ) {
        target.pathUpdateTimer += deltaTime

        if (target.pathUpdateTimer >= 2f) {
            target.pathUpdateTimer = 0f

            // Znajdź ścieżkę do punktu startowego
            val startTileX = (enemy.x / gameMap.tileSize).toInt()
            val startTileY = (enemy.y / gameMap.tileSize).toInt()
            val endTileX = (homePos.first / gameMap.tileSize).toInt()
            val endTileY = (homePos.second / gameMap.tileSize).toInt()

            val newPath = findPath(gameMap, startTileX, startTileY, endTileX, endTileY)
            target.path.clear()
            if (newPath.isNotEmpty()) {
                target.path.addAll(newPath)
                target.currentPathIndex = 0
                target.isMoving = true
            } else {
                // Nie można znaleźć ścieżki, teleportuj z powrotem
                enemy.x = homePos.first
                enemy.y = homePos.second
                enemyStates[enemy.id] = EnemyState.IDLE
                idleChangeTargetTimers[enemy.id] = 0f
            }
        }
    }

    private fun checkIfReachedHome(
        enemy: Enemy,
        homePos: Pair<Float, Float>,
        enemyStates: MutableMap<String, EnemyState>,
        idleChangeTargetTimers: MutableMap<String, Float>
    ) {
        val distanceFromHome = calculateDistance(enemy.x, enemy.y, homePos.first, homePos.second)

        if (distanceFromHome < 20f) {
            // Wróciłiśmy do domu, zmień stan na IDLE
            enemyStates[enemy.id] = EnemyState.IDLE
            idleChangeTargetTimers[enemy.id] = 0f
        }
    }

    private fun checkForPlayersInDetectionRange(
        enemy: Enemy,
        target: EnemyMovementTarget,
        players: Map<String, PlayerData>,
        detectionRange: Float,
        enemyStates: MutableMap<String, EnemyState>
    ) {
        players.values.forEach { player ->
            val distanceToPlayer = calculateDistance(enemy.x, enemy.y, player.x, player.y)

            if (distanceToPlayer <= detectionRange) {
                // Gracz w zasięgu, przerwij powrót i zacznij pościg
                enemyStates[enemy.id] = EnemyState.CHASE
                target.targetId = player.id
                target.targetX = player.x
                target.targetY = player.y
                target.pathUpdateTimer = 2f  // Wymuszenie aktualizacji ścieżki
            }
        }
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
}