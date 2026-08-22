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

package dev.lackluster.mihelper.hook.rules.systemui.compat

import com.highcapable.kavaref.KavaRef.Companion.resolve
import dev.lackluster.mihelper.hook.base.BaseHooker
import dev.lackluster.mihelper.hook.base.HookScope
import dev.lackluster.mihelper.hook.utils.d
import dev.lackluster.mihelper.hook.utils.firstConstructorOptional
import dev.lackluster.mihelper.hook.utils.firstMethodOptional

/**
 * HyperOS 4 / Android 17 SystemUI is frequently built with R8 constructor
 * inlining. `firstConstructor()` then throws; `hookAllConstructors` matches
 * nothing. Prefer the real constructor, otherwise hook a later bind method
 * and locate the target instance among the arguments.
 */
internal fun BaseHooker.hookAfterConstructed(
    target: Class<*>,
    fallbackClassNames: List<String> = emptyList(),
    fallbackMethodNames: List<String> = listOf("bind", "constructAndBind"),
    after: HookScope.(instance: Any) -> Unit
) {
    val ctor = target.resolve().firstConstructorOptional()
    if (ctor != null) {
        ctor.hook {
            val ori = proceed()
            after(thisObject)
            result(ori)
        }
        return
    }

    d { "${target.name} has no constructor (likely R8 inlined on OS4/A17), falling back to ${fallbackMethodNames.joinToString()}" }

    val classes = buildList {
        add(target)
        fallbackClassNames.mapNotNullTo(this) { it.toClassOrNull() }
    }

    var hooked = false
    classes.forEach { clazz ->
        val scope = clazz.resolve()
        fallbackMethodNames.forEach { methodName ->
            scope.firstMethodOptional(methodName)?.let { method ->
                hooked = true
                method.hook {
                    val instance = args.firstOrNull { target.isInstance(it) }
                        ?: thisObject.takeIf { target.isInstance(it) }
                    if (instance != null) {
                        after(instance)
                    }
                    result(proceed())
                }
            }
        }
    }

    if (!hooked) {
        d { "${target.name}: no constructor and no fallback method found" }
    }
}
