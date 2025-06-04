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

package pl.decodesoft.level

import pl.decodesoft.player.model.PlayerData

object LevelManager {
    private fun getXPForNextLevel(level: Int): Int {
        return 100 * level
    }

    fun addExperience(player: PlayerData, amount: Int): Boolean {
        player.experience += amount
        var leveledUp = false

        while (player.experience >= getXPForNextLevel(player.level)) {
            player.experience -= getXPForNextLevel(player.level)
            player.level++
            leveledUp = true

            // np. zwiększamy zdrowie przy awansie
            player.maxHealth += 10
            player.currentHealth = player.maxHealth
        }

        return leveledUp
    }
}