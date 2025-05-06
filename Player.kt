package pl.decodesoft.player

import com.badlogic.gdx.graphics.Color
import kotlin.math.sqrt

// Klasa Player z dodaną funkcjonalnością płynnego ruchu
data class Player(
    var x: Float,
    var y: Float,
    val id: String,
    val color: Color,
    val username: String,
    val characterClass: Int = 2, // 0-łucznik, 1-mag, 2-wojownik
    var isSelected: Boolean = false, // Dodana flaga zaznaczenia
    var maxHealth: Int = 100, // Maksymalny poziom zdrowia
    var currentHealth: Int = 100 // Aktualny poziom zdrowia
) {
    // Zmienne do obsługi płynnego ruchu
    private var targetX: Float = x // Docelowa pozycja X
    private var targetY: Float = y // Docelowa pozycja Y
    private var isMoving: Boolean = false // Czy gracz się porusza
    private var moveSpeed: Float = 200f // Prędkość ruchu (taka sama jak na serwerze)
    private var lastUpdateTime: Long = System.currentTimeMillis() // Czas ostatniej aktualizacji

    // Zmienne do interpolacji (dla płynnego ruchu innych graczy)
    private var previousX: Float = x // Poprzednia pozycja X
    private var previousY: Float = y // Poprzednia pozycja Y
    private var interpolationProgress: Float = 0f // Postęp interpolacji (0.0 - 1.0)

    // Czy gracz jest martwy
    fun isDead(): Boolean = currentHealth <= 0

    // Metoda do pobierania koloru specyficznego dla klasy postaci
    fun getClassColor(): Color {
        return when (characterClass) {
            0 -> Color(0.2f, 0.8f, 0.2f, 1f) // Zielony dla łucznika
            1 -> Color(0.2f, 0.2f, 0.9f, 1f) // Niebieski dla maga
            else -> Color(0.9f, 0.2f, 0.2f, 1f) // Czerwony dla wojownika
        }
    }

    // Metoda do pobierania nazwy klasy postaci
    fun getClassName(): String {
        return when (characterClass) {
            0 -> "Łucznik"
            1 -> "Mag"
            else -> "Wojownik"
        }
    }

    // Metoda do ustawiania docelowej pozycji ruchu
    fun setMoveTarget(newTargetX: Float, newTargetY: Float) {
        // Zapisz aktualną pozycję jako poprzednią
        previousX = x
        previousY = y

        // Ustaw nowy cel
        targetX = newTargetX
        targetY = newTargetY

        // Rozpocznij ruch
        isMoving = true
        lastUpdateTime = System.currentTimeMillis()
        interpolationProgress = 0f
    }

    // Metoda do aktualizacji pozycji lokalnego gracza (predykcja po stronie klienta)
    fun updateLocalPosition(delta: Float) {
        if (!isMoving) return

        // Oblicz odległość do celu
        val distX = targetX - x
        val distY = targetY - y
        val distance = sqrt((distX * distX + distY * distY).toDouble()).toFloat()

        // Jeśli jesteśmy wystarczająco blisko celu, zatrzymaj ruch
        if (distance < 5f) {
            isMoving = false
            return
        }

        // Oblicz znormalizowany wektor kierunku
        val dirX = distX / distance
        val dirY = distY / distance

        // Oblicz odległość ruchu
        val moveDistance = moveSpeed * delta

        // Aktualizuj pozycję
        x += dirX * moveDistance
        y += dirY * moveDistance
    }

    // Metoda do aktualizacji pozycji innych graczy (interpolacja)
    fun updateRemotePosition(delta: Float) {
        if (!isMoving) return

        // Zwiększ postęp interpolacji
        interpolationProgress += delta * 10f // Mnożnik wpływa na płynność - dostosuj wg potrzeb
        interpolationProgress = interpolationProgress.coerceIn(0f, 1f)

        // Interpoluj między poprzednią a docelową pozycją
        x = lerp(previousX, targetX, interpolationProgress)
        y = lerp(previousY, targetY, interpolationProgress)

        // Jeśli osiągnęliśmy cel, zatrzymaj interpolację
        if (interpolationProgress >= 1f) {
            isMoving = false
        }
    }

    // Metoda do korekty pozycji według serwera (gdy predykcja się rozejdzie z rzeczywistością)
    fun applyServerCorrection(serverX: Float, serverY: Float, threshold: Float = 30f) {
        val distance = sqrt(((x - serverX) * (x - serverX) + (y - serverY) * (y - serverY)).toDouble()).toFloat()

        // Jeśli różnica jest zbyt duża, zastosuj pozycję z serwera
        if (distance > threshold) {
            x = serverX
            y = serverY
            targetX = serverX
            targetY = serverY
            isMoving = false
        }
    }

    // Metoda pomocnicza do interpolacji liniowej
    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + t * (end - start)
    }
}