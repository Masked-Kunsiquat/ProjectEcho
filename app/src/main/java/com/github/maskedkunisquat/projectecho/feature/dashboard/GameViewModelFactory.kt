package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.maskedkunisquat.projectecho.domain.repository.WorldStateRepository

class GameViewModelFactory(
    private val repository: WorldStateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(GameViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(repository) as T
    }
}
