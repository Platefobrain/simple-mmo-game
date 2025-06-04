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
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import pl.decodesoft.enemy.EnemyClient
import pl.decodesoft.effects.CombatEffectsManager
import pl.decodesoft.input.PlayerInputHandler
import pl.decodesoft.klasy.CharacterClass
import pl.decodesoft.klasy.skile.SkileManager
import pl.decodesoft.network.PlayerNetworkManager


// Główna klasa kontrolująca gracza - fasada koordynująca wszystkie komponenty
class PlayerController(
    private val localPlayer: Player,
    players: Map<String, Player>,
    enemies: Map<String, EnemyClient>,
    private val camera: OrthographicCamera,
    networkScope: CoroutineScope,
    getSession: () -> DefaultWebSocketSession?,
    private val characterClass: CharacterClass,
    private val skileManager: SkileManager
) {

    private val combatEffectsManager = CombatEffectsManager()
    private val networkManager = PlayerNetworkManager(
        networkScope = networkScope,
        getSession = getSession
    )

    private val targetManager = PlayerTargetManager(
        camera = camera,
        localPlayer = localPlayer,
        players = players,
        enemies = enemies,
        characterClass = characterClass,
        onTargetChanged = { _, _ -> }
    )

    private val inputHandler = PlayerInputHandler(
        camera = camera,
        targetManager = targetManager,
        onMoveRequested = { x, y -> handleMoveRequest(x, y) },
        onControlKeyPressed = { key -> handleControlKey(key) }
    )

    // Obsługuje wejście użytkownika.
    fun handleInput(): Boolean {
        if (localPlayer.isDead()) {
            return false
        }

        return inputHandler.handleInput()
    }

    // Główna metoda aktualizacji, wywoływana w każdej klatce
    fun update(delta: Float) {
        skileManager.update(delta)
        characterClass.update(delta)
        combatEffectsManager.update(delta)
    }

    // Renderowanie
    fun render(shapeRenderer: ShapeRenderer, batch: SpriteBatch, font: BitmapFont) {
        skileManager.render(shapeRenderer)
        characterClass.renderCooldownBar(shapeRenderer, batch, font, camera)
        combatEffectsManager.render(batch, font)
    }

    // Obsługa wiadomości sieciowych
    fun handleMessage(command: String, parts: List<String>) {
        skileManager.handleSkillMessage(command, parts)
    }

    // Dodawanie efektów tekstowych obrażeń
    fun addDamageText(x: Float, y: Float, text: String, color: Color) {
        combatEffectsManager.addDamageText(x, y, text, color)
    }

    // Obsługa żądania ruchu
    private fun handleMoveRequest(x: Float, y: Float) {
        networkManager.sendMoveRequest(x, y, localPlayer.id)
    }

    // Obsługa przycisków kontrolnych (umiejętności)
    private fun handleControlKey(key: Int) {
        // Implementacja obsługi klawiszy umiejętności
        // ...
    }
}