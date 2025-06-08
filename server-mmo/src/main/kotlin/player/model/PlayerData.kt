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

package pl.decodesoft.player.model

import kotlinx.serialization.Serializable

// Rozszerzenie klasy player data o klasę postaci i zdrowie
@Serializable
data class PlayerData(
    var x: Float,
    var y: Float,
    val id: String,
    val username: String = "",
    val characterClass: CharacterClass = CharacterClass.WARRIOR, // Domyślnie wojownik
    var maxHealth: Int = 100, // Maksymalne zdrowie
    var currentHealth: Int = 100, // Aktualne zdrowie
    var level: Int = 1,
    var experience: Int = 0,
) {
    // Metoda do ustawiania wartości zdrowia
    fun setHealth(health: Int) {
        currentHealth = health.coerceIn(0, maxHealth)
    }

    // Metoda do otrzymywania obrażeń
    fun takeDamage(amount: Int) {
        currentHealth -= amount
        if (currentHealth < 0) currentHealth = 0
    }

    // Metoda do leczenia
    fun heal(amount: Int) {
        currentHealth = (currentHealth + amount).coerceIn(0, maxHealth)
    }
}