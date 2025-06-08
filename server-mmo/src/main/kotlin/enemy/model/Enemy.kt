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

package pl.decodesoft.enemy.model

data class Enemy(
    val id: String = java.util.UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val type: String = "Sheep",
    var currentHealth: Int = 20,
    val maxHealth: Int = 20,
    var level: Int = 1,
    var isAlive: Boolean = true,
    val displayName: String = getDisplayNameForType(type)
) {
    companion object {
        // Metoda zwracająca przyjazną nazwę na podstawie typu przeciwnika
        fun getDisplayNameForType(type: String): String {
            return when (type) {
                "Sheep" -> "Owca"
                "Wolf" -> "Wilk"
                "Bear" -> "Niedźwiedź"
                else -> type
            }
        }
    }
}