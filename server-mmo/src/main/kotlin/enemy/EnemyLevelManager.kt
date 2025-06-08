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

package pl.decodesoft.enemy

object EnemyLevelManager {

    // Bazowe statystyki dla różnych typów przeciwników (poziom 1)
    private val baseStats = mapOf(
        "Sheep" to EnemyBaseStats(health = 20, experienceReward = 10),
        "Wolf" to EnemyBaseStats(health = 40, experienceReward = 25),
        "Bear" to EnemyBaseStats(health = 80, experienceReward = 1000)
    )

    // Oblicza zdrowie przeciwnika na podstawie poziomu
    fun calculateHealthForLevel(type: String, level: Int): Int {
        val baseHealth = baseStats[type]?.health ?: 20
        return baseHealth + ((level - 1) * (baseHealth * 0.2).toInt())
    }

    // Oblicza nagrody doświadczenia za zabicie przeciwnika
    fun calculateExperienceReward(type: String, level: Int): Int {
        val baseExp = baseStats[type]?.experienceReward ?: 10
        val bonus = (level - 1) * (baseExp * 0.25).toInt()
        val totalExp = baseExp + bonus

        println("EXP Calculation: $type level $level -> base: $baseExp + bonus: $bonus = total: $totalExp")

        return totalExp
    }

    // Generuje poziom przeciwnika na podstawie obszaru mapy
    fun generateLevelForArea(x: Float, y: Float, mapWidth: Int = 120, mapHeight: Int = 120): Int {
        // Przykładowa logika - im dalej od centrum, tym wyższy poziom
        val centerX = mapWidth / 2f * 16f // 16 to tileSize
        val centerY = mapHeight / 2f * 16f

        val distanceFromCenter = kotlin.math.sqrt(
            (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)
        )

        // Normalizuj dystans do zakresu 0-1
        val maxDistance = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
        val normalizedDistance = (distanceFromCenter / maxDistance).coerceIn(0f, 1f)

        // Konwertuj na poziom (1-10)
        return (1 + (normalizedDistance * 9)).toInt().coerceIn(1, 10)
    }

    // Klasa pomocnicza dla bazowych statystyk
    private data class EnemyBaseStats(
        val health: Int,
        val experienceReward: Int
    )
}