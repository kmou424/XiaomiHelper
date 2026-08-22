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

import android.content.Context
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils.clzMiuiKeyguardStatusBarView
import dev.lackluster.mihelper.hook.rules.systemui.ResourcesUtils
import dev.lackluster.mihelper.hook.rules.systemui.ResourcesUtils.lock_screen_carrier_airplane_mode_on
import dev.lackluster.mihelper.hook.utils.RemotePreferences.lazyGet
import dev.lackluster.mihelper.hook.utils.extraOf
import dev.lackluster.mihelper.hook.utils.toTyped
import kotlin.BooleanArray

object HideCarrierLabel : StaticHooker() {
    enum class CarrierPosition { Unknown, Keyguard, ControlCenter }
    private var View.carrierPosition by extraOf<CarrierPosition>("KEY_CARRIER_POSITION")

    private val controlCenterHideSimOne by Preferences.SystemUI.ControlCenter.HIDE_CARRIER_ONE.lazyGet()
    private val controlCenterHideSimTwo by Preferences.SystemUI.ControlCenter.HIDE_CARRIER_TWO.lazyGet()
    private val controlCenterHideCarrierHD by Preferences.SystemUI.ControlCenter.HIDE_CARRIER_HD.lazyGet()
    private val lockscreenHideSimOne by Preferences.SystemUI.LockScreen.HIDE_CARRIER_ONE.lazyGet()
    private val lockscreenHideSimTwo by Preferences.SystemUI.LockScreen.HIDE_CARRIER_TWO.lazyGet()

    private val clzControlCenterCarrierText by "com.android.systemui.controlcenter.shade.ControlCenterCarrierText".lazyClassOrNull()
    private val clzMiuiCarrierTextController by "com.android.systemui.statusbar.policy.MiuiCarrierTextController".lazyClassOrNull()

    private val mContext by lazy {
        clzMiuiCarrierTextController?.resolve()?.firstFieldOrNull {
            name = "mContext"
        }?.toTyped<Context>()
    }
    private val mAirplane by lazy {
        clzMiuiCarrierTextController?.resolve()?.firstFieldOrNull {
            name = "mAirplane"
        }?.toTyped<Boolean>()
    }
    private val mCustomCarrier by lazy {
        clzMiuiCarrierTextController?.resolve()?.firstFieldOrNull {
            name = "mCustomCarrier"
        }?.toTyped<Array<String?>>()
    }
    private val mCarrier by lazy {
        clzMiuiCarrierTextController?.resolve()?.firstFieldOrNull {
            name = "mCarrier"
        }?.toTyped<Array<String?>>()
    }
    private val mSimError by lazy {
        clzMiuiCarrierTextController?.resolve()?.firstFieldOrNull {
            name = "mSimError"
        }?.toTyped<BooleanArray>()
    }
    private val mCardDisable by lazy {
        clzMiuiCarrierTextController?.resolve()?.firstFieldOrNull {
            name = "mCardDisable"
        }?.toTyped<BooleanArray>()
    }

    override fun onInit() {
        updateSelfState(true)
    }

    private val applyingCarrierHide = ThreadLocal.withInitial { false }

