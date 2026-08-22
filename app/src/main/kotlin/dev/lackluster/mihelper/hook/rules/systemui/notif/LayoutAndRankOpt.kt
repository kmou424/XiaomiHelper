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

package dev.lackluster.mihelper.hook.rules.systemui.notif

import android.service.notification.StatusBarNotification
import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.data.preference.Preferences
import dev.lackluster.mihelper.hook.base.StaticHooker
import dev.lackluster.mihelper.hook.rules.systemui.compat.hookAfterConstructed
import dev.lackluster.mihelper.hook.utils.RemotePreferences.get
import dev.lackluster.mihelper.hook.utils.RemotePreferences.lazyGet
import dev.lackluster.mihelper.hook.utils.extraOf
import dev.lackluster.mihelper.hook.utils.toTyped

object LayoutAndRankOpt : StaticHooker() {
    private var Any.priority by extraOf("KEY_PRIORITY", 0)
    private var Any.peopleType by extraOf("KEY_PEOPLE_TYPE", 0)

    private const val VAL_PEOPLE_ALERTING = 2
    private const val VAL_PRIORITY_PEOPLE = 1

    private val hideSectionHeader by Preferences.SystemUI.NotifCenter.LR_OPT_HIDE_SECTION_HEADER.lazyGet()
    private val hideSectionGap by Preferences.SystemUI.NotifCenter.LR_OPT_HIDE_SECTION_GAP.lazyGet()
    private val rerank by Preferences.SystemUI.NotifCenter.LR_OPT_RERANK.lazyGet()

