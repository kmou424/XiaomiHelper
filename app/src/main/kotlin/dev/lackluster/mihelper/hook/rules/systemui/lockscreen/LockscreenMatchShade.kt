/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2026 HowieHChen, howie.dev@outlook.com

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

import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get

/**
 * 关掉锁屏相对通知中心多出来的过滤，让两者列表一致。
 */
object LockscreenMatchShade : StaticHooker() {
    override fun onInit() {
        updateSelfState(Preferences.SystemUI.LockScreen.MATCH_SHADE_NOTIF.get())
    }

    override fun onHook() {
        "com.android.systemui.statusbar.notification.ExpandedNotification".toClassOrNull()
            ?.resolve()?.optional(true)?.firstMethodOrNull {
                name = "canShowOnKeyguard"
            }?.hook {
                result(true)
            }
        "com.android.systemui.statusbar.notification.policy.NotificationFilterController"
            .toClassOrNull()?.resolve()?.optional(true)?.firstMethodOrNull {
                name = "forceHideOnKeyguard"
            }?.hook {
                result(false)
            }
        $$"com.android.systemui.statusbar.notification.collection.coordinator.OriginalUnseenKeyguardCoordinator$unseenNotifFilter$1"
            .toClassOrNull()?.resolve()?.optional(true)?.firstMethodOrNull {
                name = "shouldFilterOut"
            }?.hook {
                result(false)
            }
    }
}
