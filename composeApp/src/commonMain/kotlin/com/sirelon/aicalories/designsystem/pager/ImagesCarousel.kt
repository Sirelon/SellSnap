package com.sirelon.sellsnap.designsystem.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.img_seller_empty_photo
import org.jetbrains.compose.resources.painterResource

private val PhotoCarouselShape = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = 0.dp,
    bottomStart = AppDimens.BorderRadius.xl11,
    bottomEnd = AppDimens.BorderRadius.xl11,
)

@Composable
fun ImagesCarousel(
    images: List<String>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerModifier = modifier
        .fillMaxWidth()
        .height(AppDimens.Size.xl25)
        .clip(PhotoCarouselShape)
        .background(AppTheme.colors.surfaceLow)

    if (images.isEmpty()) {
        EmptyPhotoCarousel(modifier = containerModifier)
        return
    }

    Box(
        modifier = containerModifier,
    ) {
        val pagerState = rememberPagerState(pageCount = { images.size })

        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
            key = { pageIndex -> "${pageIndex}:${images[pageIndex]}" },
        ) { pageIndex ->
            PhotoCarouselPage(
                image = images[pageIndex],
                onTap = {
                    onImageClick(pageIndex)
                },
            )
        }

        if (images.size > 1) {
            PageDots(
                pageCount = images.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppDimens.Spacing.xl3),
            )
        }
    }
}

@Composable
private fun PhotoCarouselPage(image: String, onTap: () -> Unit) {
    val imageModel = remember(image) { image.trim() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfaceLow)
            .clickable(onClick = onTap),
    ) {
        key(imageModel) {
            AppAsyncImage(
                model = imageModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyPhotoCarousel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.img_seller_empty_photo),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

// FIXME: Make it interactable (count of images in some slider, i.e.)

@PreviewLightDark
@Composable
private fun PhotoCarouselEmptyPreview() {
    PhotoCarouselPreviewSurface {
        ImagesCarousel(images = emptyList(), onImageClick = {})
    }
}

@PreviewLightDark
@Composable
private fun PhotoCarouselSinglePreview() {
    PhotoCarouselPreviewSurface {
        ImagesCarousel(images = photoCarouselPreviewImages.take(1), onImageClick = {})
    }
}

@PreviewLightDark
@Composable
private fun PhotoCarouselThreeImagesPreview() {
    PhotoCarouselPreviewSurface {
        ImagesCarousel(images = photoCarouselPreviewImages.take(3), onImageClick = {})
    }
}

@PreviewLightDark
@Composable
private fun PhotoCarouselEightImagesPreview() {
    PhotoCarouselPreviewSurface {
        ImagesCarousel(
            images = List(8) { index -> photoCarouselPreviewImages[index % photoCarouselPreviewImages.size] },
            onImageClick = {})
    }
}

@Composable
private fun PhotoCarouselPreviewSurface(
    content: @Composable () -> Unit,
) {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.background,
        ) {
            Box(modifier = Modifier.padding(bottom = AppDimens.Spacing.xl5)) {
                content()
            }
        }
    }
}

private val photoCarouselPreviewImages = listOf(
    "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=1200",
    "https://images.unsplash.com/photo-1525966222134-fcfa99b8ae77?w=1200",
    "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=1200",
)
