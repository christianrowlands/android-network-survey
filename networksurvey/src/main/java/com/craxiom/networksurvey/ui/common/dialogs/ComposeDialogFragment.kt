package com.craxiom.networksurvey.ui.common.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

/**
 * A single reusable bridge that hosts Compose dialog content inside a [DialogFragment].
 *
 * This lets still-Fragment screens (including Java fragments and AndroidX preference screens) show
 * the app's shared Compose dialogs without each one needing its own [DialogFragment] subclass.
 * Call [show] to display a dialog.
 *
 * Note: the [content] lambda is not retained across configuration changes or process death. If the
 * system recreates the fragment, [content] is null and the dialog dismisses itself rather than
 * rendering blank. This matches the behavior of the old imperatively-shown dialogs (which also did
 * not survive rotation) and is acceptable because these dialogs are transient and re-triggerable.
 */
class ComposeDialogFragment : DialogFragment() {

    /**
     * The Compose content to host. Receives a `dismiss` callback used to close the dialog.
     */
    var content: (@Composable (dismiss: () -> Unit) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val hostedContent = content
        if (hostedContent == null) {
            // Recreated by the system without its content lambda; there is nothing to show.
            dismissAllowingStateLoss()
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.craxiom.networksurvey.ui.theme.NsTheme {
                    hostedContent?.invoke { dismissAllowingStateLoss() }
                }
            }
        }
    }

    companion object {
        /**
         * Shows a dialog hosting the given Compose [content]. The content receives a `dismiss`
         * callback to close the dialog. A no-op if a dialog with the same [tag] is already showing,
         * which prevents accidentally stacking duplicates.
         */
        fun show(
            fragmentManager: FragmentManager,
            tag: String,
            content: @Composable (dismiss: () -> Unit) -> Unit,
        ) {
            if (fragmentManager.findFragmentByTag(tag) != null) return
            ComposeDialogFragment().apply {
                this.content = content
            }.show(fragmentManager, tag)
        }
    }
}
