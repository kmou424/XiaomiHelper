/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2025 HowieHChen, howie.dev@outlook.com

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
import dev.lackluster.mihelper.hook.utils.RemotePreferences.lazyGet
import dev.lackluster.mihelper.hook.utils.toTyped

object IgnoreSysIconSettings : StaticHooker() {
    private val ignoreSystem by Preferences.SystemUI.StatusBar.IconTuner.IGNORE_SYS_SETTINGS.lazyGet()
    private val hidePrivacy by Preferences.SystemUI.StatusBar.IconTuner.HIDE_PRIVACY.lazyGet()
    private val showNetSpeed by lazy {
        Preferences.SystemUI.StatusBar.IconTuner.NET_SPEED.get() != 4
    }

    override fun onInit() {
        updateSelfState(ignoreSystem || hidePrivacy)
    }

    override fun onHook() {
        hookStatusBarIconObserver()
        hookIconHideList()
        if (ignoreSystem) {
            hookNetworkSpeed()
        }
    }

    private fun hookStatusBarIconObserver() {
        "com.android.systemui.statusbar.policy.StatusBarIconObserver".toClassOrNull()?.apply {
            val statusBarIconShow = resolve().optional(true).firstFieldOrNull {
                name = "statusBarIconShow"
            }?.toTyped<Any>()
            resolve().optional(true).firstMethodOrNull {
                name = "loadStatusBarIcon"
            }?.hook {
                result(if (ignoreSystem) "" else proceed())
            }
            // OS3: isIconBlocked(slot) 在 Observer 上；OS4 已挪走，改清 statusBarIconShow
            hookAfterConstructed(this) { observer ->
                if (!ignoreSystem) return@hookAfterConstructed
                val flow = statusBarIconShow?.get(observer) ?: return@hookAfterConstructed
                val compat = MutableStateFlowCompat<Any?>().of(flow)
                when (val value = compat.getValue()) {
                    is String -> compat.setValue("")
                    is Set<*> -> compat.setValue(emptySet<String>())
                    is Collection<*> -> compat.setValue(emptyList<String>())
                    else -> compat.setValue("")
                }
            }
        }
    }

    private fun hookIconHideList() {
        "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl".toClassOrNull()?.apply {
            val iconHideList = resolve().optional(true).firstFieldOrNull {
                name = "mIconHideList"
            }?.toTyped<MutableCollection<*>>()
            if (ignoreSystem) {
                hookAfterConstructed(this) { controller ->
                    iconHideList?.get(controller)?.clear()
                }
                resolve().optional(true).firstMethodOrNull {
                    name = "onTuningChanged"
                }?.hook {
                    val key = getArg(0) as? String
                    if (key == "icon_blacklist" || key?.contains("icon_blacklist") == true) {
                        iconHideList?.get(thisObject)?.clear()
                        result(null)
                    } else {
                        result(proceed())
                    }
                }
            }
        }
    }

    private fun hookNetworkSpeed() {
        "com.android.systemui.statusbar.policy.NetworkSpeedController".toClassOrNull()?.apply {
            val mShowNetworkSpeed = resolve().optional(true).firstFieldOrNull {
                name = "mShowNetworkSpeed"
            }?.toTyped<Boolean>()
            resolve().optional(true).firstConstructorOrNull()?.hook {
                val ori = proceed()
                mShowNetworkSpeed?.set(thisObject, showNetSpeed)
                result(ori)
            }
            resolve().optional(true).firstMethodOrNull {
                name {
                    it.contains("mupdateVisibility") || it.contains("updateVisibility")
                }
            }?.hook {
                val networkSpeedController = getArg(0)
                val tag = getArg(1) as? String
                if (tag == "show" && networkSpeedController != null) {
                    mShowNetworkSpeed?.set(networkSpeedController, showNetSpeed)
                }
                result(proceed())
            }
        }
    }
}
