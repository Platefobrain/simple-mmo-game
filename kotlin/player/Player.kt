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

package pl.decodesoft.player

import com.badlogic.gdx.graphics.Color
import kotlin.math.sqrt

data class Player(
    var x: Float,
    var y: Float,
    val id: String,
    val color: Color,
    val username: String,
    val characterClass: Int = 2, // 0-łucznik, 1-mag, 2-wojownik
    var isSelected: Boolean = false,
    var maxHealth: Int = 100,
    var currentHealth: Int = 100,
    var level: Int = 1,
    var experience: Int = 0
) {
    // Zmienne do obsługi ruchu - uproszczone
    private var isMoving: Boolean = false
    private var moveSpeed: Float = 120f

    // Zmienne do interpolacji (dla płynnego ruchu gracza)
    private var targetX: Float = x
    private var targetY: Float = y
    private var previousX: Float = x
    private var previousY: Float = y
    private var interpolationProgress: Float = 0f

    fun isDead(): Boolean = currentHealth <= 0

    fun getClassColor(): Color {
        return when (characterClass) {
            0 -> Color(0.2f, 0.8f, 0.2f, 1f) // Zielony dla łucznika
            1 -> Color(0.2f, 0.2f, 0.9f, 1f) // Niebieski dla maga
            else -> Color(0.9f, 0.2f, 0.2f, 1f) // Czerwony dla wojownika
        }
    }

    fun getClassName(): String {
        return when (characterClass) {
            0 -> "Łucznik"
            1 -> "Mag"
            else -> "Wojownik"
        }
    }

    // Uproszczona metoda do ustawiania docelowej pozycji ruchu
    fun setMoveTarget(newTargetX: Float, newTargetY: Float) {
        // Zapisz aktualną pozycję jako poprzednią
        previousX = x
        previousY = y

        // Ustaw nowy cel
        targetX = newTargetX
        targetY = newTargetY

        // Rozpocznij ruch
        isMoving = true
        interpolationProgress = 0f
    }

    // Metoda do aktualizacji pozycji gracza (interpolacja bez predykcji)
    fun updatePosition(delta: Float) {
        if (!isMoving) return

        // Zwiększ postęp interpolacji
        interpolationProgress += delta * 10f // Mnożnik wpływa na płynność
        interpolationProgress = interpolationProgress.coerceIn(0f, 1f)

        // Interpoluj między poprzednią a docelową pozycją
        x = lerp(previousX, targetX, interpolationProgress)
        y = lerp(previousY, targetY, interpolationProgress)

        // Jeśli osiągnęliśmy cel, zatrzymaj interpolację
        if (interpolationProgress >= 1f) {
            isMoving = false
        }
    }

    // Metoda do aktualizacji pozycji lokalnego gracza (predykcja po stronie klienta)
    fun updateLocalPosition(delta: Float) {
        if (!isMoving) return

        // Oblicz odległość do celu
        val distX = targetX - x
        val distY = targetY - y

        // Używanie square magnitude zamiast pełnego obliczania dystansu
        val distSquared = distX * distX + distY * distY

        // Używanie kwadratu odległości zamiast samej odległości (unikamy kosztownego sqrt)
        if (distSquared < 25f) { // 5^2 = 25
            isMoving = false
            return
        }

        // Obliczamy faktyczny dystans tylko jeśli potrzebujemy
        val distance = sqrt(distSquared.toDouble()).toFloat()

        // Oblicz znormalizowany wektor kierunku
        val dirX = distX / distance
        val dirY = distY / distance

        // Oblicz odległość ruchu
        val moveDistance = moveSpeed * delta

        // Zabezpieczenie przed "przeskoczeniem" celu
        val actualMoveDistance = minOf(moveDistance, distance)

        // Aktualizuj pozycję
        x += dirX * actualMoveDistance
        y += dirY * actualMoveDistance
    }

    // Metoda do bezpośredniego ustawienia pozycji otrzymanej z serwera
    fun setServerPosition(serverX: Float, serverY: Float) {
        // Jeśli nie jesteśmy w ruchu, od razu ustaw pozycję
        if (!isMoving) {
            x = serverX
            y = serverY
            previousX = serverX
            previousY = serverY
        } else {
            // Jeśli jesteśmy w ruchu, ustaw nowy cel
            previousX = x
            previousY = y
            targetX = serverX
            targetY = serverY
            interpolationProgress = 0f
        }
    }

    // Metoda pomocnicza do interpolacji liniowej
    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + t * (end - start)
    }
}