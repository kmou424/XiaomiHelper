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
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils.readonlyStateFlowFalse
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils.readonlyStateFlowTrue
import dev.lackluster.mihelper.hook.rules.systemui.compat.hookAfterConstructed
import dev.lackluster.mihelper.hook.utils.RemotePreferences.lazyGet
import dev.lackluster.mihelper.hook.utils.toTyped

object CellularIcon : StaticHooker() {
    private val ignoreSysSettings by Preferences.SystemUI.StatusBar.IconTuner.IGNORE_SYS_SETTINGS.lazyGet()

    private val hideActivity by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_ACTIVITY.lazyGet()
    private val hideType by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_TYPE.lazyGet()
    private val hideVoWifi by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_VO_WIFI.lazyGet()
    private val hideVolte by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_VOLTE.lazyGet()
    private val hideVolteNoService by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_VOLTE_NO_SERVICE.lazyGet()
    private val hideSpeechHD by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_SPEECH_HD.lazyGet()

    private val hideRoamGlobal by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_ROAM_GLOBAL.lazyGet()
    private val hideLargeRoam by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_LARGE_ROAM.lazyGet()
    private val hideSmallRoam by Preferences.SystemUI.StatusBar.IconDetail.HIDE_CELLULAR_SMALL_ROAM.lazyGet()

    override fun onInit() {
        updateSelfState(true)
    }

    override fun onHook() {
        "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MiuiCellularIconVM".toClassOrNull()?.apply {
            if (
                hideActivity || hideType || hideVoWifi || hideVolte || hideVolteNoService || hideSpeechHD
            ) {
                val inOutVisible = resolve().optional(true).firstFieldOrNull {
                    name = "inOutVisible"
                }?.toTyped<Any>()
                val mobileTypeVisible = resolve().optional(true).firstFieldOrNull {
                    name = "mobileTypeVisible"
                }?.toTyped<Any>()
                val mobileTypeImageVisible = resolve().optional(true).firstFieldOrNull {
                    name = "mobileTypeImageVisible"
                }?.toTyped<Any>()
                val vowifiVisible = resolve().optional(true).firstFieldOrNull {
                    name = "vowifiVisible"
                }?.toTyped<Any>()
                val speechHd = resolve().optional(true).firstFieldOrNull {
                    name = "speechHd"
                }?.toTyped<Any>()
                val volteNoService = resolve().optional(true).firstFieldOrNull {
                    name = "volteNoService"
                }?.toTyped<Any>()
                val volteVisibleGlobal = resolve().optional(true).firstFieldOrNull {
                    name = "volteVisibleGlobal"
                }?.toTyped<Any>()
                hookAfterConstructed(
                    this,
                    fallbackClassNames = listOf(
                        "com.android.systemui.statusbar.pipeline.mobile.ui.binder.MiuiMobileIconBinder"
                    )
                ) { vm ->
                    if (hideActivity) {
                        inOutVisible?.set(vm, readonlyStateFlowFalse)
                    }
                    if (hideType) {
                        mobileTypeVisible?.set(vm, readonlyStateFlowFalse)
                        mobileTypeImageVisible?.set(vm, readonlyStateFlowFalse)
                    }
                    if (hideVoWifi) {
                        vowifiVisible?.set(vm, readonlyStateFlowFalse)
                    }
                    if (hideVolte) {
                        volteVisibleGlobal?.set(vm, readonlyStateFlowFalse)
                    }
                    if (hideVolteNoService) {
                        volteNoService?.set(vm, readonlyStateFlowFalse)
                    }
                    if (hideSpeechHD) {
                        speechHd?.set(vm, readonlyStateFlowFalse)
                    }
                }
            }
            val hideByGetter = mapOf(
                "getInOutVisible" to hideActivity,
                "getMobileTypeVisible" to hideType,
                "getMobileTypeImageVisible" to hideType,
                "getMobileTypeSingleVisible" to hideType,
                "getVowifiVisible" to hideVoWifi,
                "getVolteVisibleGlobal" to hideVolte,
                "getVolteNoService" to hideVolteNoService,
                "getSpeechHd" to hideSpeechHD,
                "getMobileRoamVisible" to (!hideRoamGlobal && hideLargeRoam),
                "getSmallRoamVisible" to (!hideRoamGlobal && hideSmallRoam),
            )
            hideByGetter.forEach { (method, enabled) ->
                if (!enabled) return@forEach
                resolve().optional(true).firstMethodOrNull {
                    name = method
                }?.hook {
                    result(readonlyStateFlowFalse)
                }
            }
        }
        if (hideRoamGlobal || ignoreSysSettings) {
            "com.android.systemui.statusbar.policy.StatusBarIconObserver".toClassOrNull()?.apply {
                val roamSettingBlock = resolve().optional(true).firstFieldOrNull {
                    name = "roamSettingBlock"
                }?.toTyped<Any>()
                hookAfterConstructed(this) { observer ->
                    roamSettingBlock?.set(
                        observer,
                        if (hideRoamGlobal) readonlyStateFlowTrue else readonlyStateFlowFalse
                    )
                }
            }
        }
    }
}