    override fun onHook() {
        if (controlCenterHideCarrierHD) {
            clzControlCenterCarrierText?.apply {
                val hdText = resolve().optional(true).firstFieldOrNull {
                    name = "hdText"
                }?.toTyped<View>()
                val plusText = resolve().optional(true).firstFieldOrNull {
                    name = "plusText"
                }?.toTyped<View>()
                resolve().optional(true).firstMethodOrNull {
                    name = "updateHDText"
                }?.hook {
                    val ori = proceed()
                    hdText?.get(thisObject)?.visibility = View.GONE
                    plusText?.get(thisObject)?.visibility = View.GONE
                    result(ori)
                }
            }
        }
        val metOnCarrierTextChanged = clzMiuiKeyguardStatusBarView?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "onCarrierTextChanged"
            parameters(Int::class, String::class)
        }
        if (
            controlCenterHideSimOne || controlCenterHideSimTwo ||
            lockscreenHideSimOne || lockscreenHideSimTwo
        ) {
            "com.android.systemui.controlcenter.shade.MiuiCarrierTextLayout".toClassOrNull()?.apply {
                val fldLeftCarrierTextView = resolve().optional(true).firstFieldOrNull {
                    name = "leftCarrierTextView"
                }?.toTyped<View>()
                val fldRightCarrierTextView = resolve().optional(true).firstFieldOrNull {
                    name = "rightCarrierTextView"
                }?.toTyped<View>()
                val metGetKeyguardHeaderLayout = resolve().optional(true).firstMethodOrNull {
                    name = "getKeyguardHeaderLayout"
                }?.toTyped<Boolean>()
                val metGetQsHeaderLayout = resolve().optional(true).firstMethodOrNull {
                    name = "getQsHeaderLayout"
                }?.toTyped<Boolean>()
                val tagPosition: (Any?) -> Unit = { layout ->
                    if (layout != null) {
                        val isKeyguard = runCatching { metGetKeyguardHeaderLayout?.invoke(layout) }.getOrNull()
                        val isControlCenter = runCatching { metGetQsHeaderLayout?.invoke(layout) }.getOrNull()
                        val carrierPosition =
                            if (isKeyguard == true) CarrierPosition.Keyguard
                            else if (isControlCenter == true) CarrierPosition.ControlCenter
                            else CarrierPosition.Unknown
                        fldLeftCarrierTextView?.get(layout)?.carrierPosition = carrierPosition
                        fldRightCarrierTextView?.get(layout)?.carrierPosition = carrierPosition
                    }
                }
                // 不要在构造里调 lazy getter，OS4 会在 inflate 阶段重入导致卡死
                resolve().optional(true).firstMethodOrNull {
                    name = "onFinishInflate"
                }?.hook {
                    val ori = proceed()
                    tagPosition(thisObject)
                    result(ori)
                }
                resolve().optional(true).firstMethodOrNull {
                    name = "onAttachedToWindow"
                }?.hook {
                    val ori = proceed()
                    tagPosition(thisObject)
                    result(ori)
                }
            }
            $$"com.android.systemui.controlcenter.shade.ControlCenterCarrierText$mCarrierTextCallback$1".toClassOrNull()?.apply {
                val carrierTextView = resolve().optional(true).firstFieldOrNull {
                    type("com.android.systemui.controlcenter.shade.ControlCenterCarrierText")
                }?.toTyped<View>()
                resolve().optional(true).firstMethodOrNull {
                    name = "onCarrierTextChanged"
                }?.hook {
                    if (applyingCarrierHide.get() == true) {
                        return@hook result(proceed())
                    }
                    val carrierTextView = carrierTextView?.get(thisObject) ?: return@hook result(proceed())
                    val id = carrierTextView.id
                    val hide = when (carrierTextView.carrierPosition) {
                        CarrierPosition.Keyguard ->
                            lockscreenHideSimOne && id == ResourcesUtils.normal_control_center_carrier_view ||
                                lockscreenHideSimTwo && id == ResourcesUtils.normal_control_center_carrier_second_view
                        CarrierPosition.Unknown, CarrierPosition.ControlCenter ->
                            controlCenterHideSimOne && id == ResourcesUtils.normal_control_center_carrier_view ||
                                controlCenterHideSimTwo && id == ResourcesUtils.normal_control_center_carrier_second_view
                        else -> false
                    }
                    if (!hide) {
                        return@hook result(proceed())
                    }
                    val textIdx = args.indices.firstOrNull { getArg(it) is String } ?: 1
                    if ((getArg(textIdx) as? String).isNullOrEmpty()) {
                        return@hook result(proceed())
                    }
                    applyingCarrierHide.set(true)
                    try {
                        val newArgs = args.toTypedArray()
                        newArgs[textIdx] = ""
                        result(proceed(newArgs))
                    } finally {
                        applyingCarrierHide.set(false)
                    }
                }
            }
        }
        if (metOnCarrierTextChanged != null && (lockscreenHideSimOne || lockscreenHideSimTwo)) { // < OS3.0.300
            clzMiuiKeyguardStatusBarView?.apply {
                val mDep = resolve().firstFieldOrNull {
                    name = "mDep"
                }?.toTyped<Any>()
                val carrierTextController = "com.android.systemui.statusbar.phone.KeyguardStatusBarViewControllerInject".toClassOrNull()
                    ?.resolve()?.firstFieldOrNull {
                        name = "carrierTextController"
                    }?.toTyped<Any>()
                metOnCarrierTextChanged.hook {
                    val newArgs = args.toTypedArray()
                    if (lockscreenHideSimOne && lockscreenHideSimTwo) {
                        newArgs[1] = ""
                    } else {
                        val dep = mDep?.get(thisObject)
                        val carrierController = dep?.let { carrierTextController?.get(it) }
                        if (carrierController != null) {
                            val airplaneMode = mAirplane?.get(carrierController) ?: false
                            if (airplaneMode) {
                                val context = mContext?.get(carrierController)
                                newArgs[1] = context?.getString(lock_screen_carrier_airplane_mode_on)
                            } else {
                                val simError = mSimError?.get(carrierController)
                                val cardDisable = mCardDisable?.get(carrierController)
                                val customCarrier = mCustomCarrier?.get(carrierController)
                                val carrier = mCarrier?.get(carrierController)
                                val showSlotIndex = if (lockscreenHideSimOne) 1 else 0
                                newArgs[1] = if (
                                    (simError == null || simError.size <= showSlotIndex || simError[showSlotIndex]) ||
                                    (cardDisable == null || cardDisable.size <= showSlotIndex || cardDisable[showSlotIndex])
                                ) {
                                    ""
                                } else if (
                                    customCarrier != null && customCarrier.size > showSlotIndex &&
                                    !customCarrier[showSlotIndex].isNullOrEmpty()
                                ) {
                                    customCarrier[showSlotIndex]
                                } else if (
                                    carrier != null && carrier.size > showSlotIndex &&
                                    !carrier[showSlotIndex].isNullOrEmpty()
                                ) {
                                    carrier[showSlotIndex]
                                } else {
                                    ""
                                }
                            }
                        }
                    }
                    result(proceed(newArgs))
                }
            }
        }
    }
}