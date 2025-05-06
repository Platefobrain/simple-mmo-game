package pl.decodesoft.player

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val passwordHash: String,
    var characterClass: CharacterClass = CharacterClass.WARRIOR, // Dla kompatybilności wstecznej
    val createdAt: Long = System.currentTimeMillis(),
    var maxHealth: Int = 100, // Dla kompatybilności wstecznej
    var currentHealth: Int = 100, // Dla kompatybilności wstecznej
    var nickname: String = username, // Dla kompatybilności wstecznej
    val characters: MutableList<CharacterInfo> = mutableListOf() // Lista postaci
)