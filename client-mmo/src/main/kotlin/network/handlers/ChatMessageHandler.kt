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

import com.badlogic.gdx.Gdx
import pl.decodesoft.MMOGame
import pl.decodesoft.network.BaseMessageHandler

// Handler obsługujący wiadomości czatu
class ChatMessageHandler(game: MMOGame) : BaseMessageHandler(game) {
    override val supportedMessageTypes = setOf("CHAT")

    override fun handleMessage(parts: List<String>) {
        when (parts[0]) {
            "CHAT" -> handleChatMessage(parts)
        }
    }

    private fun handleChatMessage(parts: List<String>) {
        if (parts.size >= 4) {
            val senderId = parts[1]
            val senderName = parts[2]
            // Łączymy wszystkie elementy treści wiadomości
            val content = parts.subList(3, parts.size).joinToString("|")

            // Używamy nowej publicznej metody zamiast bezpośredniego dostępu do chatSystem
            Gdx.app.postRunnable {
                game.receiveNetworkChatMessage(senderId, senderName, content)
            }
        }
    }
}