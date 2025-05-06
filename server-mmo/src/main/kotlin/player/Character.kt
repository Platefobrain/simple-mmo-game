package pl.decodesoft.player

import kotlinx.serialization.Serializable

@Serializable
data class CharacterInfo(
    val id: String,             // Unikalny identyfikator postaci
    val nickname: String,       // Nazwa postaci
    val characterClass: Int,    // Klasa postaci (0-łucznik, 1-mag, 2-wojownik)
    var maxHealth: Int = 100,   // Maksymalne zdrowie
    var currentHealth: Int = 100 // Aktualne zdrowie
)