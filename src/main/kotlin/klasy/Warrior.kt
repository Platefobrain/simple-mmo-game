package pl.decodesoft.klasy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import pl.decodesoft.player.Player
import pl.decodesoft.klasy.skile.Skile
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.klasy.skile.SkileManager

/**
 * Klasa reprezentująca atak mieczem Wojownika
 */
class Sword(
    override var x: Float,
    override var y: Float,
    private val directionX: Float,
    private val directionY: Float,
    override val casterId: String,
    private val speed: Float = 500f,
    private val maxDistance: Float = 150f, // Krótszy zasięg niż strzała
    private var distanceTraveled: Float = 0f
) : Skile {
    override val color: Color = Color.LIGHT_GRAY
    override val size = 12f

    // Hitbox dla ataku mieczem (używany do kolizji)
    private val hitbox = Rectangle(x - size/2, y - size/2, size * 1.5f, size)

    // Kąt ataku w stopniach (do renderowania)
    private val angle: Float
        get() = Math.toDegrees(kotlin.math.atan2(directionY.toDouble(), directionX.toDouble())).toFloat()

    private var lifeTime: Float = 0f
    private val maxLifeTime: Float = 0.15f // Krótki czas życia, atak mieczem jest szybki

    override fun update(delta: Float): Boolean {
        val moveDistance = speed * delta
        x += directionX * moveDistance
        y += directionY * moveDistance
        distanceTraveled += moveDistance
        lifeTime += delta

        // Aktualizuj hitbox
        hitbox.x = x - size/2
        hitbox.y = y - size/2

        // Zwraca true jeśli atak mieczem powinien być nadal aktywny
        return distanceTraveled < maxDistance && lifeTime < maxLifeTime
    }

    // Sprawdza kolizję z graczem
    override fun checkCollision(player: Player): Boolean {
        // Prosta kolizja okrąg-prostokąt
        val playerRadius = 15f
        val centerX = x
        val centerY = y

        val distance = Vector2.dst(centerX, centerY, player.x, player.y)
        return distance <= playerRadius + size/2
    }

    // Implementacja metody render z interfejsu Skile
    override fun render(shapeRenderer: ShapeRenderer) {
        shapeRenderer.color = color

        // Zapisz bieżący stan transformacji
        shapeRenderer.identity()

        // Przesuń do pozycji ataku
        shapeRenderer.translate(x, y, 0f)

        // Obróć zgodnie z kątem ataku
        shapeRenderer.rotate(0f, 0f, 1f, angle)

        // Narysuj efekt cięcia mieczem jako szeroki, krótki kształt
        // Ostrze miecza
        shapeRenderer.rectLine(-size/2, 0f, size, 0f, size/1.5f)

        // Dodatkowy efekt cięcia (półprzezroczysty ślad)
        shapeRenderer.setColor(color.r, color.g, color.b, 0.4f)
        shapeRenderer.triangle(
            size, -size/2,
            size*1.5f, 0f,
            size, size/2
        )

        // Przywróć poprzedni stan transformacji
        shapeRenderer.identity()
    }
}

/**
 * Klasa reprezentująca Wojownika
 */
