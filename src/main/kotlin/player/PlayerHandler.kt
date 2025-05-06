package pl.decodesoft.player

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.settings.InputHandler
import pl.decodesoft.klasy.Archer
import pl.decodesoft.klasy.Mage
import pl.decodesoft.klasy.Warrior
import pl.decodesoft.klasy.skile.SkileManager

/**
 * Klasa zarządzająca obsługą gracza i jego umiejętnościami w zależności od klasy postaci
 */
class PlayerHandler(
    private val localPlayer: Player,
    private val players: Map<String, Player>,
    private val camera: OrthographicCamera,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?,
    private val skileManager: SkileManager  // Zamiast arrows i fireballs
) {
    private var selectedPlayerId: String? = null

    // Klasa do przechowywania efektów tekstowych obrażeń
    private class DamageText(
        var x: Float,
        var y: Float,
        val text: String,
        val color: Color,
        var alpha: Float = 1.0f,
        var lifetime: Float = 0f
    )

    // Lista efektów tekstowych obrażeń
    private val damageTexts = mutableListOf<DamageText>()

    // Obiekty klas postaci - przekaż skileManager
    private val archer = Archer(localPlayer, networkScope, session, skileManager)
    private val mage = Mage(localPlayer, networkScope, session, skileManager)
    private val warrior = Warrior(localPlayer, networkScope, session, skileManager)

    // InputHandler do obsługi klawiszy
    private val inputHandler = InputHandler(
        localPlayer,
        players,
        camera,
        networkScope,
        session
    )

    fun handleInput() {
        if (localPlayer.isDead()) {
            // Skip input handling for dead players
            return
        }

        // Aktualizuj stan cooldownów dla wszystkich klas
        val delta = Gdx.graphics.deltaTime
        archer.update(delta)
        mage.update(delta)
        warrior.update(delta)

        // Aktualizacja efektów tekstowych obrażeń
        updateDamageTexts(delta)

        // Obsługa klawiszy
        inputHandler.handleInput(delta)

        // Obsługa LEWEGO przycisku myszy
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            val clickedPlayer = findPlayerUnderCursor()

            if (clickedPlayer != null && clickedPlayer.id != localPlayer.id) {
                // Jeśli kliknięto na innego gracza - zaznacz go bez atakowania
                selectedPlayerId?.let { prevId ->
                    players[prevId]?.isSelected = false
                }
                clickedPlayer.isSelected = true
                selectedPlayerId = clickedPlayer.id
                inputHandler.setLastTarget(clickedPlayer)
            } else {
                // Jeśli kliknięto w puste miejsce lub na samego siebie - odznacz cel
                selectedPlayerId?.let { prevId ->
                    players[prevId]?.isSelected = false
                }
                selectedPlayerId = null
                inputHandler.setLastTarget(null)
            }
        }

        // Obsługa PRAWEGO przycisku myszy (zaznaczanie/atak + ruch)
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            val clickedPlayer = findPlayerUnderCursor()

            if (clickedPlayer != null && clickedPlayer.id != localPlayer.id) {

                // Użyj auto-ataku odpowiedniej dla klasy postaci
                when (localPlayer.characterClass) {
                    0 -> archer.handleTargetClick(clickedPlayer) // Łucznik
                    1 -> mage.handleTargetClick(clickedPlayer)   // Mag
                    2 -> warrior.handleTargetClick(clickedPlayer) // Wojownik
                }

                // Zaznacz klikniętego gracza
                selectedPlayerId?.let { prevId ->
                    players[prevId]?.isSelected = false
                }
                clickedPlayer.isSelected = true
                selectedPlayerId = clickedPlayer.id
                inputHandler.setLastTarget(clickedPlayer)
            } else {
                // Kliknięto w puste miejsce - ustaw cel ruchu
                val worldCoords = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))

                // Od razu aktualizuj cel ruchu lokalnego gracza (dla natychmiastowej reakcji)
                localPlayer.setMoveTarget(worldCoords.x, worldCoords.y)

                // Wyślij informację o zamiarze ruchu do serwera
                networkScope.launch {
                    try {
                        session()?.send("MOVE_TO|${worldCoords.x}|${worldCoords.y}|0|${localPlayer.id}")
                    } catch (e: Exception) {
                        Gdx.app.error("PlayerHandler", "Błąd wysyłania celu ruchu: ${e.message}")
                    }
                }
            }
        }
    }

    fun render(shapeRenderer: ShapeRenderer, batch: SpriteBatch, font: BitmapFont) {
        // Renderuj umiejętności
        skileManager.render(shapeRenderer)

        // Renderuj wskaźniki cooldownu specyficzne dla klasy postaci
        when (localPlayer.characterClass) {
            0 -> archer.renderCooldownBar(shapeRenderer, batch, font)
            1 -> mage.renderCooldownBar(shapeRenderer, batch, font)
            2 -> warrior.renderCooldownBar(shapeRenderer, batch, font)
        }

        // Renderuj efekty tekstowe obrażeń
        renderDamageTexts(batch, font)
    }

    private fun findPlayerUnderCursor(): Player? {
        val mouseX = Gdx.input.x
        val mouseY = Gdx.input.y

        // Konwersja współrzędnych ekranu na współrzędne świata gry
        val worldCoords = camera.unproject(Vector3(mouseX.toFloat(), mouseY.toFloat(), 0f))

        // Sprawdź, czy kursor jest nad którymś z graczy (promień 15f jak w renderGame)
        return players.values.find { player ->
            val distance = Vector2.dst(worldCoords.x, worldCoords.y, player.x, player.y)
            distance <= 15f // Zakładamy, że gracze są renderowani jako kółka o promieniu 15f
        }
    }

    // Metoda do obsługi wiadomości z serwera specyficznych dla klas
    fun handleMessage(command: String, parts: List<String>) {
        when (command) {
            "RANGED_ATTACK", "SPELL_ATTACK", "MELEE_ATTACK" -> {
                // Przekaż obsługę do skileManager
                skileManager.handleSkillMessage(command, parts)
            }
            "HIT_DETAILED" -> {
                // Obsługa szczegółowych trafień (z widoczną wartością obrażeń)
                if (parts.size >= 7) {
                    val targetId = parts[1]
                    val attackerId = parts[2]
                    val attackType = parts[3]
                    val currentHealth = parts[4].toIntOrNull() ?: 0
                    val maxHealth = parts[5].toIntOrNull() ?: 100
                    val damage = parts[6].toIntOrNull() ?: 0

                    // Aktualizuj zdrowie gracza
                    players[targetId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth

                        // Dodaj efekt tekstowy z wartością obrażeń, ale tylko gdy lokalny gracz jest atakującym lub atakowanym
                        if (targetId == localPlayer.id || attackerId == localPlayer.id) {
                            addDamageText(player.x, player.y + 20f, "-$damage", Color.RED)
                        }

                        // Jeśli lokalny gracz został trafiony, możemy dodać dodatkowe efekty
                        if (targetId == localPlayer.id) {
                            Gdx.app.log("Game", "Zostałeś trafiony! Twoje zdrowie: $currentHealth/$maxHealth")
                        }
                    }
                }
            }
            "HIT" -> {
                // Obsługa podstawowych trafień (bez widocznej wartości obrażeń)
                if (parts.size >= 6) {
                    val targetId = parts[1]
                    val attackerId = parts[2]
                    val attackType = parts[3]
                    val currentHealth = parts[4].toIntOrNull() ?: 0
                    val maxHealth = parts[5].toIntOrNull() ?: 100

                    // Aktualizuj zdrowie gracza
                    players[targetId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth

                        // Nie dodajemy tutaj efektu tekstowego z obrażeniami
                    }
                }
            }
            "HEALTH_UPDATE" -> {
                if (parts.size >= 4) {
                    val playerId = parts[1]
                    val currentHealth = parts[2].toIntOrNull() ?: 0
                    val maxHealth = parts[3].toIntOrNull() ?: 100

                    players[playerId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth
                    }
                }
            }
            "RESPAWN" -> {
                if (parts.size >= 4) {
                    val playerId = parts[1]
                    val currentHealth = parts[2].toIntOrNull() ?: 100
                    val maxHealth = parts[3].toIntOrNull() ?: 100

                    players[playerId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth

                        // Dodaj efekt odrodzenia (tekst nad graczem)
                        addDamageText(player.x, player.y + 20f, "Respawn!", Color.GREEN)
                    }
                }
            }
        }
    }

    fun update(delta: Float) {
        // Aktualizuj umiejętności
        skileManager.update(delta)

        // Klasy postaci
        archer.update(delta)
        mage.update(delta)
        warrior.update(delta)

        // Aktualizacja efektów tekstowych obrażeń
        updateDamageTexts(delta)
    }

    // Dodaj nowy efekt tekstowy obrażeń
    fun addDamageText(x: Float, y: Float, text: String, color: Color) {
        damageTexts.add(DamageText(x, y, text, color))
    }

    // Aktualizacja efektów tekstowych obrażeń
    private fun updateDamageTexts(delta: Float) {
        // Aktualizacja pozycji i przezroczystości
        damageTexts.forEach { text ->
            text.y += 30f * delta // Unoszenie w górę
            text.alpha -= delta // Zanikanie
            text.lifetime += delta
        }

        // Usuwanie starych efektów
        damageTexts.removeAll { it.lifetime > 1.0f }
    }

    // Renderowanie efektów tekstowych obrażeń
    private fun renderDamageTexts(batch: SpriteBatch, font: BitmapFont) {
        if (damageTexts.isEmpty()) return

        // Zapisz oryginalny kolor czcionki
        val originalColor = font.color.cpy()

        batch.begin()
        damageTexts.forEach { text ->
            font.color = Color(text.color.r, text.color.g, text.color.b, text.alpha)
            font.draw(batch, text.text, text.x - 10f, text.y)
        }

        // Przywróć oryginalny kolor czcionki po zakończeniu renderowania
        font.color = originalColor

        batch.end()
    }
}