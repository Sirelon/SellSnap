package com.sirelon.sellsnap.features.seller.categories.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.categories.domain.RootCategoryKind
import com.sirelon.sellsnap.features.seller.categories.domain.rootCategoryKind
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_antiques
import com.sirelon.sellsnap.generated.resources.ic_baby
import com.sirelon.sellsnap.generated.resources.ic_beauty
import com.sirelon.sellsnap.generated.resources.ic_construction
import com.sirelon.sellsnap.generated.resources.ic_dumbbell
import com.sirelon.sellsnap.generated.resources.ic_home
import com.sirelon.sellsnap.generated.resources.ic_meh
import com.sirelon.sellsnap.generated.resources.ic_music
import com.sirelon.sellsnap.generated.resources.ic_other_sales
import com.sirelon.sellsnap.generated.resources.ic_palette
import com.sirelon.sellsnap.generated.resources.ic_shirt
import com.sirelon.sellsnap.generated.resources.ic_smartphone
import com.sirelon.sellsnap.generated.resources.ic_ticket
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
        RootCategoryKind.ANTIQUES -> painterResource(Res.drawable.ic_antiques)
        RootCategoryKind.BEAUTY -> painterResource(Res.drawable.ic_beauty)
        RootCategoryKind.CONSTRUCTION -> painterResource(Res.drawable.ic_construction)
        RootCategoryKind.MUSIC_EDUCATION -> painterResource(Res.drawable.ic_music)
        RootCategoryKind.LEISURE -> painterResource(Res.drawable.ic_ticket)
        RootCategoryKind.OTHER_SALES -> painterResource(Res.drawable.ic_other_sales)
        else -> null
    }
