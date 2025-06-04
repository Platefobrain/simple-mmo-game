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

package pl.decodesoft

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
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
import pl.decodesoft.enemy.EnemyClient
import pl.decodesoft.klasy.Archer
import pl.decodesoft.klasy.Mage
import pl.decodesoft.klasy.Warrior
import pl.decodesoft.klasy.skile.SkileManager
import pl.decodesoft.map.GameMap
import pl.decodesoft.msg.ChatSystem
import pl.decodesoft.network.MessageManager
import pl.decodesoft.player.Player
import pl.decodesoft.player.PlayerController
import pl.decodesoft.screens.CharacterCreationScreen
import pl.decodesoft.screens.CharacterSelectionScreen
import pl.decodesoft.screens.DeathScreen
import pl.decodesoft.screens.LoginScreen
import pl.decodesoft.settings.KeyboardHelper
import pl.decodesoft.states.*
import java.util.concurrent.ConcurrentHashMap

// Główny kod gry
class MMOGame : ApplicationAdapter() {
    private lateinit var currentState: GameState
    lateinit var batch: SpriteBatch
    lateinit var uiBatch: SpriteBatch
    lateinit var shapeRenderer: ShapeRenderer
    lateinit var font: BitmapFont
    lateinit var camera: OrthographicCamera
    lateinit var gameMap: GameMap
    lateinit var chatSystem: ChatSystem
    lateinit var keyboardHelper: KeyboardHelper
    private lateinit var messageManager: MessageManager

    private var deathScreen: DeathScreen? = null

    // Zmienna do kontrolowania widoczności pomocy
    var showHelp = false
    var lastHelpToggleTime = 0f
    val helpToggleCooldown = 0.3f

    // Dane gracza
    var localPlayerId = "player_${System.currentTimeMillis()}"
    var username = "Guest"
    lateinit var localPlayer: Player
    val layout = GlyphLayout()

    // Mapa wszystkich graczy
    val players = ConcurrentHashMap<String, Player>()
    val pathTiles = mutableListOf<Pair<Int, Int>>()

    // Komunikacja
    lateinit var networkScope: CoroutineScope
    private var client: HttpClient? = null
    var session: DefaultWebSocketSession? = null

    // Obsługa gracza i umiejętności
    lateinit var playerController: PlayerController

    // wrogowie enemymanager
    val enemies = ConcurrentHashMap<String, EnemyClient>()
    var enemyUpdateTimer = 0f
    val enemyUpdateInterval = 1f

    private var loginScreen: LoginScreen? = null
    private var characterSelectionScreen: CharacterSelectionScreen? = null
    private var characterCreationScreen: CharacterCreationScreen? = null

    override fun create() {
        deathScreen = DeathScreen(this)
        batch = SpriteBatch()
        uiBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/OpenSans-Regular.ttf"))
        val parameter = FreeTypeFontParameter().apply {
            size = 15
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
            color = Color.WHITE

            borderWidth = 0.5f
            borderColor = Color.WHITE
        }
        font = generator.generateFont(parameter)
        font.data.markupEnabled = true
        generator.dispose()

        camera = OrthographicCamera()
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        val csv = Gdx.files.internal("assets/maps/map.csv").readString("UTF-8")
        gameMap = GameMap(120, 120, 16)
        gameMap.loadFromCsv(csv)

        // Inicjalizacja zakresu coroutine dla komunikacji sieciowej
        networkScope = CoroutineScope(Dispatchers.IO)

        // Inicjalizacja ekranu logowania
        loginScreen = LoginScreen(this)
        loginScreen?.show()

        // wiadomosci graczy
        messageManager = MessageManager(this)

        // Ustaw początkowy stan gry
        changeState(LoginState(this))
    }

    // metoda zmian stanu
    fun changeState(newState: GameState) {
        // Jeśli już istnieje stan, wywołaj wyjście
        if (::currentState.isInitialized) {
            currentState.exit()
        }

        // Ustawienie nowego stanu
        currentState = newState

        // Wywołanie wejścia do nowego stanu
        currentState.enter()
    }

    // Pokazuje ekran wyboru postaci
    fun showCharacterSelectionScreen() {
        changeState(CharacterSelectionState(this))
    }

    // Pokazuje ekran tworzenia postaci
    fun showCharacterCreationScreen(slotIndex: Int) {
        changeState(CharacterCreationState(this, slotIndex))
    }

    // Przełącza na ekran logowania
    fun switchToLoginScreen() {
        changeState(LoginState(this))
    }

    // Chat
    fun receiveNetworkChatMessage(senderId: String, senderName: String, content: String) {
        chatSystem.receiveMessage(senderId, senderName, content)
    }

