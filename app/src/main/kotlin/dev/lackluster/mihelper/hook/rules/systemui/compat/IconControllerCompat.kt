package dev.lackluster.mihelper.hook.rules.systemui.compat

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.makeAccessible
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils.clzStatusBarIconControllerImpl

object IconControllerCompat {
    @Volatile
    var iconController: Any? = null

    private val metSetIcon2 by lazy {
        clzStatusBarIconControllerImpl?.resolve()?.firstMethodOrNull {
            name = "setIcon"
            parameterCount = 2
        }?.self?.apply { makeAccessible() }
    }
    private val metSetIcon3 by lazy {
        clzStatusBarIconControllerImpl?.resolve()?.firstMethodOrNull {
            name = "setIcon"
            parameterCount = 3
            parameters(CharSequence::class, String::class, Int::class)
        }?.self?.apply { makeAccessible() }
    }
    private val metSetIconVisibility by lazy {
        clzStatusBarIconControllerImpl?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "setIconVisibility"
            parameters(String::class, Boolean::class)
        }?.self?.apply { makeAccessible() }
    }
    private val metRemoveAllIconsForSlot by lazy {
        clzStatusBarIconControllerImpl?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "removeAllIconsForSlot"
            parameters(String::class, Boolean::class)
        }?.self?.apply { makeAccessible() }
    }

    fun setIcon(iconController: Any, contentDescription: String? = null, slot: String, iconResId: Int) {
        metSetIcon3?.invoke(iconController, contentDescription, slot, iconResId)
    }

    fun setIcon(iconController: Any, slot: String, holder: Any) {
        metSetIcon2?.invoke(iconController, slot, holder)
    }

    fun setIconVisibility(iconController: Any, slot: String, visible: Boolean) {
        metSetIconVisibility?.invoke(iconController, slot, visible)
    }

    fun removeAllIconsForSlot(iconController: Any, slot: String) {
        metRemoveAllIconsForSlot?.invoke(iconController, slot, true)
    }

    fun refreshIconGroups(controller: Any? = iconController) {
        if (controller == null) return
        val refreshOne = clzStatusBarIconControllerImpl?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "refreshIconGroup"
        }?.self?.apply { makeAccessible() }
        val refreshAll = clzStatusBarIconControllerImpl?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "refreshIconGroups"
        }?.self?.apply { makeAccessible() }
        val groups = clzStatusBarIconControllerImpl?.resolve()?.optional(true)?.firstFieldOrNull {
            name = "mIconGroups"
        }?.self?.apply { makeAccessible() }?.get(controller) as? Iterable<*>
        groups?.forEach { group ->
            if (group != null) runCatching { refreshOne?.invoke(controller, group) }
        }
        runCatching { refreshAll?.invoke(controller, 0) }
    }
}