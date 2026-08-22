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

import android.telephony.SubscriptionManager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils.clzCoroutineScope
import dev.lackluster.mihelper.hook.rules.systemui.compat.hookAfterConstructed
import dev.lackluster.mihelper.hook.rules.systemui.compat.CommonClassUtils.readonlyStateFlowFalse
import dev.lackluster.mihelper.hook.rules.systemui.compat.FlowCompat.cancelJob
import dev.lackluster.mihelper.hook.rules.systemui.compat.FlowCompat.combineFlows
import dev.lackluster.mihelper.hook.rules.systemui.compat.MutableStateFlowCompat
import dev.lackluster.mihelper.hook.rules.systemui.compat.ReadonlyStateFlowCompat
import dev.lackluster.mihelper.hook.utils.HostExecutor
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.RemotePreferences.lazyGet
import dev.lackluster.mihelper.hook.utils.d
import dev.lackluster.mihelper.hook.utils.e
import dev.lackluster.mihelper.hook.utils.extraOf
import dev.lackluster.mihelper.hook.utils.toTyped
import java.util.concurrent.ConcurrentHashMap

object HideCellularIcon : StaticHooker() {
    private var Any.defDataSubIdFlow by extraOf<Any>("KEY_DEF_DATA_CONFIG_FLOW")

    private val enableStackedMobile by Preferences.SystemUI.StatusBar.StackedMobile.ENABLED.lazyGet()
    private val hideSimAuto by lazy {
        Preferences.SystemUI.StatusBar.IconDetail.HIDE_SIM_AUTO.get() && !enableStackedMobile
    }
    private val hideSimOne by lazy {
        Preferences.SystemUI.StatusBar.IconDetail.HIDE_SIM_ONE.get() || enableStackedMobile
    }
    private val hideSimTwo by lazy {
        Preferences.SystemUI.StatusBar.IconDetail.HIDE_SIM_TWO.get() || enableStackedMobile
    }

    private val hideSimJobMap = ConcurrentHashMap<Int, List<Any?>>()

    override fun onInit() {
        updateSelfState(hideSimAuto || hideSimOne || hideSimTwo)
    }

