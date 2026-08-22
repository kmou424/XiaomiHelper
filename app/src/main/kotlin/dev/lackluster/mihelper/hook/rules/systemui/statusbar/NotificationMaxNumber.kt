/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2023 HowieHChen, howie.dev@outlook.com

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.lackluster.mihelper.hook.rules.systemui.statusbar

import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.systemui.compat.MutableStateFlowCompat
import dev.lackluster.mihelper.hook.rules.systemui.compat.hookAfterConstructed
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.RemotePreferences.observe
import dev.lackluster.mihelper.hook.utils.toTyped

object NotificationMaxNumber : StaticHooker() {
    private val internalMaxIconFlow by lazy {
        MutableStateFlowCompat(Preferences.SystemUI.StatusBar.NOTIF_MAX_COUNT.get())
    }

    override fun onInit() {
        updateSelfState(Preferences.SystemUI.StatusBar.ENABLE_NOTIF_MAX_COUNT.get())
        Preferences.SystemUI.StatusBar.NOTIF_MAX_COUNT.observe {
            internalMaxIconFlow.setValue(it)
        }
    }

    override fun onHook() {
        val replacementFlow = {
            internalMaxIconFlow.toReadonlyStateFlow() ?: internalMaxIconFlow.toMutableStateFlow()
        }
        // OS3：替换 NotificationIconObserver.maxIconFlow
        // OS4：该字段仍在，但已是 combine Flow；构造可能被内联，ViewModel 才是 binder 真正读的地方
        "com.android.systemui.statusbar.policy.NotificationIconObserver".toClassOrNull()?.apply {
            val maxIconFlow = resolve().optional(true).firstFieldOrNull {
                name = "maxIconFlow"
            }?.toTyped<Any>()
            hookAfterConstructed(this) { observer ->
                replacementFlow()?.let { maxIconFlow?.set(observer, it) }
            }
        }
        listOf(
            "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel",
            "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerAlwaysOnDisplayViewModel",
        ).forEach { className ->
            className.toClassOrNull()?.apply {
                val maxIcons = resolve().optional(true).firstFieldOrNull {
                    name = "maxIcons"
                }?.toTyped<Any>()
                hookAfterConstructed(this) { vm ->
                    replacementFlow()?.let { maxIcons?.set(vm, it) }
                }
            }
        }
        "com.android.systemui.statusbar.phone.NotificationIconContainer".toClassOrNull()?.apply {
            resolve().optional(true).firstMethodOrNull {
                name = "setMaxIconsAmount"
            }?.hook {
                val max = internalMaxIconFlow.getValue() ?: getArg(0)
                result(proceed(arrayOf(max)))
            }
        }
    }
}
