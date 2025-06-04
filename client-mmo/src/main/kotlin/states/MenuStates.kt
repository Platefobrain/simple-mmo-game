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

package pl.decodesoft.states

import pl.decodesoft.MMOGame
import pl.decodesoft.screens.CharacterCreationScreen
import pl.decodesoft.screens.CharacterSelectionScreen
import pl.decodesoft.screens.LoginScreen

// Stan logowania
class LoginState(game: MMOGame) : BaseGameState(game) {
    private var loginScreen: LoginScreen? = null

    override fun enter() {
        loginScreen = LoginScreen(game)
        loginScreen?.show()
    }

    override fun exit() {
        loginScreen?.dispose()
        loginScreen = null
    }

    override fun update(delta: Float) {
        // Brak dodatkowej logiki aktualizacji
    }

    override fun render(delta: Float) {
        loginScreen?.render(delta)
    }

    override fun resize(width: Int, height: Int) {
        loginScreen?.resize(width, height)
    }
}

// Stan wyboru postaci
class CharacterSelectionState(game: MMOGame) : BaseGameState(game) {
    private var characterSelectionScreen: CharacterSelectionScreen? = null

    override fun enter() {
        println("Pokazuję ekran wyboru postaci, użytkownik: ${game.username}, ID: ${game.localPlayerId}")
        characterSelectionScreen = CharacterSelectionScreen(game, game.localPlayerId, game.username)
        characterSelectionScreen?.show()
    }

    override fun exit() {
        characterSelectionScreen?.dispose()
        characterSelectionScreen = null
    }

    override fun update(delta: Float) {
        // Brak dodatkowej logiki aktualizacji
    }

    override fun render(delta: Float) {
        characterSelectionScreen?.render(delta)
    }

    override fun resize(width: Int, height: Int) {
        characterSelectionScreen?.resize(width, height)
    }
}

// Stan tworzenia postaci
class CharacterCreationState(game: MMOGame, private val slotIndex: Int) : BaseGameState(game) {
    private var characterCreationScreen: CharacterCreationScreen? = null

    override fun enter() {
        characterCreationScreen = CharacterCreationScreen(game, game.localPlayerId, game.username, slotIndex)
        characterCreationScreen?.show()
    }

    override fun exit() {
        characterCreationScreen?.dispose()
        characterCreationScreen = null
    }

    override fun update(delta: Float) {
        // Brak dodatkowej logiki aktualizacji
    }

    override fun render(delta: Float) {
        characterCreationScreen?.render(delta)
    }

    override fun resize(width: Int, height: Int) {
        characterCreationScreen?.resize(width, height)
    }
}