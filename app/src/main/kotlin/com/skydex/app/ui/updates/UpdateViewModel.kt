package com.skydex.app.ui.updates

import androidx.lifecycle.ViewModel
import com.skydex.app.updates.UpdateManager
import com.skydex.app.updates.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Thin UI wrapper around [UpdateManager], which owns the state so downloads survive navigation. */
@HiltViewModel
class UpdateViewModel @Inject constructor(private val updates: UpdateManager) : ViewModel() {
    val state: StateFlow<UpdateState> = updates.state

    init {
        // Safe to call repeatedly: the manager checks once per process.
        updates.checkOnLaunch()
    }

    fun check() = updates.check()

    fun download() = updates.download()

    fun skip(versionCode: Int) = updates.skip(versionCode)

    fun dismiss() = updates.dismissBanner()

    fun cancel() = updates.cancelDownload()

    fun onPermissionResult() = updates.onPermissionResult()

    fun showConfirmation() = updates.showConfirmation()
}
