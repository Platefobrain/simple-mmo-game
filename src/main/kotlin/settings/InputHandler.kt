package pl.decodesoft.settings

import com.badlogic.gdx.graphics.OrthographicCamera
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import pl.decodesoft.player.Player

class InputHandler(
    private val player: Player,
    private val players: Map<String, Player>,
    private val camera: OrthographicCamera,
    private val networkScope: CoroutineScope,
    private val session: () -> DefaultWebSocketSession?,
) {
    private var lastTarget: Player? = null // Ostatnio wybrany cel

    fun handleInput(delta: Float) {
        // Miejsce na przyszłe bindy klawiszy
    }

    // Metoda wywoływana przez PlayerHandler, gdy zostanie wybrany cel
    fun setLastTarget(player: Player?) {
        lastTarget = player
    }
}