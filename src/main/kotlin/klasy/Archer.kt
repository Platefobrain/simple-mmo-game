package pl.decodesoft.klasy

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.player.Player
import kotlin.math.atan2
import pl.decodesoft.klasy.skile.Skile
import pl.decodesoft.klasy.skile.SkileManager

/**
 * Klasa reprezentująca strzałę wystrzeloną przez Łucznika
 */
class Arrow(
    override var x: Float,
    override var y: Float,
    private val directionX: Float,
    private val directionY: Float,
    override val casterId: String,
    private val speed: Float = 400f,
    private val maxDistance: Float = 500f,
    private var distanceTraveled: Float = 0f
) : Skile {
    override val color: Color = Color.YELLOW
    override val size = 8f

    // Hitbox dla strzały (używany do kolizji)
    private val hitbox = Rectangle(x - size/2, y - size/2, size, size)

    // Kąt strzały w stopniach (do renderowania)
    private val angle: Float
        get() = Math.toDegrees(atan2(directionY.toDouble(), directionX.toDouble())).toFloat()

    override fun update(delta: Float): Boolean {
        val moveDistance = speed * delta
        x += directionX * moveDistance
        y += directionY * moveDistance
        distanceTraveled += moveDistance

        // Aktualizuj hitbox
        hitbox.x = x - size/2
        hitbox.y = y - size/2

        // Zwraca true jeśli strzała powinna być nadal aktywna
        return distanceTraveled < maxDistance
    }

    // Sprawdza kolizję z graczem
    override fun checkCollision(player: Player): Boolean {
        // Prosta kolizja okrąg-prostokąt
        val playerRadius = 15f
        val centerX = x
        val centerY = y

        val distance = Vector2.dst(centerX, centerY, player.x, player.y)
        return distance <= playerRadius
    }

    // Implementacja metody render z interfejsu Skile
    override fun render(shapeRenderer: ShapeRenderer) {
        shapeRenderer.color = color

        // Zapisz bieżący stan transformacji
        shapeRenderer.identity()

        // Przesuń do pozycji strzały
        shapeRenderer.translate(x, y, 0f)

        // Obróć zgodnie z kątem strzały
        shapeRenderer.rotate(0f, 0f, 1f, angle)

        // Narysuj strzałę jako prostokąt (ostrze + trzon)
        // Trzon strzały
        shapeRenderer.rectLine(-size, 0f, size, 0f, 2f)

        // Ostrze strzały (trójkąt)
        shapeRenderer.triangle(
            size, 0f,
            size - 4f, 3f,
            size - 4f, -3f
        )

        // Przywróć poprzedni stan transformacji
        shapeRenderer.identity()
    }
}

class Archer(
    private val player: Player,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?,
    private val skileManager: SkileManager
) {
    // Cooldown strzelania
    private val shootCooldown = 3.0f
    private var shootTimer = 0f

    // Dodane zmienne do obsługi podejścia do celu
    private var targetPlayer: Player? = null
    private val attackRange = 300f // Maksymalny zasięg strzału
    private val moveToRange = 280f // Zasięg, na jaki podchodzimy do przeciwnika (mniejszy niż attackRange)

    /**
     * Przetwarzanie akcji Łucznika - wywoływane w każdej klatce
     */
    fun update(delta: Float) {
        // Odliczaj czas cooldownu strzelania
        if (shootTimer > 0) {
            shootTimer -= delta
        }

        // Sprawdź czy mamy cel do podejścia
        targetPlayer?.let { target ->
            // Oblicz dystans do celu
            val distance = Vector2.dst(player.x, player.y, target.x, target.y)

            // Jeśli jesteśmy w zasięgu ataku i cooldown się skończył
            if (distance <= attackRange && shootTimer <= 0) {
                // Wykonaj strzał
                shootArrow(target.x, target.y)
                shootTimer = shootCooldown
                // Wyczyść cel po wykonaniu strzału
                targetPlayer = null
            }
        }
    }

    /**
     * Obsługa akcji kliknięcia na gracza - ustawia cel do podejścia i ataku
     */
    fun handleTargetClick(targetPlayer: Player): Boolean {
        // Ustaw gracza jako cel do podejścia
        this.targetPlayer = targetPlayer

        // Oblicz dystans do celu
        val distance = Vector2.dst(player.x, player.y, targetPlayer.x, targetPlayer.y)

        // Jeśli jesteśmy już w zasięgu i cooldown się skończył, strzelaj od razu
        if (distance <= attackRange && shootTimer <= 0) {
            shootArrow(targetPlayer.x, targetPlayer.y)
            shootTimer = shootCooldown
            // Wyczyść cel po wykonaniu strzału
            this.targetPlayer = null
            return true
        }

        // Zwróć false, jeśli nie wykonaliśmy natychmiastowego strzału
        return false
    }

    /**
     * Anuluje obecny cel ataku
     */
    fun cancelTarget() {
        this.targetPlayer = null
    }

    /**
     * Sprawdza czy łucznik powinien ruszyć się w kierunku celu
     */
    fun shouldMoveToTarget(): Boolean {
        if (targetPlayer == null) return false

        val distance = Vector2.dst(player.x, player.y, targetPlayer!!.x, targetPlayer!!.y)
        return distance > moveToRange
    }

    /**
     * Zwraca pozycję celu do ruchu (lub null jeśli nie ma celu)
     */
    fun getTargetPosition(): Vector2? {
        return targetPlayer?.let { target ->
            Vector2(target.x, target.y)
        }
    }

    /**
     * Zwraca optymalną odległość, na jaką łucznik powinien podejść do celu
     */
    fun getMoveToRange(): Float {
        return moveToRange
    }

    /**
     * Wystrzelenie strzały w określonym kierunku
     */
    private fun shootArrow(targetX: Float, targetY: Float) {
        // Oblicz kierunek strzały
        val dirX = targetX - player.x
        val dirY = targetY - player.y
        val distance = Vector2.dst(player.x, player.y, targetX, targetY)

        // Normalizacja wektora kierunku
        val normalizedDirX = dirX / distance
        val normalizedDirY = dirY / distance

        // Utwórz nową strzałę
        val arrow = Arrow(
            player.x,
            player.y,
            normalizedDirX,
            normalizedDirY,
            player.id
        )

        // Dodaj strzałę do menedżera umiejętności
        skileManager.addSkill(arrow)

        // Wyślij informację o strzale do serwera
        networkScope.launch {
            try {
                val message = "RANGED_ATTACK|${player.x}|${player.y}|$normalizedDirX|$normalizedDirY|${player.id}"
                session()?.send(Frame.Text(message))
            } catch (e: Exception) {
                Gdx.app.error("Archer", "Błąd wysyłania informacji o strzale: ${e.message}")
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

        if (shootTimer > 0) {
            // Rysowanie tła paska
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color.DARK_GRAY
            shapeRenderer.rect(barX, barY, barWidth, barHeight)

            // Rysowanie paska postępu
            val progress = 1 - (shootTimer / shootCooldown)
            shapeRenderer.color = Color.ORANGE
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight)
            shapeRenderer.end()

            // Tekst cooldownu
            batch.begin()
            font.color = Color.WHITE
            val textX = barX + 5
            val textY = barY + barHeight - 5
            font.draw(batch, "Strzał: ${String.format("%.1f", shootTimer)}s", textX, textY)
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
            font.draw(batch, "Gotowy do strzału!", textX, textY)
            batch.end()
        }
    }
}