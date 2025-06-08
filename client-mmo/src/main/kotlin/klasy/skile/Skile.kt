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

package pl.decodesoft.klasy.skile

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import pl.decodesoft.enemy.EnemyClient
import pl.decodesoft.player.Player

/**
 * Interfejs dla wszystkich typów umiejętności w grze
 */
interface Skile {
    // Podstawowe właściwości umiejętności
    val id: String
    val x: Float
    val y: Float
    val casterId: String
    val color: Color
    val size: Float

    // Dodana właściwość do oznaczania pocisków do usunięcia
    var isToRemove: Boolean

    // Metody, które muszą zaimplementować wszystkie umiejętności
    fun update(delta: Float): Boolean
    fun checkCollision(player: Player): Boolean
    fun checkCollision(enemy: EnemyClient): Boolean
    fun render(shapeRenderer: ShapeRenderer)
    fun markForRemoval()
}