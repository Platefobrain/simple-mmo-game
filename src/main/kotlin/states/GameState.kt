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

// Interfejs dla wszystkich stanów gry
interface GameState {
    fun enter()    // Wywoływane przy wejściu w stan
    fun exit()     // Wywoływane przy wyjściu ze stanu
    fun update(delta: Float) // Aktualizacja stanu
    fun render(delta: Float) // Renderowanie stanu
    fun resize(width: Int, height: Int) // Obsługa zmiany rozmiaru okna
    fun handleInput(): Boolean // Obsługa wejścia użytkownika
}

// Klasa bazowa dla stanów gry
abstract class BaseGameState(protected val game: MMOGame) : GameState {
    // Domyślne implementacje
    override fun enter() {}
    override fun exit() {}
    override fun resize(width: Int, height: Int) {}
    override fun handleInput(): Boolean = false
}