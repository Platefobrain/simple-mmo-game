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

class ChaseBehavior {
    fun updateChasingEnemy(
        enemy: Enemy,
        players: Map<String, PlayerData>,
        gameMap: GameMap,
        deltaTime: Float,
        detectionRange: Float,
        maxRoamDistance: Float,
        idleTargetChangeTime: Float,
        enemyTargets: MutableMap<String, EnemyMovementTarget>,
        enemyStates: MutableMap<String, EnemyState>,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        idleChangeTargetTimers: MutableMap<String, Float>
    ) {
        val target = enemyTargets[enemy.id] ?: return
        val playerId = target.targetId
        val player = players[playerId]

        if (player == null) {
            // Gracz zniknął, wróć do stanu IDLE
            enemyStates[enemy.id] = EnemyState.IDLE
            idleChangeTargetTimers[enemy.id] = idleTargetChangeTime  // Wymuszenie natychmiastowej zmiany celu
            return
        }

        // Sprawdź, czy gracz jest nadal w zasięgu
        val distanceToPlayer = calculateDistance(enemy.x, enemy.y, player.x, player.y)

        if (distanceToPlayer <= detectionRange) {
            // Kontynuuj pościg
            updateChaseTarget(enemy, player, target, gameMap, deltaTime)
        } else {
            // Gracz wyszedł poza zasięg
            handlePlayerOutOfRange(
                enemy,
                target,
                maxRoamDistance,
                enemyHomePositions,
                enemyStates,
                idleTargetChangeTime,
                idleChangeTargetTimers
            )
        }
    }

    private fun updateChaseTarget(
        enemy: Enemy,
        player: PlayerData,
        target: EnemyMovementTarget,
        gameMap: GameMap,
        deltaTime: Float
    ) {
        // Aktualizuj cel na pozycję gracza
        target.targetX = player.x
        target.targetY = player.y

        // Zwiększ timer aktualizacji ścieżki
        target.pathUpdateTimer += deltaTime

        // Aktualizuj ścieżkę co kilka sekund
        if (target.pathUpdateTimer >= 2f) {
            target.pathUpdateTimer = 0f

            // Znajdź nową ścieżkę do gracza
            val startTileX = (enemy.x / gameMap.tileSize).toInt()
            val startTileY = (enemy.y / gameMap.tileSize).toInt()
            val endTileX = (player.x / gameMap.tileSize).toInt()
            val endTileY = (player.y / gameMap.tileSize).toInt()

            val newPath = findPath(gameMap, startTileX, startTileY, endTileX, endTileY)
            target.path.clear()
            target.path.addAll(newPath)
            target.currentPathIndex = 0
            target.isMoving = newPath.isNotEmpty()
        }
    }

    private fun handlePlayerOutOfRange(
        enemy: Enemy,
        target: EnemyMovementTarget,
        maxRoamDistance: Float,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        enemyStates: MutableMap<String, EnemyState>,
        idleTargetChangeTime: Float,
        idleChangeTargetTimers: MutableMap<String, Float>
    ) {
        // Sprawdź czy przeciwnik nie oddalił się zbyt daleko od domu
        val homePos = enemyHomePositions[enemy.id] ?: Pair(enemy.x, enemy.y)
        val distanceFromHome = calculateDistance(enemy.x, enemy.y, homePos.first, homePos.second)

        if (distanceFromHome > maxRoamDistance) {
            // Przeciwnik zbyt daleko od domu, zmień stan na RETURN
            switchToReturnState(enemy, target, homePos, enemyStates)
        } else {
            // Przeciwnik w dopuszczalnym zasięgu, wróć do IDLE
            enemyStates[enemy.id] = EnemyState.IDLE
            idleChangeTargetTimers[enemy.id] = idleTargetChangeTime  // Wymuszenie natychmiastowej zmiany celu
        }
    }

    private fun switchToReturnState(
        enemy: Enemy,
        target: EnemyMovementTarget,
        homePos: Pair<Float, Float>,
        enemyStates: MutableMap<String, EnemyState>
    ) {
        enemyStates[enemy.id] = EnemyState.RETURN

        // Ustaw cel na powrót do domu
        target.targetId = ""
        target.targetX = homePos.first
        target.targetY = homePos.second
        target.pathUpdateTimer = 2f  // Wymuszenie aktualizacji ścieżki
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
}