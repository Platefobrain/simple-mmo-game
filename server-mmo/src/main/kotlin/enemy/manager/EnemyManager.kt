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

package pl.decodesoft.enemy.manager

import pl.decodesoft.enemy.ai.ChaseBehavior
import pl.decodesoft.enemy.ai.IdleBehavior
import pl.decodesoft.enemy.ai.ReturnBehavior
import pl.decodesoft.enemy.model.Enemy
import pl.decodesoft.enemy.model.EnemyMovementTarget
import pl.decodesoft.enemy.model.EnemyState
import pl.decodesoft.enemy.spawn.EnemySpawner
import pl.decodesoft.map.GameMap
import pl.decodesoft.player.model.PlayerData
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EnemyManager {
    private val enemies = mutableMapOf<String, Enemy>()
    private val enemyTargets = mutableMapOf<String, EnemyMovementTarget>()
    private val detectionRange = 200f
    private val maxRoamDistance = 200f
    private val enemyHomePositions = mutableMapOf<String, Pair<Float, Float>>()
    private val enemyStates = mutableMapOf<String, EnemyState>()
    private val idleChangeTargetTimers = mutableMapOf<String, Float>()
    private val idleTargetChangeTime = 5f
    private val respawnTimers = mutableMapOf<String, Float>()
    private val respawnTime = 15f

    // Komponenty zachowania AI
    private val idleBehavior = IdleBehavior()
    private val chaseBehavior = ChaseBehavior()
    private val returnBehavior = ReturnBehavior()

    // Spawner przeciwników
    private val enemySpawner = EnemySpawner()

    // Metody spawnowania przeciwników
    fun spawnSheep(x: Float, y: Float, level: Int? = null): Enemy =
        enemySpawner.spawnSheep(x, y, level, enemies, enemyHomePositions, enemyStates, idleChangeTargetTimers, ::setRandomIdleTarget)

    fun spawnWolf(x: Float, y: Float, level: Int? = null): Enemy =
        enemySpawner.spawnWolf(x, y, level, enemies, enemyHomePositions, enemyStates, idleChangeTargetTimers, ::setRandomIdleTarget)

    fun spawnBear(x: Float, y: Float, level: Int? = null): Enemy =
        enemySpawner.spawnBear(x, y, level, enemies, enemyHomePositions, enemyStates, idleChangeTargetTimers, ::setRandomIdleTarget)

    // Nowa metoda do respawnu z zachowaniem poziomu
    private fun respawnEnemy(enemyId: String): Enemy? {
        val deadEnemy = enemies[enemyId] ?: return null
        val homePos = enemyHomePositions[enemyId] ?: return null

        // Usuń starego przeciwnika z wszystkich map
        enemies.remove(enemyId)
        enemyStates.remove(enemyId)
        idleChangeTargetTimers.remove(enemyId)
        enemyHomePositions.remove(enemyId)

        // Utwórz nowego przeciwnika z tym samym poziomem na pozycji home
        val respawnedEnemy = when (deadEnemy.type) {
            "Sheep" -> spawnSheep(homePos.first, homePos.second, deadEnemy.level)
            "Wolf" -> spawnWolf(homePos.first, homePos.second, deadEnemy.level)
            "Bear" -> spawnBear(homePos.first, homePos.second, deadEnemy.level)
            else -> return null
        }

        return respawnedEnemy
    }

    // Metoda ustawiająca losowy cel patrolowania
    fun setRandomIdleTarget(enemyId: String, currentX: Float, currentY: Float) {
        // Sprawdź tylko czy przeciwnik istnieje (jeśli to konieczne)
        if (!enemies.containsKey(enemyId)) return

        val homePos = enemyHomePositions[enemyId] ?: Pair(currentX, currentY)

        // Losowy kąt i dystans
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val distance = Random.nextDouble(30.0, 100.0)  // Dystans patrolu

        // Oblicz nowy cel
        val newX = homePos.first + (distance * cos(angle)).toFloat()
        val newY = homePos.second + (distance * sin(angle)).toFloat()

        // Sprawdź, czy cel jest w prawidłowym obszarze mapy (prosta walidacja)
        val tileSize = 16f // Ustaw zgodnie z rozmiarem Twojej mapy
        val tileX = (newX / tileSize).toInt().coerceIn(0, 119) // Zakładam, że mapSize to 120x120
        val tileY = (newY / tileSize).toInt().coerceIn(0, 119)

        // Utwórz lub zmodyfikuj cel
        val target = enemyTargets[enemyId] ?: EnemyMovementTarget(
            targetX = tileX * tileSize + tileSize/2,
            targetY = tileY * tileSize + tileSize/2
        )

        target.targetId = ""  // Brak konkretnego celu
        target.targetX = tileX * tileSize + tileSize/2
        target.targetY = tileY * tileSize + tileSize/2
        target.isMoving = true
        target.path.clear()   // Wyczyść starą ścieżkę
        target.currentPathIndex = 0
        target.pathUpdateTimer = 2f  // Wymuszenie aktualizacji ścieżki

        enemyTargets[enemyId] = target
    }

    // Metody dostępowe
    fun getEnemies(): Collection<Enemy> = enemies.values.filter { it.isAlive }

    // Metoda zadawania obrażeń
    fun damageEnemy(id: String, amount: Int): Boolean {
        val enemy = enemies[id] ?: return false
        enemy.currentHealth -= amount
        if (enemy.currentHealth <= 0) {
            enemy.currentHealth = 0
            enemy.isAlive = false
            respawnTimers[id] = respawnTime
            return true
        }
        return false
    }

    // Metoda respawnu przeciwników z zachowaniem poziomu
    fun updateRespawnTimers(deltaTime: Float): List<Enemy> {
        val respawnedEnemies = mutableListOf<String>()
        val resurrectedEnemies = mutableListOf<Enemy>()

        respawnTimers.forEach { (enemyId, timeLeft) ->
            val newTime = timeLeft - deltaTime
            if (newTime <= 0) {
                // Czas respawnu upłynął - używamy nowej metody respawnu
                val respawnedEnemy = respawnEnemy(enemyId)

                if (respawnedEnemy != null) {
                    respawnedEnemies.add(enemyId)
                    resurrectedEnemies.add(respawnedEnemy)
                }
            } else {
                // Aktualizuj pozostały czas
                respawnTimers[enemyId] = newTime
            }
        }

        // Usuń przeciwników, którzy zostali odrodzeni, z listy timerów
        respawnedEnemies.forEach { respawnTimers.remove(it) }

        return resurrectedEnemies
    }

    // Główna metoda aktualizacji celów przeciwników
    fun updateEnemyTargets(players: Map<String, PlayerData>, gameMap: GameMap, deltaTime: Float) {
        // Dla każdego żywego przeciwnika
        enemies.values.filter { it.isAlive }.forEach { enemy ->
            // Aktualizuj timer zmiany celu dla zachowania Idle
            idleChangeTargetTimers[enemy.id] = (idleChangeTargetTimers[enemy.id] ?: 0f) + deltaTime

            // Pobierz aktualny stan przeciwnika
            val currentState = enemyStates[enemy.id] ?: EnemyState.IDLE

            when (currentState) {
                EnemyState.IDLE ->
                    idleBehavior.updateIdleEnemy(
                        enemy,
                        players,
                        gameMap,
                        deltaTime,
                        detectionRange,
                        idleTargetChangeTime,
                        idleChangeTargetTimers,
                        enemyTargets,
                        enemyStates,
                        ::setRandomIdleTarget
                    )

                EnemyState.CHASE ->
                    chaseBehavior.updateChasingEnemy(
                        enemy,
                        players,
                        gameMap,
                        deltaTime,
                        detectionRange,
                        maxRoamDistance,
                        idleTargetChangeTime,
                        enemyTargets,
                        enemyStates,
                        enemyHomePositions,
                        idleChangeTargetTimers
                    )

                EnemyState.RETURN ->
                    returnBehavior.updateReturningEnemy(
                        enemy,
                        players,
                        gameMap,
                        deltaTime,
                        detectionRange,
                        enemyTargets,
                        enemyStates,
                        enemyHomePositions,
                        idleChangeTargetTimers
                    )
            }
        }
    }

    // Funkcja aktualizująca pozycje przeciwników
    fun updateEnemyPositions(gameMap: GameMap, deltaTimeSeconds: Float): List<Enemy> {
        val updatedEnemies = mutableListOf<Enemy>()

        enemyTargets.forEach { (enemyId, target) ->
            if (!target.isMoving || target.path.isEmpty()) return@forEach

            val enemy = enemies[enemyId] ?: return@forEach
            if (!enemy.isAlive) return@forEach

            // Ustal prędkość w zależności od stanu
            val speed = when (enemyStates[enemyId]) {
                EnemyState.IDLE -> 30f
                EnemyState.CHASE -> 70f
                EnemyState.RETURN -> 60f
                else -> 30f // Domyślna prędkość
            }

            // Jeśli doszliśmy do końca ścieżki
            if (target.currentPathIndex >= target.path.size) {
                handleDirectMovement(enemy, target, gameMap, deltaTimeSeconds, speed, updatedEnemies)
            } else {
                moveAlongPath(enemy, target, gameMap, deltaTimeSeconds, speed, updatedEnemies)
            }
        }

        return updatedEnemies
    }

    private fun handleDirectMovement(
        enemy: Enemy,
        target: EnemyMovementTarget,
        gameMap: GameMap,
        deltaTimeSeconds: Float,
        speed: Float,
        updatedEnemies: MutableList<Enemy>
    ) {
        // Sprawdź, czy jesteśmy wystarczająco blisko celu
        val distToFinalTarget = sqrt(
            (target.targetX - enemy.x).pow(2) +
                    (target.targetY - enemy.y).pow(2)
        )

        if (distToFinalTarget < 5f) {
            // Doszliśmy do celu
            target.isMoving = false
            if (enemyStates[enemy.id] == EnemyState.IDLE) {
                // W stanie IDLE po dojściu do celu zaczynamy szukać nowego
                idleChangeTargetTimers[enemy.id] = idleTargetChangeTime
            }
            return
        }

        // Bezpośredni ruch do celu
        val dirX = (target.targetX - enemy.x) / distToFinalTarget
        val dirY = (target.targetY - enemy.y) / distToFinalTarget
        val moveDistance = speed * deltaTimeSeconds

        val newX = enemy.x + dirX * moveDistance
        val newY = enemy.y + dirY * moveDistance
        val tileX = (newX / gameMap.tileSize).toInt()
        val tileY = (newY / gameMap.tileSize).toInt()

        if (gameMap.isWalkable(tileX, tileY)) {
            enemy.x = newX
            enemy.y = newY
            updatedEnemies.add(enemy)
        } else {
            // Jeśli ruch niemożliwy, spróbuj znaleźć nową ścieżkę w następnej klatce
            target.pathUpdateTimer = 2f  // Wymuszenie aktualizacji ścieżki
        }
    }

    private fun moveAlongPath(
        enemy: Enemy,
        target: EnemyMovementTarget,
        gameMap: GameMap,
        deltaTimeSeconds: Float,
        speed: Float,
        updatedEnemies: MutableList<Enemy>
    ) {
        // Poruszanie do następnego punktu ścieżki
        val nextPathPoint = target.path[target.currentPathIndex]
        val nextX = (nextPathPoint.first * gameMap.tileSize) + (gameMap.tileSize / 2f)
        val nextY = (nextPathPoint.second * gameMap.tileSize) + (gameMap.tileSize / 2f)

        val distToNextPoint = sqrt(
            (nextX - enemy.x).pow(2) +
                    (nextY - enemy.y).pow(2)
        )

        if (distToNextPoint < 5f) {
            target.currentPathIndex++
            return
        }

        val dirX = (nextX - enemy.x) / distToNextPoint
        val dirY = (nextY - enemy.y) / distToNextPoint
        val moveDistance = speed * deltaTimeSeconds

        enemy.x += dirX * moveDistance
        enemy.y += dirY * moveDistance
        updatedEnemies.add(enemy)
    }

    fun getEnemyState(enemyId: String): String {
        return enemyStates[enemyId]?.toString() ?: "IDLE" // Domyślnie IDLE, jeśli stan nie jest określony
    }
}