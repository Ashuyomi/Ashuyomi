package exh.assets.ehassets.exh.assets

import androidx.compose.ui.graphics.vector.ImageVector
import exh.assets.ehassets.exh.AssetsGroup
import kotlin.collections.List as ____KtList

object EhassetsGroup

val AssetsGroup.Ehassets: EhassetsGroup
    get() = EhassetsGroup

private var __AllAssets: ____KtList<ImageVector>? = null

val EhassetsGroup.AllAssets: ____KtList<ImageVector>
    get() {
        if (__AllAssets != null) {
            return __AllAssets!!
        }
        __AllAssets = listOf()
        return __AllAssets!!
    }
