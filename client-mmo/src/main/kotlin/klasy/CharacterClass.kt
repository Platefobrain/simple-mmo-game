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

package pl.decodesoft.klasy

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.enemy.EnemyClient
import pl.decodesoft.player.Player
import pl.decodesoft.klasy.skile.SkileManager

/**
 * Abstrakcyjna klasa bazowa dla wszystkich klas postaci w grze
 */
abstract class CharacterClass(
    protected val player: Player,
    protected val networkScope: CoroutineScope,
    protected val session: () -> DefaultWebSocketSession?,
    protected val skileManager: SkileManager
) {
    // Wspólne właściwości dla wszystkich klas postaci
    private var targetPlayer: Player? = null
    private var targetEnemy: EnemyClient? = null

    // Właściwości do nadpisania przez klasy potomne
    protected abstract val attackCooldown: Float
    protected abstract var attackTimer: Float
    protected abstract val attackRange: Float
    protected abstract val attackName: String
    protected abstract val attackColor: Color

    /**
     * Aktualizacja stanu postaci
     */
    open fun update(delta: Float) {
        // Odliczaj czas cooldownu
        if (attackTimer > 0) {
            attackTimer -= delta
        }

        // Sprawdź czy mamy cel gracza do podejścia
        targetPlayer?.let { target ->
            // Oblicz dystans do celu
            val distance = Vector2.dst(player.x, player.y, target.x, target.y)

            // Jeśli jesteśmy w zasięgu ataku i cooldown się skończył
            if (distance <= attackRange && attackTimer <= 0) {
                // Wykonaj atak
                performAttack(target.x, target.y, target.id)
                attackTimer = attackCooldown
                // Wyczyść cel po wykonaniu ataku
                targetPlayer = null
            }
        }

        // Sprawdź czy mamy cel przeciwnika do podejścia
        targetEnemy?.let { target ->
            // Oblicz dystans do celu
            val distance = Vector2.dst(player.x, player.y, target.x, target.y)

            // Jeśli jesteśmy w zasięgu ataku i cooldown się skończył
            if (distance <= attackRange && attackTimer <= 0) {
                // Wykonaj atak
                performAttack(target.x, target.y, "enemy_${target.id}")
                attackTimer = attackCooldown
                // Wyczyść cel po wykonaniu ataku
                targetEnemy = null
            }
        }
    }

    /**
     * Obsługa kliknięcia na przeciwnika
     */
    open fun handleEnemyClick(targetEnemy: EnemyClient): Boolean {
        // Resetuj cel gracza przy wyborze przeciwnika
        this.targetEnemy = targetEnemy
        this.targetPlayer = null

        // Oblicz dystans do celu
        val distance = Vector2.dst(player.x, player.y, targetEnemy.x, targetEnemy.y)

        // Jeśli jesteśmy już w zasięgu i cooldown się skończył, atakuj od razu
        if (distance <= attackRange && attackTimer <= 0) {
            performAttack(targetEnemy.x, targetEnemy.y, "enemy_${targetEnemy.id}")
            attackTimer = attackCooldown
            // Wyczyść cel po wykonaniu ataku
            this.targetEnemy = null
            return true
        }

        // Zwróć false, jeśli nie wykonaliśmy natychmiastowego ataku
        return false
    }

    /**
     * Obsługa kliknięcia na gracza
     */
    open fun handleTargetClick(targetPlayer: Player): Boolean {
        // Ustaw gracza jako cel do podejścia
        this.targetPlayer = targetPlayer
        this.targetEnemy = null // Wyczyść poprzedni cel przeciwnika

        // Oblicz dystans do celu
        val distance = Vector2.dst(player.x, player.y, targetPlayer.x, targetPlayer.y)

        // Jeśli jesteśmy już w zasięgu i cooldown się skończył, atakuj od razu
        if (distance <= attackRange && attackTimer <= 0) {
            performAttack(targetPlayer.x, targetPlayer.y, targetPlayer.id)
            attackTimer = attackCooldown
            // Wyczyść cel po wykonaniu ataku
            this.targetPlayer = null
            return true
        }

        // Zwróć false, jeśli nie wykonaliśmy natychmiastowego ataku
        return false
    }

    /**
     * Metoda abstrakcyjna do wykonania ataku
     */
    protected abstract fun performAttack(targetX: Float, targetY: Float, targetId: String)

    /**
     * Metoda do wysyłania wiadomości o ataku na serwer
     */
    protected fun sendAttackMessage(
        attackType: String,
        targetX: Float,
        targetY: Float,
        normalizedDirX: Float,
        normalizedDirY: Float,
        targetId: String
    ) {
        networkScope.launch {
            try {
                // Najpierw definiujemy zmienną message
                val message = "$attackType|${player.x}|${player.y}|$normalizedDirX|$normalizedDirY|${player.id}|$targetId"
                
                val currentSession: DefaultWebSocketSession? = session()
                if (currentSession != null) {
                    currentSession.send(Frame.Text(message))
                } else {
                    Gdx.app.error(this::class.java.simpleName, "Błąd wysyłania informacji o ataku: sesja jest null")
                }
            } catch (e: Exception) {
                Gdx.app.error(this::class.java.simpleName, "Błąd wysyłania informacji o ataku: ${e.message}")
            }
        }
    }

    //Renderowanie wskaźnika cooldownu
    open fun renderCooldownBar(shapeRenderer: ShapeRenderer, batch: SpriteBatch, font: BitmapFont, camera: OrthographicCamera) {
        // Pobierz pozycję kamery
        val cameraX = camera.position.x - camera.viewportWidth / 2
        val cameraY = camera.position.y - camera.viewportHeight / 2

        // Wymiary paska
        val barWidth = 200f
        val barHeight = 20f

        // Pozycjonowanie paska względem kamery, a nie gracza
        val barX = cameraX + (camera.viewportWidth / 2) - (barWidth / 2) // Wyśrodkowany na ekranie
        val barY = cameraY + 250f // 50 pikseli od dołu ekranu

        // Włączamy blending dla przezroczystości
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        if (attackTimer > 0) {
            // Rysowanie tła paska
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0.2f, 0.2f, 0.2f, 0.7f) // Ciemnoszary z przezroczystością
            shapeRenderer.rect(barX, barY, barWidth, barHeight)

            // Rysowanie paska postępu
            val progress = 1 - (attackTimer / attackCooldown)
            val attackColorWithAlpha = attackColor.cpy().also { it.a = 0.9f } // Kopia koloru ataku z przezroczystością
            shapeRenderer.color = attackColorWithAlpha
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight)
            shapeRenderer.end()

            // Zapisujemy obecny kolor czcionki przed zmianą
            val oldColor = Color(font.color)

            // Tekst cooldownu
            batch.begin()
            font.color = Color.WHITE

            // Tekst cooldownu
            val cooldownText = "$attackName: ${String.format("%.1f", attackTimer)}s"

            // Używamy istniejącego GlyphLayout jeśli dostępny, w przeciwnym razie tworzymy nowy
            val layout = GlyphLayout(font, cooldownText)

            // Wyśrodkowanie tekstu w pasku
            val textX = barX + (barWidth - layout.width) / 2
            val textY = barY + (barHeight + layout.height) / 2

            font.draw(batch, cooldownText, textX, textY)

            // Przywracamy oryginalny kolor czcionki - WAŻNE
            font.color = oldColor

            batch.end()
        } else {
            // Cooldown zakończony - pokaż pełny pasek
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0.0f, 0.8f, 0.0f, 0.9f) // Zielony z przezroczystością
            shapeRenderer.rect(barX, barY, barWidth, barHeight)
            shapeRenderer.end()

            // Zapisujemy obecny kolor czcionki przed zmianą
            val oldColor = Color(font.color)

            batch.begin()
            font.color = Color.WHITE

            // Tekst "Gotowy do ataku!"
            val readyText = "Gotowy do ataku!"

            // Używamy istniejącego GlyphLayout jeśli dostępny, w przeciwnym razie tworzymy nowy
            val layout = GlyphLayout(font, readyText)

            // Wyśrodkowanie tekstu w pasku
            val textX = barX + (barWidth - layout.width) / 2
            val textY = barY + (barHeight + layout.height) / 2

            font.draw(batch, readyText, textX, textY)

            // Przywracamy oryginalny kolor czcionki - WAŻNE
            font.color = oldColor

            batch.end()
        }

        // Opcjonalnie wyłączamy blending, jeśli nie jest używany w innych miejscach
        // Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}