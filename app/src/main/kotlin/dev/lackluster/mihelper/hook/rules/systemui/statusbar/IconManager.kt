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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.Modifiers
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import dev.lackluster.mihelper.data.Constants
import dev.lackluster.mihelper.data.Constants.COMPOUND_ICON_REAL_SLOTS
import dev.lackluster.mihelper.data.Constants.IconSlots
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.systemui.compat.IconControllerCompat
import dev.lackluster.mihelper.hook.rules.systemui.mobile.StackedMobileIcon
import dev.lackluster.mihelper.hook.utils.RemotePreferences
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.d
import dev.lackluster.mihelper.hook.utils.toTyped
import kotlin.collections.listOf
import java.util.concurrent.atomic.AtomicBoolean

object IconManager : StaticHooker() {
    private val refreshScheduled = AtomicBoolean(false)

    private fun iconPositionMode() = Preferences.SystemUI.StatusBar.IconTuner.ICON_POSITION.get()
    private fun iconPositionAutoReorder() = Preferences.SystemUI.StatusBar.IconTuner.ICON_POSITION_REORDER.get()
    private fun addStackedMobile() = Preferences.SystemUI.StatusBar.StackedMobile.ENABLED.get()
    private fun addCompoundIcon() = Preferences.SystemUI.StatusBar.IconTuner.COMPOUND_ICON.get() in 1..3
    private fun leftContainer() = Preferences.SystemUI.StatusBar.IconTuner.LEFT_CONTAINER.get() != 0
    private fun leftExtraBlockedSlots() = Preferences.SystemUI.StatusBar.IconTuner.LEFT_EXT_BLOCK_LIST.get()

    private fun slotsCustom(): List<String> {
        return Preferences.SystemUI.StatusBar.IconTuner.ICON_POSITION_VAL.get().mapNotNull { str ->
            str.split(":").takeIf { it.size == 2 }
        }.sortedBy {
            it[0].toInt()
        }.map {
            it[1]
        }.takeIf {
            it.isNotEmpty()
        } ?: Constants.STATUS_BAR_ICONS_DEFAULT.toList()
    }

    private fun leftSlots(): List<String> {
        return mutableListOf<String>().apply {
            if (Preferences.SystemUI.StatusBar.IconTuner.LEFT_COMPOUND_ICON.get()) addAll(COMPOUND_ICON_REAL_SLOTS)
            if (Preferences.SystemUI.StatusBar.IconTuner.LEFT_LOCATION.get()) add(IconSlots.LOCATION)
            if (Preferences.SystemUI.StatusBar.IconTuner.LEFT_ALARM_CLOCK.get()) add(IconSlots.ALARM_CLOCK)
            if (Preferences.SystemUI.StatusBar.IconTuner.LEFT_ZEN.get()) add(IconSlots.ZEN)
            if (Preferences.SystemUI.StatusBar.IconTuner.LEFT_VOLUME.get()) add(IconSlots.VOLUME)
        }
    }

    private fun finalSlots(): Array<String> {
        val base: List<String> = when (iconPositionMode()) {
            1 -> Constants.STATUS_BAR_ICONS_SWAP
            2 -> slotsCustom()
            else -> Constants.STATUS_BAR_ICONS_DEFAULT
        }
        return base.let { array ->
            val slotsList = array.toMutableList()
            if (addCompoundIcon() && !slotsList.contains(IconSlots.COMPOUND_ICON_STUB)) {
                slotsList.add(
                    slotsList.indexOf(IconSlots.ZEN),
                    IconSlots.COMPOUND_ICON_STUB
                )
            }
            if (addStackedMobile()) {
                if (!slotsList.contains(IconSlots.STACKED_MOBILE_ICON)) {
                    slotsList.addAll(
                        slotsList.indexOf("mobile"),
                        listOf(
                            IconSlots.STACKED_MOBILE_TYPE,
                            IconSlots.STACKED_MOBILE_ICON,
                            IconSlots.SINGLE_MOBILE_SIM1,
                            IconSlots.SINGLE_MOBILE_SIM2,
                        )
                    )
                }
            }
            if (leftContainer()) {
                val left = leftSlots()
                slotsList.sortByDescending { it in left }
            }
            if (addCompoundIcon()) {
                slotsList.addAll(
                    slotsList.indexOf(IconSlots.COMPOUND_ICON_STUB),
                    COMPOUND_ICON_REAL_SLOTS
                )
                slotsList.remove(IconSlots.COMPOUND_ICON_STUB)
            }
            slotsList.toTypedArray()
        }
    }

