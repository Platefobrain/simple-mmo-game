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

import pl.decodesoft.MMOGame
import pl.decodesoft.network.handlers.*

// Klasa zarządzająca wszystkimi handlerami wiadomości
class MessageManager(game: MMOGame) {
    private val handlers = mutableListOf<MessageHandler>()

    init {
        // Rejestrowanie wszystkich handlerów
        handlers.add(PlayerMessageHandler(game))
        handlers.add(EnemyMessageHandler(game))
        handlers.add(ChatMessageHandler(game))
        handlers.add(CombatMessageHandler(game))
        handlers.add(PathfindingMessageHandler(game))
        handlers.add(TextMessageHandler(game))
    }

    // Przetwarzanie wiadomości
    fun processMessage(message: String) {
        // Najpierw sprawdzamy, czy wiadomość zawiera separator "|"
        if (message.contains("|")) {
            val parts = message.split("|")
            if (parts.isEmpty()) return

            val messageType = parts[0]
            val handlerFound = handlers.firstOrNull { it.canHandle(messageType) }

            handlerFound?.handleMessage(parts) ?: run {
                // Logowanie nieobsługiwanych wiadomości z formatem
                println("Nieobsługiwana wiadomość: $message")
            }
        } else {
            // To zwykła wiadomość tekstowa, przekazujemy ją jako pojedynczy element listy
            val textHandlers = handlers.filter { it.canHandle(message) }

            if (textHandlers.isNotEmpty()) {
                textHandlers.first().handleMessage(listOf(message))
            } else {
                // Logowanie nieobsługiwanych wiadomości bez formatu
                println("Nieobsługiwana wiadomość tekstowa: $message")
            }
        }
    }
}