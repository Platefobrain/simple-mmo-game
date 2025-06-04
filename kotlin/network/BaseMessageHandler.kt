package pl.decodesoft.network

import pl.decodesoft.MMOGame

// Abstrakcyjna klasa bazowa dla handlerów wiadomości
abstract class BaseMessageHandler(
    protected val game: MMOGame
) : MessageHandler {
    // Lista typów wiadomości obsługiwanych przez ten handler
    protected abstract val supportedMessageTypes: Set<String>

    override fun canHandle(messageType: String): Boolean {
        return messageType in supportedMessageTypes
    }
}