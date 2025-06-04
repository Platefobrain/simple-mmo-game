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

package pl.decodesoft.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import pl.decodesoft.MMOGame

// Klasa odpowiedzialna za renderowanie elementów interfejsu użytkownika
class GameUI(private val game: MMOGame) {

    private fun resetFontColor() {
        // Ustawia domyślny kolor czcionki na biały (nieprzezroczysty)
        game.font.color = Color.WHITE
    }

     // Renderuje wszystkie elementy UI
     fun render() {
         // Zakończ jakiekolwiek aktywne operacje renderowania
         if (game.uiBatch.isDrawing) {
             game.uiBatch.end()
         }
         if (game.batch.isDrawing) {
             game.batch.end()
         }
         if (game.shapeRenderer.isDrawing) {
             game.shapeRenderer.end()
         }

         // Renderuj pasek XP
         renderXPBar()

         // Renderowanie nazw graczy i klas
         renderPlayerNames()

         // Renderowanie informacji o grze
         renderGameInfo()

         // Renderowanie nazw przeciwników
         renderEnemyNames()

         // Renderowanie informacji o zaznaczonym graczu
         renderSelectedPlayerInfo()

         // Rozpocznij uiBatch przed renderowaniem czatu
         game.uiBatch.begin()

         // Renderowanie czatu
         game.chatSystem.render(game.uiBatch, game.font, game.camera)

         // Renderowanie pomocy
         if (game.showHelp) {
             game.keyboardHelper.renderHelpText(game.uiBatch, game.font)
         }

         // Zakończ uiBatch
         game.uiBatch.end()
     }

    // Renderuje pasek XP
    private fun renderXPBar() {
        val xpBarWidth = 800f
        val xpBarHeight = 10f

        // Wyśrodkowanie na ekranie i 150px od dołu
        val xpBarX = (game.camera.viewportWidth - xpBarWidth) / 2  // wyśrodkowanie poziome
        val xpBarY = 100f

        val currentXP = game.localPlayer.experience
        val currentLevel = game.localPlayer.level
        val xpToNextLevel = 100 * currentLevel

        val xpPercent = currentXP.toFloat() / xpToNextLevel.toFloat()

        // Ustawienie projekcji na widok ekranu (UI), a nie na widok świata
        val oldProjection = game.shapeRenderer.projectionMatrix.cpy()

        // Ustawienie macierzy projekcji na identyczną z widokiem UI (zazwyczaj ortograficzna projekcja ekranu)
        val uiProjection = game.uiBatch.projectionMatrix.cpy()  // zakładamy, że uiBatch używa projekcji UI
        game.shapeRenderer.projectionMatrix = uiProjection

        // Włączamy blending dla przezroczystości
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Tło paska (ciemnoszare z przezroczystością)
        game.shapeRenderer.color = Color(0.2f, 0.2f, 0.2f, 0.7f)
        game.shapeRenderer.rect(xpBarX, xpBarY, xpBarWidth, xpBarHeight)

        // Wypełnienie paska
        game.shapeRenderer.color = Color(0.6f, 0.2f, 0.8f, 0.4f)
        game.shapeRenderer.rect(xpBarX, xpBarY, xpBarWidth * xpPercent.coerceIn(0f, 1f), xpBarHeight)

        game.shapeRenderer.end()

        // Przywróć oryginalną macierz projekcji
        game.shapeRenderer.projectionMatrix = oldProjection

        // Teraz narysuj tekst również używając projekcji UI
        val oldBatchProjection = game.batch.projectionMatrix.cpy()
        game.batch.projectionMatrix = uiProjection
        game.batch.begin()

        // Zapisz oryginalny kolor czcionki
        val originalFontColor = game.font.color.cpy()

        // Przygotuj tekst XP
        val xpText = "XP: $currentXP / $xpToNextLevel"

        // Oblicz wymiary tekstu, aby móc go wycentrować
        game.layout.setText(game.font, xpText)
        val textWidth = game.layout.width
        val textHeight = game.layout.height

        // Ustaw kolor czcionki na biały dla lepszego kontrastu na ciemnoszarym tle
        game.font.color = Color.WHITE

        // Oblicz pozycję tekstu, aby był wyśrodkowany w pasku
        val textX = xpBarX + (xpBarWidth - textWidth) / 2
        val textY = xpBarY + (xpBarHeight + textHeight) / 2

        // Narysuj tekst wyśrodkowany w pasku XP
        game.font.draw(game.batch, xpText, textX, textY)

        // Przywróć oryginalny kolor czcionki
        game.font.color = originalFontColor

        game.batch.end()

        // Przywróć oryginalną macierz projekcji
        game.batch.projectionMatrix = oldBatchProjection

        // Wyłączamy blending (opcjonalnie, jeśli nie jest używany w innych miejscach)
        // Gdx.gl.glDisable(GL20.GL_BLEND);
        resetFontColor()
    }

