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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.decodesoft.enemy.manager.EnemyManager
import pl.decodesoft.map.GameMap
import pl.decodesoft.msg.ChatManager
import pl.decodesoft.pathfinding.findPath
import pl.decodesoft.player.api.requests.CharacterCreateRequest
import pl.decodesoft.player.api.requests.CharacterSelectRequest
import pl.decodesoft.player.api.requests.CharactersListRequest
import pl.decodesoft.player.api.responses.CharacterCreateResponse
import pl.decodesoft.player.api.responses.CharacterSelectResponse
import pl.decodesoft.player.api.responses.CharactersListResponse
import pl.decodesoft.player.combat.PlayerCombatManager
import pl.decodesoft.player.manager.UserManager
import pl.decodesoft.player.model.CharacterClass
import pl.decodesoft.player.model.PlayerData
import pl.decodesoft.player.movement.MovementTarget
import pl.decodesoft.player.movement.PlayerMovementManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

// Modele danych do obsługi uwierzytelniania
@Serializable
data class AuthRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val success: Boolean, val message: String, val userId: String = "")

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

// Modyfikacja głównej funkcji serwera
fun main() {
    // Mapa przechowująca aktywne sesje graczy
    val connections = ConcurrentHashMap<String, DefaultWebSocketSession>()

    // Mapa przechowująca dane o pozycjach graczy
    val playerPositions = ConcurrentHashMap<String, PlayerData>()

    // Map to store movement targets for server-side movement
    val playerMovementTargets = ConcurrentHashMap<String, MovementTarget>()

    val playerMovementManager = PlayerMovementManager(connections, playerPositions, playerMovementTargets)

    // Variables for the game update loop
    val updateInterval = 17L // 20 updates per second

    // Manager użytkowników
    val userManager = UserManager()

    // Manager pisania wiadomości
    val chatManager = ChatManager()

    // enemy manager
    val enemyManager = EnemyManager()
    enemyManager.spawnSheep(900f, 700f, level = 3) // poziom 3
    enemyManager.spawnWolf(950f, 800f, level = 5)  // poziom 5
    enemyManager.spawnBear(1000f, 900f, level = 8)  // poziom 8
    enemyManager.spawnBear(1100f, 950f, level = 8)  // poziom 8
    enemyManager.spawnBear(1050f, 1000f, level = 8)  // poziom 8
    enemyManager.spawnBear(1200f, 1200f, level = 8)  // poziom 8
    enemyManager.spawnBear(1250f, 1100f, level = 8)  // poziom 8
    enemyManager.spawnBear(1300f, 1150f, level = 8)  // poziom 8



    val playerCombatManager = PlayerCombatManager(connections, playerPositions, userManager, enemyManager)

    // Create a coroutine scope for network operations and game loop
    val networkScope = CoroutineScope(Dispatchers.IO)

    // Funkcja pomocnicza do obsługi różnych typów ataków
    suspend fun handleAttack(
        parts: List<String>,
        sessionId: String,
        message: String,
        attackType: String,
        attackDescription: String
    ) {
        if (parts.size >= 7) {
            try {
                // Parsuj koordynaty
                val startX = parts[1].toFloat()
                val startY = parts[2].toFloat()
                val endX = parts[3].toFloat()
                val endY = parts[4].toFloat()
                val attackerId = parts[5]
                val targetId = parts[6]

                // Rozgłoś animację ataku do wszystkich graczy oprócz atakującego
                broadcastToOthers(connections, sessionId, message)

                // Zadaj obrażenia - używa nowej klasy PlayerCombatManager
                playerCombatManager.processHitMessage(targetId, attackerId, attackType)

                println("Wykonano $attackDescription od $attackerId do $targetId ($startX,$startY -> $endX,$endY)")
            } catch (e: NumberFormatException) {
                println("Błąd parsowania współrzędnych ataku: ${e.message}")
            }
        }
    }

    val gameMap = GameMap(120, 120, 16)
    val mapCsv = object {}.javaClass.getResourceAsStream("/map.csv")?.bufferedReader()?.readText()
        ?: error("Nie znaleziono pliku map.csv")
    gameMap.loadFromCsv(mapCsv)

    // Start the game loop to continuously update player positions
    fun startGameLoop() {

        networkScope.launch {
            while (true) {
                val deltaTime = updateInterval / 1000f

                playerMovementManager.updatePlayerPositions(gameMap, deltaTime, 120f)

                enemyManager.updateEnemyTargets(playerPositions, gameMap, deltaTime)

                val respawnedEnemies = enemyManager.updateRespawnTimers(deltaTime)

                if (respawnedEnemies.isNotEmpty()) {
                    val respawnInfo = respawnedEnemies.joinToString(";") { enemy ->
                        "${enemy.id},${enemy.x},${enemy.y},${enemy.type},${enemy.currentHealth},${enemy.maxHealth},${enemyManager.getEnemyState(enemy.id)},${enemy.level}"
                    }

                    val respawnMessage = "ENEMY_RESPAWN|$respawnInfo"
                    broadcastToAll(connections, respawnMessage)
                }

                val updatedEnemies = enemyManager.updateEnemyPositions(gameMap, deltaTime)

                if (updatedEnemies.isNotEmpty()) {
                    val enemyUpdates = updatedEnemies.joinToString(";") {
                        val enemyState = enemyManager.getEnemyState(it.id)
                        "${it.id},${it.x},${it.y},${it.type},${it.currentHealth},${it.maxHealth},${enemyState},${it.level}"
                    }

                    val enemyUpdateMessage = "ENEMY_POSITIONS|$enemyUpdates"
                    broadcastToAll(connections, enemyUpdateMessage)
                }

                delay(updateInterval)
            }
        }
    }

    // połączenie
    embeddedServer(Netty, port = 8081) {
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
                        // Tylko ustaw który slot jest wybrany:
                        val user = userManager.getUserById(request.userId)!!
                        user.selectedCharacterSlot = request.characterSlot
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

                                        val user = userManager.getUserById(playerId)
                                        if (user != null && user.hasSelectedCharacter()) {
                                            // Pobierz dane z wybranej postaci zamiast z User
                                            val selectedCharacter = user.getSelectedCharacter()!!

                                            playerPositions[playerId] = PlayerData(
                                                x, y, playerId, selectedCharacter.nickname,
                                                CharacterClass.entries[selectedCharacter.characterClass],
                                                selectedCharacter.maxHealth, selectedCharacter.currentHealth,
                                                selectedCharacter.level, selectedCharacter.experience
                                            )

                                            broadcastToOthers(
                                                connections, sessionId,
                                                "JOIN|$x|$y|$playerId|${selectedCharacter.nickname}|${selectedCharacter.characterClass}|${selectedCharacter.currentHealth}|${selectedCharacter.maxHealth}|${selectedCharacter.level}|${selectedCharacter.experience}"
                                            )
                                        } else {
                                            // Użytkownik nie ma wybranej postaci - błąd
                                            send("ERROR|Musisz najpierw wybrać postać")
                                            return@webSocket
                                        }

                                        // Wyślij informacje o wszystkich istniejących graczach do nowego gracza
                                        playerPositions.forEach { (id, player) ->
                                            if (id != playerId) {
                                                send("JOIN|${player.x}|${player.y}|${player.id}|${player.username}|${player.characterClass.ordinal}|${player.currentHealth}|${player.maxHealth}|${player.level}|${player.experience}")
                                            }
                                        }
                                    }
                                }

                                "PATHFIND" -> {
                                    if (parts.size >= 5) {
                                        val startX = parts[1].toInt()
                                        val startY = parts[2].toInt()
                                        val endX = parts[3].toInt()
                                        val endY = parts[4].toInt()

                                        val path = findPath(gameMap, startX, startY, endX, endY)

                                        // Przekształć do prostego formatu np. PATH|x1:y1,x2:y2,...
                                        val pathString = path.joinToString(",") { "${it.first}:${it.second}" }
                                        send("PATH|$pathString")
                                    } else {
                                        send("PATH|ERROR|Nieprawidłowa liczba argumentów")
                                    }
                                }

                                // New handler for MOVE_TO messages - client sends movement intention, server calculates
                                "MOVE_TO" -> {
                                    if (parts.size >= 5) {
                                        val targetX = parts[1].toFloat()
                                        val targetY = parts[2].toFloat()
                                        val moveToRange = parts[3].toFloat()
                                        val playerId = parts[4]

                                        playerPositions[playerId]?.let { player ->
                                            // Konwertuj na koordynaty mapy
                                            val startTileX = (player.x / gameMap.tileSize).toInt()
                                            val startTileY = (player.y / gameMap.tileSize).toInt()
                                            val endTileX = (targetX / gameMap.tileSize).toInt()
                                            val endTileY = (targetY / gameMap.tileSize).toInt()

                                            // Znajdź ścieżkę
                                            val path = findPath(gameMap, startTileX, startTileY, endTileX, endTileY)

                                            if (path.isNotEmpty()) {
                                                // Zapisz ścieżkę w celu ruchu
                                                val movementTarget = MovementTarget(
                                                    targetX = targetX,
                                                    targetY = targetY,
                                                    moveToRange = moveToRange,
                                                    path = path.toMutableList(),
                                                    currentPathIndex = 0
                                                )
                                                playerMovementTargets[playerId] = movementTarget

                                                // Wyślij ścieżkę do klienta dla wizualizacji
                                                val pathString = path.joinToString(",") { "${it.first}:${it.second}" }
                                                send("PATH|$pathString")
                                            } else {
                                                // Brak ścieżki - poinformuj klienta
                                                send("MOVE_FAILED|$playerId|no_path")
                                            }
                                        }
                                    }
                                }

                                // Legacy MOVE handler - now only used by the server to broadcast
                                "MOVE" -> {
                                    // We no longer process direct MOVE commands from clients
                                    // Movement is now calculated server-side
                                }

                                "GET_ENEMIES" -> {
                                    val list = enemyManager.getEnemies()
                                        .joinToString(";") { "${it.id},${it.x},${it.y},${it.type},${it.currentHealth},${it.maxHealth},${it.level}" }
                                    send("ENEMY_LIST|$list")
                                }

                                "DAMAGE_ENEMY" -> {
                                    if (parts.size >= 3) {
                                        val enemyId = parts[1]
                                        val damage = parts[2].toIntOrNull() ?: 0
                                        val died = enemyManager.damageEnemy(enemyId, damage)
                                        broadcastToAll(connections, "ENEMY_HIT|$enemyId|$damage")

                                        if (died) {
                                            broadcastToAll(connections, "ENEMY_DIED|$enemyId")
                                        }
                                    }
                                }

                                "RANGED_ATTACK" -> {
                                    handleAttack(parts, sessionId, message, "ARROW", "atak dystansowy")
                                }

                                "SPELL_ATTACK" -> {
                                    handleAttack(parts, sessionId, message, "FIREBALL", "atak magiczny")
                                }

                                "MELEE_ATTACK" -> {
                                    handleAttack(parts, sessionId, message, "MELEE", "atak wręcz")
                                }

                                "HIT" -> {
                                    if (parts.size >= 4) {
                                        println("Otrzymano wiadomość HIT od klienta: $message")
                                    }
                                }

                                "DAMAGE" -> {
                                    if (parts.size >= 3) {
                                        val targetId = parts[1]
                                        val damage = parts[2].toIntOrNull() ?: 0

                                        playerPositions[targetId]?.let { targetPlayer ->
                                            targetPlayer.takeDamage(damage)

                                            // Aktualizuj zdrowie w wybranej postaci użytkownika
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.getSelectedCharacter()?.let { character ->
                                                    character.currentHealth = targetPlayer.currentHealth
                                                    userManager.updateUser(user)
                                                }
                                            }

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
                                            targetPlayer.heal(healAmount)

                                            // Aktualizuj zdrowie w wybranej postaci
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.getSelectedCharacter()?.let { character ->
                                                    character.currentHealth = targetPlayer.currentHealth
                                                    userManager.updateUser(user)
                                                }
                                            }

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

                                            // Aktualizuj zdrowie w wybranej postaci
                                            userManager.getUserById(targetId)?.let { user ->
                                                user.getSelectedCharacter()?.let { character ->
                                                    character.currentHealth = targetPlayer.currentHealth
                                                    userManager.updateUser(user)
                                                }
                                            }

                                            // Przekazujemy aktualizację wszystkim
                                            broadcastToAll(connections, message)
                                        }
                                    }
                                }

                                "RESPAWN" -> {
                                    if (parts.size >= 2) {
                                        val respawningPlayerId = parts[1]

                                        playerPositions[respawningPlayerId]?.let { player ->
                                            player.currentHealth = player.maxHealth

                                            // Aktualizuj zdrowie w wybranej postaci
                                            userManager.getUserById(respawningPlayerId)?.let { user ->
                                                user.getSelectedCharacter()?.let { character ->
                                                    character.currentHealth = player.maxHealth
                                                    userManager.updateUser(user)
                                                }
                                            }

                                            val respawnMessage =
                                                "RESPAWN|$respawningPlayerId|${player.currentHealth}|${player.maxHealth}"
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