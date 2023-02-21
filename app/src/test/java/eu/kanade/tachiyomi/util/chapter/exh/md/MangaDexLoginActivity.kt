package eu.kanade.tachiyomi.util.chapter.exh.md

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import eu.kanade.tachiyomi.util.lang.launchIO
import exh.md.utils.MdUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(data: Uri?) {
        val code = data?.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                MdUtil.getEnabledMangaDex(Injekt.get())?.login(code)
                returnToSettings()
            }
        } else {
            lifecycleScope.launchIO {
                MdUtil.getEnabledMangaDex(Injekt.get())?.logout()
                returnToSettings()
            }
        }
    }
}
