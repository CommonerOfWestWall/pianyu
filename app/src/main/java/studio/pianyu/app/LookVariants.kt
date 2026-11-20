package studio.pianyu.app

internal data class LookVariant(val parentId: String, val detail: String)

internal val LookVariants = mapOf(
    "spring-cool" to LookVariant("cinema-tungsten-cool-bright", "叶色偏浅绿，亮部更柔"),
    "autumn-cool" to LookVariant("cinema-tungsten-cool-bright", "收低彩度，突出雾里的冷灰和青绿"),
    "olive-story" to LookVariant("cinema", "黄绿偏橄榄，砖墙和林地暗部更深"),
    "summer-warm" to LookVariant("cinema-daylight-warm-bright", "突出金黄日照，保留蓝色")
)

internal fun Look.primaryStyle(): Look {
    val variant = LookVariants[id] ?: return this
    return Looks.first { it.id == variant.parentId }
}

internal fun Look.variants(): List<IndexedValue<Look>> =
    Looks.withIndex().filter { LookVariants[it.value.id]?.parentId == id }

internal fun Look.browseTags(): Set<LookSort> =
    guidance.tags + variants().flatMap { it.value.guidance.tags }

internal val VariantRecipes by lazy {
    LookVariants.map { (id, variant) ->
        val index = Looks.indexOfFirst { it.id == id }
        val look = Looks[index]
        CreativeRecipe("variant-$id", look.name, "${look.primaryStyle().name} · ${variant.detail}",
            EditSettings(lookIndex = index))
    }
}
