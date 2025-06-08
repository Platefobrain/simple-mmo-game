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

package pl.decodesoft.player.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val characters: MutableList<CharacterInfo> = mutableListOf(),
    var selectedCharacterSlot: Int? = null // Który slot postaci jest aktualnie wybrany
) {
    // Pomocnicze metody do pobierania danych wybranej postaci
    fun getSelectedCharacter(): CharacterInfo? {
        return selectedCharacterSlot?.let { slot ->
            characters.getOrNull(slot)
        }
    }

    fun hasSelectedCharacter(): Boolean = getSelectedCharacter() != null
}