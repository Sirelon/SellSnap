package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.categories.domain.RootCategoryKind
import com.sirelon.sellsnap.features.seller.categories.domain.rootCategoryKind
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_baby
import com.sirelon.sellsnap.generated.resources.ic_dumbbell
import com.sirelon.sellsnap.generated.resources.ic_home
import com.sirelon.sellsnap.generated.resources.ic_meh
import com.sirelon.sellsnap.generated.resources.ic_palette
import com.sirelon.sellsnap.generated.resources.ic_shirt
import com.sirelon.sellsnap.generated.resources.ic_smartphone
import org.jetbrains.compose.resources.painterResource

@Composable
fun categoryIconPainter(categoryId: Int): Painter? =
    when (rootCategoryKind(_currentOlxCountry, categoryId)) {
        RootCategoryKind.ELECTRONICS -> painterResource(Res.drawable.ic_smartphone)
        RootCategoryKind.FASHION -> painterResource(Res.drawable.ic_shirt)
        RootCategoryKind.HOME_GARDEN -> painterResource(Res.drawable.ic_home)
        RootCategoryKind.BABY -> painterResource(Res.drawable.ic_baby)
        RootCategoryKind.SPORT_HOBBY -> painterResource(Res.drawable.ic_dumbbell)
        RootCategoryKind.OTHER -> painterResource(Res.drawable.ic_palette)
        RootCategoryKind.SPARE_PARTS -> painterResource(Res.drawable.ic_meh)
        else -> null
    }
