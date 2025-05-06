package pl.decodesoft.player

import kotlinx.serialization.Serializable

// Żądania i odpowiedzi dla API
@Serializable
data class CharactersListRequest(val userId: String)

@Serializable
data class CharactersListResponse(
    val success: Boolean,
    val message: String,
    val characters: List<CharacterInfo> = emptyList()
)

@Serializable
data class CharacterCreateRequest(
    val userId: String,
    val characterClass: Int,
    val nickname: String,
    val slotIndex: Int
)

@Serializable
data class CharacterCreateResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class CharacterSelectRequest(
    val userId: String,
    val characterSlot: Int
)

@Serializable
data class CharacterSelectResponse(
    val success: Boolean,
    val message: String
)