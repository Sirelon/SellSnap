package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_baby
import com.sirelon.sellsnap.generated.resources.ic_dumbbell
import com.sirelon.sellsnap.generated.resources.ic_home
import com.sirelon.sellsnap.generated.resources.ic_meh
import com.sirelon.sellsnap.generated.resources.ic_palette
import com.sirelon.sellsnap.generated.resources.ic_shirt
import com.sirelon.sellsnap.generated.resources.ic_smartphone
import com.sirelon.sellsnap.generated.resources.ic_tree_pine
import org.jetbrains.compose.resources.painterResource

@Composable
fun categoryIconPainter(categoryId: Int): Painter? = when (categoryId) {
    37 -> painterResource(Res.drawable.ic_smartphone)
    891 -> painterResource(Res.drawable.ic_shirt) // fashion
    899 -> painterResource(Res.drawable.ic_home) // home and garden
    36 -> painterResource(Res.drawable.ic_baby) // baby and children
    903 -> painterResource(Res.drawable.ic_dumbbell) // sport. hobbie
    3901 -> painterResource(Res.drawable.ic_palette) // other
    3 -> painterResource(Res.drawable.ic_meh) // details
    35 -> painterResource(Res.drawable.ic_tree_pine) // animals
    else -> null
}
