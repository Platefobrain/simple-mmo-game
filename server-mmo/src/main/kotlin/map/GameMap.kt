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

package pl.decodesoft.map

class GameMap(private val width: Int, private val height: Int, val tileSize: Int) {
    private val tiles = Array(height) { BooleanArray(width) } // true = przechodzony, false = ściana

    fun isWalkable(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return tiles[y][x]
    }

    private fun setWalkable(x: Int, y: Int, walkable: Boolean) {
        if (x in 0 until width && y in 0 until height) {
            tiles[y][x] = walkable
        }
    }

    fun loadFromCsv(csv: String) {
        val lines = csv.trim().lines()
        val mapHeight = lines.size
        for ((y, line) in lines.withIndex()) {
            val correctedY = mapHeight - 1 - y // Odwrócenie osi Y
            val cols = line.split(",")
            for ((x, cell) in cols.withIndex()) {
                setWalkable(x, correctedY, cell.trim() == "0")
            }
        }
    }
}
