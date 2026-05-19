package com.github.maskedkunisquat.projectecho.domain.model

import kotlin.random.Random

object TribeNameGenerator {

    private val adjectives = listOf(
        "Ashen", "Bone", "Cinder", "Crimson", "Dusk",
        "Ember", "Flint", "Gilded", "Hollow", "Iron",
        "Obsidian", "Pale", "Sable", "Salt", "Scarlet",
        "Silent", "Smoldering", "Stone", "Thorn", "Verdant",
    )

    private val epithets = listOf(
        "Blessed", "Born", "Bound", "Eaters", "Fallen",
        "Forged", "Keepers", "Kindled", "Kin", "Made",
        "Marked", "Risen", "Runners", "Scarred", "Singers",
        "Sworn", "Touched", "Walkers", "Watchers", "Woven",
    )

    fun generate(seed: Int): String {
        val rng = Random(seed)
        val adj = adjectives[rng.nextInt(adjectives.size)]
        val ep  = epithets[rng.nextInt(epithets.size)]
        return "The $adj-$ep"
    }
}
