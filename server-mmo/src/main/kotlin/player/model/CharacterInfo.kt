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

// Informacje o postaci
@Serializable
data class CharacterInfo(
    val id: String,             // Unikalny identyfikator postaci
    val nickname: String,       // Nazwa postaci
    val characterClass: Int,    // Klasa postaci (0-łucznik, 1-mag, 2-wojownik)
    var maxHealth: Int = 100,   // Maksymalne zdrowie
    var currentHealth: Int = 100, // Aktualne zdrowie
    var level: Int = 1,
    var experience: Int = 0
)