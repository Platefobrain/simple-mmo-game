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

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import pl.decodesoft.klasy.projectiles.Sword
import pl.decodesoft.klasy.skile.SkileManager
import pl.decodesoft.player.Player


/**
 * Klasa reprezentująca Wojownika
 */
class Warrior(
    player: Player,
    networkScope: CoroutineScope,
    session: () -> DefaultWebSocketSession?,
    skileManager: SkileManager
) : CharacterClass(player, networkScope, session, skileManager) {

    // Nadpisane właściwości z klasy bazowej
    override val attackCooldown = 3f
    override var attackTimer = 0f
    override val attackRange = 45f
    override val attackName = "Atak mieczem"
    override val attackColor: Color = Color.ORANGE

    /**
     * Wykonuje atak mieczem w kierunku celu
     */
    override fun performAttack(targetX: Float, targetY: Float, targetId: String) {
        // Oblicz kierunek ataku
        val dirX = targetX - player.x
        val dirY = targetY - player.y
        val distance: Float = Vector2.dst(player.x, player.y, targetX, targetY)

        // Normalizacja wektora kierunku
        val normalizedDirX = dirX / distance
        val normalizedDirY = dirY / distance

        // Utwórz nowy atak mieczem
        val sword = Sword(
            player.x,
            player.y,
            normalizedDirX,
            normalizedDirY,
            player.id,
            targetId
        )

        // Dodaj atak do menedżera umiejętności
        skileManager.addSkill(sword)

        // Wyślij informację o ataku do serwera
        sendAttackMessage(
            "MELEE_ATTACK",
            targetX,
            targetY,
            normalizedDirX,
            normalizedDirY,
            targetId
        )
    }
}