    // Aktualizuje istniejącego przeciwnika lub tworzy nowego
    fun updateEnemy(id: String, x: Float, y: Float, type: String, hp: Int, maxHp: Int, level: Int = 1, state: String = "IDLE"): EnemyClient {

        return enemies[id]?.let { existingEnemy ->
            existingEnemy.updateTargetPosition(x, y)
            existingEnemy.currentHealth = hp
            existingEnemy.maxHealth = maxHp
            existingEnemy.level = level
            existingEnemy.updateState(state)
            existingEnemy.isAlive = hp > 0
            existingEnemy

        } ?: run {
            val newEnemy = EnemyClient(id, x, y, type, hp, maxHp, level, state)
            newEnemy.isAlive = hp > 0
            enemies[id] = newEnemy
            newEnemy
        }
    }

    // Teleportuje przeciwnika do nowej pozycji (dla respawnu)
    fun respawnEnemy(id: String, x: Float, y: Float, type: String, hp: Int, maxHp: Int, level: Int = 1, state: String): EnemyClient {
        return enemies[id]?.let { enemy ->
            enemy.teleportToPosition(x, y)
            enemy.currentHealth = hp
            enemy.maxHealth = maxHp
            enemy.level = level
            enemy.updateState(state)
            enemy.isAlive = true
            enemy
        } ?: run {
            val newEnemy = EnemyClient(id, x, y, type, hp, maxHp, level, state)
            newEnemy.isAlive = true
            enemies[id] = newEnemy
            newEnemy
        }
    }

    // Aktualizuje zdrowie przeciwnika
    fun updateEnemyHealth(id: String, damage: Int): Boolean {
        return enemies[id]?.let { enemy ->
            enemy.currentHealth -= damage
            if (enemy.currentHealth <= 0) {
                enemy.currentHealth = 0
                enemy.isAlive = false
            }
            true
        } ?: false
    }

    // Oznacza przeciwnika jako martwego
    fun markEnemyAsDead(id: String): Boolean {
        return enemies[id]?.let { enemy ->
            enemy.isAlive = false
            enemy.isSelected = false
            true
        } ?: false
    }

    // Dodaje nowego gracza do gry
    fun addPlayer(id: String, x: Float, y: Float, username: String, characterClass: Int, currentHealth: Int, maxHealth: Int, level: Int = 1, experience: Int = 0) {
        if (id != localPlayerId) {
            val newPlayer = Player(x, y, id, Color.BLUE, username, characterClass, level = level, experience = experience)
            newPlayer.currentHealth = currentHealth
            newPlayer.maxHealth = maxHealth
            players[id] = newPlayer
        }
    }

    // Aktualizuje pozycję gracza
    fun updatePlayerPosition(id: String, x: Float, y: Float) {
        players[id]?.let { player ->
            if (id == localPlayerId) {
                // Korekta serwera dla lokalnego gracza
                player.setServerPosition(x, y)
            } else {
                // Ustawienie celu ruchu dla innych graczy
                player.setMoveTarget(x, y)
            }
        }
    }

    //Obsługuje nieudany ruch gracza
    fun handleMoveFailed(playerId: String, reason: String) {
        if (playerId == localPlayerId) {
            // Anuluj ruch lokalnego gracza
            localPlayer.setMoveTarget(localPlayer.x, localPlayer.y)

            // Wyświetl informację dla gracza
            val messageText = when (reason) {
                "no_path" -> "Nie mogę tam dojść!"
                else -> "Ruch niemożliwy!"
            }
            playerController.addDamageText(localPlayer.x, localPlayer.y + 20f, messageText, Color.YELLOW)
        }
    }

    // Usuwa gracza z gry
    fun removePlayer(id: String) {
        players.remove(id)
    }

    // Aktualizuje zdrowie gracza
    fun updatePlayerHealth(playerId: String, currentHealth: Int, maxHealth: Int) {
        players[playerId]?.let { player ->
            player.currentHealth = currentHealth
            player.maxHealth = maxHealth
        }
    }

    // Bezpośrednio ustawia zdrowie przeciwnika (bez odejmowania)
    fun updateEnemyHealthExplicit(enemyId: String, currentHealth: Int, maxHealth: Int) {
        enemies[enemyId]?.let { enemy ->
            enemy.currentHealth = currentHealth
            enemy.maxHealth = maxHealth
            enemy.isAlive = currentHealth > 0
        }
    }

    // Pobiera gracza po ID
    fun getPlayer(playerId: String): Player? {
        return players[playerId]
    }

    // Pobiera przeciwnika po ID
    fun getEnemy(enemyId: String): EnemyClient? {
        return enemies[enemyId]
    }

    // Dodaje efekt tekstowy obrażeń
    fun addDamageText(x: Float, y: Float, text: String, color: Color) {
        playerController.addDamageText(x, y, text, color)
    }

    // Obsługuje śmierć gracza
    fun handlePlayerDeath(playerId: String) {
        if (playerId == localPlayerId) {
            Gdx.app.postRunnable {
                changeState(DeadState(this))
                deathScreen?.show()
            }
        }
    }