    val leftBlockList: List<String>
        get() = getLeftBlockList(finalSlots().toList())

    private fun tunerSlots(): Map<String, PreferenceKey<Int>> {
        return buildMap {
            putAll(
                mapOf(
                    "no_sim" to Preferences.SystemUI.StatusBar.IconTuner.NO_SIM,
                    "airplane" to Preferences.SystemUI.StatusBar.IconTuner.AIRPLANE,
                    "wifi" to Preferences.SystemUI.StatusBar.IconTuner.WIFI,
                    "demo_wifi" to Preferences.SystemUI.StatusBar.IconTuner.WIFI,
                    "hotspot" to Preferences.SystemUI.StatusBar.IconTuner.HOTSPOT,
                    "vpn" to Preferences.SystemUI.StatusBar.IconTuner.VPN,
                    "network_speed" to Preferences.SystemUI.StatusBar.IconTuner.NET_SPEED,
                    "bluetooth" to Preferences.SystemUI.StatusBar.IconTuner.BLUETOOTH,
                    "bluetooth_handsfree_battery" to Preferences.SystemUI.StatusBar.IconTuner.BLUETOOTH_BATTERY,
                    "handle_battery" to Preferences.SystemUI.StatusBar.IconTuner.HANDLE_BATTERY,
                    "nfc" to Preferences.SystemUI.StatusBar.IconTuner.NFC,
                    "gps" to Preferences.SystemUI.StatusBar.IconTuner.LOCATION,
                    IconSlots.LOCATION to Preferences.SystemUI.StatusBar.IconTuner.LOCATION,
                    "wireless_headset" to Preferences.SystemUI.StatusBar.IconTuner.WIRELESS_HEADSET,
                    "phone" to Preferences.SystemUI.StatusBar.IconTuner.PHONE,
                    "pad" to Preferences.SystemUI.StatusBar.IconTuner.PAD,
                    "pc" to Preferences.SystemUI.StatusBar.IconTuner.PC,
                    "sound_box_group" to Preferences.SystemUI.StatusBar.IconTuner.SOUND_BOX_GROUP,
                    "stereo" to Preferences.SystemUI.StatusBar.IconTuner.STEREO,
                    "sound_box_screen" to Preferences.SystemUI.StatusBar.IconTuner.SOUND_BOX_SCREEN,
                    "sound_box" to Preferences.SystemUI.StatusBar.IconTuner.SOUND_BOX,
                    "tv" to Preferences.SystemUI.StatusBar.IconTuner.TV,
                    "glasses" to Preferences.SystemUI.StatusBar.IconTuner.GLASSES,
                    "car" to Preferences.SystemUI.StatusBar.IconTuner.CAR,
                    "camera" to Preferences.SystemUI.StatusBar.IconTuner.CAMERA,
                    "dist_compute" to Preferences.SystemUI.StatusBar.IconTuner.DIST_COMPUTE,
                    "headset" to Preferences.SystemUI.StatusBar.IconTuner.HEADSET,
                    IconSlots.ALARM_CLOCK to Preferences.SystemUI.StatusBar.IconTuner.ALARM_CLOCK,
                    IconSlots.ZEN to Preferences.SystemUI.StatusBar.IconTuner.ZEN,
                    IconSlots.VOLUME to Preferences.SystemUI.StatusBar.IconTuner.VOLUME,
                    "second_space" to Preferences.SystemUI.StatusBar.IconTuner.SECOND_SPACE,
                )
            )
            if (addCompoundIcon()) {
                COMPOUND_ICON_REAL_SLOTS.forEach {
                    put(it, Preferences.SystemUI.StatusBar.IconTuner.COMPOUND_ICON)
                }
            }
            if (addStackedMobile()) {
                put(IconSlots.STACKED_MOBILE_ICON, Preferences.SystemUI.StatusBar.StackedMobile.STACKED_MOBILE_ICON)
                put(IconSlots.STACKED_MOBILE_TYPE, Preferences.SystemUI.StatusBar.StackedMobile.STACKED_MOBILE_TYPE)
                put(IconSlots.SINGLE_MOBILE_SIM1, Preferences.SystemUI.StatusBar.StackedMobile.SINGLE_MOBILE_SIM1)
                put(IconSlots.SINGLE_MOBILE_SIM2, Preferences.SystemUI.StatusBar.StackedMobile.SINGLE_MOBILE_SIM2)
            }
        }
    }

