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

package dev.lackluster.mihelper.hook.rules.systemui.lockscreen

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.firstFieldCompat
import dev.lackluster.mihelper.hook.utils.toTyped

object HideDisturbNotification : StaticHooker() {
    override fun onInit() {
        updateSelfState(Preferences.SystemUI.LockScreen.HIDE_DISTURB_NOTIF.get())
    }

    override fun onHook() {
        hookOs3ZenController()
        hookOs4ZenView()
    }

    private fun hookOs3ZenController() {
        "com.android.systemui.statusbar.notification.zen.ZenModeViewController".toClassOrNull()?.apply {
            val manuallyDismissed = resolve().optional(true).firstFieldOrNull {
                name = "manuallyDismissed"
            }?.toTyped<Boolean>()
            resolve().optional(true).firstMethodOrNull {
                name {
                    it.startsWith("updateVisibility")
                }
            }?.hook {
                manuallyDismissed?.set(thisObject, true)
                result(proceed())
            }
        }
    }

    private fun hookOs4ZenView() {
        "com.miui.systemui.notification.view.viewmodel.NotificationNumStateViewModel"
            .toClassOrNull()?.resolve()?.optional(true)?.firstMethodOrNull {
                name { it == "isZenModeEnabled" || it == "getIsZenModeEnabled" }
            }?.hook {
                result(false)
            }
        "com.miui.systemui.notification.view.NotificationNumStateView".toClassOrNull()?.apply {
            val zenView = firstFieldCompat("zenView")?.toTyped<View>()
            resolve().optional(true).firstMethodOrNull {
                name = "updateZenViewText"
            }?.hook {
                val ori = proceed()
                zenView?.get(thisObject)?.visibility = View.GONE
                result(ori)
            }
        }
    }
}
