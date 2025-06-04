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

package pl.decodesoft.network.handlers

import pl.decodesoft.MMOGame
import pl.decodesoft.network.BaseMessageHandler

// Handler obsługujący wiadomości związane z przeciwnikami
class EnemyMessageHandler(game: MMOGame) : BaseMessageHandler(game) {
    override val supportedMessageTypes = setOf(
        "ENEMY_LIST", "ENEMY_HIT", "ENEMY_POSITIONS",
        "ENEMY_DIED", "ENEMY_RESPAWN"
    )

    override fun handleMessage(parts: List<String>) {
        when (parts[0]) {
            "ENEMY_LIST" -> handleEnemyListMessage(parts)
            "ENEMY_HIT" -> handleEnemyHitMessage(parts)
            "ENEMY_POSITIONS" -> handleEnemyPositionsMessage(parts)
            "ENEMY_DIED" -> handleEnemyDiedMessage(parts)
            "ENEMY_RESPAWN" -> handleEnemyRespawnMessage(parts)
        }
    }

    private fun handleEnemyListMessage(parts: List<String>) {
        println("Client: Received ENEMY_LIST: ${parts[1]}") // Debug

        val list = parts.getOrNull(1)?.split(";") ?: return
        list.forEach {
            val data = it.split(",")
            println("Client: Enemy data: $data") // Debug

            if (data.size >= 6) {
                val id = data[0]
                val x = data[1].toFloat()
                val y = data[2].toFloat()
                val type = data[3]
                val hp = data[4].toInt()
                val maxHp = data[5].toInt()
                val level = if (data.size >= 7) data[6].toIntOrNull() ?: 1 else 1

                println("Client: Parsing enemy $id - Type: $type, Level: $level, HP: $hp/$maxHp") // Debug

                // Używamy metody z MMOGame z poziomem
                game.updateEnemy(id, x, y, type, hp, maxHp, level)
            }
        }
    }

    private fun handleEnemyPositionsMessage(parts: List<String>) {
        //println("Client: Received ENEMY_POSITIONS") // Debug

        val updates = parts.getOrNull(1)?.split(";") ?: return
        updates.forEach {
            val data = it.split(",")
            //println("Client: Position data: $data") // Debug

            if (data.size >= 7) {
                val id = data[0]
                val x = data[1].toFloat()
                val y = data[2].toFloat()
                val type = data[3]
                val hp = data[4].toInt()
                val maxHp = data[5].toInt()
                val level = if (data.size >= 8) data[7].toIntOrNull() ?: 1 else 1
                val state = data[6]

               // println("Client: Updating position for enemy $id - Level: $level") // Debug

                // Używamy metody z MMOGame z poziomem
                game.updateEnemy(id, x, y, type, hp, maxHp, level, state)
            }
        }
    }

    private fun handleEnemyHitMessage(parts: List<String>) {
        if (parts.size >= 3) {
            val id = parts[1]
            val dmg = parts[2].toIntOrNull() ?: return

            // Używamy metody z MMOGame
            game.updateEnemyHealth(id, dmg)
        }
    }

    private fun handleEnemyDiedMessage(parts: List<String>) {
        if (parts.size >= 2) {
            val enemyId = parts[1]

            // Używamy metody z MMOGame
            game.markEnemyAsDead(enemyId)
        }
    }

    private fun handleEnemyRespawnMessage(parts: List<String>) {
        val respawns = parts.getOrNull(1)?.split(";") ?: return
        respawns.forEach {
            val data = it.split(",")
            if (data.size >= 7) {
                val id = data[0]
                val x = data[1].toFloat()
                val y = data[2].toFloat()
                val type = data[3]
                val hp = data[4].toInt()
                val maxHp = data[5].toInt()
                val level = if (data.size >= 8) data[7].toIntOrNull() ?: 1 else 1
                val state = data[6]

                // Używamy metody z MMOGame z poziomem
                game.respawnEnemy(id, x, y, type, hp, maxHp, level, state)
            }
        }
    }
}