    override fun onInit() {
        updateSelfState(true)
        attachLeftIfNeeded()
    }

    override fun onHook() {
        val statusBarBlockList = applyStaticBlockLists()
        hookSetBlockList()
        hookIconOrder(statusBarBlockList)
        hookUserUnlockRefresh()
    }

    private fun attachLeftIfNeeded() {
        if (leftContainer()) {
            attach(LeftContainer)
        }
    }

    private fun shouldCustomizeOrder(): Boolean {
        return iconPositionMode() != 0 || addCompoundIcon() || iconPositionAutoReorder()
    }

    private fun desiredSlotOrder(statusBarBlockList: List<String>): Array<String> {
        val slots = finalSlots()
        return if (iconPositionAutoReorder()) {
            val stackedMobileSlots = setOf(
                IconSlots.STACKED_MOBILE_TYPE,
                IconSlots.STACKED_MOBILE_ICON,
                IconSlots.SINGLE_MOBILE_SIM1,
                IconSlots.SINGLE_MOBILE_SIM2,
            )
            slots.sortedBy {
                (it !in statusBarBlockList) || (it in stackedMobileSlots)
            }.toTypedArray()
        } else {
            slots
        }
    }

    private fun hookIconOrder(statusBarBlockList: List<String>) {
        fun desired() = desiredSlotOrder(statusBarBlockList)
        val clzList = "com.android.systemui.statusbar.phone.ui.StatusBarIconList".toClassOrNull() ?: return
        val clzSlot = $$"com.android.systemui.statusbar.phone.ui.StatusBarIconList$Slot".toClassOrNull() ?: return
        val fldSlots = clzList.resolve().optional(true).firstFieldOrNull {
            name = "mSlots"
        }?.toTyped<java.util.ArrayList<Any?>>() ?: return
        val fldName = clzSlot.resolve().optional(true).firstFieldOrNull {
            name = "mName"
        }?.toTyped<String>() ?: return
        val ctorSlot = clzSlot.resolve().optional(true).firstConstructorOrNull {
            parameterCount = 1
        }?.self?.apply { isAccessible = true }

        fun reorder(listObj: Any?) {
            val slots = listObj?.let { fldSlots.get(it) } ?: return
            val byName = LinkedHashMap<String, Any?>()
            slots.forEach { slot ->
                val name = fldName.get(slot) ?: return@forEach
                byName.putIfAbsent(name, slot)
            }
            val reordered = ArrayList<Any?>(slots.size)
            desired().forEach { name ->
                byName.remove(name)?.let { reordered.add(it) }
            }
            reordered.addAll(byName.values)
            slots.clear()
            slots.addAll(reordered)
        }

        fun insertIndex(slots: java.util.ArrayList<Any?>, slotName: String): Int {
            val order = desired()
            val desiredIdx = order.indexOf(slotName)
            if (desiredIdx < 0) return slots.size
            var insertAt = 0
            slots.forEachIndexed { index, slot ->
                val name = fldName.get(slot) ?: return@forEachIndexed
                val idx = order.indexOf(name)
                if (idx in 0 until desiredIdx) {
                    insertAt = index + 1
                }
            }
            return insertAt
        }

        // OS3 钩的是 Array<String> 构造；OS4 构造被内联进 Dagger factory
        "com.android.systemui.statusbar.dagger.CentralSurfacesDependenciesModule_ProvideStatusBarIconListFactory"
            .toClassOrNull()
            ?.resolve()
            ?.optional(true)
            ?.firstMethodOrNull { name = "provideStatusBarIconList" }
            ?.hook {
                val list = proceed()
                if (shouldCustomizeOrder()) {
                    reorder(list)
                }
                result(list)
            }
        clzList.resolve().optional(true).firstMethodOrNull {
            name = "findOrInsertSlot"
        }?.hook {
            if (!shouldCustomizeOrder()) {
                return@hook result(proceed())
            }
            val slotName = getArg(0) as? String ?: return@hook result(proceed())
            val slots = fldSlots.get(thisObject) ?: return@hook result(proceed())
            val existing = slots.indexOfFirst { fldName.get(it) == slotName }
            if (existing >= 0) {
                return@hook result(existing)
            }
            val insertAt = insertIndex(slots, slotName)
            val newSlot = ctorSlot?.let { runCatching { it.newInstance(slotName) }.getOrNull() }
            if (newSlot != null) {
                slots.add(insertAt, newSlot)
                d { "insert tuned slot $slotName at $insertAt" }
                result(insertAt)
            } else {
                result(proceed())
            }
        }
    }

