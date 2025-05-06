package pl.decodesoft.msg

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ChatSystem(
    private val localPlayerId: String,
    private val username: String,
    private val networkScope: CoroutineScope,
    private val getSession: () -> DefaultWebSocketSession?
) {
    private val activeMessages = ConcurrentLinkedQueue<ChatMessage>() // Aktywne wiadomości (widoczne normalnie)
    private val allMessages = mutableListOf<ChatMessage>() // Pełna historia wszystkich wiadomości
    private val isTyping = AtomicBoolean(false)
    private var currentInput = StringBuilder()
    private val maxMessages = 10
    private val maxMessageLifetime = 10f // czas życia wiadomości w sekundach
    private val chatInputProcessor = ChatInputProcessor()
    private var originalInputProcessor: com.badlogic.gdx.InputProcessor? = null

    // Śledzenie, czy Enter został właśnie zwolniony
    private var wasEnterPressed = false

    // Struktura wiadomości czatu
    data class ChatMessage(
        val senderId: String,
        val senderName: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        var lifetime: Float = 0f
    )

    // Input Processor dla obsługi wpisywania tekstu
    inner class ChatInputProcessor : InputAdapter() {
        override fun keyTyped(character: Char): Boolean {
            if (character >= ' ') {
                currentInput.append(character)
                return true
            }
            return false
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACKSPACE && currentInput.isNotEmpty()) {
                currentInput.deleteCharAt(currentInput.length - 1)
                return true
            } else if (keycode == Input.Keys.ESCAPE) {
                currentInput.clear()
                endTyping()
                return true
            } else if (keycode == Input.Keys.ENTER) {
                val message = currentInput.toString().trim()
                if (message.isNotEmpty()) {
                    sendMessage(message)
                }
                currentInput.clear()
                endTyping() // Automatycznie zamyka tryb czatu po wysłaniu
                wasEnterPressed = true // Ustawienie flagi, aby zapobiec natychmiastowemu wejściu w tryb czatu
                return true
            }
            return false
        }
    }

    fun handleInput(): Boolean {
        // Sprawdź, czy Enter został właśnie zwolniony
        if (wasEnterPressed) {
            if (!Gdx.input.isKeyPressed(Input.Keys.ENTER)) {
                wasEnterPressed = false // Zresetuj flagę, gdy Enter zostanie zwolniony
            }
            return isTyping.get()
        }

        // Jeśli naciśnięto Enter, rozpocznij wprowadzanie
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (!isTyping.get()) {
                // Rozpocznij pisanie
                startTyping()
                return true
            }
        }

        return isTyping.get()
    }

    private fun startTyping() {
        if (!isTyping.get()) {
            isTyping.set(true)
            originalInputProcessor = Gdx.input.inputProcessor
            Gdx.input.inputProcessor = chatInputProcessor
        }
    }

    private fun endTyping() {
        if (isTyping.get()) {
            isTyping.set(false)
            Gdx.input.inputProcessor = originalInputProcessor
        }
    }

    private fun sendMessage(content: String) {
        val message = ChatMessage(localPlayerId, username, content)
        addMessage(message)

        // Wyślij wiadomość do serwera
        networkScope.launch {
            try {
                getSession()?.send("CHAT|$localPlayerId|$username|$content")
            } catch (e: Exception) {
                Gdx.app.error("Chat", "Error sending message: ${e.message}")
            }
        }
    }

    fun receiveMessage(senderId: String, senderName: String, content: String) {
        if (senderId != localPlayerId) {
            addMessage(ChatMessage(senderId, senderName, content))
        }
    }

    private fun addMessage(message: ChatMessage) {
        // Dodaj do aktywnych wiadomości
        activeMessages.add(message)
        // Dodaj również do pełnej historii
        allMessages.add(message)

        // Ogranicz liczbę aktywnych wiadomości
        while (activeMessages.size > maxMessages) {
            activeMessages.poll()
        }
    }

    fun update(delta: Float) {
        // Aktualizuj czas życia wiadomości (tylko dla aktywnych wiadomości w trybie normalnym)
        if (!isTyping.get()) {
            val iterator = activeMessages.iterator()
            while (iterator.hasNext()) {
                val message = iterator.next()
                message.lifetime += delta
                if (message.lifetime > maxMessageLifetime) {
                    iterator.remove()
                }
            }
        }
    }

    fun render(batch: SpriteBatch, font: BitmapFont) {

        // Ustawienia czcionki
        val originalScale = font.data.scaleX
        font.data.setScale(1.0f)

        // Ustal wysokość ekranu i pole wprowadzania tekstu
        val screenHeight = Gdx.graphics.height.toFloat()
        val inputHeight = 30f // Wysokość pola wprowadzania
        val lineHeight = font.lineHeight
        val baseX = 10f

        // pozycja
        val inputY = inputHeight - 10f

        if (isTyping.get()) {
            // Rysuj pole tekstowe na dole
            font.color = Color.WHITE
            val text = "Say: ${currentInput}_" // Dodaj kursor
            font.draw(batch, text, baseX, inputY)

            // Gdy tryb czatu jest aktywny, pokazuj wiadomości nad polem tekstowym
            val messagesToShow = if (allMessages.size > 20) {
                allMessages.takeLast(20)
            } else {
                allMessages.toList()
            }

            // Oblicz maksymalną wysokość dla wiadomości (przestrzeń ponad polem tekstowym)
            val maxMessagesHeight = screenHeight - inputHeight - 20f
            val availableLines = (maxMessagesHeight / lineHeight).toInt()

            // Oblicz, które wiadomości pokazać, jeśli jest ich więcej niż mieści się na ekranie
            val visibleCount = minOf(messagesToShow.size, availableLines)
            val startIndex = maxOf(0, messagesToShow.size - visibleCount)
            val endIndex = messagesToShow.size

            // render xd
            for (i in startIndex until endIndex) {
                val message = messagesToShow[i]
                val index = endIndex - i // Odwrócenie kolejności

                // Oblicz pozycję Y dla tej wiadomości (zaczynając od pozycji pola tekstowego)
                val currentY = inputY + index * lineHeight

                // Ustaw kolor
                if (message.senderId == localPlayerId) {
                    font.color = Color(0.2f, 0.8f, 0.2f, 0.8f) // Zielony dla własnych wiadomości
                } else {
                    font.color = Color(0.8f, 0.8f, 1f, 0.8f) // Niebieski dla innych
                }

                // Wyświetl wiadomość
                val messageText = "${message.senderName}: ${message.content}"
                font.draw(batch, messageText, baseX, currentY)
            }
        } else {
            // xd
            val messagesToShow = activeMessages.toList()

            // podpowiedz
            font.color = Color(0.7f, 0.7f, 0.7f, 0.5f)
            font.draw(batch, "Naciśnij Enter, aby czatować", baseX, inputY)

            // Renderuj wiadomości nad podpowiedzią, od najnowszej (na dole) do najstarszej (wyżej)
            for (i in messagesToShow.indices) {
                val message = messagesToShow[messagesToShow.size - 1 - i] // Odwrócenie kolejności (najnowsze na dole)

                // Oblicz pozycję Y dla tej wiadomości zaczynając od podpowiedzi
                val currentY = inputY + (i + 1) * lineHeight

                // Oblicz przezroczystość bazując na czasie życia wiadomości
                val alpha = if (message.lifetime > maxMessageLifetime - 2f) {
                    (maxMessageLifetime - message.lifetime) / 2f
                } else {
                    1f
                }

                // Ustaw kolor
                if (message.senderId == localPlayerId) {
                    font.color = Color(0.2f, 0.8f, 0.2f, alpha) // Zielony dla własnych wiadomości
                } else {
                    font.color = Color(0.8f, 0.8f, 1f, alpha) // Niebieski dla innych
                }

                // Wyświetl wiadomość
                val text = "${message.senderName}: ${message.content}"
                font.draw(batch, text, baseX, currentY)
            }
        }

        // Przywróć oryginalną skalę czcionki
        font.data.setScale(originalScale)

    }
}