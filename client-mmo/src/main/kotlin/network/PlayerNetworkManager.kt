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

package pl.decodesoft.network

import com.badlogic.gdx.Gdx
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


// Klasa obsługująca komunikację sieciową dla gracza
class PlayerNetworkManager(
    private val networkScope: CoroutineScope,
    private val getSession: () -> DefaultWebSocketSession?
) {
    fun sendMoveRequest(x: Float, y: Float, playerId: String) {
        networkScope.launch {
            try {
                val session = getSession()
                if (session != null) {
                    session.send("MOVE_TO|$x|$y|0|$playerId")
                } else {
                    Gdx.app.error("PlayerNetworkManager", "Sesja jest null, nie można wysłać ruchu")
                }
            } catch (e: Exception) {
                Gdx.app.error("PlayerNetworkManager", "Błąd wysyłania celu ruchu: ${e.message}")
            }
        }
    }

    @Suppress("unused")
    fun sendSkillUse(skillType: String, targetId: String?, x: Float, y: Float, playerId: String) {
        networkScope.launch {
            try {
                val session = getSession()
                if (session != null) {
                    val targetMsg = targetId ?: "null"
                    session.send("SKILL|$skillType|$x|$y|$targetMsg|$playerId")
                } else {
                    Gdx.app.error("PlayerNetworkManager", "Sesja jest null, nie można użyć umiejętności")
                }
            } catch (e: Exception) {
                Gdx.app.error("PlayerNetworkManager", "Błąd wysyłania użycia umiejętności: ${e.message}")
            }
        }
    }
}