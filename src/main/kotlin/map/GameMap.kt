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

package pl.decodesoft.map

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch

class GameMap(private val width: Int, val height: Int, private val tileSize: Int) {
    private val tiles = Array(height) { IntArray(width) } // Każda komórka to ID tekstury

    // Tekstury według ID
    private val wodaTexture = Texture("assets/maps/tiles/woda.png")     // default
    private val klifTexture = Texture("assets/maps/tiles/klif.png")     // 1
    private val klif1Texture = Texture("assets/maps/tiles/klif1.png")   // 2
    private val klif3Texture = Texture("assets/maps/tiles/klif3.png")   // 3
    private val grassTexture = Texture("assets/maps/tiles/grass.png")   // 4
    private val klif4Texture = Texture("assets/maps/tiles/klif4.png")   // 5
    private val woda7Texture = Texture("assets/maps/tiles/woda7.png")   // 6
    private val woda3Texture = Texture("assets/maps/tiles/woda3.png")   // 7
    private val woda2Texture = Texture("assets/maps/tiles/woda2.png")   // 8
    private val woda4Texture = Texture("assets/maps/tiles/woda4.png")   // 9
    private val woda6Texture = Texture("assets/maps/tiles/woda6.png")   // 10
    private val klif5Texture = Texture("assets/maps/tiles/klif5.png")   // 12
    private val woda5Texture = Texture("assets/maps/tiles/woda5.png")   // 13
    private val klif2Texture = Texture("assets/maps/tiles/klif2.png")   // 14
    private val woda1Texture = Texture("assets/maps/tiles/woda1.png")   // 15, 18
    private val pieTexture = Texture("assets/maps/tiles/5.png")         // 16
    private val wallTexture = Texture("assets/maps/tiles/wall.png")     // 17

    // Niewykorzystane, ale załadowane tekstury
    private val dwaTexture = Texture("assets/maps/tiles/2.png")
    private val trzyTexture = Texture("assets/maps/tiles/3.png")
    private val czteTexture = Texture("assets/maps/tiles/4.png")
    private val szeTexture = Texture("assets/maps/tiles/6.png")
    private val sieTexture = Texture("assets/maps/tiles/7.png")
    private val oTexture = Texture("assets/maps/tiles/8.png")
    private val dziewTexture = Texture("assets/maps/tiles/9.png")
    private val dzieTexture = Texture("assets/maps/tiles/10.png")

    fun loadFromCsv(csv: String) {
        val lines = csv.trim().lines()
        for ((y, line) in lines.withIndex()) {
            val cols = line.split(",")
            for ((x, cell) in cols.withIndex()) {
                if (x < width && y < height) {
                    tiles[y][x] = cell.trim().toIntOrNull() ?: 0
                }
            }
        }
    }

    fun draw(batch: SpriteBatch) {
        for (y in 0 until height) {
            val correctedY = height - 1 - y
            for (x in 0 until width) {
                val texture = when (tiles[y][x]) {
                    1 -> klifTexture
                    2 -> klif1Texture
                    3 -> klif3Texture
                    4 -> grassTexture
                    5 -> klif4Texture
                    6 -> woda7Texture
                    7 -> woda3Texture
                    8 -> woda2Texture
                    9 -> woda4Texture
                    10 -> woda6Texture
                    12 -> klif5Texture
                    13 -> woda5Texture
                    14 -> klif2Texture
                    15 -> woda1Texture
                    16 -> pieTexture
                    17 -> wallTexture
                    18 -> woda1Texture
                    else -> wodaTexture
                }

                batch.draw(
                    texture,
                    x * tileSize.toFloat(),
                    correctedY * tileSize.toFloat(),
                    tileSize.toFloat(),
                    tileSize.toFloat()
                )
            }
        }
    }

    fun dispose() {
        wallTexture.dispose()
        grassTexture.dispose()
        dwaTexture.dispose()
        trzyTexture.dispose()
        czteTexture.dispose()
        pieTexture.dispose()
        szeTexture.dispose()
        sieTexture.dispose()
        oTexture.dispose()
        dziewTexture.dispose()
        dzieTexture.dispose()
        wodaTexture.dispose()
        woda1Texture.dispose()
        woda2Texture.dispose()
        woda3Texture.dispose()
        woda4Texture.dispose()
        woda5Texture.dispose()
        woda6Texture.dispose()
        woda7Texture.dispose()
        klifTexture.dispose()
        klif1Texture.dispose()
        klif2Texture.dispose()
        klif3Texture.dispose()
        klif4Texture.dispose()
        klif5Texture.dispose()
    }
}
