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

package pl.decodesoft.klasy.skile

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.player.Player

/**
 * Klasa reprezentująca umiejętność szarży wojownika
 */
class ChargeWarrior(
    private val player: Player,
    private val players: Map<String, Player>,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?
) {
    // Cooldown dla umiejętności szarży
    private val chargeCooldown = 4.0f
    private var chargeTimer = 0f

    // Stan szarży
    private var isCharging = false
    private var chargeTargetX = 0f
    private var chargeTargetY = 0f
    private var chargeSpeed = 400f // Szybka szarża
    private var chargeDuration = 0.5f // Czas trwania szarży
    private var currentChargeDuration = 0f

    // Cooldown dla trafień
    private val hitCooldown = 2.0f
    private var hitTimer = 0f

    /**
     * Aktualizacja stanu szarży
     */
    fun update(delta: Float) {
        // Odliczaj czas cooldownu szarży
        if (chargeTimer > 0) {
            chargeTimer -= delta
        }

        // Odliczaj czas cooldownu trafień
        if (hitTimer > 0) {
            hitTimer -= delta
        }

        // Obsługa trwającej szarży
        if (isCharging) {
            currentChargeDuration += delta

            if (currentChargeDuration >= chargeDuration) {
                // Koniec szarży
                isCharging = false
                currentChargeDuration = 0f
            } else {
                // Kontynuuj szarżę
                moveTowardsChargeTarget(delta)
            }
        }
    }

    /**
     * Aktywacja szarży w kierunku współrzędnych X,Y
     */
    fun activateCharge(targetX: Float, targetY: Float): Boolean {
        // Sprawdź czy można użyć szarży (cooldown dobiegł końca)
        if (chargeTimer <= 0 && !isCharging) {
            chargeTowards(targetX, targetY)
            chargeTimer = chargeCooldown
            return true
        }
        return false
    }

    /**
     * Aktywacja szarży w kierunku wskazanego gracza
     */
    fun activateChargeTowardsPlayer(targetPlayer: Player): Boolean {
        return activateCharge(targetPlayer.x, targetPlayer.y)
    }

    /**
     * Rozpoczęcie szarży w określonym kierunku
     */
    private fun chargeTowards(targetX: Float, targetY: Float) {
        // Ustaw cel szarży
        chargeTargetX = targetX
        chargeTargetY = targetY

        // Aktywuj szarżę
        isCharging = true
        currentChargeDuration = 0f

        // Wyślij informację o szarży do serwera
        networkScope.launch {
            try {
                session()?.send("CHARGE|${player.x}|${player.y}|$targetX|$targetY|${player.id}")
            } catch (e: Exception) {
                Gdx.app.error("ChargeWarrior", "Błąd wysyłania informacji o szarży: ${e.message}")
            }
        }
    }

    /**
     * Ruch podczas szarży - bardzo szybki
     */
    private fun moveTowardsChargeTarget(delta: Float) {
        // Oblicz odległość do celu
        val distX = chargeTargetX - player.x
        val distY = chargeTargetY - player.y
        val distance = Vector2.dst(player.x, player.y, chargeTargetX, chargeTargetY)

        // Jeśli jesteśmy wystarczająco blisko celu, zatrzymaj szarżę
        if (distance < 10f) {
            isCharging = false
            currentChargeDuration = 0f

            // Sprawdź kolizje z przeciwnikami
            checkChargeCollisions()
            return
        }

        // Oblicz znormalizowany wektor kierunku
        val dirX = distX / distance
        val dirY = distY / distance

        // Oblicz nową pozycję - szarża jest bardzo szybka
        val moveDistance = chargeSpeed * delta
        val newX = player.x + dirX * moveDistance
        val newY = player.y + dirY * moveDistance

        // Ograniczenie obszaru ruchu
        val clampedX = newX.coerceIn(0f, Gdx.graphics.width.toFloat())
        val clampedY = newY.coerceIn(0f, Gdx.graphics.height.toFloat())

        // Aktualizacja pozycji
        player.x = clampedX
        player.y = clampedY

        // Wysyłanie aktualizacji pozycji do serwera
        networkScope.launch {
            try {
                session()?.send("MOVE|$clampedX|$clampedY|${player.id}")
            } catch (e: Exception) {
                Gdx.app.error("ChargeWarrior", "Błąd wysyłania pozycji: ${e.message}")
            }
        }

        // Sprawdź kolizje po ruchu
        checkChargeCollisions()
    }

    /**
     * Sprawdzenie kolizji podczas szarży
     */
    private fun checkChargeCollisions() {
        // Sprawdzaj kolizje tylko jeśli minął cooldown trafień
        if (hitTimer <= 0) {
            // Sprawdź kolizje z innymi graczami
            players.values.forEach { targetPlayer ->
                // Pomijamy kolizje z samym sobą
                if (targetPlayer.id != player.id) {
                    val distance = Vector2.dst(player.x, player.y, targetPlayer.x, targetPlayer.y)
                    if (distance <= 30f) { // Zwiększony obszar kolizji dla szarży
                        // Wyślij informację o trafieniu
                        networkScope.launch {
                            try {
                                session()?.send("HIT|${targetPlayer.id}|${player.id}|CHARGE")
                            } catch (e: Exception) {
                                Gdx.app.error("ChargeWarrior", "Błąd wysyłania informacji o trafieniu: ${e.message}")
                            }
                        }

                        // Ustaw timer cooldownu trafień
                        hitTimer = hitCooldown

                        // Po pierwszym trafieniu przerywamy pętlę
                        return
                    }
                }
            }
        }
    }

    /**
     * Renderowanie wskaźnika cooldownu
     */
    fun renderCooldownBar(shapeRenderer: ShapeRenderer, batch: SpriteBatch, font: BitmapFont) {
        // Pozycjonowanie paska na dole ekranu w centrum
        val barWidth = 200f
        val barHeight = 20f
        val screenWidth = Gdx.graphics.width.toFloat()
        val barX = (screenWidth / 2) - (barWidth / 2) // Wyśrodkowanie paska
        val barY = 30f // Dolna część ekranu

        if (chargeTimer > 0) {
            // Rysowanie tła paska
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color.DARK_GRAY
            shapeRenderer.rect(barX, barY, barWidth, barHeight)

            // Rysowanie paska postępu
            val progress = 1 - (chargeTimer / chargeCooldown)
            shapeRenderer.color = Color.ORANGE
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight)
            shapeRenderer.end()

            // Tekst cooldownu
            batch.begin()
            font.color = Color.WHITE
            val textX = barX + 5
            val textY = barY + barHeight - 5
            font.draw(batch, "Szarża (Q): ${String.format("%.1f", chargeTimer)}s", textX, textY)
            batch.end()
        } else {
            // Cooldown zakończony - pokaż pełny pasek
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color.LIME
            shapeRenderer.rect(barX, barY, barWidth, barHeight)
            shapeRenderer.end()

            batch.begin()
            font.color = Color.WHITE
            val textX = barX + 5
            val textY = barY + barHeight - 5
            font.draw(batch, "Szarża (Q) gotowa!", textX, textY)
            batch.end()
        }
    }

    /**
     * Renderowanie efektu szarży
     */
    fun renderChargeEffect(shapeRenderer: ShapeRenderer) {
        if (isCharging) {
            // Rysuj efekt szarży (np. smugę za graczem)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

            // Kolor efektu szarży
            shapeRenderer.color = Color(0.8f, 0.2f, 0.2f, 0.5f)

            // Smugi za wojownikiem
            val dirX = chargeTargetX - player.x
            val dirY = chargeTargetY - player.y
            val distance = Vector2.dst(player.x, player.y, chargeTargetX, chargeTargetY)

            if (distance > 0) {
                val normalizedDirX = -dirX / distance // Odwrócony kierunek, żeby smuga była za graczem
                val normalizedDirY = -dirY / distance

                // Rysuj kilka okręgów tworzących smugę
                for (i in 1..5) {
                    val alpha = 0.4f - (i * 0.08f) // Zmniejszająca się przezroczystość
                    val size = 15f - (i * 1.5f) // Zmniejszający się rozmiar

                    shapeRenderer.color.a = alpha
                    val offsetX = normalizedDirX * (i * 8f)
                    val offsetY = normalizedDirY * (i * 8f)

                    shapeRenderer.circle(player.x + offsetX, player.y + offsetY, size)
                }
            }

            shapeRenderer.end()
        }
    }

    /**
     * Gettery do udostępnienia informacji o cooldownie i stanie szarży
     */
    fun getChargeCooldown(): Float = chargeTimer
    fun getMaxChargeCooldown(): Float = chargeCooldown
    fun isCharging(): Boolean = isCharging
}