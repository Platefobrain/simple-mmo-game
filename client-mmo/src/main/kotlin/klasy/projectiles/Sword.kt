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

package pl.decodesoft.klasy.projectiles

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import pl.decodesoft.enemy.EnemyClient
import pl.decodesoft.player.Player
import pl.decodesoft.klasy.skile.Skile
import java.util.UUID

/**
 * Klasa reprezentująca atak mieczem Wojownika
 */
class Sword(
    override var x: Float,
    override var y: Float,
    private val directionX: Float,
    private val directionY: Float,
    override val casterId: String,
    private val targetId: String? = null,
    private val speed: Float = 500f,
    private val maxDistance: Float = 150f, // Krótszy zasięg niż strzała
    private var distanceTraveled: Float = 0f
) : Skile {
    override val id: String = UUID.randomUUID().toString()
    override val color: Color = Color.LIGHT_GRAY
    override val size = 12f

    // Dodaj zmienną dla flagi usuwania
    private var shouldRemove: Boolean = false

    // Hitbox dla ataku mieczem (używany do kolizji)
    private val hitbox = Rectangle(x - size/2, y - size/2, size * 1.5f, size)

    // Kąt ataku w stopniach (do renderowania)
    private val angle: Float
        get() = Math.toDegrees(kotlin.math.atan2(directionY.toDouble(), directionX.toDouble())).toFloat()

    private var lifeTime: Float = 0f
    private val maxLifeTime: Float = 0.15f // Krótki czas życia, atak mieczem jest szybki

    override fun update(delta: Float): Boolean {
        // Jeśli strzała jest oznaczona do usunięcia, zwróć false
        if (shouldRemove) return false

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
        // Jeśli mamy określony cel, sprawdzaj kolizje tylko z tym celem
        if (targetId != null && targetId != player.id) {
            return false
        }

        // Prosta kolizja okrąg-prostokąt
        val playerRadius = 15f
        val centerX = x
        val centerY = y

        val distance = Vector2.dst(centerX, centerY, player.x, player.y)
        return distance <= playerRadius + size/2
    }

    // Sprawdza kolizję z przeciwnikiem
    override fun checkCollision(enemy: EnemyClient): Boolean {
        // Jeśli mamy określony cel, sprawdzaj kolizje tylko z tym celem
        if (targetId != null && targetId != "enemy_${enemy.id}") {
            return false
        }

        // Podobna implementacja jak dla gracza
        val enemyRadius = 15f
        val centerX = x
        val centerY = y

        val distance = Vector2.dst(centerX, centerY, enemy.x, enemy.y)
        return distance <= enemyRadius
    }

    override var isToRemove: Boolean = false

    // Oznaczanie strzały do usunięcia
    override fun markForRemoval() {
        shouldRemove = true
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