    override fun onHook() {
        val clzMobileIconInteractor = "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconInteractor".toClassOrNull()
        if (hideSimAuto) {
            "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl".toClassOrNull()?.apply {
                val defaultDataSubId = resolve().firstFieldOrNull {
                    name = "defaultDataSubId"
                }?.toTyped<Any>()
                resolve().firstMethodOrNull {
                    name = "getMobileConnectionInteractorForSubId"
                }?.hook {
                    val mobileIconInteractor = proceed()
                    val defaultDataSubIdFlow = defaultDataSubId?.get(thisObject)
                    mobileIconInteractor.defDataSubIdFlow = defaultDataSubIdFlow
                    result(mobileIconInteractor)
                }
            }
        }
        if (enableStackedMobile) {
            hideOriginalMobileViews()
        }
        "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconViewModel".toClassOrNull()?.apply {
            val subscriptionId = resolve().firstFieldOrNull {
                name = "subscriptionId"
            }?.toTyped<Int>()
            val isVisible = resolve().firstFieldOrNull {
                name = "isVisible"
            }?.toTyped<Any>()
            hookAfterConstructed(
                this,
                fallbackClassNames = listOf(
                    "com.android.systemui.statusbar.pipeline.mobile.ui.binder.MiuiMobileIconBinder",
                    "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView"
                ),
                fallbackMethodNames = listOf("bind", "constructAndBind")
            ) { vm ->
                val subId = subscriptionId?.get(vm)
                val slotIndex = subId?.let { SubscriptionManager.getSlotIndex(it) }
                d { "MobileIconViewModel: subId=$subId slotIndex=$slotIndex" }
                hideSimJobMap.remove(subId)?.forEach {
                    cancelJob(it)
                }
                if (enableStackedMobile) {
                    isVisible?.set(vm, readonlyStateFlowFalse)
                    return@hookAfterConstructed
                }
                if (hideSimAuto && subId != null) {
                    val coroutineScope = args.firstOrNull { clzCoroutineScope?.isInstance(it) == true } ?: return@hookAfterConstructed
                    val mobileIconInteractor = args.firstOrNull { clzMobileIconInteractor?.isInstance(it) == true } ?: return@hookAfterConstructed
                    val defaultDataSubIdFlow = mobileIconInteractor.defDataSubIdFlow?.let {
                        ReadonlyStateFlowCompat<Int?>().of(it)
                    } ?: return@hookAfterConstructed
                    val oriVisibleFlow = isVisible?.get(vm)?.let {
                        ReadonlyStateFlowCompat<Boolean>().of(it)
                    } ?: return@hookAfterConstructed
                    val proxyStateFlow = MutableStateFlowCompat(false)
                    val jobs = combineFlows(
                        coroutineScope,
                        oriVisibleFlow,
                        false,
                        defaultDataSubIdFlow,
                        -1,
                        proxyStateFlow
                    ) { a, b ->
                        return@combineFlows a && (b == subId)
                    }
                    hideSimJobMap[subId] = jobs
                    isVisible.set(vm, proxyStateFlow.toReadonlyStateFlow())
                    return@hookAfterConstructed
                }
                if ((slotIndex == 0 && hideSimOne) || (slotIndex == 1 && hideSimTwo)) {
                    isVisible?.set(vm, readonlyStateFlowFalse)
                    return@hookAfterConstructed
                }
                if (slotIndex == -1 && (hideSimOne || hideSimTwo)) {
                    val coroutineScope = args.firstOrNull { clzCoroutineScope?.isInstance(it) == true } ?: return@hookAfterConstructed
                    val oriVisibleFlow = isVisible?.get(vm)?.let {
                        ReadonlyStateFlowCompat<Boolean>().of(it)
                    } ?: return@hookAfterConstructed
                    val slotIndexCheckFlow = MutableStateFlowCompat(false)
                    val proxyStateFlow = MutableStateFlowCompat(false)
                    val jobs = combineFlows(
                        coroutineScope,
                        oriVisibleFlow,
                        false,
                        slotIndexCheckFlow,
                        false,
                        proxyStateFlow
                    ) { a, b ->
                        return@combineFlows a && b
                    }
                    hideSimJobMap[subId] = jobs
                    isVisible.set(vm, proxyStateFlow.toReadonlyStateFlow())
                    HostExecutor.execute(
                        tag = "CheckSlotIndex_${subId}",
                        backgroundTask = {
                            var slot = -1
                            var currentDelayMs = 200L
                            val maxRetries = 8

                            for (i in 0 until maxRetries) {
                                try {
                                    Thread.sleep(currentDelayMs)
                                } catch (t: InterruptedException) {
                                    e(t) { "Canceled!" }
                                    Thread.currentThread().interrupt()
                                    return@execute -1
                                }

                                slot = SubscriptionManager.getSlotIndex(subId)

                                if (slot != -1) {
                                    d { "MobileIconViewModel: Slot resolved! subId=$subId, slotIndex=$slot, attempts=${i + 1}" }
                                    break
                                }
                                currentDelayMs *= 2
                            }

                            return@execute slot
                        },
                        runOnMain = true,
                        onResult = { slot ->
                            val shouldHide = (slot == 0 && hideSimOne) || (slot == 1 && hideSimTwo)
                            if (!shouldHide) {
                                slotIndexCheckFlow.setValue(true)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun hideOriginalMobileViews() {
        val clzMobileVm = "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconViewModel".toClassOrNull()
        val clzLocVm = "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.LocationBasedMobileViewModel".toClassOrNull()
        val isVisible = clzMobileVm?.resolve()?.firstFieldOrNull {
            name = "isVisible"
        }?.toTyped<Any>()
        val commonImpl = clzLocVm?.resolve()?.firstFieldOrNull {
            name = "commonImpl"
        }?.toTyped<Any>()

        fun resolveVm(candidate: Any?): Any? {
            if (candidate == null) return null
            if (clzMobileVm?.isInstance(candidate) == true) return candidate
            if (clzLocVm?.isInstance(candidate) == true) {
                return commonImpl?.get(candidate) ?: candidate
            }
            return null
        }

        fun hideIfPresent(candidate: Any?) {
            val vm = resolveVm(candidate) ?: return
            isVisible?.set(vm, readonlyStateFlowFalse)
            d { "hide original mobile isVisible on ${vm.javaClass.simpleName}" }
        }

        "com.android.systemui.statusbar.pipeline.mobile.ui.binder.MiuiMobileIconBinder"
            .toClassOrNull()
            ?.resolve()
            ?.optional(true)
            ?.firstMethodOrNull { name = "bind" }
            ?.hook {
                args.forEach { hideIfPresent(it) }
                result(proceed())
            }
        "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView"
            .toClassOrNull()
            ?.resolve()
            ?.optional(true)
            ?.firstMethodOrNull { name = "constructAndBind" }
            ?.hook {
                args.forEach { hideIfPresent(it) }
                result(proceed())
            }
    }
}