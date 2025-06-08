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

package pl.decodesoft.pathfinding

import pl.decodesoft.map.GameMap
import java.util.*

data class Node(val x: Int, val y: Int, val cost: Int, val parent: Node?)

fun findPath(map: GameMap, startX: Int, startY: Int, endX: Int, endY: Int): List<Pair<Int, Int>> {
    val open = PriorityQueue<Node>(compareBy { it.cost })
    val visited = mutableSetOf<Pair<Int, Int>>()

    open.add(Node(startX, startY, 0, null))

    while (open.isNotEmpty()) {
        val current = open.poll()
        val key = current.x to current.y
        if (key in visited) continue
        visited.add(key)

        if (current.x == endX && current.y == endY) {
            val path = mutableListOf<Pair<Int, Int>>()
            var node: Node? = current
            while (node != null) {
                path.add(node.x to node.y)
                node = node.parent
            }
            return path.reversed()
        }

        val directions = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
        for ((dx, dy) in directions) {
            val nx = current.x + dx
            val ny = current.y + dy
            if (map.isWalkable(nx, ny)) {
                open.add(Node(nx, ny, current.cost + 1, current))
            }
        }
    }

    return emptyList()
}