    // Obsługuje odrodzenie gracza
    fun respawnPlayer(playerId: String, currentHealth: Int, maxHealth: Int) {
        players[playerId]?.let { player ->
            player.currentHealth = currentHealth
            player.maxHealth = maxHealth
        }
    }

    // Obsługuje wiadomość o ataku
    fun handleAttackMessage(attackType: String, parts: List<String>) {
        playerController.handleMessage(attackType, parts)
    }

    // Aktualizuje dane ścieżki pathfinding
    fun updatePathTiles(pathData: List<Pair<Int, Int>>) {
        pathTiles.clear()
        pathTiles.addAll(pathData)
    }

    // Obsługuje wiadomości systemowe i wyświetla je w czacie
    fun receiveSystemMessage(message: String) {
        // "System" jako nadawca, pokazuje wiadomość w innym kolorze
        chatSystem.receiveMessage("system", "System", message)
    }

    fun sendWebSocketMessage(message: String) {
        networkScope.launch {
            try {
                val currentSession = session
                if (currentSession != null) {
                    currentSession.send(Frame.Text(message))
                } else {
                    Gdx.app.error("WebSocket", "Cannot send message - no active session")
                }
            } catch (e: Exception) {
                Gdx.app.error("WebSocket", "Error sending message: ${e.message}")
            }
        }
    }

    // Metoda wywoływana po pomyślnym wyborze postaci
    fun startGame(userUsername: String, userId: String, characterClass: Int = 2, nickname: String = userUsername, level: Int = 1, experience: Int = 0) {
        username = userUsername
        localPlayerId = userId

        // Tworzenie gracza z wybraną klasą postaci i nickiem
        localPlayer = Player(
            Gdx.graphics.width / 2f,
            Gdx.graphics.height / 2f,
            localPlayerId,
            Color.RED,
            nickname,
            characterClass,
            level = level,
            experience = experience
        )

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
            session = { session },
            enemies = enemies
        )

        // Inicjalizacja obsługi gracza i umiejętności
        playerController = PlayerController(
            localPlayer = localPlayer,
            players = players,
            enemies = enemies,
            camera = camera,
            networkScope = networkScope,
            getSession = { session },
            characterClass = when (localPlayer.characterClass) {
                0 -> Archer(localPlayer, networkScope, { session }, skileManager)
                1 -> Mage(localPlayer, networkScope, { session }, skileManager)
                else -> Warrior(localPlayer, networkScope, { session }, skileManager)
            },
            skileManager = skileManager  // Przekazujemy tę samą instancję
        )

        // Uruchomienie połączenia websocket
        connectToServer()

        // Zmiana stanu gry
        changeState(PlayingState(this))
    }

    private fun connectToServer() {
        networkScope.launch {
            try {
                client = HttpClient(CIO) {
                    install(WebSockets)
                }

                client?.webSocket("ws://localhost:8081/ws") {
                    session = this

                    // Rejestracja gracza z uwierzytelnieniem i klasą postaci
                    send(Frame.Text("JOIN|${localPlayer.x}|${localPlayer.y}|${localPlayer.id}|$username|${localPlayer.characterClass}"))
                    send(Frame.Text("GET_ENEMIES"))
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
        messageManager.processMessage(message)
    }

    override fun render() {
        // Aktualizacja aktualnego stanu
        currentState.update(Gdx.graphics.deltaTime)

        // Obsługa wejścia aktualnego stanu
        currentState.handleInput()

        // Renderowanie aktualnego stanu
        currentState.render(Gdx.graphics.deltaTime)
    }

    // podazanie za graczem
    fun updateCamera() {
        // Interpolacja liniowa dla płynnego śledzenia
        val lerpFactor = 0.5f // Wartość od 0 do 1, im bliżej 1, tym szybsze śledzenie
        val targetX = localPlayer.x
        val targetY = localPlayer.y

        camera.position.x += (targetX - camera.position.x) * lerpFactor
        camera.position.y += (targetY - camera.position.y) * lerpFactor
    }

    // Respawn graczy
    fun respawnPlayer() {
        Gdx.app.log("Respawn", "Respawn requested")

        // Pobierz aktualny stan gry
        val currentDeadState = currentState as? DeadState

        if (currentDeadState != null) {
            // Jeśli jesteśmy w stanie śmierci, użyj jego metody do odradzania
            currentDeadState.handleRespawn()
        } else {
            Gdx.app.error("Respawn", "Cannot respawn - not in death state")
        }
    }

    override fun resize(width: Int, height: Int) {
        // Przekierowanie do aktualnego stanu
        currentState.resize(width, height)
    }

    override fun dispose() {
        batch.dispose()
        uiBatch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        loginScreen?.dispose()
        characterSelectionScreen?.dispose()
        characterCreationScreen?.dispose()
        deathScreen?.dispose()
        gameMap.dispose()

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
            //config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
            config.setForegroundFPS(60)
            Lwjgl3Application(MMOGame(), config)
        }
    }
}