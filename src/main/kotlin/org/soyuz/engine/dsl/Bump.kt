package org.soyuz.engine.dsl

import org.soyuz.engine.core.Application

fun bump(configure: BumpScope.() -> Unit) {
    Application.init()

    try {
        BumpScope().configure()
        Application.run()
    } finally {
        Application.close()
    }
}