    //Renderuje nazwy graczy
    private fun renderPlayerNames() {
        // Najpierw renderujemy tła za pomocą ShapeRenderer
        game.shapeRenderer.projectionMatrix = game.camera.combined

        // Włączamy blending dla przezroczystości
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        try {
            game.players.values.forEach { player ->
                val playerText = "${player.username} [#9933CC](${player.level})"

                // Oblicz wymiary tekstu
                game.layout.setText(game.font, playerText)
                val textWidth = game.layout.width
                val textHeight = game.layout.height

                // Dodaj padding wokół tekstu
                val padding = 4f
                val backgroundWidth = textWidth + (padding * 2)
                val backgroundHeight = textHeight + (padding * 2)

                // Pozycja tła (wycentrowane nad graczem)
                val backgroundX = player.x - backgroundWidth / 2
                val backgroundY = player.y + 50 - padding

                // Rysuj tło (ciemnoszare z przezroczystością)
                game.shapeRenderer.color = Color(0f, 0f, 0f, 0.1f) // Czarne tło z 60% przezroczystości
                game.shapeRenderer.rect(backgroundX, backgroundY, backgroundWidth, backgroundHeight)

            }
        } catch (e: Exception) {
            Gdx.app.error("GameUI", "Error rendering player name backgrounds: ${e.message}")
        }

        game.shapeRenderer.end()

        // Teraz renderujemy tekst
        game.batch.projectionMatrix = game.camera.combined
        game.batch.begin()

        try {
            game.players.values.forEach { player ->
                // Tekst z poziomem w nawiasie, kolorem żółtym
                val playerText = "${player.username} [#FFFF00](${player.level})"

                // Ustaw kolor domyślny dla pozostałej części tekstu (nazwa)
                game.font.color = Color.WHITE

                // Oblicz szerokość napisu, by wycentrować go nad głową postaci
                game.layout.setText(game.font, playerText)

                game.font.draw(game.batch, playerText, player.x - game.layout.width / 2, player.y + 60)
            }
        } catch (e: Exception) {
            Gdx.app.error("GameUI", "Error rendering player names: ${e.message}")
        }

        game.batch.end()
        resetFontColor()
    }

    // Renderuje nazwy przeciwników
    private fun renderEnemyNames() {
        game.uiBatch.begin()

        try {
            game.enemies.values.filter { it.isAlive }.forEach { enemy ->
                enemy.renderName(game.uiBatch, game.font, game.camera, game.layout)
            }
        } catch (e: Exception) {
            Gdx.app.error("GameUI", "Error rendering enemy names: ${e.message}")
        }

        game.uiBatch.end()
    }

    // Renderuje informacje o grze
    private fun renderGameInfo() {
        // Pobierz pozycję kamery dla elementów UI
        val cameraX = game.camera.position.x - game.camera.viewportWidth / 2
        val cameraY = game.camera.position.y - game.camera.viewportHeight / 2

        game.batch.projectionMatrix = game.camera.combined
        game.batch.begin()

        try {
            game.font.color = Color.WHITE
            game.font.draw(game.batch, "Gracze online: ${game.players.size}", cameraX + 10f, cameraY + game.camera.viewportHeight - 10f)
            game.font.draw(
                game.batch,
                "Zalogowany jako: ${game.username} (${game.localPlayer.getClassName()})",
                cameraX + 10f,
                cameraY + game.camera.viewportHeight - 30f
            )
            // Wyświetlenie poziomu gracza
            val levelText = "Poziom: ${game.localPlayer.level}"
            game.font.draw(game.batch, levelText, cameraX + 10f, cameraY + game.camera.viewportHeight - 50f)
        } catch (e: Exception) {
            Gdx.app.error("GameUI", "Error rendering game info: ${e.message}")
        }

        game.batch.end()
    }

    // Renderuje informacje o zaznaczonym graczu
    private fun renderSelectedPlayerInfo() {
        // Pobierz pozycję kamery dla elementów UI
        val cameraX = game.camera.position.x - game.camera.viewportWidth / 2
        val cameraY = game.camera.position.y - game.camera.viewportHeight / 2

        game.batch.projectionMatrix = game.camera.combined
        game.batch.begin()

        try {
            // Dodaj informację o zaznaczonym graczu
            val selectedPlayer = game.players.values.find { it.isSelected }
            if (selectedPlayer != null) {
                game.font.color = Color.WHITE
                game.font.draw(
                    game.batch,
                    "Zaznaczony gracz: ${selectedPlayer.username}",
                    cameraX + 10f,
                    cameraY + game.camera.viewportHeight - 90f
                )
            }
        } catch (e: Exception) {
            Gdx.app.error("GameUI", "Error rendering selected player info: ${e.message}")
        }

        game.batch.end()
    }
}