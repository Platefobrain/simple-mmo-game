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

package pl.decodesoft.player.api.responses

import kotlinx.serialization.Serializable
import pl.decodesoft.player.model.CharacterInfo

@Serializable
data class CharactersListResponse(
    val success: Boolean,
    val message: String,
    val characters: List<CharacterInfo> = emptyList()
)

@Serializable
data class CharacterCreateResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class CharacterSelectResponse(
    val success: Boolean,
    val message: String
)