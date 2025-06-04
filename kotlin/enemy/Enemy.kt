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

package pl.decodesoft.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import pl.decodesoft.Strings
import kotlin.math.pow
import kotlin.math.sqrt

class EnemyClient(
    val id: String,
    var x: Float,
    var y: Float,
    private val type: String,
    var currentHealth: Int,
    var maxHealth: Int,
    var level: Int = 1, // Dodany poziom przeciwnika
    private var state: String = "IDLE",
    var isSelected: Boolean = false,
    var isAlive: Boolean = true
) {
    // Pozyskaj nazwy po polsku bazując na typie
    private val displayName: String
        get() = when (type) {
            "Sheep" -> Strings.ENEMY_SHEEP  // "Owca"
            "Wolf" -> Strings.ENEMY_WOLF    // "Wilk"
            "Bear" -> Strings.ENEMY_BEAR    // "Niedźwiedź"
            else -> type
        }

    // Prędkość zależna od stanu
    private val speed: Float
        get() = when (state) {
            "IDLE" -> 30f
            "CHASE" -> 50f
            "RETURN" -> 70f
            else -> 30f
        }

    // Zmienne dla ruchu
    private var targetX: Float = x
    private var targetY: Float = y

    // Aktualizacja docelowej pozycji
    fun updateTargetPosition(newX: Float, newY: Float) {
        // Oblicz kwadraty różnic współrzędnych
        val deltaX = newX - x
        val deltaY = newY - y

        // Oblicz kwadrat odległości (bez pierwiastka)
        val distanceSquared = deltaX * deltaX + deltaY * deltaY

        // Porównaj z kwadratem maksymalnej odległości (50f * 50f = 2500f)
        if (distanceSquared > 2500f) { // 50^2 = 2500
            // Musimy obliczyć pierwiastek tylko raz, gdy faktycznie jest potrzebny
            val distance = sqrt(distanceSquared)

            // Obliczamy kierunek do nowego celu
            val dirX = deltaX / distance
            val dirY = deltaY / distance

            // Ustawiamy cel bliżej obecnej pozycji (maksymalnie 50 jednostek)
            targetX = x + dirX * 50f
            targetY = y + dirY * 50f
        } else {
            // Normalny przypadek - cel jest w rozsądnej odległości
            targetX = newX
            targetY = newY
        }
    }

    // Aktualizacja stanu przeciwnika
    fun updateState(newState: String) {
        state = newState
    }

    // Aktualizacja pozycji w każdej klatce bazująca na prędkości
    fun update(deltaTimeSeconds: Float) {
        // Nie aktualizuj martwych przeciwników
        if (!isAlive) return

        // Oblicz odległość do celu
        val distToTarget = sqrt((targetX - x).pow(2) + (targetY - y).pow(2))

        // Jeśli jesteśmy już bardzo blisko celu, możemy go od razu osiągnąć
        if (distToTarget < 1f) {
            x = targetX
            y = targetY
            return
        }

        // Oblicz kierunek do celu
        val dirX = (targetX - x) / distToTarget
        val dirY = (targetY - y) / distToTarget

        // Oblicz dystans do pokonania w tej klatce
        val moveDistance = speed * deltaTimeSeconds

        if (moveDistance >= distToTarget) {
            x = targetX
            y = targetY
        } else {
            // W przeciwnym razie porusz się w kierunku celu z odpowiednią prędkością
            x += dirX * moveDistance
            y += dirY * moveDistance
        }
    }

    fun render(shapeRenderer: ShapeRenderer) {
        // Nie renderuj martwych przeciwników
        if (!isAlive) return
        // Standardowe renderowanie przeciwnika
        shapeRenderer.color = Color.GRAY
        shapeRenderer.circle(x, y, 15f)

        // Health bar
        shapeRenderer.color = Color.DARK_GRAY
        shapeRenderer.rect(x - 20f, y + 20f, 40f, 6f)

        val ratio = currentHealth.toFloat() / maxHealth
        shapeRenderer.color = if (ratio > 0.5f) Color.GREEN else if (ratio > 0.25f) Color.ORANGE else Color.RED
        shapeRenderer.rect(x - 20f, y + 20f, 40f * ratio, 6f)
    }

    // Zaktualizowana metoda do renderowania nazwy z poziomem
    fun renderName(batch: SpriteBatch, font: BitmapFont, camera: OrthographicCamera, layout: GlyphLayout) {
        // Nie renderuj nazw martwych przeciwników
        if (!isAlive) return

        // Korzystamy z projekcji kamery do obliczenia pozycji na ekranie
        val posScreen = camera.project(Vector3(x, y, 0f))

        // Nazwa przeciwnika z poziomem w nawiasie
        val nameText = "$displayName [#FFFF00]($level)"
        layout.setText(font, nameText)

        // Zmieniam kolor na biały (taki sam jak dla graczy)
        font.color = Color.WHITE

        // Używamy współrzędnych ekranu z layout dla idealnego wyśrodkowania
        font.draw(batch, layout, posScreen.x - layout.width / 2, posScreen.y + 40)
    }

    // Dodaj tę metodę dla bezpośredniego ustawiania pozycji i celu
    fun teleportToPosition(newX: Float, newY: Float) {
        x = newX
        y = newY
        targetX = newX
        targetY = newY
    }
}