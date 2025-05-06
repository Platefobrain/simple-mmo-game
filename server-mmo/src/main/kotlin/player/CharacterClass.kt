/*
 * Licencja: Wszelkie prawa zastrzeżone.
 * Możesz używać, modyfikować, kopiować i dystrybuować ten projekt do własnych celów,
 * ale nie możesz używać go do celów komercyjnych, chyba że uzyskasz zgodę od autora.
 * Projekt jest dostarczany "tak jak jest", bez żadnych gwarancji.
 * Używasz go na własne ryzyko.
 * Autor: Copyright [2025] [Platefobrain]
 */

package pl.decodesoft.player

import kotlinx.serialization.Serializable

// Wyliczenie dostępnych klas postaci
enum class CharacterClass {
    ARCHER, // Łucznik
    MAGE,   // Mag
    WARRIOR // Wojownik
}

// Rozszerzenie klasy player data o klasę postaci i zdrowie
@Serializable
data class PlayerData(
    var x: Float,
    var y: Float,
    val id: String,
    val username: String = "",
    val characterClass: CharacterClass = CharacterClass.WARRIOR, // Domyślnie wojownik
    var maxHealth: Int = 100, // Maksymalne zdrowie
    var currentHealth: Int = 100 // Aktualne zdrowie
) {
    // Metoda do ustawiania wartości zdrowia
    fun setHealth(health: Int) {
        currentHealth = health.coerceIn(0, maxHealth)
    }

    // Metoda do otrzymywania obrażeń
    fun takeDamage(damage: Int) {
        currentHealth = (currentHealth - damage).coerceIn(0, maxHealth)
    }

    // Metoda do leczenia
    fun heal(amount: Int) {
        currentHealth = (currentHealth + amount).coerceIn(0, maxHealth)
    }
}