    private fun applyTuner(statusBarList: MutableList<String>, controlList: MutableList<String>) {
        tunerSlots().forEach { (slot, key) ->
            handleIcon(key, slot, statusBarList, controlList)
        }
        if (addStackedMobile()) {
            listOf("stacked_mobile", "mobile", IconSlots.DEMO_MOBILE).forEach { slot ->
                if (!statusBarList.contains(slot)) statusBarList.add(slot)
                if (!controlList.contains(slot)) controlList.add(slot)
            }
        }
        if (leftContainer()) {
            leftSlots().forEach {
                if (!statusBarList.contains(it)) statusBarList.add(it)
            }
        }
    }

    private fun overrideValue(key: PreferenceKey<Int>): Int {
        return when (key) {
            Preferences.SystemUI.StatusBar.StackedMobile.SINGLE_MOBILE_SIM1,
            Preferences.SystemUI.StatusBar.StackedMobile.SINGLE_MOBILE_SIM2 -> {
                val real = key.get()
                if (real == 0) 4 else real
            }
            else -> key.get()
        }
    }

    private fun hookSetBlockList() {
        val fldLocation = "com.android.systemui.statusbar.phone.ui.IconManager".toClassOrNull()
            ?.resolve()?.optional(true)?.firstFieldOrNull {
                name = "mLocation"
            }?.toTyped<Any>()
        "com.android.systemui.statusbar.phone.ui.IconManager".toClassOrNull()
            ?.resolve()?.optional(true)?.firstMethodOrNull {
                name = "setBlockList"
            }?.hook {
                val locName = runCatching {
                    val loc = fldLocation?.get(thisObject) ?: return@runCatching null
                    loc.javaClass.getMethod("name").invoke(loc) as? String
                }.getOrNull()
                // OS3：只有真正的控制中心用 CONTROL_CENTER_BLOCK_LIST。
                // QS_FAKE 是下拉过渡条，必须继续走 RIGHT_BLOCK_LIST，否则一滑就会露出状态栏已隐藏的图标。
                val qs = locName == "QS"
                val mutable = mutableBlockList(getArg(0) as? List<*>)
                val dummy = mutableListOf<String>()
                if (qs) {
                    applyTuner(dummy, mutable)
                } else {
                    applyTuner(mutable, dummy)
                }
                result(proceed(arrayOf(mutable)))
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutableBlockList(list: List<*>?): MutableList<String> {
        val strings = list?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
        if (list is MutableList<*> && list.size == strings.size) {
            return list as MutableList<String>
        }
        return strings
    }

    private fun handleIcon(key: PreferenceKey<Int>, name: String, statusBarList: MutableList<String>, controlList: MutableList<String>) {
        when (overrideValue(key)) {
            1 -> {
                if (statusBarList.contains(name)) statusBarList.remove(name)
                if (controlList.contains(name)) controlList.remove(name)
            }
            2 -> {
                if (statusBarList.contains(name)) statusBarList.remove(name)
                if (!controlList.contains(name)) controlList.add(name)
            }
            3 -> {
                if (!statusBarList.contains(name)) statusBarList.add(name)
                if (controlList.contains(name)) controlList.remove(name)
            }
            4 -> {
                if (!statusBarList.contains(name)) statusBarList.add(name)
                if (!controlList.contains(name)) controlList.add(name)
            }
            else -> return
        }
    }

    fun getLeftBlockList(allIcons: List<String>): List<String> {
        return allIcons.toMutableList().apply {
            removeAll(leftSlots())
            leftExtraBlockedSlots().split(',', ' ', '，').forEach {
                if (!contains(it)) {
                    add(it)
                }
            }
        }.toList()
    }

    private fun hookUserUnlockRefresh() {
        "android.app.Application".toClassOrNull()?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "attachBaseContext"
            parameters(Context::class)
            superclass()
        }?.hook {
            val ctx = getArg(0) as? Context
            val ori = proceed()
            if (ctx != null) {
                scheduleIconRefresh(ctx.applicationContext ?: ctx)
            }
            result(ori)
        }
    }

    private fun scheduleIconRefresh(ctx: Context) {
        if (!refreshScheduled.compareAndSet(false, true)) return
        val handler = Handler(Looper.getMainLooper())
        val run = Runnable { onUserUnlocked() }
        val unlocked = runCatching {
            ctx.getSystemService(UserManager::class.java)?.isUserUnlocked == true
        }.getOrDefault(false)
        if (unlocked) {
            handler.postDelayed(run, 1500)
        }
        runCatching {
            ctx.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        handler.postDelayed(run, 1500)
                    }
                },
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                Context.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun onUserUnlocked() {
        runCatching { RemotePreferences.reinit(module) }
        attachLeftIfNeeded()
        applyStaticBlockLists()
        StackedMobileIcon.onUserUnlocked()
        IconControllerCompat.refreshIconGroups()
        StackedMobileIcon.hideOfficialSlots()
        d { "reapplied status bar icons after user unlock" }
    }

    private fun applyStaticBlockLists(): MutableList<String> {
        val clzMiuiIconManagerUtils = "com.android.systemui.statusbar.phone.MiuiIconManagerUtils".toClassOrNull()
        val fldRightBlockList = clzMiuiIconManagerUtils?.resolve()?.optional(true)?.firstFieldOrNull {
            name = "RIGHT_BLOCK_LIST"
            modifiers(Modifiers.STATIC)
        }?.toTyped<List<String>>()
        val fldControlCenterBlockList = clzMiuiIconManagerUtils?.resolve()?.optional(true)?.firstFieldOrNull {
            name = "CONTROL_CENTER_BLOCK_LIST"
            modifiers(Modifiers.STATIC)
        }?.toTyped<List<String>>()
        val fldMiniRightBlockList = clzMiuiIconManagerUtils?.resolve()?.optional(true)?.firstFieldOrNull {
            name = "MINI_RIGHT_BLOCK_LIST"
            modifiers(Modifiers.STATIC)
        }?.toTyped<List<String>>()
        val statusBarBlockList = mutableBlockList(fldRightBlockList?.get(null))
        val controlCenterBlockList = mutableBlockList(fldControlCenterBlockList?.get(null))
        val miniRightBlockList = mutableBlockList(fldMiniRightBlockList?.get(null))
        applyTuner(statusBarBlockList, controlCenterBlockList)
        applyTuner(miniRightBlockList, mutableListOf())
        if (fldRightBlockList != null && fldRightBlockList.get(null) !== statusBarBlockList) {
            fldRightBlockList.set(null, statusBarBlockList)
        }
        if (fldControlCenterBlockList != null && fldControlCenterBlockList.get(null) !== controlCenterBlockList) {
            fldControlCenterBlockList.set(null, controlCenterBlockList)
        }
        if (fldMiniRightBlockList != null && fldMiniRightBlockList.get(null) !== miniRightBlockList) {
            fldMiniRightBlockList.set(null, miniRightBlockList)
        }
        return statusBarBlockList
    }
}
