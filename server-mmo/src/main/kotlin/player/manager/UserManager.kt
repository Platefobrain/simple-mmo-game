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

package pl.decodesoft.player.manager

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.decodesoft.player.model.CharacterClass
import pl.decodesoft.player.model.CharacterInfo
import pl.decodesoft.player.model.User
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UserManager {
    private val usersFile = File("users.json")
    private val users = ConcurrentHashMap<String, User>()
    private var lastSaveTime = System.currentTimeMillis()
    private val saveInterval = 900000 // 15 minut między zapisami
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
            currentHealth = maxHealth,
            level = 1,
            experience = 0
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

        // Jeśli usuwamy aktualnie wybraną postać, zresetuj wybór
        if (user.selectedCharacterSlot == user.characters.indexOf(characterToRemove)) {
            user.selectedCharacterSlot = null
        }

        updateUser(user)

        return Result.success(true)
    }
}