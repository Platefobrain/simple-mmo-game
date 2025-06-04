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

@Serializable
data class CharacterCreateRequest(
    val userId: String,
    val characterClass: Int,
    val nickname: String,
    val slotIndex: Int
)

@Serializable
data class CharacterCreateResponse(
    val success: Boolean,
    val message: String
)

class CharacterCreationScreen(
    private val game: MMOGame,
    private val userId: String,
    private val username: String,
    private val slotIndex: Int
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

    private var selectedClass = 2 // Domyślnie wojownik (0-łucznik, 1-mag, 2-wojownik)
    private var nicknameField: TextField? = null
    private var playerNickname: String = username // Domyślnie ustawione na username

    private var creationScope = CoroutineScope(Dispatchers.IO)
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
        } catch (e: Exception) {
            Gdx.app.error("CharacterCreation", "Nie można załadować tekstur: ${e.message}")
        }

        stage = Stage(viewport, batch)
        Gdx.input.inputProcessor = stage

        // Tworzymy prosty skin na potrzeby interfejsu
        skin = runCatching {
            Skin(Gdx.files.internal("assets/uiskin.json"))
        }.getOrElse {
            createBasicSkin()
        }

        createUI()
    }

    private fun createBasicSkin(): Skin {
        val skin = Skin()
        skin.add("default", font)

        val textButtonStyle = TextButton.TextButtonStyle().apply {
            font = this@CharacterCreationScreen.font
            fontColor = Color.WHITE
            downFontColor = Color.LIGHT_GRAY
            up = skin.newDrawable("white", Color.DARK_GRAY)
            down = skin.newDrawable("white", Color.GRAY)
            over = skin.newDrawable("white", Color(0.4f, 0.4f, 0.5f, 1f))
        }
        skin.add("default", textButtonStyle)

        val labelStyle = Label.LabelStyle().apply {
            font = this@CharacterCreationScreen.font
            fontColor = Color.WHITE
        }
        skin.add("default", labelStyle)

        val textFieldStyle = TextField.TextFieldStyle().apply {
            font = this@CharacterCreationScreen.font
            fontColor = Color.WHITE
            background = skin.newDrawable("white", Color(0.2f, 0.2f, 0.2f, 1f))
            cursor = skin.newDrawable("white", Color.WHITE)
            selection = skin.newDrawable("white", Color(0.3f, 0.3f, 0.7f, 1f))
        }
        skin.add("default", textFieldStyle)

        return skin
    }

    private fun createUI() {
        val table = Table()
        table.setFillParent(true)

        val titleLabel = Label("Stwórz nową postać", skin)
        titleLabel.setFontScale(1.5f)

        // Dodaj etykietę i pole tekstowe na nickname
        val nicknameLabel = Label("Nazwa postaci:", skin)
        nicknameField = TextField(username, skin) // Domyślnie ustawione na username
        nicknameField?.maxLength = 20

        // Listener do obsługi zmian w nicku
        nicknameField?.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                playerNickname = nicknameField?.text ?: username
            }
        })

        val loggedAsLabel = Label("Zalogowany jako: $username (Slot ${slotIndex + 1})", skin)

        // Tworzenie tabeli z klasami
        val classesTable = Table()

        // Tworzenie paneli klas
        val archerTable = createClassPanel("Łucznik", "Specjalizuje się w atakach dystansowych\ni wysokich obrażeniach pojedynczego celu.", 0)
        val mageTable = createClassPanel("Mag", "Włada potężną magią obszarową\ni potrafi kontrolować pole bitwy.", 1)
        val warriorTable = createClassPanel("Wojownik", "Wytrzymały czempion walczący wręcz,\nidealny do obrony sojuszników.", 2)

        // Dodawanie klas do tabeli
        classesTable.add(archerTable).pad(10f)
        classesTable.add(mageTable).pad(10f)
        classesTable.add(warriorTable).pad(10f)

        // Przycisk zatwierdzający
        val confirmButton = TextButton("Stwórz postać", skin)
        confirmButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                createCharacter()
            }
        })

        // Przycisk anulowania
        val cancelButton = TextButton("Anuluj", skin)
        cancelButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                game.showCharacterSelectionScreen()
            }
        })

        // Przyciski w jednym wierszu
        val buttonsTable = Table()
        buttonsTable.add(confirmButton).width(150f).height(40f).padRight(20f)
        buttonsTable.add(cancelButton).width(150f).height(40f)

        // Aktualizacja układu
        table.add(titleLabel).colspan(3).pad(20f)
        table.row()
        table.add(loggedAsLabel).colspan(3).pad(10f)
        table.row()

        // Dodaj pole nicku
        val nicknameTable = Table()
        nicknameTable.add(nicknameLabel).padRight(10f)
        nicknameTable.add(nicknameField).width(200f)
        table.add(nicknameTable).colspan(3).pad(10f)
        table.row()

        table.add(classesTable).colspan(3).pad(20f)
        table.row()
        table.add(buttonsTable).colspan(3).pad(20f)

        stage.addActor(table)
    }

    private fun createClassPanel(name: String, description: String, classIndex: Int): Table {
        val panel = Table()
        panel.background = skin.newDrawable("white", Color(0.2f, 0.2f, 0.3f, 0.8f))

        val nameLabel = Label(name, skin)
        nameLabel.setFontScale(1.2f)

        // Sprawdzanie tekstur
        val image = if (::archerTexture.isInitialized && ::mageTexture.isInitialized && ::warriorTexture.isInitialized) {
            Image(when (classIndex) {
                0 -> archerTexture
                1 -> mageTexture
                else -> warriorTexture
            })
        } else {
            // Jeśli tekstury nie są dostępne, utwórz kolorowy kwadrat
            val colorSquare = Table()
            colorSquare.background = skin.newDrawable("white", when (classIndex) {
                0 -> Color(0.2f, 0.8f, 0.2f, 1f) // Zielony dla łucznika
                1 -> Color(0.2f, 0.2f, 0.9f, 1f) // Niebieski dla maga
                else -> Color(0.9f, 0.2f, 0.2f, 1f) // Czerwony dla wojownika
            })
            colorSquare
        }

        val descLabel = Label(description, skin)
        descLabel.wrap = true

        val selectButton = TextButton("Wybierz", skin)

        // Podświetl wybraną klasę
        if (selectedClass == classIndex) {
            panel.background = skin.newDrawable("white", Color(0.3f, 0.5f, 0.3f, 0.8f))
        }

        selectButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                selectedClass = classIndex
                stage.clear()
                createUI()
            }
        })

        panel.add(nameLabel).colspan(2).pad(5f)
        panel.row()
        panel.add(image).size(128f, 128f).pad(10f)
        panel.row()
        panel.add(descLabel).width(180f).height(70f).pad(5f)
        panel.row()
        panel.add(selectButton).width(120f).height(40f).pad(10f)

        return panel
    }

    private fun createCharacter() {
        // Walidacja nicku
        if (playerNickname.isBlank() || playerNickname.length < 3) {
            showError("Nazwa postaci musi mieć co najmniej 3 znaki")
            return
        }

        creationScope.launch {
            try {
                val response = httpClient.post("http://localhost:8081/character/create") {
                    contentType(ContentType.Application.Json)
                    setBody(CharacterCreateRequest(userId, selectedClass, playerNickname, slotIndex))
                }

                val createResponse = Json.decodeFromString<CharacterCreateResponse>(response.bodyAsText())

                if (createResponse.success) {
                    // Powrót do ekranu wyboru postaci
                    Gdx.app.postRunnable {
                        game.showCharacterSelectionScreen()
                    }
                } else {
                    Gdx.app.error("CharacterCreation", "Błąd tworzenia postaci: ${createResponse.message}")
                    withContext(Dispatchers.Default) {
                        showError(createResponse.message)
                    }
                }
            } catch (e: Exception) {
                Gdx.app.error("CharacterCreation", "Błąd połączenia: ${e.message}")
                withContext(Dispatchers.Default) {
                    showError("Błąd połączenia z serwerem")
                }
            }
        }
    }

    private fun showError(message: String) {
        val dialog = Dialog("Błąd", skin)
        dialog.text(message)
        dialog.button("OK")
        dialog.show(stage)
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

        creationScope.cancel()
        httpClient.close()
    }
}