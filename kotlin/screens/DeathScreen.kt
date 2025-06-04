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

package pl.decodesoft.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import pl.decodesoft.MMOGame

class DeathScreen(private val game: MMOGame) {
    private var batch = SpriteBatch()
    private var shapeRenderer = ShapeRenderer()
    private var font: BitmapFont
    private var glyphLayout = GlyphLayout()

    private val buttonWidth = 200f
    private val buttonHeight = 60f
    private var buttonX = 0f
    private var buttonY = 0f

    init {
        font = BitmapFont().apply {
            data.setScale(2f)
            color = Color.WHITE
        }

        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    fun show() {
        // Wczytaj czcionkę
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontParameter().apply {
            size = 24
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
            color = Color.WHITE
        }
        font = generator.generateFont(parameter)
        generator.dispose()
    }

    fun render() {

        // Czyszczenie ekranu
        Gdx.gl.glClearColor(0.1f, 0.0f, 0.0f, 1f) // Ciemny czerwony tło
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Rysowanie informacji o śmierci
        batch.begin()

        val deathText = "Zginąłeś!"
        glyphLayout.setText(font, deathText)
        font.color = Color.RED
        font.draw(batch, deathText,
            (Gdx.graphics.width - glyphLayout.width) / 2,
            Gdx.graphics.height * 0.7f)

        batch.end()

        // Rysowanie przycisku respawn
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Sprawdź, czy przycisk jest wciśnięty
        val mouseX = Gdx.input.x.toFloat()
        val mouseY = Gdx.graphics.height - Gdx.input.y.toFloat() // Odwrócona oś Y

        val isButtonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY <= buttonY + buttonHeight

        shapeRenderer.color = if (isButtonHovered) Color.LIME else Color.FOREST
        shapeRenderer.rect(buttonX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.end()

        // Rysowanie obramowania przycisku
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(buttonX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.end()

        // Rysowanie tekstu przycisku
        batch.begin()
        val buttonText = "Respawn"
        glyphLayout.setText(font, buttonText)
        font.color = Color.WHITE
        font.draw(batch, buttonText,
            buttonX + (buttonWidth - glyphLayout.width) / 2,
            buttonY + buttonHeight / 2 + glyphLayout.height / 2)
        batch.end()

        // Obsługa kliknięcia przycisku
        if (isButtonHovered && Gdx.input.justTouched()) {
            // Wywołaj metodę respawn z głównej klasy gry
            game.respawnPlayer()
        }
    }

    fun resize(width: Int, height: Int) {
        // Aktualizacja pozycji przycisku po zmianie rozmiaru okna
        buttonX = (width - buttonWidth) / 2
        buttonY = height * 0.4f
    }

    fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }
}