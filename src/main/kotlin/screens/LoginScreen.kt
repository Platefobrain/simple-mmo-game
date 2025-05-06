package pl.decodesoft.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
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
import kotlin.math.abs
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import pl.decodesoft.MMOGame

// Model danych dla logowania/rejestracji
@Serializable
data class AuthRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val success: Boolean, val message: String, val userId: String = "")

// Ekran logowania i rejestracji
class LoginScreen(private val game: MMOGame) : Screen {
    private lateinit var stage: Stage
    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: FitViewport
    private lateinit var font: BitmapFont
    private lateinit var skin: Skin
    private lateinit var shapeRenderer: ShapeRenderer

    private var loginScope = CoroutineScope(Dispatchers.IO)
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var errorMessage: String? = null
    private var isLoading = false

    override fun show() {
        camera = OrthographicCamera()
        viewport = FitViewport(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat(), camera)
        batch = SpriteBatch()
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontParameter().apply {
            size = 24
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
            color = Color.WHITE
        }
        font = generator.generateFont(parameter)
        generator.dispose()
        shapeRenderer = ShapeRenderer()

        stage = Stage(viewport, batch)
        Gdx.input.inputProcessor = stage

        // Tworzymy prosty skin na potrzeby interfejsu
        skin = runCatching {
            Skin(Gdx.files.internal("assets/uiskin.json"))
        }.getOrElse {
            // Obsługa błędu (można dodać logowanie)
            createBasicSkin()
        }

        createUI()
    }

    private fun createBasicSkin(): Skin {
        val skin = Skin()
        skin.add("default", font)

        val textFieldStyle = TextField.TextFieldStyle().apply {
            fontColor = Color.WHITE
            background = skin.newDrawable("white", Color.DARK_GRAY)
            cursor = skin.newDrawable("white", Color.WHITE)
            selection = skin.newDrawable("white", Color.BLUE)
        }
        skin.add("default", textFieldStyle)

        val labelStyle = Label.LabelStyle().apply {
            fontColor = Color.WHITE
        }
        skin.add("default", labelStyle)

        return skin
    }

    private fun createUI() {
        val table = Table()
        table.setFillParent(true)

        val titleLabel = Label("Łapsze Niżne MMORPG", skin)
        titleLabel.setFontScale(2f)

        val usernameLabel = Label("Login:", skin)
        val usernameField = TextField("", skin)

        val passwordLabel = Label("Hasło:", skin)
        val passwordField = TextField("", skin)
        passwordField.isPasswordMode = true

        val loginButton = TextButton("Zaloguj się", skin)
        val registerButton = TextButton("Zarejestruj się", skin)

        val statusLabel = Label("", skin)
        statusLabel.setAlignment(Align.center)

        // Układ interfejsu
        table.add(titleLabel).colspan(2).pad(20f)
        table.row()
        table.add(usernameLabel).padRight(10f)
        table.add(usernameField).width(200f)
        table.row()
        table.add(passwordLabel).padRight(10f)
        table.add(passwordField).width(200f)
        table.row().pad(10f)
        table.add(loginButton).padRight(10f)
        table.add(registerButton)
        table.row()
        table.add(statusLabel).colspan(2).pad(20f)

        // Obsługa przycisków
        loginButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val username = usernameField.text
                val password = passwordField.text

                if (username.isBlank() || password.isBlank()) {
                    statusLabel.setText("Proszę wpisać nazwę użytkownika i hasło")
                    statusLabel.color = Color.RED
                    return
                }

                loginUser(username, password, statusLabel)
            }
        })

        registerButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val username = usernameField.text
                val password = passwordField.text

                if (username.isBlank() || password.isBlank()) {
                    statusLabel.setText("Proszę wpisać nazwę użytkownika i hasło")
                    statusLabel.color = Color.RED
                    return
                }

                registerUser(username, password, statusLabel)
            }
        })

        stage.addActor(table)
    }

    // W klasie LoginScreen w metodzie loginUser, zmień:
    private fun loginUser(username: String, password: String, statusLabel: Label) {
        isLoading = true
        statusLabel.setText("Logowanie...")
        statusLabel.color = Color.YELLOW

        loginScope.launch {
            try {
                val response = httpClient.post("http://localhost:8080/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(AuthRequest(username, password))
                }

                val authResponse = Json.decodeFromString<AuthResponse>(response.bodyAsText())

                if (authResponse.success) {
                    statusLabel.setText("Zalogowano pomyślnie!")
                    statusLabel.color = Color.GREEN

                    // Przejście do ekranu wyboru postaci po krótkim opóźnieniu
                    delay(1000)

                    // KLUCZOWA ZMIANA TUTAJ - bezpośrednio ustawiamy dane w MMOGame
                    Gdx.app.postRunnable {
                        // Ustaw dane użytkownika w głównej klasie gry PRZED przejściem do następnego ekranu
                        game.username = username
                        game.localPlayerId = authResponse.userId

                        println("Ustawiono dane użytkownika w MMOGame: ${game.username}, ID: ${game.localPlayerId}")

                        // Teraz dopiero tworzymy ekran wyboru postaci
                        game.showCharacterSelectionScreen()
                    }
                } else {
                    statusLabel.setText(authResponse.message)
                    statusLabel.color = Color.RED
                }
                isLoading = false
            } catch (e: Exception) {
                Gdx.app.postRunnable {
                    statusLabel.setText("Connection error: ${e.message}")
                    statusLabel.color = Color.RED
                    isLoading = false
                }
            }
        }
    }

    private fun registerUser(username: String, password: String, statusLabel: Label) {
        isLoading = true
        statusLabel.setText("Rejestrowanie...")
        statusLabel.color = Color.YELLOW

        loginScope.launch {
            try {
                val response = httpClient.post("http://localhost:8080/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(AuthRequest(username, password))
                }

                val authResponse = Json.decodeFromString<AuthResponse>(response.bodyAsText())

                withContext(Dispatchers.Default) {
                    if (authResponse.success) {
                        statusLabel.setText("Rejestracja udana! Teraz możesz się zalogować.")
                        statusLabel.color = Color.GREEN
                    } else {
                        statusLabel.setText(authResponse.message)
                        statusLabel.color = Color.RED
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Default) {
                    statusLabel.setText("Connection error: ${e.message}")
                    statusLabel.color = Color.RED
                    isLoading = false
                }
            }
        }
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        batch.projectionMatrix = camera.combined

        stage.act(delta)
        stage.draw()

        // Wyświetlanie komunikatu o błędzie
        if (errorMessage != null) {
            batch.begin()
            font.color = Color.RED
            font.draw(batch, errorMessage, 400f, 100f, 0f, Align.center, false)
            batch.end()
        }

        // Animacja ładowania
        if (isLoading) {
            shapeRenderer.projectionMatrix = camera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            val time = (System.currentTimeMillis() % 2000) / 2000f
            val centerX = Gdx.graphics.width / 2f

            for (i in 0 until 3) {
                val alpha = (time + i * 0.33f) % 1f
                val size = 10f * (1f - abs(alpha * 2 - 1))
                shapeRenderer.color = Color(1f, 1f, 1f, 1f)
                // Center the dots horizontally (centerX) while keeping vertical position (50f)
                shapeRenderer.circle(centerX + (i - 1) * 30f, 50f, size)
            }
            shapeRenderer.end()
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun hide() {
    }

    override fun dispose() {
        stage.dispose()
        batch.dispose()
        font.dispose()
        shapeRenderer.dispose()
        loginScope.cancel()
        httpClient.close()
    }
}