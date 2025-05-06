package pl.decodesoft.klasy.skile

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import pl.decodesoft.player.Player

/**
 * Interfejs dla wszystkich typów umiejętności w grze
 */
interface Skile {
    // Podstawowe właściwości umiejętności
    val x: Float
    val y: Float
    val casterId: String
    val color: Color
    val size: Float

    // Metody, które muszą zaimplementować wszystkie umiejętności
    fun update(delta: Float): Boolean
    fun checkCollision(player: Player): Boolean
    fun render(shapeRenderer: ShapeRenderer)
}