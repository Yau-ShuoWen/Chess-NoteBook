package com.shuowen.chess.ui.appearance

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.asImageBitmap
import com.shuowen.chess.chess.Color
import com.shuowen.chess.chess.Piece
import com.shuowen.chess.chess.PieceType

private const val ASSET_ROOT = "chess_appearance"
private const val CLASSIC_ID = "classic"
private val imageExtensions = setOf("png", "webp", "jpg", "jpeg")
private val pieceNames = mapOf(
    PieceType.KING to "king",
    PieceType.QUEEN to "queen",
    PieceType.ROOK to "rook",
    PieceType.BISHOP to "bishop",
    PieceType.KNIGHT to "knight",
    PieceType.PAWN to "pawn",
)

@Immutable
data class AppearancePack(
    val id: String,
    val name: String,
    val files: Map<String, String> = emptyMap(),
    val boardColors: Map<String, String> = emptyMap(),
    val isClassic: Boolean = false,
)

@Immutable
data class ChessAppearance(
    val board: AppearancePack = classicPack(),
    val pieces: AppearancePack = classicPack(),
)

val LocalChessAppearance = compositionLocalOf { ChessAppearance() }

class ChessAppearanceController internal constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("chess_appearance", Context.MODE_PRIVATE)

    val themePacks = listOf(classicPack()) + discoverPacks()

    var selectedThemeId by mutableStateOf(validSelection(themePacks))
        private set

    val appearance: ChessAppearance
        get() {
            val theme = themePacks.firstOrNull { it.id == selectedThemeId } ?: classicPack()
            return ChessAppearance(board = theme, pieces = theme)
        }

    fun selectTheme(id: String) {
        if (themePacks.none { it.id == id }) return
        selectedThemeId = id
        preferences.edit().putString("theme", id).apply()
    }

    private fun validSelection(packs: List<AppearancePack>): String {
        val saved = preferences.getString("theme", CLASSIC_ID)
        return saved?.takeIf { id -> packs.any { it.id == id } } ?: CLASSIC_ID
    }

    private fun discoverPacks(): List<AppearancePack> {
        val themesPath = "$ASSET_ROOT/themes"
        val requiredPieces = Color.entries.flatMap { color ->
            pieceNames.values.map { piece -> "${color.name.lowercase()}_$piece" }
        }.toSet()
        return runCatching { appContext.assets.list(themesPath).orEmpty() }.getOrDefault(emptyArray()).mapNotNull { packId ->
            if (packId == CLASSIC_ID) return@mapNotNull null
            val packPath = "$themesPath/$packId"
            val files = runCatching { appContext.assets.list(packPath).orEmpty() }.getOrDefault(emptyArray())
                .filter { file -> file.substringAfterLast('.', "").lowercase() in imageExtensions }
                .associateBy { file -> file.substringBeforeLast('.').lowercase() }
            val boardColors = readBoardColors("$packPath/board_colors.txt")
            val hasBoard = listOf("light", "dark").all(files::containsKey) ||
                listOf("light", "dark").all(boardColors::containsKey)
            if (!requiredPieces.all(files::containsKey) || !hasBoard) return@mapNotNull null
            AppearancePack(
                id = packId,
                name = packId.replace('_', ' '),
                files = files.mapValues { (_, file) -> "$packPath/$file" },
                boardColors = boardColors,
            )
        }.sortedBy(AppearancePack::name)
    }

    private fun readBoardColors(path: String): Map<String, String> = runCatching {
        appContext.assets.open(path).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('=', limit = 2).map(String::trim)
                if (parts.size == 2 && parts[0] in setOf("light", "dark")) parts[0] to parts[1] else null
            }.toMap()
        }.filterValues { value -> runCatching { android.graphics.Color.parseColor(value) }.isSuccess }
    }.getOrDefault(emptyMap())
}

@Composable
fun rememberChessAppearanceController(): ChessAppearanceController {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) { ChessAppearanceController(context) }
}

fun AppearancePack.boardImage(context: Context, light: Boolean): ImageBitmap? =
    loadImage(context, files[if (light) "light" else "dark"])

fun AppearancePack.boardColor(light: Boolean): UiColor? =
    boardColors[if (light) "light" else "dark"]?.let { value ->
        runCatching { UiColor(android.graphics.Color.parseColor(value)) }.getOrNull()
    }

fun AppearancePack.pieceImage(context: Context, piece: Piece): ImageBitmap? {
    val color = piece.color.name.lowercase()
    val type = pieceNames.getValue(piece.type)
    return loadImage(context, files["${color}_$type"])
}

private fun loadImage(context: Context, path: String?): ImageBitmap? {
    if (path == null) return null
    return runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }.getOrNull()
}

private fun classicPack() = AppearancePack(
    id = CLASSIC_ID,
    name = "经典版",
    isClassic = true,
)
