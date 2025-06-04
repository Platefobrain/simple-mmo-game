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

package pl.decodesoft.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import pl.decodesoft.player.PlayerTargetManager

/**
 * Klasa odpowiedzialna za obsługę wejścia gracza
 */
class PlayerInputHandler(
    private val camera: OrthographicCamera,
    private val targetManager: PlayerTargetManager,
    private val onMoveRequested: (Float, Float) -> Unit,
    private val onControlKeyPressed: (Int) -> Unit
) {
    fun handleInput(): Boolean {
        // Obsługa klawiszy kontrolnych (umiejętności)
        if (handleControlKeys()) {
            return true
        }

        // Obsługa LEWEGO przycisku myszy
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return handleLeftMouseButton()
        }

        // Obsługa PRAWEGO przycisku myszy
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            return handleRightMouseButton()
        }

        return false
    }

    private fun handleControlKeys(): Boolean {
        // Obsługa klawiszy Q, W, E, R itd.
        for (key in arrayOf(Input.Keys.Q, Input.Keys.W, Input.Keys.E, Input.Keys.R,
            Input.Keys.SPACE, Input.Keys.NUM_1, Input.Keys.NUM_2)) {
            if (Gdx.input.isKeyJustPressed(key)) {
                onControlKeyPressed(key)
                return true
            }
        }
        return false
    }

    private fun handleLeftMouseButton(): Boolean {
        val entity = targetManager.findEntityUnderCursor()

        if (entity != null) {
            val (target, entityType) = entity
            targetManager.setTarget(target, entityType)
        } else {
            targetManager.clearTarget()
        }

        return true
    }

    private fun handleRightMouseButton(): Boolean {
        try {
            val entity = targetManager.findEntityUnderCursor()

            if (entity != null) {
                val (target, entityType) = entity
                targetManager.setTarget(target, entityType)
                // Sprawdzamy czy atak został wykonany
                val attackPerformed = targetManager.requestAttackOnTarget()
                return attackPerformed // Zwracamy true tylko jeśli atak został rzeczywiście wykonany
            } else {
                // Ruch do wskazanego miejsca
                try {
                    val worldCoords = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))
                    onMoveRequested(worldCoords.x, worldCoords.y)
                    return true // Żądanie ruchu zostało przekazane pomyślnie
                } catch (e: Exception) {
                    Gdx.app.error("PlayerInputHandler", "Błąd przy konwersji współrzędnych: ${e.message}")
                    return false // Zwracamy false jeśli nie udało się przetworzyć współrzędnych
                }
            }
        } catch (e: Exception) {
            Gdx.app.error("PlayerInputHandler", "Błąd podczas obsługi prawego przycisku myszy: ${e.message}")
            return false // Zwracamy false w przypadku jakiegokolwiek nieoczekiwanego błędu
        }
    }
}