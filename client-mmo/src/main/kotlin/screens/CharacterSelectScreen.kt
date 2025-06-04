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
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.FitViewport
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.decodesoft.MMOGame

// Model danych dla listy postaci
@Serializable
data class CharactersListRequest(val userId: String)

@Serializable
data class CharactersListResponse(
    val success: Boolean,
    val message: String,
    val characters: List<CharacterInfo> = emptyList()
)

@Serializable
data class CharacterInfo(
    val id: String,
    val nickname: String,
    val characterClass: Int,
    val maxHealth: Int = 100,
    val currentHealth: Int = 100,
    val level: Int = 1,
    val experience: Int = 0
)

// Ekran wyboru postaci z systemem slotów
class CharacterSelectionScreen(
    private val game: MMOGame,
    private val userId: String,
    private val username: String
) : Screen {
    private lateinit var stage: Stage
    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: FitViewport
    private lateinit var font: BitmapFont
    private lateinit var skin: Skin

    // Tekstury postaci
    private lateinit var archerTexture: Texture
    private lateinit var mageTexture: Texture
    private lateinit var warriorTexture: Texture
    private lateinit var emptySlotTexture: Texture

    // Lista dostępnych postaci (max 3)
    private var characters = mutableListOf<CharacterInfo?>()

    private var selectionScope = CoroutineScope(Dispatchers.IO)
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override fun show() {
        camera = OrthographicCamera()
        viewport = FitViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat(), camera)
        batch = SpriteBatch()

        // Wczytaj czcionkę
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = 24
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
            color = Color.WHITE
        }
        font = generator.generateFont(parameter)
        generator.dispose()

        // Wczytaj tekstury postaci
        try {
            archerTexture = Texture(Gdx.files.internal("textures/archer.png"))
            mageTexture = Texture(Gdx.files.internal("textures/mage.png"))
            warriorTexture = Texture(Gdx.files.internal("textures/warrior.png"))
            emptySlotTexture = Texture(Gdx.files.internal("textures/empty_slot.png"))
        } catch (e: Exception) {
            Gdx.app.error("CharacterSelection", "Nie można załadować tekstur: ${e.message}")
            // Jeśli tekstury nie są dostępne, użyjemy domyślnych kolorowych kwadratów
        }

        stage = Stage(viewport, batch)
        Gdx.input.inputProcessor = stage

        // Tworzymy prosty skin na potrzeby interfejsu
        skin = runCatching {
            Skin(Gdx.files.internal("assets/uiskin.json"))
        }.getOrElse {
            createBasicSkin()
        }

        // Inicjalizuj puste sloty
        for (i in 0 until 3) {
            characters.add(null)
        }

        // Pobierz postacie z serwera
        loadCharacters()
    }

    private fun loadCharacters() {
        selectionScope.launch {
            try {
                // Pobierz listę postaci użytkownika
                val response = httpClient.post("http://localhost:8081/character/list") {
                    contentType(ContentType.Application.Json)
                    setBody(CharactersListRequest(userId))
                }

                val charactersResponse = Json.decodeFromString<CharactersListResponse>(response.bodyAsText())

                if (charactersResponse.success) {
                    // Aktualizuj listę postaci (max 3)
                    withContext(Dispatchers.Default) {
                        // Resetuj listę
                        for (i in 0 until 3) {
                            characters[i] = null
                        }

                        // Wypełnij sloty postaciami
                        charactersResponse.characters.forEachIndexed { index, character ->
                            if (index < 3) {
                                characters[index] = character
                            }
                        }

                        // Utwórz UI z zaktualizowanymi postaciami
                        createUI()
                    }
                } else {
                    Gdx.app.error("CharacterSelection", "Błąd pobierania postaci: ${charactersResponse.message}")
                    // Nawet w przypadku błędu, pokaż interfejs z pustymi slotami
                    withContext(Dispatchers.Default) {
                        createUI()
                    }
                }
            } catch (e: Exception) {
                Gdx.app.error("CharacterSelection", "Błąd połączenia: ${e.message}")
                withContext(Dispatchers.Default) {
                    createUI()
                }
            }
        }
    }

    private fun createBasicSkin(): Skin {
        val skin = Skin()
        skin.add("default", font)

        val textButtonStyle = TextButton.TextButtonStyle().apply {
            font = this@CharacterSelectionScreen.font
            fontColor = Color.WHITE
            downFontColor = Color.LIGHT_GRAY
            up = skin.newDrawable("white", Color.DARK_GRAY)
            down = skin.newDrawable("white", Color.GRAY)
            over = skin.newDrawable("white", Color(0.4f, 0.4f, 0.5f, 1f))
        }
        skin.add("default", textButtonStyle)

        val labelStyle = Label.LabelStyle().apply {
            font = this@CharacterSelectionScreen.font
            fontColor = Color.WHITE
        }
        skin.add("default", labelStyle)

        return skin
    }

    private fun createUI() {
        stage.clear()

        val table = Table()
        table.setFillParent(true)

        val titleLabel = Label("Wybierz lub stwórz nową postać", skin)
        titleLabel.setFontScale(1.5f)

        val loggedAsLabel = Label("Zalogowany jako: $username", skin)

        // Tworzenie tabeli ze slotami postaci
        val charactersTable = Table()

        // Dodawanie slotów postaci
        for (i in 0 until 3) {
            val characterSlot = createCharacterSlot(i)
            charactersTable.add(characterSlot).pad(15f)
        }

        val backButton = TextButton("Wyloguj", skin)
        backButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                game.switchToLoginScreen()
            }
        })

        // Aktualizacja układu
        table.add(titleLabel).colspan(3).pad(20f)
        table.row()
        table.add(loggedAsLabel).colspan(3).pad(10f)
        table.row()
        table.add(charactersTable).colspan(3).pad(20f)
        table.row()
        table.add(backButton).colspan(3).pad(20f).width(150f).height(40f)

        stage.addActor(table)
    }

    private fun createCharacterSlot(slotIndex: Int): Table {
        val character = if (slotIndex < characters.size) characters[slotIndex] else null

        val panel = Table()
        panel.background = skin.newDrawable("white", Color(0.2f, 0.2f, 0.3f, 0.8f))

        if (character != null) {
            // Slot z postacią
            val nameLabel = Label(character.nickname, skin)
            nameLabel.setFontScale(1.2f)

            val classLabel = Label(getClassName(character.characterClass), skin)

            // Etykieta poziomu
            val levelLabel = Label("Poziom: ${character.level}", skin)

            // Obraz postaci
            val characterImage = if (::archerTexture.isInitialized && ::mageTexture.isInitialized && ::warriorTexture.isInitialized) {
                Image(when (character.characterClass) {
                    0 -> archerTexture
                    1 -> mageTexture
                    else -> warriorTexture
                })
            } else {
                // Jeśli tekstury nie są dostępne, tworzymy kolorowy kwadrat
                val colorSquare = Table()
                colorSquare.background = skin.newDrawable("white", when (character.characterClass) {
                    0 -> Color(0.2f, 0.8f, 0.2f, 1f) // Zielony dla łucznika
                    1 -> Color(0.2f, 0.2f, 0.9f, 1f) // Niebieski dla maga
                    else -> Color(0.9f, 0.2f, 0.2f, 1f) // Czerwony dla wojownika
                })
                colorSquare
            }

            val healthLabel = Label("HP: ${character.currentHealth}/${character.maxHealth}", skin)

            val playButton = TextButton("Graj", skin)
            playButton.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    // Wybór postaci i rozpoczęcie gry
                    selectCharacter(slotIndex, character)
                }
            })

            // Układ elementów w panelu
            panel.add(nameLabel).colspan(2).pad(5f)
            panel.row()
            panel.add(classLabel).colspan(2).pad(5f)
            panel.row()
            panel.add(levelLabel).colspan(2).pad(5f)
            panel.row()
            panel.add(characterImage).size(128f, 128f).pad(10f)
            panel.row()
            panel.add(healthLabel).pad(5f)
            panel.row()
            panel.add(playButton).width(120f).height(40f).pad(10f)

        } else {
            // Pusty slot
            val emptyLabel = Label("Pusty slot", skin)
            emptyLabel.setFontScale(1.2f)

            // Grafika pustego slotu
            val emptyImage = if (::emptySlotTexture.isInitialized) {
                Image(emptySlotTexture)
            } else {
                val emptySquare = Table()
                emptySquare.background = skin.newDrawable("white", Color(0.3f, 0.3f, 0.3f, 0.5f))
                emptySquare
            }

            val createButton = TextButton("Stwórz postać", skin)
            createButton.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    createNewCharacter(slotIndex)
                }
            })

            panel.add(emptyLabel).colspan(2).pad(5f)
            panel.row()
            panel.add(emptyImage).size(128f, 128f).pad(20f)
            panel.row()
            panel.add(createButton).width(150f).height(40f).pad(10f)
        }

        return panel
    }

    private fun getClassName(classIndex: Int): String {
        return when (classIndex) {
            0 -> "Łucznik"
            1 -> "Mag"
            else -> "Wojownik"
        }
    }

    private fun selectCharacter(slotIndex: Int, character: CharacterInfo) {
        selectionScope.launch {
            try {
                // Żądanie wyboru postaci
                val response = httpClient.post("http://localhost:8081/character/select") {
                    contentType(ContentType.Application.Json)
                    setBody(CharacterSelectRequest(userId, slotIndex))
                }

                val selectResponse = Json.decodeFromString<CharacterSelectResponse>(response.bodyAsText())

                if (selectResponse.success) {
                    // Przejście do gry z wybraną postacią
                    Gdx.app.postRunnable {
                        game.startGame(username, userId, character.characterClass, character.nickname, level = character.level, experience = character.experience)
                    }
                } else {
                    Gdx.app.error("CharacterSelection", "Błąd wyboru postaci: ${selectResponse.message}")
                }
            } catch (e: Exception) {
                Gdx.app.error("CharacterSelection", "Błąd połączenia: ${e.message}")
            }
        }
    }

    private fun createNewCharacter(slotIndex: Int) {
        // Przejście do ekranu tworzenia postaci
        game.showCharacterCreationScreen(slotIndex)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        batch.projectionMatrix = camera.combined

        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        stage.dispose()
        batch.dispose()
        font.dispose()

        if (::archerTexture.isInitialized) archerTexture.dispose()
        if (::mageTexture.isInitialized) mageTexture.dispose()
        if (::warriorTexture.isInitialized) warriorTexture.dispose()
        if (::emptySlotTexture.isInitialized) emptySlotTexture.dispose()

        selectionScope.cancel()
        httpClient.close()
    }
}

// Modele danych dla żądań i odpowiedzi wyboru postaci
@Serializable
data class CharacterSelectRequest(val userId: String, val characterSlot: Int)

@Serializable
data class CharacterSelectResponse(val success: Boolean, val message: String)