package pl.decodesoft.enemy.spawn

import pl.decodesoft.enemy.EnemyLevelManager
import pl.decodesoft.enemy.model.Enemy
import pl.decodesoft.enemy.model.EnemyState

class EnemySpawner {
    companion object {
        const val TYPE_SHEEP = "Sheep"
        const val TYPE_WOLF = "Wolf"
        const val TYPE_BEAR = "Bear"
    }

    // Uniwersalna metoda spawnu przeciwników z poziomem
    private fun spawnEnemy(
        x: Float,
        y: Float,
        type: String,
        level: Int? = null, // Opcjonalny poziom, jeśli null to wygeneruje automatycznie
        enemies: MutableMap<String, Enemy>,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        enemyStates: MutableMap<String, EnemyState>,
        idleChangeTargetTimers: MutableMap<String, Float>,
        setRandomIdleTarget: (String, Float, Float) -> Unit
    ): Enemy {
        // Wygeneruj poziom jeśli nie podano
        val enemyLevel = level ?: EnemyLevelManager.generateLevelForArea(x, y)

        // Oblicz zdrowie na podstawie typu i poziomu
        val health = EnemyLevelManager.calculateHealthForLevel(type, enemyLevel)

        // WAŻNE: Jawnie przekaż wszystkie parametry do konstruktora
        val enemy = Enemy(
            id = java.util.UUID.randomUUID().toString(),
            x = x,
            y = y,
            type = type,
            currentHealth = health,
            maxHealth = health,
            level = enemyLevel,  // <-- Upewnij się, że to jest przekazane
            isAlive = true,
            displayName = Enemy.getDisplayNameForType(type)
        )

        enemies[enemy.id] = enemy

        // Zapisz punkt startowy i ustaw początkowy stan IDLE
        enemyHomePositions[enemy.id] = Pair(x, y)
        enemyStates[enemy.id] = EnemyState.IDLE
        idleChangeTargetTimers[enemy.id] = 0f

        // Ustaw losowy cel początkowy dla zachowania Idle
        setRandomIdleTarget(enemy.id, x, y)

        println("Spawned ${enemy.type} at level ${enemy.level} with ${enemy.maxHealth} HP") // Debug

        return enemy
    }

    // Metody specyficzne dla typów z opcjonalnym poziomem
    fun spawnSheep(
        x: Float,
        y: Float,
        level: Int? = null,
        enemies: MutableMap<String, Enemy>,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        enemyStates: MutableMap<String, EnemyState>,
        idleChangeTargetTimers: MutableMap<String, Float>,
        setRandomIdleTarget: (String, Float, Float) -> Unit
    ): Enemy = spawnEnemy(x, y, TYPE_SHEEP, level, enemies, enemyHomePositions, enemyStates, idleChangeTargetTimers, setRandomIdleTarget)

    fun spawnWolf(
        x: Float,
        y: Float,
        level: Int? = null,
        enemies: MutableMap<String, Enemy>,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        enemyStates: MutableMap<String, EnemyState>,
        idleChangeTargetTimers: MutableMap<String, Float>,
        setRandomIdleTarget: (String, Float, Float) -> Unit
    ): Enemy = spawnEnemy(x, y, TYPE_WOLF, level, enemies, enemyHomePositions, enemyStates, idleChangeTargetTimers, setRandomIdleTarget)

    fun spawnBear(
        x: Float,
        y: Float,
        level: Int? = null,
        enemies: MutableMap<String, Enemy>,
        enemyHomePositions: MutableMap<String, Pair<Float, Float>>,
        enemyStates: MutableMap<String, EnemyState>,
        idleChangeTargetTimers: MutableMap<String, Float>,
        setRandomIdleTarget: (String, Float, Float) -> Unit
    ): Enemy = spawnEnemy(x, y, TYPE_BEAR, level, enemies, enemyHomePositions, enemyStates, idleChangeTargetTimers, setRandomIdleTarget)
}