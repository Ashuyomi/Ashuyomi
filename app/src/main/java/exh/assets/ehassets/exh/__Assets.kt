package exh.assets.ehassets.exh

import androidx.compose.ui.graphics.vector.ImageVector
import exh.assets.ehassets.ExhGroup
import exh.assets.ehassets.exh.assets.AllAssets
import exh.assets.ehassets.exh.assets.Ehassets
import kotlin.collections.List as ____KtList

object AssetsGroup

val ExhGroup.Assets: AssetsGroup
    get() = AssetsGroup

private var __AllAssets: ____KtList<ImageVector>? = null

val AssetsGroup.AllAssets: ____KtList<ImageVector>
    get() {
        if (__AllAssets != null) {
            return __AllAssets!!
        }
        __AllAssets = Ehassets.AllAssets + listOf()
        return __AllAssets!!
    }
