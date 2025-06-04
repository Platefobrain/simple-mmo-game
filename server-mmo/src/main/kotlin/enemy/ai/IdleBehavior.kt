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

class IdleBehavior {
    fun updateIdleEnemy(
        enemy: Enemy,
        players: Map<String, PlayerData>,
        gameMap: GameMap,
        deltaTime: Float,
        detectionRange: Float,
        idleTargetChangeTime: Float,
        idleChangeTargetTimers: MutableMap<String, Float>,
        enemyTargets: MutableMap<String, EnemyMovementTarget>,
        enemyStates: MutableMap<String, EnemyState>,
        setRandomIdleTarget: (String, Float, Float) -> Unit
    ) {
        // Znajdź najbliższego gracza w zasięgu detekcji
        val closestPlayer = findNearestPlayerInRange(enemy, players, detectionRange)

        if (closestPlayer != null) {
            // Znaleziono gracza w zasięgu - zmień stan na CHASE
            switchToChaseState(enemy, closestPlayer, enemyTargets, enemyStates)
        } else {
            // Żaden gracz nie jest w zasięgu - kontynuuj patrolowanie
            updatePatrolling(enemy, gameMap, deltaTime, idleTargetChangeTime, idleChangeTargetTimers, enemyTargets, setRandomIdleTarget)
        }
    }

    private fun findNearestPlayerInRange(
        enemy: Enemy,
        players: Map<String, PlayerData>,
        detectionRange: Float
    ): PlayerData? {
        var closestPlayer: PlayerData? = null
        var closestDistance = Float.MAX_VALUE

        players.values.forEach { player ->
            val distance = calculateDistance(enemy.x, enemy.y, player.x, player.y)

            if (distance < detectionRange && distance < closestDistance) {
                closestDistance = distance
                closestPlayer = player
            }
        }

        return closestPlayer
    }

    private fun switchToChaseState(
        enemy: Enemy,
        player: PlayerData,
        enemyTargets: MutableMap<String, EnemyMovementTarget>,
        enemyStates: MutableMap<String, EnemyState>
    ) {
        enemyStates[enemy.id] = EnemyState.CHASE

        // Ustaw nowy cel (gracza)
        val target = enemyTargets[enemy.id] ?: EnemyMovementTarget(
            targetId = player.id,
            targetX = player.x,
            targetY = player.y
        )

        target.targetId = player.id
        target.targetX = player.x
        target.targetY = player.y
        target.isMoving = true
        target.pathUpdateTimer = 2f  // Wymuszenie natychmiastowej aktualizacji ścieżki

        enemyTargets[enemy.id] = target
    }

    private fun updatePatrolling(
        enemy: Enemy,
        gameMap: GameMap,
        deltaTime: Float,
        idleTargetChangeTime: Float,
        idleChangeTargetTimers: MutableMap<String, Float>,
        enemyTargets: MutableMap<String, EnemyMovementTarget>,
        setRandomIdleTarget: (String, Float, Float) -> Unit
    ) {
        val target = enemyTargets[enemy.id]

        if (target == null) {
            // Jeśli przeciwnik nie ma celu, ustaw nowy
            setRandomIdleTarget(enemy.id, enemy.x, enemy.y)
        } else if ((idleChangeTargetTimers[enemy.id] ?: 0f) >= idleTargetChangeTime || !target.isMoving) {
            // Czas na zmianę celu patrolu lub poprzedni cel został osiągnięty
            idleChangeTargetTimers[enemy.id] = 0f
            setRandomIdleTarget(enemy.id, enemy.x, enemy.y)
        } else {
            updatePatrolPath(enemy, target, gameMap, deltaTime)
        }
    }

    private fun updatePatrolPath(
        enemy: Enemy,
        target: EnemyMovementTarget,
        gameMap: GameMap,
        deltaTime: Float
    ) {
        // Aktualizuj ścieżkę, jeśli nie została jeszcze wygenerowana
        if (target.path.isEmpty() && target.pathUpdateTimer >= 2f) {
            target.pathUpdateTimer = 0f

            // Znajdź ścieżkę
            val startTileX = (enemy.x / gameMap.tileSize).toInt()
            val startTileY = (enemy.y / gameMap.tileSize).toInt()
            val endTileX = (target.targetX / gameMap.tileSize).toInt()
            val endTileY = (target.targetY / gameMap.tileSize).toInt()

            val newPath = findPath(gameMap, startTileX, startTileY, endTileX, endTileY)
            if (newPath.isNotEmpty()) {
                target.path.clear()
                target.path.addAll(newPath)
                target.currentPathIndex = 0
                target.isMoving = true
            } else {
                // Nie można znaleźć ścieżki, ustaw nowy cel - to trzeba będzie przesłać do głównego menedżera
            }
        } else {
            target.pathUpdateTimer += deltaTime
        }
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
}