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

package pl.decodesoft.msg

import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

// Klasa do obsługi wiadomości czatu na serwerze
class ChatManager {
    // Historia wiadomości czatu (ostatnie N wiadomości)
    private val chatHistory = mutableListOf<ChatMessage>()
    private val maxHistorySize = 100

    // Struktura wiadomości czatu
    data class ChatMessage(
        val senderId: String,
        val senderName: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Dodaj wiadomość do historii
    private fun addMessage(message: ChatMessage) {
        chatHistory.add(message)
        // Ogranicz rozmiar historii
        if (chatHistory.size > maxHistorySize) {
            chatHistory.removeAt(0)
        }
    }

    // Wyślij wiadomość do wszystkich graczy
    suspend fun broadcastMessage(
        connections: ConcurrentHashMap<String, DefaultWebSocketSession>,
        message: ChatMessage
    ) {
        // Dodaj wiadomość do historii
        addMessage(message)

        // Format wiadomości czatu
        val chatMessage = "CHAT|${message.senderId}|${message.senderName}|${message.content}"

        // Wyślij do wszystkich
        connections.forEach { (_, session) ->
            session.send(chatMessage)
        }
    }
}