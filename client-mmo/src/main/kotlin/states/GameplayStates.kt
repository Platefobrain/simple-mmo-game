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

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import pl.decodesoft.MMOGame
import pl.decodesoft.screens.DeathScreen
import pl.decodesoft.ui.GameUI

// Stan rozgrywki
class PlayingState(game: MMOGame) : BaseGameState(game) {
    // Inicjalizujemy gameUI jako lazy property, aby upewnić się, że game jest już zainicjalizowane
    private val gameUI by lazy { GameUI(game) }

    override fun enter() {
        // Inicjalizacja elementów gry wymaganych do rozgrywki
    }

    override fun exit() {
        // Czyszczenie zasobów
    }

    override fun update(delta: Float) {
        // Aktualizacja systemu czatu
        game.chatSystem.update(delta)

        // Aktualizacja umiejętności i efektów klas
        game.playerController.update(delta)

        // Aktualizacja przeciwników
        if (game.enemyUpdateTimer >= game.enemyUpdateInterval) {
            game.enemyUpdateTimer = 0f
        }
        game.enemyUpdateTimer += delta

        game.enemies.values.toList().filter { it.isAlive }.forEach { enemy ->
            enemy.update(delta)
        }

        // Aktualizacja pozycji graczy
        game.players.values.forEach { player ->
            if (player.id == game.localPlayerId) {
                player.updateLocalPosition(delta)
            } else {
                player.updatePosition(delta)
            }
        }

        // Sprawdź śmierć gracza
        checkPlayerDeath()
    }

    override fun render(delta: Float) {
        // Czyszczenie ekranu
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Aktualizacja kamery
        game.updateCamera()
        game.camera.update()

        // Rysuje mapę
        game.batch.begin()
        game.gameMap.draw(game.batch)
        game.batch.end()

        // Rysowanie graczy
        game.batch.projectionMatrix = game.camera.combined
        game.shapeRenderer.projectionMatrix = game.camera.combined

        // Rysowanie graczy z kolorami odpowiadającymi klasie
        try {
            // Rysuj wszystkie wypełnione postacie
            game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            game.players.values.forEach { player ->
                game.shapeRenderer.color = player.getClassColor()
                game.shapeRenderer.circle(player.x, player.y, 15f)
            }
            game.shapeRenderer.end()

            // Rysowanie ścieżki pathfindingu
            try {
                game.shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                game.shapeRenderer.color = Color.YELLOW
                val tileSize = 16f

                game.pathTiles.forEach { (tileX, tileY) ->
                    val px = tileX * tileSize + tileSize / 2
                    val py = tileY * tileSize + tileSize / 2
                    game.shapeRenderer.rect(px - tileSize / 2, py - tileSize / 2, tileSize, tileSize)
                }
                game.shapeRenderer.end()
            } catch (e: Exception) {
                if (game.shapeRenderer.isDrawing) game.shapeRenderer.end()
                Gdx.app.error("Render", "Błąd rysowania ścieżki: ${e.message}")
            }

            // Rysuj obramowania zaznaczonych graczy
            val selectedPlayers = game.players.values.filter { it.isSelected }
            if (selectedPlayers.isNotEmpty()) {
                game.shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                selectedPlayers.forEach { player ->
                    game.shapeRenderer.color = Color.YELLOW
                    game.shapeRenderer.circle(player.x, player.y, 18f) // Nieco większy promień dla obramowania
                }
                game.shapeRenderer.end()
            }

            // Renderowanie pasków życia
            game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            game.players.values.forEach { player ->
                // Rysuj tło paska życia (szary)
                game.shapeRenderer.color = Color.DARK_GRAY
                game.shapeRenderer.rect(player.x - 30f, player.y + 35f, 60f, 8f)

                // Rysuj aktualny stan paska życia (zielony lub czerwony, zależnie od ilości HP)
                val healthRatio = player.currentHealth.toFloat() / player.maxHealth.toFloat()
                if (healthRatio > 0.5f) {
                    game.shapeRenderer.color = Color.GREEN
                } else if (healthRatio > 0.25f) {
                    game.shapeRenderer.color = Color.ORANGE
                } else {
                    game.shapeRenderer.color = Color.RED
                }
                game.shapeRenderer.rect(player.x - 30f, player.y + 35f, 60f * healthRatio, 8f)
            }
            game.shapeRenderer.end()

            // Renderowanie strzał i efektów klas
            game.playerController.render(game.shapeRenderer, game.batch, game.font)
        } catch (e: Exception) {
            if (game.shapeRenderer.isDrawing) {
                game.shapeRenderer.end()
            }
            Gdx.app.error("RenderGame", "Error rendering: ${e.message}")
        }

        // Renderowanie przeciwników
        game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        game.enemies.values.toList().filter { it.isAlive }.forEach { it.render(game.shapeRenderer) }
        game.shapeRenderer.end()

        val selectedEnemies = game.enemies.values.toList().filter { it.isSelected }
        if (selectedEnemies.isNotEmpty()) {
            game.shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            selectedEnemies.forEach { enemy ->
                game.shapeRenderer.color = Color.YELLOW
                game.shapeRenderer.circle(enemy.x, enemy.y, 18f) // Nieco większy promień dla obramowania
            }
            game.shapeRenderer.end()
        }

        // Rysowanie UI - teraz używamy klasy GameUI
        gameUI.render()
    }

