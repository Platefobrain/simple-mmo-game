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
 * Klasa reprezentująca kulę ognia wystrzeloną przez Maga
 */
class Fireball(
    override var x: Float,
    override var y: Float,
    private val directionX: Float,
    private val directionY: Float,
    override val casterId: String,
    private val targetId: String? = null,
    private val speed: Float = 350f,
    private val maxDistance: Float = 450f,
    private var distanceTraveled: Float = 0f
) : Skile {
    override val id: String = UUID.randomUUID().toString()
    override val color: Color = Color.ORANGE
    override val size = 12f
    private val innerSize = 6f

    // Dodaj zmienną dla flagi usuwania
    private var shouldRemove: Boolean = false

    // Hitbox dla kuli ognia (używany do kolizji)
    private val hitbox = Rectangle(x - size/2, y - size/2, size, size)

    override fun update(delta: Float): Boolean {
        // Jeśli strzała jest oznaczona do usunięcia, zwróć false
        if (shouldRemove) return false

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
        if (targetId != null && targetId != player.id) {
            return false
        }

        // Prosta kolizja okrąg-okrąg
        val playerRadius = 15f
        val centerX = x
        val centerY = y

        val distance = Vector2.dst(centerX, centerY, player.x, player.y)
        return distance <= playerRadius + size/2
    }

    // Implementacja sprawdzania kolizji z przeciwnikiem
    override fun checkCollision(enemy: EnemyClient): Boolean {
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

    // Implementacja oznaczania pocisku do usunięcia
    override fun markForRemoval() {
        shouldRemove = true
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