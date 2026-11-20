package studio.pianyu.app

internal enum class LookSort(val label: String) {
    DAYLIGHT("日光"),
    ARTIFICIAL("灯光"),
    WARM("暖色"),
    COOL("冷色"),
    SPRING("春季"),
    SUMMER("夏季"),
    AUTUMN("秋季"),
    WINTER("冬季")
}

private val Seasons = listOf(LookSort.SPRING, LookSort.SUMMER, LookSort.AUTUMN, LookSort.WINTER)

internal fun sortedLookCards(sort: LookSort?): List<IndexedValue<Look>> {
    if (sort == null) return LookCards
    val start = Seasons.indexOf(sort)
    val priority = if (start < 0) listOf(sort) else Seasons.drop(start) + Seasons.take(start)
    return LookCards.sortedBy { card ->
        val tags = card.value.browseTags()
        val rank = priority.indexOfFirst { it in tags }
        if (rank < 0) priority.size else rank
    }
}
