package pl.decodesoft.network

// Interfejs dla obsługi wiadomości sieciowych
interface MessageHandler {
    // Sprawdza, czy ten handler obsługuje dany typ wiadomości
    fun canHandle(messageType: String): Boolean

    // Przetwarza wiadomość określonego typu
    fun handleMessage(parts: List<String>)
}