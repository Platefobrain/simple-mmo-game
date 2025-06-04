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

// Klasa do śledzenia celów ruchu dla graczy
data class MovementTarget(
    val targetX: Float,  // Końcowy cel X
    val targetY: Float,  // Końcowy cel Y
    val moveToRange: Float = 0f,  // Używany dla ataków wymagających zbliżenia
    var isMoving: Boolean = true,
    // Dodajemy ścieżkę i aktualny punkt
    val path: MutableList<Pair<Int, Int>> = mutableListOf(),
    var currentPathIndex: Int = 0
)