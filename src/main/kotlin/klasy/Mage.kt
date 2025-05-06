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
import pl.decodesoft.klasy.skile.Skile
import pl.decodesoft.klasy.skile.SkileManager

/**
 * Klasa reprezentująca kulę ognia wystrzeloną przez Maga
 */
class Fireball(
    override var x: Float,
    override var y: Float,
    private val directionX: Float,
    private val directionY: Float,
    override val casterId: String,
    private val speed: Float = 350f,
    private val maxDistance: Float = 450f,
    private var distanceTraveled: Float = 0f
) : Skile {
    override val color: Color = Color.ORANGE
    override val size = 12f
    private val innerSize = 6f

    // Hitbox dla kuli ognia (używany do kolizji)
    private val hitbox = Rectangle(x - size/2, y - size/2, size, size)

    override fun update(delta: Float): Boolean {
        val moveDistance = speed * delta
        x += directionX * moveDistance
        y += directionY * moveDistance
        distanceTraveled += moveDistance

        // Aktualizuj hitbox
        hitbox.x = x - size/2
        hitbox.y = y - size/2

        // Zwraca true jeśli kula ognia powinna być nadal aktywna
        return distanceTraveled < maxDistance
    }

    // Sprawdza kolizję z graczem
    override fun checkCollision(player: Player): Boolean {
        // Prosta kolizja okrąg-okrąg
        val playerRadius = 15f
        val centerX = x
        val centerY = y

        val distance = Vector2.dst(centerX, centerY, player.x, player.y)
        return distance <= playerRadius + size/2
    }

    // Metoda do renderowania kuli ognia
    override fun render(shapeRenderer: ShapeRenderer) {
        shapeRenderer.color = color
        shapeRenderer.circle(x, y, size)

        // Wewnętrzna część kuli ognia (jaśniejsza)
        shapeRenderer.color = Color.YELLOW
        shapeRenderer.circle(x, y, innerSize)
    }
}

class Mage(
    private val player: Player,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?,
    private val skileManager: SkileManager
) {
    // Cooldown rzucania kuli ognia
    private val castCooldown = 4.0f
    private var castTimer = 0f

    // Dodane zmienne do obsługi podejścia do celu
    private var targetPlayer: Player? = null
    private val attackRange = 250f // Maksymalny zasięg kuli ognia
    private val moveToRange = 230f // Zasięg, na jaki podchodzimy do przeciwnika (mniejszy niż attackRange)

    /**
     * Przetwarzanie akcji Maga - wywoływane w każdej klatce
     */
    fun update(delta: Float) {
        // Odliczaj czas cooldownu rzucania
        if (castTimer > 0) {
            castTimer -= delta
        }

        // Sprawdź czy mamy cel do podejścia
        targetPlayer?.let { target ->
            // Oblicz dystans do celu
            val distance = Vector2.dst(player.x, player.y, target.x, target.y)

            // Jeśli jesteśmy w zasięgu ataku i cooldown się skończył
            if (distance <= attackRange && castTimer <= 0) {
                // Rzuć kulę ognia
                castFireball(target.x, target.y)
                castTimer = castCooldown
                // Wyczyść cel po wykonaniu ataku
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

        // Jeśli jesteśmy już w zasięgu i cooldown się skończył, rzuć kulę od razu
        if (distance <= attackRange && castTimer <= 0) {
            castFireball(targetPlayer.x, targetPlayer.y)
            castTimer = castCooldown
            // Wyczyść cel po wykonaniu ataku
            this.targetPlayer = null
            return true
        }

        // Zwróć false, jeśli nie wykonaliśmy natychmiastowego ataku
        return false
    }

    /**
     * Anuluje obecny cel ataku
     */
    fun cancelTarget() {
        this.targetPlayer = null
    }

    /**
     * Sprawdza czy mag powinien ruszyć się w kierunku celu
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
     * Zwraca optymalną odległość, na jaką mag powinien podejść do celu
     */
    fun getMoveToRange(): Float {
        return moveToRange
    }

    /**
     * Rzucenie kuli ognia w określonym kierunku
     */
    private fun castFireball(targetX: Float, targetY: Float) {
        // Oblicz kierunek kuli ognia
        val dirX = targetX - player.x
        val dirY = targetY - player.y
        val distance = Vector2.dst(player.x, player.y, targetX, targetY)

        // Normalizacja wektora kierunku
        val normalizedDirX = dirX / distance
        val normalizedDirY = dirY / distance

        // Utwórz nową kulę ognia
        val fireball = Fireball(
            player.x,
            player.y,
            normalizedDirX,
            normalizedDirY,
            player.id
        )

        // Dodaj kulę ognia do menedżera umiejętności
        skileManager.addSkill(fireball)

        // Wyślij informację o kuli ognia do serwera
        networkScope.launch {
            try {
                val message = "SPELL_ATTACK|${player.x}|${player.y}|$normalizedDirX|$normalizedDirY|${player.id}"
                session()?.send(Frame.Text(message))
            } catch (e: Exception) {
                Gdx.app.error("Mage", "Błąd wysyłania informacji o kuli ognia: ${e.message}")
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

        if (castTimer > 0) {
            // Rysowanie tła paska
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color.DARK_GRAY
            shapeRenderer.rect(barX, barY, barWidth, barHeight)

            // Rysowanie paska postępu
            val progress = 1 - (castTimer / castCooldown)
            shapeRenderer.color = Color.FIREBRICK
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight)
            shapeRenderer.end()

            // Tekst cooldownu
            batch.begin()
            font.color = Color.WHITE
            val textX = barX + 5
            val textY = barY + barHeight - 5
            font.draw(batch, "Kula ognia: ${String.format("%.1f", castTimer)}s", textX, textY)
            batch.end()
        } else {
            // Cooldown zakończony - pokaż pełny pasek
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color.RED
            shapeRenderer.rect(barX, barY, barWidth, barHeight)
            shapeRenderer.end()

            batch.begin()
            font.color = Color.WHITE
            val textX = barX + 5
            val textY = barY + barHeight - 5
            font.draw(batch, "Gotowy do rzucenia ognia!", textX, textY)
            batch.end()
        }
    }
}