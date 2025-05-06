/*
 * Licencja: Wszelkie prawa zastrzeżone.
 * Możesz używać, modyfikować, kopiować i dystrybuować ten projekt do własnych celów,
 * ale nie możesz używać go do celów komercyjnych, chyba że uzyskasz zgodę od autora.
 * Projekt jest dostarczany "tak jak jest", bez żadnych gwarancji.
 * Używasz go na własne ryzyko.
 * Autor: Copyright [2025] [Platefobrain]
 */

package pl.decodesoft

import at.favre.lib.crypto.bcrypt.BCrypt
import com.badlogic.gdx.math.Vector2
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.decodesoft.msg.ChatManager
import pl.decodesoft.player.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.List
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.find
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.math.sqrt

// Modele danych do obsługi uwierzytelniania
@Serializable
data class AuthRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val success: Boolean, val message: String, val userId: String = "")

// Data class to track movement targets for server-side movement
data class MovementTarget(
    val targetX: Float,
    val targetY: Float,
    val moveToRange: Float = 0f,  // Used for character-specific movements
    var isMoving: Boolean = true
)

// Klasa do zarządzania użytkownikami
class UserManager {
    private val usersFile = File("users.json")
    private val users = ConcurrentHashMap<String, User>()
    private var lastSaveTime = System.currentTimeMillis()
    private val saveInterval = 10 // 1 godzina między zapisami
    private var pendingUpdates = false

    init {
        loadUsers()
    }

    private fun loadUsers() {
        if (usersFile.exists()) {
            try {
                val json = usersFile.readText()
                val userList = Json.decodeFromString<List<User>>(json)
                userList.forEach { user ->
                    users[user.username.lowercase()] = user
                }
                println("Loaded ${users.size} users from ${usersFile.absolutePath}")
            } catch (e: Exception) {
                println("Error loading users: ${e.message}")
            }
        }
    }

    fun saveUsers() {
        try {
            val json = Json.encodeToString(users.values.toList())
            usersFile.writeText(json)
            println("Saved ${users.size} users to ${usersFile.absolutePath}")
        } catch (e: Exception) {
            println("Error saving users: ${e.message}")
        }
    }


    // Inicjalizacja zdrowia w zależności od klasy postaci
    private fun initializeHealthForUser(user: User) {
        user.maxHealth = when (user.characterClass) {
            CharacterClass.WARRIOR -> 150
            CharacterClass.ARCHER -> 100
            CharacterClass.MAGE -> 80
        }

        // Przywracamy zdrowie do maksymalnego
        user.currentHealth = user.maxHealth

        updateUser(user)
    }

    fun registerUser(username: String, password: String): Result<User> {
        if (username.length < 3) {
            return Result.failure(Exception("Nazwa użytkownika musi mieć co najmniej 3 znaki"))
        }

        if (password.length < 6) {
            return Result.failure(Exception("Hasło musi mieć co najmniej 6 znaków"))
        }

        val normalizedUsername = username.lowercase()

        if (users.containsKey(normalizedUsername)) {
            return Result.failure(Exception("Nazwa użytkownika już istnieje"))
        }

        // Hash hasła za pomocą bcrypt
        val passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        // Tworzenie nowego użytkownika z unikalnym ID
        val userId = UUID.randomUUID().toString()
        val user = User(userId, username, passwordHash)

        // Inicjalizacja zdrowia w zależności od domyślnej klasy
        initializeHealthForUser(user)

        users[normalizedUsername] = user
        saveUsers()

        return Result.success(user)
    }

    fun authenticateUser(username: String, password: String): Result<User> {
        val normalizedUsername = username.lowercase()
        val user = users[normalizedUsername] ?: return Result.failure(Exception("Nie znaleziono użytkownika"))

        // Weryfikacja hasła
        val result = BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash)

