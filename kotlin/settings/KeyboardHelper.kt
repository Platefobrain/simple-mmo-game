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

package pl.decodesoft.settings

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch

// Klasa pomocnicza do wyświetlania informacji o dostępnych skrótach klawiszowych
class KeyboardHelper(
    private val characterClass: Int
) {
    // Wyświetla pomoc dotyczącą skrótów klawiszowych w zależności od klasy postaci
    fun renderHelpText(batch: SpriteBatch, font: BitmapFont) {

        // Wspólne skróty
        font.color = Color.WHITE
        font.draw(batch, "Skróty klawiszowe:", 10f, Gdx.graphics.height - 60f)
        font.draw(batch, "ESC - Pokaż/ukryj pomoc", 10f, Gdx.graphics.height - 80f)  // "Pokaż/ukryj pomoc"

        // Skróty specyficzne dla klasy
        when (characterClass) {
            0 -> { // Łucznik
                font.color = Color.YELLOW
                font.draw(batch, "Klasa: Łucznik", 10f, Gdx.graphics.height - 120f)
                font.draw(batch, "Prawy przycisk myszy - Atak/ruch", 10f, Gdx.graphics.height - 140f)
                // Tu można dodać skróty specyficzne dla łucznika
            }
            1 -> { // Mag
                font.color = Color.BLUE
                font.draw(batch, "Klasa: Mag", 10f, Gdx.graphics.height - 120f)
                font.draw(batch, "Prawy przycisk myszy - Atak/ruch", 10f, Gdx.graphics.height - 140f)
                // Tu można dodać skróty specyficzne dla maga
            }
            2 -> { // Wojownik
                font.color = Color.RED
                font.draw(batch, "Klasa: Wojownik", 10f, Gdx.graphics.height - 120f)
                font.draw(batch, "Prawy przycisk myszy - Atak/ruch", 10f, Gdx.graphics.height - 140f)
                font.draw(batch, "Q - Szarża w kierunku zaznaczonego celu lub kursora", 10f, Gdx.graphics.height - 160f)
            }
        }
    }
}