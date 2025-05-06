package pl.decodesoft.klasy.skile

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.decodesoft.player.Player
import pl.decodesoft.klasy.Arrow
import pl.decodesoft.klasy.Fireball
import pl.decodesoft.klasy.Sword
import io.ktor.websocket.Frame

/**
 * Klasa zarządzająca wszystkimi umiejętnościami (pociskami) w grze
 */
class SkileManager(
    private val localPlayerId: String,
    private val players: Map<String, Player>,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?
) {
    // Lista wszystkich aktywnych umiejętności
    private val activeSkills = mutableListOf<Skile>()

    // Dodaj nową umiejętność do listy
    fun addSkill(skill: Skile) {
        activeSkills.add(skill)
    }

    // Aktualizacja wszystkich umiejętności
    fun update(delta: Float) {
        val skillsToRemove = mutableListOf<Skile>()

        activeSkills.forEach { skill ->
            val isActive = skill.update(delta)
            if (!isActive) {
                skillsToRemove.add(skill)
            } else {
                // Sprawdź kolizje
                players.values.forEach { player ->
                    // Pomijamy kolizje z casterem
                    if (player.id != skill.casterId && skill.checkCollision(player)) {
                        skillsToRemove.add(skill)
                        // Wysyłanie informacji o trafieniu
                        if (skill.casterId == localPlayerId) {
                            sendHitMessage(player.id, skill)
                        }
                    }
                }
            }
        }

        // Usuń umiejętności, które wyszły poza zasięg lub trafiły w cel
        activeSkills.removeAll(skillsToRemove)
    }

    // Renderowanie wszystkich umiejętności
    fun render(shapeRenderer: ShapeRenderer) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        activeSkills.forEach { skill ->
            skill.render(shapeRenderer)
        }
        shapeRenderer.end()
    }

    // Wysyłanie wiadomości o trafieniu
    private fun sendHitMessage(targetId: String, skill: Skile) {
        networkScope.launch {
            try {
                val hitType = when (skill) {
                    is Arrow -> "ARROW"
                    is Fireball -> "FIREBALL"
                    is Sword -> "SWORD"
                    else -> "UNKNOWN"
                }
                val message = "HIT|$targetId|${skill.casterId}|$hitType"
                session()?.send(Frame.Text(message))
            } catch (e: Exception) {
                Gdx.app.error("SkileManager", "Błąd wysyłania informacji o trafieniu: ${e.message}")
            }
        }
    }

    fun handleSkillMessage(msgType: String, parts: List<String>) {
        when (msgType) {
            "RANGED_ATTACK" -> {
                if (parts.size >= 6) {
                    val startX = parts[1].toFloat()
                    val startY = parts[2].toFloat()
                    val dirX = parts[3].toFloat()
                    val dirY = parts[4].toFloat()
                    val casterId = parts[5]

                    // Nie tworzymy strzały dla lokalnego gracza (już została utworzona)
                    if (casterId != localPlayerId) {
                        addSkill(Arrow(startX, startY, dirX, dirY, casterId))
                    }
                }
            }

            "SPELL_ATTACK" -> {
                if (parts.size >= 6) {
                    val startX = parts[1].toFloat()
                    val startY = parts[2].toFloat()
                    val dirX = parts[3].toFloat()
                    val dirY = parts[4].toFloat()
                    val casterId = parts[5]

                    // Nie tworzymy kuli ognia dla lokalnego gracza
                    if (casterId != localPlayerId) {
                        addSkill(Fireball(startX, startY, dirX, dirY, casterId))
                    }
                }
            }

            "MELEE_ATTACK" -> {
                if (parts.size >= 6) {
                    val startX = parts[1].toFloat()
                    val startY = parts[2].toFloat()
                    val dirX = parts[3].toFloat()
                    val dirY = parts[4].toFloat()
                    val casterId = parts[5]

                    // Nie tworzymy ataku mieczem dla lokalnego gracza
                    if (casterId != localPlayerId) {
                        addSkill(Sword(startX, startY, dirX, dirY, casterId))
                    }
                }
            }
        }
    }
}