class Warrior(
    private val player: Player,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?,
    private val skileManager: SkileManager
) {
    // Cooldown dla ataku mieczem
    private val meleeCooldown = 3f // coldown
    private var meleeTimer = 0f

    // Zmienne do obsługi podejścia do celu
    private var targetPlayer: Player? = null
    private val attackRange = 45f // Maksymalny zasięg ataku mieczem
    private val moveToRange = 35f // Zasięg, na jaki podchodzimy do przeciwnika (mniejszy niż attackRange)

    /**
     * Aktualizacja stanu wojownika
     */
    fun update(delta: Float) {
        // Aktualizuj cooldown
        if (meleeTimer > 0) {
            meleeTimer -= delta
        }

        // Sprawdź czy mamy cel do podejścia
        targetPlayer?.let { target ->
            // Oblicz dystans do celu
            val distance = Vector2.dst(player.x, player.y, target.x, target.y)

            // Jeśli jesteśmy w zasięgu ataku i cooldown się skończył
            if (distance <= attackRange && meleeTimer <= 0) {
                // Wykonaj atak
                meleeAttack(target.x, target.y)
                meleeTimer = meleeCooldown
                // Wyczyść cel po wykonaniu ataku
                targetPlayer = null
            }
        }
    }

    /**
     * Obsługa kliknięcia na gracza (podejście i atak mieczem)
     */
    fun handleTargetClick(targetPlayer: Player): Boolean {
        // Ustaw gracza jako cel do podejścia
        this.targetPlayer = targetPlayer

        // Zwróć prawdę, aby pokazać że obsłużyliśmy kliknięcie
        return true
    }

    /**
     * Anuluje obecny cel ataku
     */
    fun cancelTarget() {
        this.targetPlayer = null
    }

    /**
     * Sprawdza czy wojownik powinien ruszyć się w kierunku celu
     */
    fun shouldMoveToTarget(): Boolean {
        if (targetPlayer == null) return false

        val distance = Vector2.dst(player.x, player.y, targetPlayer!!.x, targetPlayer!!.y)
        return distance > moveToRange // Używamy moveToRange zamiast attackRange
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
     * Zwraca optymalną odległość, na jaką wojownik powinien podejść do celu
     */
    fun getMoveToRange(): Float {
        return moveToRange
    }

    /**
     * Wykonuje atak mieczem w kierunku celu
     */
    private fun meleeAttack(targetX: Float, targetY: Float) {
        // Oblicz kierunek ataku
        val dirX = targetX - player.x
        val dirY = targetY - player.y
        val distance = Vector2.dst(player.x, player.y, targetX, targetY)

        // Normalizacja wektora kierunku
        val normalizedDirX = dirX / distance
        val normalizedDirY = dirY / distance

        // Utwórz nowy atak mieczem
        val sword = Sword(
            player.x,
            player.y,
            normalizedDirX,
            normalizedDirY,
            player.id
        )

        // Dodaj atak do menedżera umiejętności
        skileManager.addSkill(sword)

        // Wyślij informację o ataku do serwera
        networkScope.launch {
            try {
                val message = "MELEE_ATTACK|${player.x}|${player.y}|$normalizedDirX|$normalizedDirY|${player.id}"
                session()?.send(Frame.Text(message))
            } catch (e: Exception) {
                Gdx.app.error("Warrior", "Błąd wysyłania informacji o ataku mieczem: ${e.message}")
            }
        }
    }

    /**
     * Renderowanie wskaźnika cooldownu
     */
    fun renderCooldownBar(shapeRenderer: ShapeRenderer, batch: SpriteBatch, font: BitmapFont) {
        // Pozycjonowanie paska na dole ekranu
        val barWidth = 200f
        val barHeight = 20f
        val screenWidth = Gdx.graphics.width.toFloat()
        val barX = (screenWidth / 2) - (barWidth / 2) // Wyśrodkowanie paska
        val barY = 30f // Dolna część ekranu

        // Renderowanie paska ataku mieczem
        if (meleeTimer > 0) {
            // Rysowanie tła paska
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color.DARK_GRAY
            shapeRenderer.rect(barX, barY, barWidth, barHeight)

            // Rysowanie paska postępu
            val progress = 1 - (meleeTimer / meleeCooldown)
            shapeRenderer.color = Color.ORANGE
            shapeRenderer.rect(barX, barY, barWidth * progress, barHeight)
            shapeRenderer.end()

            // Tekst cooldownu
            batch.begin()
            font.color = Color.WHITE
            val textX = barX + 5
            val textY = barY + barHeight - 5
            font.draw(batch, "Atak mieczem: ${String.format("%.1f", meleeTimer)}s", textX, textY)
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
            font.draw(batch, "Gotowy do ataku!", textX, textY)
            batch.end()
        }
    }
}