    private val clzPipelineEntry by "com.android.systemui.statusbar.notification.collection.PipelineEntry".lazyClassOrNull()
    private val clzListEntry by "com.android.systemui.statusbar.notification.collection.ListEntry".lazyClassOrNull()
    private val metGetPeopleType by lazy {
        "com.android.systemui.statusbar.notification.collection.coordinator.ConversationCoordinator".toClassOrNull()
            ?.resolve()?.optional(true)?.firstMethodOrNull {
                name = "getPeopleType"
            }?.toTyped<Int>()
    }
    private val asListEntry by lazy {
        clzPipelineEntry?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "asListEntry"
        }?.toTyped<Any>()
    }
    private val getRepresentativeEntry by lazy {
        clzListEntry?.resolve()?.optional(true)?.firstMethodOrNull {
            name = "getRepresentativeEntry"
        }?.toTyped<Any>()
            ?: clzPipelineEntry?.resolve()?.optional(true)?.firstMethodOrNull {
                name = "getRepresentativeEntry"
            }?.toTyped<Any>()
    }
    private val mSbn by lazy {
        "com.android.systemui.statusbar.notification.collection.NotificationEntry".toClassOrNull()
            ?.resolve()?.optional(true)?.firstFieldOrNull {
                name = "mSbn"
            }?.toTyped<StatusBarNotification>()
    }
    private val mIsFocusNotification by lazy {
        "com.android.systemui.statusbar.notification.ExpandedNotification".toClassOrNull()
            ?.resolve()?.optional(true)?.firstFieldOrNull {
                name = "mIsFocusNotification"
            }?.toTyped<Boolean>()
    }
    private val mIsSystemApp by lazy {
        "com.android.systemui.statusbar.notification.ExpandedNotification".toClassOrNull()
            ?.resolve()?.optional(true)?.firstFieldOrNull {
                name = "mIsSystemApp"
            }?.toTyped<Boolean>()
    }

    override fun onInit() {
        updateSelfState(Preferences.SystemUI.NotifCenter.ENABLE_LAYOUT_RANK_OPT.get())
    }

    override fun onHook() {
        if (hideSectionHeader) {
            "com.android.systemui.statusbar.notification.collection.provider.SectionHeaderVisibilityProvider".toClassOrNull()?.apply {
                val neverShowSectionHeaders = resolve().optional(true).firstFieldOrNull {
                    name = "neverShowSectionHeaders"
                }?.toTyped<Boolean>()
                val sectionHeadersVisible = resolve().optional(true).firstFieldOrNull {
                    name = "sectionHeadersVisible"
                }?.toTyped<Boolean>()
                hookAfterConstructed(this) { provider ->
                    neverShowSectionHeaders?.set(provider, true)
                    sectionHeadersVisible?.set(provider, true)
                }
            }
            $$"com.android.systemui.statusbar.notification.collection.coordinator.MiuiNotifCoordinator$trackNotifUnoccludedState$1$1".toClassOrNull()?.apply {
                resolve().optional(true).firstMethodOrNull {
                    name = "emit"
                }?.hook {
                    val newArgs = args.toTypedArray()
                    newArgs[0] = true
                    result(proceed(newArgs))
                }
            }
        }
        if (hideSectionGap) {
            "com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm".toClassOrNull()?.apply {
                val gapFields = listOf(
                    "mGapHeight",
                    "mGapHeightOnLockscreen",
                    "mBundleGapHeight",
                    "mBundleExpandedGapHeight",
                    "mGroupingDisabledSectionGapHeight",
                ).mapNotNull { name ->
                    resolve().optional(true).firstFieldOrNull { this.name = name }?.toTyped<Float>()
                }
                val zeroGaps: (Any?) -> Unit = { algo ->
                    if (algo != null) {
                        gapFields.forEach { it.set(algo, 0.0f) }
                    }
                }
                hookAfterConstructed(this) { algo -> zeroGaps(algo) }
                resolve().optional(true).firstMethodOrNull { name = "initView" }?.hook {
                    val ori = proceed()
                    zeroGaps(thisObject)
                    result(ori)
                }
                resolve().optional(true).firstMethodOrNull { name = "getGapForLocation" }?.hook {
                    result(0.0f)
                }
                resolve().optional(true).firstMethodOrNull { name = "getGapHeightForChild" }?.hook {
                    result(0.0f)
                }
            }
        }
        if (rerank) {
            $$"com.android.systemui.statusbar.notification.collection.coordinator.ConversationCoordinator$peopleAlertingSectioner$1".toClassOrNull()?.apply {
                val type = resolve().optional(true).firstFieldOrNull {
                    type(Int::class)
                }?.toTyped<Int>()
                val conversationCoordinator = resolve().optional(true).firstFieldOrNull {
                    type("com.android.systemui.statusbar.notification.collection.coordinator.ConversationCoordinator")
                }?.toTyped<Any>()
                resolve().optional(true).firstMethodOrNull {
                    name = "isInSection"
                }?.hook {
                    val ori = proceed()
                    val pipelineEntry = getArg(0)
                    val isConversation = ori as? Boolean ?: false
                    if (isConversation) {
                        val nowType = type?.get(thisObject) ?: 0
                        val coordinator = conversationCoordinator?.get(thisObject)
                        val peopleType = if (coordinator != null) {
                            metGetPeopleType?.invoke(coordinator, pipelineEntry) ?: 0
                        } else {
                            0
                        }
                        pipelineEntry?.priority = if (nowType == 1) VAL_PRIORITY_PEOPLE else VAL_PEOPLE_ALERTING
                        pipelineEntry?.peopleType = peopleType
                        result(false)
                    } else {
                        result(ori)
                    }
                }
            }
            $$"com.android.systemui.statusbar.notification.collection.legacy.NotificationRankingManagerInjectorImplKt$miuiRankingComparator$1".toClassOrNull()?.apply {
                resolve().optional(true).firstMethodOrNull {
                    name = "compare"
                }?.hook {
                    val notification1 = sbnOf(getArg(0))
                    val notification2 = sbnOf(getArg(1))
                    val tail1 = notification1?.notification?.extras?.getBoolean("miui.showAtTail", false)
                    val tail2 = notification2?.notification?.extras?.getBoolean("miui.showAtTail", false)
                    val isSystemWarning1 = isSystemWarning(notification1)
                    val isSystemWarning2 = isSystemWarning(notification2)
                    val isFocusNotification1 = isFocusNotification(notification1)
                    val isFocusNotification2 = isFocusNotification(notification2)
                    val priorityMessage1 = priorityMessage(getArg(0))
                    val priorityMessage2 = priorityMessage(getArg(0))
                    result(
                        if (tail1 != tail2) {
                            if (tail2 == true) -1 else 1
                        } else if (isSystemWarning1 != isSystemWarning2) {
                            if (isSystemWarning1) -1 else 1
                        } else if (isFocusNotification1 != isFocusNotification2) {
                            if (isFocusNotification1) -1 else 1
                        } else if (priorityMessage1 != priorityMessage2) {
                            if (priorityMessage1 > priorityMessage2) -1 else 1
                        } else {
                            0
                        }
                    )
                }
            }
        }
    }

    private fun sbnOf(pipelineEntry: Any?): StatusBarNotification? {
        if (pipelineEntry == null) return null
        val listEntry = asListEntry?.invoke(pipelineEntry) ?: pipelineEntry
        val notificationEntry = getRepresentativeEntry?.invoke(listEntry) ?: return null
        return mSbn?.get(notificationEntry)
    }

    private fun isSystemWarning(notification: Any?): Boolean {
        return notification is StatusBarNotification && mIsSystemApp?.get(notification) == true &&
                notification.notification?.extras?.getBoolean("miui.systemWarnings", false) == true
    }

    private fun isFocusNotification(notification: Any?): Boolean {
        return notification != null && mIsFocusNotification?.get(notification) == true
    }

    private fun priorityMessage(pipelineEntry: Any?): Int {
        val priority =
            if (pipelineEntry == null) {
                0
            } else {
                pipelineEntry.priority ?: 0
            }
        val peopleType =
            if (pipelineEntry == null) {
                0
            } else {
                pipelineEntry.peopleType ?: 0
            }
        return priority * 10000 + peopleType
    }
}