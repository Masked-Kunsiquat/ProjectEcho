package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.WorldState

object NarrativeResolver {
    fun resolve(template: String, state: WorldState): String {
        val tribe = state.tribes.values.firstOrNull()
        return template
            .replace("{{tribeName}}", tribe?.name ?: "the tribe")
            .replace("{{population}}", tribe?.population?.toString() ?: "?")
            .replace("{{foodSupply}}", tribe?.foodSupply?.toString() ?: "?")
            .replace("{{tick}}", state.worldTimeTick.toString())
    }
}