        return if (result.verified) {
            Result.success(user)
        } else {
            Result.failure(Exception("Nieprawidłowe hasło"))
        }
    }

    fun getUserById(id: String): User? {
        return users.values.find { it.id == id }
    }

    fun updateUser(updatedUser: User) {
        val normalizedUsername = updatedUser.username.lowercase()
        if (users.containsKey(normalizedUsername)) {
            users[normalizedUsername] = updatedUser
            pendingUpdates = true

            // Zapisuj tylko co jakiś czas, nie po każdej aktualizacji
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSaveTime > saveInterval) {
                saveUsers()
                lastSaveTime = currentTime
                pendingUpdates = false
            }
        }
    }

    // Sprawdza, czy nazwa postaci jest już zajęta
    private fun isNicknameTaken(nickname: String): Boolean {
        return users.values.any { user ->
            user.characters.any { character ->
                character.nickname.equals(nickname, ignoreCase = true)
            }
        }
    }

    // Tworzy nową postać dla użytkownika
    fun createCharacter(userId: String, nickname: String, characterClassOrdinal: Int): Result<CharacterInfo> {
        val user = getUserById(userId) ?: return Result.failure(Exception("Nie znaleziono użytkownika"))

        if (user.characters.size >= 3) {
            return Result.failure(Exception("Osiągnięto maksymalną liczbę postaci (3)"))
        }

        if (isNicknameTaken(nickname)) {
            return Result.failure(Exception("Nazwa postaci jest już zajęta"))
        }

        // Określ klasę postaci na podstawie wartości liczbowej
        val characterClass = CharacterClass.entries.toTypedArray()
            .getOrElse(characterClassOrdinal) { CharacterClass.WARRIOR }

        // Ustal maksymalne zdrowie dla klasy
        val maxHealth = when (characterClass) {
            CharacterClass.WARRIOR -> 150
            CharacterClass.ARCHER -> 100
            CharacterClass.MAGE -> 80
        }

        // Tworzenie nowej postaci
        val characterId = UUID.randomUUID().toString()
        val newCharacter = CharacterInfo(
            id = characterId,
            nickname = nickname,
            characterClass = characterClassOrdinal,
            maxHealth = maxHealth,
            currentHealth = maxHealth
        )

        // Dodaj postać do konta użytkownika
        user.characters.add(newCharacter)
        updateUser(user)

        return Result.success(newCharacter)
    }

    // Pobiera informacje o aktywnej postaci użytkownika na podstawie indeksu slotu
    fun getCharacterBySlot(userId: String, slotIndex: Int): Result<CharacterInfo> {
        val user = getUserById(userId) ?: return Result.failure(Exception("Nie znaleziono użytkownika"))

        if (slotIndex < 0 || slotIndex >= user.characters.size) {
            return Result.failure(Exception("Nieprawidłowy slot postaci"))
        }

        return Result.success(user.characters[slotIndex])
    }

    // Usuwa postać z konta użytkownika
    fun deleteCharacter(userId: String, characterId: String): Result<Boolean> {
        val user = getUserById(userId) ?: return Result.failure(Exception("Nie znaleziono użytkownika"))

        val characterToRemove = user.characters.find { it.id == characterId }
            ?: return Result.failure(Exception("Nie znaleziono postaci"))

        user.characters.remove(characterToRemove)
        updateUser(user)

        return Result.success(true)
    }
}

// Wysyła wiadomość do wszystkich oprócz nadawcy
suspend fun broadcastToOthers(connections: ConcurrentHashMap<String, DefaultWebSocketSession>,
                              senderSessionId: String, message: String) {
    connections.forEach { (sessionId, session) ->
        if (sessionId != senderSessionId) {
            session.send(message)
        }
    }
}

// Wysyła wiadomość do wszystkich
suspend fun broadcastToAll(connections: ConcurrentHashMap<String, DefaultWebSocketSession>, message: String) {
    connections.forEach { (_, session) ->
        session.send(message)
    }
}

