package com.sirelon.sellsnap.designsystem.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sirelon.sellsnap.composeapp.generated.resources.Res
import com.sirelon.sellsnap.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class IconItem(val name: String, val resource: DrawableResource)

@Composable
fun IconPreviewScreen() {
    val icons = listOf(
        IconItem("arrow_left", Res.drawable.ic_arrow_left),
        IconItem("baby", Res.drawable.ic_baby),
        IconItem("book_open", Res.drawable.ic_book_open),
        IconItem("camera", Res.drawable.ic_camera),
        IconItem("car", Res.drawable.ic_car),
        IconItem("check", Res.drawable.ic_check),
        IconItem("chevron_left", Res.drawable.ic_chevron_left),
        IconItem("chevron_right", Res.drawable.ic_chevron_right),
        IconItem("circle_alert", Res.drawable.ic_circle_alert),
        IconItem("circle_check_big", Res.drawable.ic_circle_check_big),
        IconItem("copy", Res.drawable.ic_copy),
        IconItem("dollar_sign", Res.drawable.ic_dollar_sign),
        IconItem("dumbbell", Res.drawable.ic_dumbbell),
        IconItem("eye", Res.drawable.ic_eye),
        IconItem("file_text", Res.drawable.ic_file_text),
        IconItem("frown", Res.drawable.ic_frown),
        IconItem("gift", Res.drawable.ic_gift),
        IconItem("heart", Res.drawable.ic_heart),
        IconItem("home", Res.drawable.ic_home),
        IconItem("layout_grid", Res.drawable.ic_layout_grid),
        IconItem("meh", Res.drawable.ic_meh),
        IconItem("palette", Res.drawable.ic_palette),
        IconItem("pen_line", Res.drawable.ic_pen_line),
        IconItem("refresh_cw", Res.drawable.ic_refresh_cw),
        IconItem("server", Res.drawable.ic_server),
        IconItem("share_2", Res.drawable.ic_share_2),
        IconItem("shirt", Res.drawable.ic_shirt),
        IconItem("smartphone", Res.drawable.ic_smartphone),
        IconItem("sparkles", Res.drawable.ic_sparkles),
        IconItem("tag", Res.drawable.ic_tag),
        IconItem("tree_pine", Res.drawable.ic_tree_pine),
        IconItem("trending_up", Res.drawable.ic_trending_up),
        IconItem("triangle_alert", Res.drawable.ic_triangle_alert),
        IconItem("upload", Res.drawable.ic_upload),
        IconItem("user", Res.drawable.ic_user),
        IconItem("wand_sparkles", Res.drawable.ic_wand_sparkles),
        IconItem("wifi_off", Res.drawable.ic_wifi_off),
        IconItem("wrench", Res.drawable.ic_wrench),
        IconItem("x", Res.drawable.ic_x),
    )

    Column(
        modifier = Modifier
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(
            text = "Icon Preview (${icons.size} icons)",
            modifier = Modifier.padding(bottom = 16.dp),
            fontSize = 20.sp
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.fillMaxSize(),
            content = {
                items(icons) { icon ->
                    IconPreviewItem(icon)
                }
            }
        )
    }
}

@Composable
private fun IconPreviewItem(icon: IconItem) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .background(Color.LightGray, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(icon.resource),
                contentDescription = icon.name,
                modifier = Modifier.padding(bottom = 8.dp),
                tint = Color.Black
            )
            Text(
                text = icon.name,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Preview
@Composable
fun IconPreviewScreenPreview() {
    IconPreviewScreen()
}
