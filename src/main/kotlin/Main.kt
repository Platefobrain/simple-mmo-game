package pl.decodesoft

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.decodesoft.msg.ChatSystem
import pl.decodesoft.player.Player
import pl.decodesoft.player.PlayerHandler
import pl.decodesoft.screens.CharacterSelectionScreen
import pl.decodesoft.screens.LoginScreen
import pl.decodesoft.settings.KeyboardHelper
import java.util.concurrent.ConcurrentHashMap
import pl.decodesoft.klasy.skile.SkileManager
import pl.decodesoft.screens.CharacterCreationScreen
import pl.decodesoft.screens.DeathScreen

// Główny kod gry
class MMOGame : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var camera: OrthographicCamera
    private lateinit var chatSystem: ChatSystem
    private lateinit var keyboardHelper: KeyboardHelper

    private var deathScreen: DeathScreen? = null
    private var deathTimer = 0f
    private val minDeathTime = 2f // Minimalny czas w stanie śmierci (2 sekundy)

    // Zmienna do kontrolowania widoczności pomocy
    private var showHelp = false
    private var lastHelpToggleTime = 0f
    private val helpToggleCooldown = 0.3f

    // Dane gracza
    var localPlayerId = "player_${System.currentTimeMillis()}"
    var username = "Guest"
    private lateinit var localPlayer: Player
    private val layout = GlyphLayout()

    // Mapa wszystkich graczy
    private val players = ConcurrentHashMap<String, Player>()

    // Komunikacja
    private lateinit var networkScope: CoroutineScope
    private var client: HttpClient? = null
    private var session: DefaultWebSocketSession? = null

    // Obsługa gracza i umiejętności
    private lateinit var playerHandler: PlayerHandler

    // Stan gry
    private enum class GameState {
        LOGIN, CHARACTER_SELECTION, CHARACTER_CREATION, PLAYING, DEAD
    }

    private var currentState: GameState = GameState.LOGIN
    private var loginScreen: LoginScreen? = null
    private var characterSelectionScreen: CharacterSelectionScreen? = null
    private var characterCreationScreen: CharacterCreationScreen? = null

    override fun create() {
        deathScreen = DeathScreen(this)
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontParameter().apply {
            size = 15
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
            color = Color.WHITE
        }
        font = generator.generateFont(parameter)
        generator.dispose()

        camera = OrthographicCamera()
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        // Inicjalizacja zakresu coroutine dla komunikacji sieciowej
        networkScope = CoroutineScope(Dispatchers.IO)

        // Inicjalizacja ekranu logowania
        loginScreen = LoginScreen(this)
        loginScreen?.show()

        // Ustaw początkowy stan gry
        currentState = GameState.LOGIN


    }

    // Pokazuje ekran wyboru postaci
    fun showCharacterSelectionScreen(screen: CharacterSelectionScreen? = null) {
        println("Pokazuję ekran wyboru postaci, użytkownik: $username, ID: $localPlayerId")

        // Przypisz do zmiennej lokalnej
        characterSelectionScreen = screen ?: CharacterSelectionScreen(this, localPlayerId, username)
        // Użyj bezpiecznego operatora wywoływania ?.
        characterSelectionScreen?.show()
        currentState = GameState.CHARACTER_SELECTION
    }

    // Pokazuje ekran tworzenia postaci
    fun showCharacterCreationScreen(slotIndex: Int) {
        characterCreationScreen = CharacterCreationScreen(this, localPlayerId, username, slotIndex)
        characterCreationScreen?.show()
        currentState = GameState.CHARACTER_CREATION
    }

    // Przełącza na ekran logowania
    fun switchToLoginScreen() {
        loginScreen = LoginScreen(this)
        loginScreen?.show()
        currentState = GameState.LOGIN
    }

    // Metoda wywoływana po pomyślnym wyborze postaci
    fun startGame(userUsername: String, userId: String, characterClass: Int = 2, nickname: String = userUsername) {
        username = userUsername
        localPlayerId = userId

        // Tworzenie gracza z wybraną klasą postaci i nickiem
        localPlayer = Player(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f, localPlayerId, Color.RED, nickname, characterClass)

        // Inicjalizacja czatu
        chatSystem = ChatSystem(
            localPlayerId = localPlayerId,
            username = nickname, // Użyj nickname zamiast username
            networkScope = networkScope,
            getSession = { session }
        )

        // Dodaj tę linię poniżej-inicjalizacja keyboarder
        keyboardHelper = KeyboardHelper(localPlayer.characterClass)

        // Dodajemy lokalnego gracza do mapy
        players[localPlayerId] = localPlayer

        // Inicjalizacja menedżera umiejętności
        val skileManager = SkileManager(
            localPlayerId = localPlayerId,
            players = players,
            networkScope = networkScope,
            session = { session }
        )

        // Inicjalizacja obsługi gracza i umiejętności z managerial
        playerHandler = PlayerHandler(
            localPlayer = localPlayer,
            players = players,
            camera = camera,
            networkScope = networkScope,
            session = { session },
            skileManager = skileManager
        )

        // Uruchomienie połączenia websocket
        connectToServer()

        // Zmiana stanu gry
        currentState = GameState.PLAYING
    }

    private fun connectToServer() {
        networkScope.launch {
            try {
                client = HttpClient(CIO) {
                    install(WebSockets)
                }

                client?.webSocket("ws://localhost:8080/ws") {
                    session = this

                    // Rejestracja gracza z uwierzytelnieniem i klasą postaci
                    send("JOIN|${localPlayer.x}|${localPlayer.y}|${localPlayer.id}|$username|${localPlayer.characterClass}")

                    // Odbieranie wiadomości
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val message = frame.readText()
                            processMessage(message)
                        }
                    }
                }
            } catch (e: Exception) {
                Gdx.app.error("WebSocket", "Błąd połączenia: ${e.message}")
            }
        }
    }

    private fun processMessage(message: String) {
        val parts = message.split("|")
        when (parts[0]) {
            "JOIN" -> {
                if (parts.size >= 7) {
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    val id = parts[3]
                    val playerUsername = if (parts.size >= 5) parts[4] else "Unknown"
                    val characterClass = if (parts.size >= 6) parts[5].toIntOrNull() ?: 2 else 2
                    val currentHealth = if (parts.size >= 7) parts[6].toIntOrNull() ?: 100 else 100
                    val maxHealth = if (parts.size >= 8) parts[7].toIntOrNull() ?: 100 else 100

                    if (id != localPlayerId) {
                        val newPlayer = Player(x, y, id, Color.BLUE, playerUsername, characterClass)
                        newPlayer.currentHealth = currentHealth
                        newPlayer.maxHealth = maxHealth
                        players[id] = newPlayer
                    }
                }
            }

            "MOVE" -> {
                if (parts.size >= 4) {
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    val id = parts[3]

                    players[id]?.let { player ->
                        if (id == localPlayerId) {
                            // Korekta serwera dla lokalnego gracza
                            player.applyServerCorrection(x, y)
                        } else {
                            // Ustawienie celu ruchu dla innych graczy
                            player.setMoveTarget(x, y)
                        }
                    }
                }
            }

            "LEAVE" -> {
                if (parts.size >= 2) {
                    val id = parts[1]
                    players.remove(id)
                }
            }

            "CHAT" -> {
                if (parts.size >= 4) {
                    val senderId = parts[1]
                    val senderName = parts[2]
                    val content = parts.subList(3, parts.size).joinToString("|")

                    // Odbierz wiadomość czatu
                    Gdx.app.postRunnable {
                        chatSystem.receiveMessage(senderId, senderName, content)
                    }
                }
            }

            "HIT_DETAILED" -> {
                if (parts.size >= 7) {
                    val targetId = parts[1]
                    val attackerId = parts[2]
                    val attackType = parts[3]
                    val currentHealth = parts[4].toIntOrNull() ?: 0
                    val maxHealth = parts[5].toIntOrNull() ?: 100
                    val damage = parts[6].toIntOrNull() ?: 0

                    // Aktualizuj zdrowie trafionego gracza
                    players[targetId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth

                        // Dodaj efekt tekstowy obrażeń, tylko jeśli jesteśmy atakującym lub atakowanym
                        if (targetId == localPlayerId || attackerId == localPlayerId) {
                            playerHandler.addDamageText(player.x, player.y + 20f, "-$damage", Color.RED)
                        }
                    }

                    // Jeśli lokalny gracz został trafiony
                    if (targetId == localPlayerId) {
                        Gdx.app.log("HIT", "Zostałeś trafiony! Twoje zdrowie: $currentHealth/$maxHealth")
                    }
                }
            }

            "HIT" -> {
                if (parts.size >= 5) {
                    val targetId = parts[1]
                    val attackerId = parts[2]
                    val attackType = parts[3]
                    val currentHealth = parts[4].toIntOrNull() ?: 0
                    val maxHealth = if (parts.size >= 6) parts[5].toIntOrNull() ?: 100 else 100

                    // Aktualizuj zdrowie trafionego gracza bez pokazywania efektu tekstowego
                    players[targetId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth

                        // Nie dodajemy tutaj efektu tekstowego z obrażeniami
                        // Efekt jest dodawany tylko w przypadku HIT_DETAILED
                    }

                    // Jeśli lokalny gracz został trafiony
                    if (targetId == localPlayerId) {
                        Gdx.app.log("HIT", "Zostałeś trafiony! Twoje zdrowie: $currentHealth/$maxHealth")
                    }
                }
            }

            "HEALTH_UPDATE" -> {
                if (parts.size >= 4) {
                    val playerId = parts[1]
                    val currentHealth = parts[2].toIntOrNull() ?: 0
                    val maxHealth = parts[3].toIntOrNull() ?: 100

                    players[playerId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth
                    }
                }
            }

            "PLAYER_DIED" -> {
                if (parts.size >= 2) {
                    val playerId = parts[1]

                    // Jeśli to lokalny gracz zginął
                    if (playerId == localPlayerId) {
                        Gdx.app.postRunnable {
                            // Przełącz na ekran śmierci, tylko jeśli to lokalny gracz
                            currentState = GameState.DEAD
                            deathTimer = 0f
                            deathScreen?.show()
                        }
                    }
                }
            }

            "RESPAWN" -> {
                if (parts.size >= 4) {
                    val playerId = parts[1]
                    val currentHealth = parts[2].toIntOrNull() ?: 100
                    val maxHealth = parts[3].toIntOrNull() ?: 100

                    players[playerId]?.let { player ->
                        player.currentHealth = currentHealth
                        player.maxHealth = maxHealth
                    }
                }
            }

            "RANGED_ATTACK", "SPELL_ATTACK", "MELEE_ATTACK" -> {
                playerHandler.handleMessage(parts[0], parts)
            }

            else -> {
                Gdx.app.log("WebSocket", "Odebrano: $message")
            }
        }
    }

    override fun render() {
        when (currentState) {
            GameState.LOGIN -> {
                loginScreen?.render(Gdx.graphics.deltaTime)
            }

            GameState.CHARACTER_SELECTION -> {
                characterSelectionScreen?.render(Gdx.graphics.deltaTime)
            }

            GameState.CHARACTER_CREATION -> {
                characterCreationScreen?.render(Gdx.graphics.deltaTime)
            }

            GameState.PLAYING -> {
                renderGame()
                // Sprawdź, czy gracz nie zginął
                checkPlayerDeath()
            }

            GameState.DEAD -> {
                deathTimer += Gdx.graphics.deltaTime
                deathScreen?.render()
            }
        }
    }

    private fun renderGame() {
        // Obsługa wejścia z modułu input handler
        val chatHandled = chatSystem.handleInput()
        if (!chatHandled) {
            playerHandler.handleInput()
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && lastHelpToggleTime > helpToggleCooldown) {
            showHelp = !showHelp
            lastHelpToggleTime = 0f
        }

        lastHelpToggleTime += Gdx.graphics.deltaTime

        // Aktualizacja systemu czatu
        chatSystem.update(Gdx.graphics.deltaTime)

        // Aktualizacja umiejętności i efektów klas
        playerHandler.update(Gdx.graphics.deltaTime)

        // Czyszczenie ekranu
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Aktualizacja kamery
        camera.update()

        // Aktualizacja pozycji graczy - płynny ruch
        val delta = Gdx.graphics.deltaTime
        players.values.forEach { player ->
            if (player.id == localPlayerId) {
                // Dla lokalnego gracza używamy predykcji po stronie klienta
                player.updateLocalPosition(delta)
            } else {
                // Dla innych graczy używamy interpolacji
                player.updateRemotePosition(delta)
            }
        }

        // Rysowanie graczy
        batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined

        // Rysowanie graczy z kolorami odpowiadającymi klasie
        try {
            // Rysuj wszystkie wypełnione postacie
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            players.values.forEach { player ->
                shapeRenderer.color = player.getClassColor()
                shapeRenderer.circle(player.x, player.y, 15f)
            }
            shapeRenderer.end()

            // Rysuj obramowania zaznaczonych graczy
            val selectedPlayers = players.values.filter { it.isSelected }
            if (selectedPlayers.isNotEmpty()) {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                selectedPlayers.forEach { player ->
                    shapeRenderer.color = Color.YELLOW
                    shapeRenderer.circle(player.x, player.y, 18f) // Nieco większy promień dla obramowania
                }
                shapeRenderer.end()
            }

            // Renderowanie pasków życia
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            players.values.forEach { player ->
                // Rysuj tło paska życia (szary)
                shapeRenderer.color = Color.DARK_GRAY
                shapeRenderer.rect(player.x - 30f, player.y + 35f, 60f, 8f)

                // Rysuj aktualny stan paska życia (zielony lub czerwony, zależnie od ilości HP)
                val healthRatio = player.currentHealth.toFloat() / player.maxHealth.toFloat()
                if (healthRatio > 0.5f) {
                    shapeRenderer.color = Color.GREEN
                } else if (healthRatio > 0.25f) {
                    shapeRenderer.color = Color.ORANGE
                } else {
                    shapeRenderer.color = Color.RED
                }
                shapeRenderer.rect(player.x - 30f, player.y + 35f, 60f * healthRatio, 8f)
            }
            shapeRenderer.end()

            // Renderowanie strzał i efektów klas
            playerHandler.render(shapeRenderer, batch, font)
        } catch (e: Exception) {
            // Upewnij się, że shape renderer jest zakończony nawet w przypadku wyjątku
            if (shapeRenderer.isDrawing) {
                shapeRenderer.end()
            }
            Gdx.app.error("RenderGame", "Error rendering: ${e.message}")
        }

        // Rysowanie UI (tekst)
        batch.begin()
        try {
            // Wyświetlenie nazw graczy i klas nad postaciami
            players.values.forEach { player ->
                // Nazwa gracza i klasa
                val playerText = player.username
                layout.setText(font, playerText)
                font.color = Color.WHITE
                font.draw(batch, layout, player.x - layout.width / 2, player.y + 60)
            }

            // Informacje o grze
            font.draw(batch, "Gracze online: ${players.size}", 10f, Gdx.graphics.height - 10f)
            font.draw(batch, "Zalogowany jako: $username (${localPlayer.getClassName()})", 10f, Gdx.graphics.height - 30f)

            // Dodaj informację o zaznaczonym graczu
            val selectedPlayer = players.values.find { it.isSelected }
            if (selectedPlayer != null) {
                font.draw(batch, "Zaznaczony gracz: ${selectedPlayer.username}", 10f, Gdx.graphics.height - 50f)
            }

            // Renderowanie czatu
            chatSystem.render(batch, font)

            if (showHelp) {
                keyboardHelper.renderHelpText(batch, font)
            }

        } catch (e: Exception) {
            Gdx.app.error("RenderGame", "Error rendering text: ${e.message}")
        } finally {
            batch.end()
        }


    }

    // Dodaj metodę wykrywania śmierci, którą wywołasz w renderGame()
    private fun checkPlayerDeath() {
        if (currentState == GameState.PLAYING && localPlayer.currentHealth <= 0) {
            // Zmień stan gry na DEAD
            currentState = GameState.DEAD
            deathTimer = 0f
            deathScreen?.show()
        }
    }

    // Dodaj metodę do odradzania gracza
    fun respawnPlayer() {
        Gdx.app.log("Respawn", "Respawn requested, timer: $deathTimer, min time: $minDeathTime")

        // Sprawdź, czy minął minimalny czas w stanie śmierci
        if (deathTimer >= minDeathTime) {
            Gdx.app.log("Respawn", "Sending respawn message")
            // Wyślij wiadomość o odrodzeniu
            networkScope.launch {
                try {
                    session?.send("RESPAWN|${localPlayer.id}")
                    // Zmień stan gry z powrotem na PLAYING
                    Gdx.app.postRunnable {
                        Gdx.app.log("Respawn", "Switching back to PLAYING state")
                        currentState = GameState.PLAYING
                    }
                } catch (e: Exception) {
                    Gdx.app.error("Respawn", "Error sending respawn message: ${e.message}")
                }
            }
        } else {
            Gdx.app.log("Respawn", "Respawn blocked, death timer not elapsed yet")
        }
    }

    override fun resize(width: Int, height: Int) {
        when (currentState) {
            GameState.LOGIN -> loginScreen?.resize(width, height)
            GameState.CHARACTER_SELECTION -> characterSelectionScreen?.resize(width, height)
            GameState.CHARACTER_CREATION -> characterCreationScreen?.resize(width, height)
            GameState.DEAD -> deathScreen?.resize(width, height)
            GameState.PLAYING -> {
                camera.viewportWidth = width.toFloat()
                camera.viewportHeight = height.toFloat()
                camera.update()
            }
        }
    }

    override fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        loginScreen?.dispose()
        characterSelectionScreen?.dispose()
        characterCreationScreen?.dispose()
        deathScreen?.dispose()

        // Zamknięcie połączenia websocket
        networkScope.launch {
            session?.close()
            client?.close()
        }

        // Anulowanie wszystkich koroutyn
        networkScope.cancel()
    }

    // Punkt wejścia dla aplikacji desktopowej
    object Launcher {
        @JvmStatic
        fun main(args: Array<String>) {
            val config = Lwjgl3ApplicationConfiguration()
            config.setTitle("MMO Game")
            config.setWindowedMode(800, 600)
            // config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
            config.setForegroundFPS(60)
            Lwjgl3Application(MMOGame(), config)
        }
    }
}