// Add this function to send a message to specific players only
suspend fun sendToSpecificPlayers(
    connections: ConcurrentHashMap<String, DefaultWebSocketSession>,
    playerIds: List<String>,
    message: String
) {
    playerIds.forEach { playerId ->
        // Find all connections associated with this player
        connections.forEach { (_, session) ->
            // For each connection, check if it belongs to any of the target players
            session.send(message)
        }
    }
}

// Funkcja obliczająca odległość między punktem (x, y) a linią przechodzącą przez (x1,y1) i (x2,y2)
fun calculateDistanceFromPointToLine(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val a = x - x1
    val b = y - y1
    val c = x2 - x1
    val d = y2 - y1

    val dot = a * c + b * d
    val lenSq = c * c + d * d
    val param = if (lenSq != 0f) dot / lenSq else -1f

    val xx: Float
    val yy: Float

    if (param < 0f) {
        xx = x1
        yy = y1
    } else if (param > 1f) {
        xx = x2
        yy = y2
    } else {
        xx = x1 + param * c
        yy = y1 + param * d
    }

    val dx = x - xx
    val dy = y - yy
    return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

// Modyfikacja głównej funkcji serwera
fun main() {
    // Mapa przechowująca aktywne sesje graczy
    val connections = ConcurrentHashMap<String, DefaultWebSocketSession>()

    // Mapa przechowująca dane o pozycjach graczy
    val playerPositions = ConcurrentHashMap<String, PlayerData>()

    // Map to store movement targets for server-side movement
    val playerMovementTargets = ConcurrentHashMap<String, MovementTarget>()

    // Variables for the game update loop
    var lastUpdateTime = System.currentTimeMillis()
    val updateInterval = 50L // 20 updates per second

    // Manager użytkowników
    val userManager = UserManager()

    // Manager pisania wiadomości
    val chatManager = ChatManager()

    // Create a coroutine scope for network operations and game loop
    val networkScope = CoroutineScope(Dispatchers.IO)

    // Function to update all player positions on the server
    suspend fun updatePlayerPositions() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastUpdateTime) / 1000f
        lastUpdateTime = currentTime

        // Speed is controlled by the server
        val speed = 200f // Units per second

        // Process movements for all players with targets
        playerMovementTargets.forEach { (playerId, target) ->
            if (!target.isMoving) return@forEach

            playerPositions[playerId]?.let { player ->
                // Calculate direction to target
                val distX = target.targetX - player.x
                val distY = target.targetY - player.y
                val distance = sqrt((distX * distX + distY * distY).toDouble()).toFloat()

                // If target has a range (character-specific targeting), check if we're close enough
                if (target.moveToRange > 0f && distance <= target.moveToRange) {
                    target.isMoving = false
                    return@forEach
                }

                // If close to target, stop moving
                if (distance < 5f) {
                    target.isMoving = false
                    return@forEach
                }

                // Calculate normalized direction
                val dirX = distX / distance
                val dirY = distY / distance

                // Calculate movement distance
                val moveDistance = speed * deltaTime

                // Calculate new position
                val newX = player.x + dirX * moveDistance
                val newY = player.y + dirY * moveDistance

                // Optional: Limit movement area
                val clampedX = newX.coerceIn(0f, 800f)  // Adjust bounds as needed
                val clampedY = newY.coerceIn(0f, 600f)  // Adjust bounds as needed

                // Update player position
                player.x = clampedX
                player.y = clampedY

                // Broadcast the new position to all clients
                val moveMessage = "MOVE|$clampedX|$clampedY|$playerId"
                broadcastToAll(connections, moveMessage)
            }
        }

        // Remove completed movement targets
        playerMovementTargets.entries.removeIf { !it.value.isMoving }
    }

    // Start the game loop to continuously update player positions
    fun startGameLoop() {
        networkScope.launch {
            while (true) {
                updatePlayerPositions()
                delay(updateInterval)
            }
        }
    }

    // połączenie
    embeddedServer(Netty, port = 8080) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        routing {
            // endpojnty uwierzytelniania
            route("/auth") {
                post("/register") {
                    val request = call.receive<AuthRequest>()
                    val result = userManager.registerUser(request.username, request.password)

                    if (result.isSuccess) {
                        val user = result.getOrNull()!!
                        call.respond(AuthResponse(true, "Rejestracja przebiegła pomyślnie", user.id))
                    } else {
                        call.respond(AuthResponse(false, result.exceptionOrNull()?.message ?: "Unknown error"))
                    }
                }

                post("/login") {
                    val request = call.receive<AuthRequest>()
                    val result = userManager.authenticateUser(request.username, request.password)

                    if (result.isSuccess) {
                        val user = result.getOrNull()!!
                        call.respond(AuthResponse(true, "Logowanie powiodło się", user.id))
                    } else {
                        call.respond(AuthResponse(false, result.exceptionOrNull()?.message ?: "Unknown error"))
                    }
                }
            }

            // Nowy endpoint do wyboru klasy postaci
            route("/character") {
                // Endpoint do pobierania listy postaci
                post("/list") {
                    val request = call.receive<CharactersListRequest>()
                    val user = userManager.getUserById(request.userId)

                    if (user != null) {
                        call.respond(CharactersListResponse(
                            success = true,
                            message = "Lista postaci pobrana pomyślnie",
                            characters = user.characters
                        ))
                    } else {
                        call.respond(CharactersListResponse(
                            success = false,
                            message = "Nie znaleziono użytkownika"
                        ))
                    }
                }

                // Endpoint do tworzenia nowej postaci
                post("/create") {
                    val request = call.receive<CharacterCreateRequest>()
                    val result = userManager.createCharacter(
                        request.userId,
                        request.nickname,
                        request.characterClass
                    )

                    if (result.isSuccess) {
                        call.respond(CharacterCreateResponse(
                            success = true,
                            message = "Postać została stworzona pomyślnie"
                        ))
                    } else {
                        call.respond(CharacterCreateResponse(
                            success = false,
                            message = result.exceptionOrNull()?.message ?: "Nieznany błąd"
                        ))
                    }
                }

                // Endpoint do wyboru postaci do gry
                post("/select") {
                    val request = call.receive<CharacterSelectRequest>()
                    val result = userManager.getCharacterBySlot(request.userId, request.characterSlot)

                    if (result.isSuccess) {
                        val character = result.getOrNull()!!

                        // Aktualizuj główne dane użytkownika na podstawie wybranej postaci
                        val user = userManager.getUserById(request.userId)!!
                        user.characterClass = CharacterClass.entries.toTypedArray()
                            .getOrElse(character.characterClass) { CharacterClass.WARRIOR }
                        user.nickname = character.nickname
                        user.maxHealth = character.maxHealth
                        user.currentHealth = character.currentHealth

                        userManager.updateUser(user)

                        call.respond(CharacterSelectResponse(
                            success = true,
                            message = "Postać wybrana pomyślnie"
                        ))
                    } else {
                        call.respond(CharacterSelectResponse(
                            success = false,
                            message = result.exceptionOrNull()?.message ?: "Nieznany błąd"
                        ))
                    }
                }

                // Endpoint do usuwania postaci (opcjonalny)
                post("/delete") {
                    val request = call.receive<Map<String, String>>()
                    val userId = request["userId"] ?: return@post call.respond(mapOf("success" to false, "message" to "Brak ID użytkownika"))
                    val characterId = request["characterId"] ?: return@post call.respond(mapOf("success" to false, "message" to "Brak ID postaci"))

                    val result = userManager.deleteCharacter(userId, characterId)

                    if (result.isSuccess) {
                        call.respond(mapOf("success" to true, "message" to "Postać została usunięta pomyślnie"))
                    } else {
                        call.respond(mapOf("success" to false, "message" to (result.exceptionOrNull()?.message ?: "Nieznany błąd")))
                    }
                }
            }

            // websocket dla komunikacji w grze
            webSocket("/ws") {
                val sessionId = call.request.local.remoteHost + "_" + System.currentTimeMillis()
                var playerId = "" // ID gracza zostanie ustawione po otrzymaniu JOIN
                var username = "" // Nazwa użytkownika

                try {
                    // Dodajemy nowe połączenie
                    connections[sessionId] = this
                    println("Nowe połączenie: $sessionId (Aktywni gracze: ${connections.size})")

                    send("Witaj graczu!")

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val message = frame.readText()
                            println("Odebrano: $message")

                            // Analiza wiadomości
                            val parts = message.split("|")
                            when (parts[0]) {
                                "JOIN" -> {
                                    if (parts.size >= 4) {
                                        val x = parts[1].toFloat()
                                        val y = parts[2].toFloat()
                                        playerId = parts[3]
                                        username = if (parts.size >= 5) parts[4] else "Unknown"
                                        val characterClassOrdinal =
                                            if (parts.size >= 6) parts[5].toIntOrNull() ?: 0 else 0
                                        val characterClass = CharacterClass.entries.toTypedArray()
                                            .getOrElse(characterClassOrdinal) { CharacterClass.WARRIOR }

                                        // Sprawdzamy, czy użytkownik istnieje w bazie
                                        val user = userManager.getUserById(playerId)
                                        if (user != null) {
                                            // Użytkownik jest zautoryzowany
                                            // Używamy nicku z bazy danych
                                            val playerNickname = user.nickname

                                            // Używamy zapisanej klasy postaci
                                            val savedCharacterClass = user.characterClass

                                            // Zapisujemy pozycję nowego gracza z klasą postaci, zdrowiem i nickiem
                                            playerPositions[playerId] = PlayerData(
                                                x, y, playerId, playerNickname, savedCharacterClass,
                                                user.maxHealth, user.currentHealth
                                            )

                                            // Powiadom wszystkich o nowym graczu
                                            broadcastToOthers(
                                                connections, sessionId,
                                                "JOIN|$x|$y|$playerId|$playerNickname|${savedCharacterClass.ordinal}|${user.currentHealth}|${user.maxHealth}"
                                            )
                                        } else {
                                            // Nieznany użytkownik - używamy domyślnych wartości
                                            // Ustalamy maksymalne zdrowie na podstawie klasy postaci
                                            val maxHealth = when (characterClass) {
                                                CharacterClass.WARRIOR -> 150
                                                CharacterClass.ARCHER -> 100
                                                CharacterClass.MAGE -> 80
                                            }

                                            playerPositions[playerId] = PlayerData(
                                                x, y, playerId, username, characterClass,
                                                maxHealth, maxHealth
                                            )

                                            broadcastToOthers(
                                                connections, sessionId,
                                                "JOIN|$x|$y|$playerId|$username|${characterClass.ordinal}|$maxHealth|$maxHealth"
                                            )
                                        }

                                        // Wyślij informacje o wszystkich istniejących graczach do nowego gracza
                                        playerPositions.forEach { (id, player) ->
                                            if (id != playerId) {
                                                send("JOIN|${player.x}|${player.y}|${player.id}|${player.username}|${player.characterClass.ordinal}|${player.currentHealth}|${player.maxHealth}")
                                            }
                                        }
                                    }
                                }

                                // New handler for MOVE_TO messages - client sends movement intention, server calculates
                                "MOVE_TO" -> {
                                    if (parts.size >= 5) {
                                        val targetX = parts[1].toFloat()
                                        val targetY = parts[2].toFloat()
                                        val moveToRange = parts[3].toFloat()
                                        val playerId = parts[4]

                                        // Set the movement target for this player
                                        playerMovementTargets[playerId] = MovementTarget(targetX, targetY, moveToRange)
                                    }
                                }

                                // łucznik
                                "RANGED_ATTACK" -> {
                                    if (parts.size >= 6) {
                                        val startX = parts[1].toFloat()
                                        val startY = parts[2].toFloat()
                                        val dirX = parts[3].toFloat()
                                        val dirY = parts[4].toFloat()
                                        val shooterId = parts[5]

                                        // Rozgłoś informację o strzale do wszystkich graczy (oprócz strzelca)
                                        broadcastToOthers(connections, sessionId, message)

                                        // Sprawdź kolizje z innymi graczami
                                        playerPositions.forEach { (targetId, targetPlayer) ->
                                            if (targetId != shooterId) {
                                                val distance = calculateDistanceFromPointToLine(
                                                    targetPlayer.x, targetPlayer.y,
                                                    startX, startY,
                                                    startX + dirX * 200, startY + dirY * 200  // Większy zasięg dla strzały
                                                )

                                                // Mniejszy promień kolizji dla strzały w porównaniu do kuli ognia
                                                if (distance < 15f) {

                                                    // Upewnij się, że zdrowie nie spadnie poniżej zera
                                                    if (targetPlayer.currentHealth < 0) targetPlayer.currentHealth = 0

                                                    // Aktualizuj stan gracza w bazie danych
                                                    userManager.getUserById(targetId)?.let { user ->
                                                        user.currentHealth = targetPlayer.currentHealth
                                                        userManager.updateUser(user)
                                                    }

                                                    // Wyślij informację o trafieniu do wszystkich graczy
                                                    val hitMessage = "HIT|$targetId|$shooterId|ARROW|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
                                                    broadcastToAll(connections, hitMessage)

                                                    println("Gracz $targetId został trafiony strzałą od $shooterId, pozostałe zdrowie: ${targetPlayer.currentHealth}/${targetPlayer.maxHealth}")
                                                }
                                            }
                                        }
                                    }
                                }

                                // mage
                                "SPELL_ATTACK" -> {
                                    if (parts.size >= 6) {
                                        val startX = parts[1].toFloat()
                                        val startY = parts[2].toFloat()
                                        val dirX = parts[3].toFloat()
                                        val dirY = parts[4].toFloat()
                                        val casterId = parts[5]

                                        // Rozgłoś informację o kuli ognia do wszystkich graczy (oprócz rzucającego)
                                        broadcastToOthers(connections, sessionId, message)

                                        // Sprawdź kolizje z innymi graczami
                                        playerPositions.forEach { (targetId, targetPlayer) ->
                                            if (targetId != casterId) {
                                                val distance = calculateDistanceFromPointToLine(
                                                    targetPlayer.x, targetPlayer.y,
                                                    startX, startY,
                                                    startX + dirX * 100, startY + dirY * 100
                                                )

                                                if (distance < 30f) { // Większy promień dla kuli ognia

                                                    // Upewnij się, że zdrowie nie spadnie poniżej zera
                                                    if (targetPlayer.currentHealth < 0) targetPlayer.currentHealth = 0

                                                    // Aktualizuj stan gracza w bazie danych
                                                    userManager.getUserById(targetId)?.let { user ->
                                                        user.currentHealth = targetPlayer.currentHealth
                                                        userManager.updateUser(user)
                                                    }

                                                    // Wyślij informację o trafieniu do wszystkich graczy
                                                    val hitMessage =
                                                        "HIT|$targetId|$casterId|FIREBALL|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
                                                    broadcastToAll(connections, hitMessage)

                                                    println("Gracz $targetId został trafiony kulą ognia od $casterId, pozostałe zdrowie: ${targetPlayer.currentHealth}/${targetPlayer.maxHealth}")
                                                }
                                            }
                                        }
                                    }
                                }

                                // warrior
                                "MELEE_ATTACK" -> {
                                    if (parts.size >= 6) {
                                        val startX = parts[1]
                                        val startY = parts[2]
                                        val attackerId = parts[5]

                                        // Rozgłoś informację o ataku mieczem do wszystkich graczy (oprócz atakującego)
                                        broadcastToOthers(connections, sessionId, message)

                                        // W przypadku ataku mieczem możemy sprawdzić kolizje na serwerze
                                        val attackerX = startX.toFloat()
                                        val attackerY = startY.toFloat()

                                        // Sprawdź, czy któryś gracz znajduje się w zasięgu ataku (blisko atakującego)
                                        playerPositions.forEach { (targetId, targetPlayer) ->
                                            if (targetId != attackerId) {
                                                val distance = Vector2.dst(attackerX, attackerY, targetPlayer.x, targetPlayer.y)

                                                // Zasięg ataku mieczem jest krótki (około 40 jednostek)
                                                if (distance < 40f) {

                                                    // Upewnij się, że zdrowie nie spadnie poniżej zera
                                                    if (targetPlayer.currentHealth < 0) targetPlayer.currentHealth = 0

                                                    // Aktualizuj stan gracza w bazie danych
                                                    userManager.getUserById(targetId)?.let { user ->
                                                        user.currentHealth = targetPlayer.currentHealth
                                                        userManager.updateUser(user)
                                                    }

                                                    // Wyślij informację o trafieniu do wszystkich graczy
                                                    val hitMessage = "HIT|$targetId|$attackerId|MELEE|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
                                                    broadcastToAll(connections, hitMessage)

                                                    println("Gracz $targetId został trafiony atakiem mieczem od $attackerId, pozostałe zdrowie: ${targetPlayer.currentHealth}/${targetPlayer.maxHealth}")
                                                }
                                            }
                                        }
                                    }
                                }

                                "HIT" -> {
                                    if (parts.size >= 3) {
                                        val targetId = parts[1]
                                        val attackerId = parts[2]
                                        val attackType = if (parts.size >= 4) parts[3] else "UNKNOWN"

                                        // Obliczamy obrażenia w zależności od typu ataku
                                        val damage = when (attackType) {
                                            "ARROW" -> 12
                                            "FIREBALL" -> 8
                                            "SWORD" -> 15
                                            else -> 5
                                        }

                                        // Aplikujemy obrażenia dla celu
                                        playerPositions[targetId]?.let { targetPlayer ->
                                            // Zadajemy obrażenia
                                            targetPlayer.takeDamage(damage)

                                            // Aktualizujemy zdrowie użytkownika, jeśli istnieje
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.currentHealth = targetPlayer.currentHealth
                                                userManager.updateUser(user)
                                            }

                                            // Wysyłamy dwa rodzaje wiadomości:

                                            // 1. Informacja o trafieniu ze szczegółami obrażeń - tylko dla atakującego i atakowanego
                                            val detailedHitMessage = "HIT_DETAILED|$targetId|$attackerId|$attackType|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}|$damage"
                                            sendToSpecificPlayers(connections, listOf(targetId, attackerId), detailedHitMessage)

                                            // 2. Podstawowa informacja o trafieniu (bez wartości obrażeń) - dla wszystkich pozostałych graczy
                                            val basicHitMessage = "HIT|$targetId|$attackerId|$attackType|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
                                            broadcastToAll(connections, basicHitMessage)

                                            println("Gracz $targetId został trafiony przez $attackerId atakiem: $attackType, pozostałe zdrowie: ${targetPlayer.currentHealth}/${targetPlayer.maxHealth}")

                                            // Respawn kod
                                            if (targetPlayer.currentHealth <= 0) {
                                                val deathMessage = "PLAYER_DIED|$targetId"
                                                broadcastToAll(connections, deathMessage)

                                                println("Gracz $targetId zginął.")
                                            }
                                        }
                                    }
                                }

                                "DAMAGE" -> {
                                    if (parts.size >= 3) {
                                        val targetId = parts[1]
                                        val damage = parts[2].toIntOrNull() ?: 0

                                        playerPositions[targetId]?.let { targetPlayer ->
                                            // Zadajemy obrażenia
                                            targetPlayer.takeDamage(damage)

                                            // Aktualizujemy zdrowie użytkownika, jeśli istnieje
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.currentHealth = targetPlayer.currentHealth
                                                userManager.updateUser(user)
                                            }

                                            // Wysyłamy aktualizację zdrowia do wszystkich
                                            val healthUpdateMessage =
                                                "HEALTH_UPDATE|$targetId|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
                                            broadcastToAll(connections, healthUpdateMessage)
                                        }
                                    }
                                }

                                "HEAL" -> {
                                    if (parts.size >= 3) {
                                        val targetId = parts[1]
                                        val healAmount = parts[2].toIntOrNull() ?: 0

                                        playerPositions[targetId]?.let { targetPlayer ->
                                            // Leczymy gracza
                                            targetPlayer.heal(healAmount)

                                            // Aktualizujemy zdrowie użytkownika, jeśli istnieje
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.currentHealth = targetPlayer.currentHealth
                                                userManager.updateUser(user)
                                            }

                                            // Wysyłamy aktualizację zdrowia do wszystkich
                                            val healthUpdateMessage =
                                                "HEALTH_UPDATE|$targetId|${targetPlayer.currentHealth}|${targetPlayer.maxHealth}"
                                            broadcastToAll(connections, healthUpdateMessage)
                                        }
                                    }
                                }

                                "HEALTH_UPDATE" -> {
                                    if (parts.size >= 3) {
                                        val targetId = parts[1]
                                        val health = parts[2].toIntOrNull() ?: 0

                                        playerPositions[targetId]?.let { targetPlayer ->
                                            // Ustawiamy nowe zdrowie
                                            targetPlayer.setHealth(health)

                                            // Aktualizujemy zdrowie użytkownika, jeśli istnieje
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.currentHealth = targetPlayer.currentHealth
                                                userManager.updateUser(user)
                                            }

                                            // Przekazujemy aktualizację wszystkim
                                            broadcastToAll(connections, message)
                                        }
                                    }
                                }

                                "RESPAWN" -> {
                                    if (parts.size >= 2) {
                                        val respawningPlayerId = parts[1]  // Zmiana nazwy zmiennej

                                        playerPositions[respawningPlayerId]?.let { player ->
                                            // Resetujemy zdrowie do maksymalnej wartości
                                            player.currentHealth = player.maxHealth

                                            // Aktualizujemy zdrowie użytkownika, jeśli istnieje
                                            userManager.getUserById(playerId)?.let { user ->
                                                user.currentHealth = player.maxHealth
                                                userManager.updateUser(user)
                                            }

                                            // Powiadamiamy wszystkich o odrodzeniu gracza
                                            val respawnMessage =
                                                "RESPAWN|$playerId|${player.currentHealth}|${player.maxHealth}"
                                            broadcastToAll(connections, respawnMessage)
                                        }
                                    }
                                }

                                "CHAT" -> {
                                    if (parts.size >= 4) {
                                        val senderId = parts[1]
                                        val senderName = parts[2]
                                        val content = parts.subList(3, parts.size).joinToString("|")

                                        // Zapisz i rozgłoś wiadomość
                                        val chatMessage = ChatManager.ChatMessage(senderId, senderName, content)
                                        chatManager.broadcastMessage(connections, chatMessage)
                                    }
                                }

                                else -> {
                                    // Echo dla nieznanych komend
                                    send("Echo: $message")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Błąd: ${e.localizedMessage}")
                } finally {
                    // Usuwamy rozłączonego gracza
                    connections.remove(sessionId)
                    if (playerId.isNotEmpty()) {
                        playerPositions.remove(playerId)
                        playerMovementTargets.remove(playerId) // Also remove from movement targets
                        println("Gracz $username ($playerId) rozłączony (Pozostali gracze: ${playerPositions.size})")

                        // Informujemy innych graczy o wyjściu
                        broadcastToAll(connections, "LEAVE|$playerId")
                    }
                }
                // Dodaj obsługę zamknięcia
                Runtime.getRuntime().addShutdownHook(Thread {
                    println("Zatrzymywanie serwera, zapisywanie danych użytkowników...")
                    userManager.saveUsers()
                    println("Dane zostały zapisane")
                })
            }
        }

        // Start the game loop
        startGameLoop()

    }.start(wait = true)
}
