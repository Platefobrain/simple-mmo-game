/*
 * Licencja: Wszelkie prawa zastrzeżone.
 * Możesz używać, modyfikować, kopiować i dystrybuować ten projekt do własnych celów,
 * ale nie możesz używać go do celów komercyjnych, chyba że uzyskasz zgodę od autora.
 * Projekt jest dostarczany "tak jak jest", bez żadnych gwarancji.
 * Używasz go na własne ryzyko.
 * Autor: Copyright [2025] [Platefobrain]
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
