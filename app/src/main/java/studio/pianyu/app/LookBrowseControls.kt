package studio.pianyu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun LookPriorityChips(
    selected: LookSort?, onSelect: (LookSort?) -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()
) {
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = selected?.let { (it.ordinal - 2).coerceAtLeast(0) } ?: 0
    )
    LazyRow(modifier.fillMaxWidth().height(32.dp).selectableGroup().semantics {
        contentDescription = "按场景排序"
    }, state = rowState, contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        item { LookChip("全部", selected == null, enabled) { onSelect(null) } }
        items(LookSort.entries, key = { it.name }) { sort ->
            LookChip(sort.label, selected == sort, enabled) { onSelect(sort) }
        }
    }
}

@Composable
internal fun LookVariantChips(
    primary: Look, activeIndex: Int, enabled: Boolean, onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier, label: String = "变体"
) {
    val variants = primary.variants()
    if (variants.isEmpty()) return
    val primaryIndex = Looks.indexOf(primary)
    Row(modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brass)
        LazyRow(Modifier.weight(1f).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            item { LookChip("标准", activeIndex == primaryIndex, enabled) { onChoose(primaryIndex) } }
            items(variants, key = { it.value.id }) { (index, variant) ->
                LookChip(variant.name, activeIndex == index, enabled) { onChoose(index) }
            }
        }
    }
}

@Composable
internal fun LookChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.height(32.dp).clip(shape)
        .background(if (selected) Brass.copy(alpha = .22f) else Color.Transparent)
        .border(1.dp, if (selected) Brass.copy(alpha = .65f) else WarmWhite.copy(alpha = .28f), shape)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> WarmWhite.copy(alpha = .38f)
                selected -> Brass
                else -> WarmWhite
            })
    }
}