    override fun handleInput(): Boolean {
        // Obsługa wejścia z renderGame()
        val chatHandled = game.chatSystem.handleInput()
        if (!chatHandled) {
            game.playerController.handleInput()
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            // Obsługa pathfindingu
            val startX = (game.localPlayer.x / 16f).toInt()
            val startY = (game.localPlayer.y / 16f).toInt()
            val endX = 10
            val endY = 10

            game.networkScope.launch {
                game.session?.send(Frame.Text("PATHFIND|$startX|$startY|$endX|$endY"))
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && game.lastHelpToggleTime > game.helpToggleCooldown) {
            game.showHelp = !game.showHelp
            game.lastHelpToggleTime = 0f
        }

        game.lastHelpToggleTime += Gdx.graphics.deltaTime

        return true
    }

    override fun resize(width: Int, height: Int) {
        game.camera.viewportWidth = width.toFloat()
        game.camera.viewportHeight = height.toFloat()
        game.camera.update()
    }

    private fun checkPlayerDeath() {
        if (game.localPlayer.currentHealth <= 0) {
            game.changeState(DeadState(game))
        }
    }
}

// Stan śmierci
class DeadState(game: MMOGame) : BaseGameState(game) {
    private var deathScreen: DeathScreen? = null
    private var deathTimer = 0f
    private val minDeathTime = 2f  // Przenosimy tę wartość tutaj z MMOGame

    override fun enter() {
        deathTimer = 0f
        deathScreen = DeathScreen(game)
        deathScreen?.show()
    }

    override fun exit() {
        deathScreen?.dispose()
        deathScreen = null
    }

    override fun update(delta: Float) {
        deathTimer += delta
    }

    override fun render(delta: Float) {
        deathScreen?.render()
    }

    override fun resize(width: Int, height: Int) {
        deathScreen?.resize(width, height)
    }

    // Dodana metoda canRespawn() - sprawdza, czy można się odrodzić
    private fun canRespawn(): Boolean {
        return deathTimer >= minDeathTime
    }

    // Dodana metoda handleRespawn() - wykonuje proces odradzania
    fun handleRespawn() {
        if (canRespawn()) {
            Gdx.app.log("Respawn", "Sending respawn message")
            // Wysyłamy wiadomość do serwera
            game.sendWebSocketMessage("RESPAWN|${game.localPlayer.id}")

            Gdx.app.postRunnable {
                Gdx.app.log("Respawn", "Switching back to PLAYING state")
                game.changeState(PlayingState(game))
            }
        } else {
            Gdx.app.log("Respawn", "Respawn blocked, death timer not elapsed yet")
        }
    }
}