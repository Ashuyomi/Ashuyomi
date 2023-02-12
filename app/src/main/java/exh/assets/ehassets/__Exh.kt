package exh.assets.ehassets

import androidx.compose.ui.graphics.vector.ImageVector
import exh.assets.EhAssets
import exh.assets.ehassets.exh.AllAssets
import exh.assets.ehassets.exh.Assets
import kotlin.collections.List as ____KtList

object ExhGroup

val EhAssets.Exh: ExhGroup
    get() = ExhGroup

private var __AllAssets: ____KtList<ImageVector>? = null

val ExhGroup.AllAssets: ____KtList<ImageVector>
    get() {
        if (__AllAssets != null) {
            return __AllAssets!!
        }
        __AllAssets = Assets.AllAssets + listOf()
        return __AllAssets!!
    }
