package studio.pianyu.app

import kotlin.math.pow

data class MeteringIntent(val id: String, val label: String)
val MeteringIntents = listOf(
    MeteringIntent("balanced", "均衡"), MeteringIntent("center", "中心"),
    MeteringIntent("protect_highlights", "保护高光"), MeteringIntent("protect_shadows", "保护阴影")
)
val MeteringMethods = MeteringIntents.map { it.id }.toSet() + "center_weighted"

data class PrintStock(val id: String, val name: String)
private val photoPapers = listOf(
    PrintStock("kodak_portra_endura", "Kodak Portra Endura"),
    PrintStock("kodak_supra_endura", "Kodak Supra Endura"),
    PrintStock("kodak_ultra_endura", "Kodak Ultra Endura"),
    PrintStock("kodak_endura_premier", "Kodak Endura Premier"),
    PrintStock("fujifilm_crystal_archive_typeii", "Fujifilm Crystal Archive Type II")
)
private val motionPrints = listOf(PrintStock("kodak_2383", "Kodak 2383"), PrintStock("kodak_2393", "Kodak 2393"))
private val monoPapers = listOf(PrintStock("ilford_multigrade_iv_rc", "Ilford Multigrade IV RC"))
val PrintStocks = photoPapers + motionPrints + monoPapers

fun Look.printStocks(): List<PrintStock> = when {
    positive -> emptyList()
    paper == "kodak_2383" -> motionPrints
    id == "softmono" -> monoPapers
    else -> photoPapers
}

fun Look.effectivePrintStock(requested: String): String =
    printStocks().firstOrNull { it.id == requested }?.id ?: paper

fun Look.printExposureGain(ev: Float): Double = if (positive) 1.0 else
    2.0.pow((ev.takeIf { it.isFinite() } ?: 0f).coerceIn(-2f, 2f).toDouble())
