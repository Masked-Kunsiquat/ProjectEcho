package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.maskedkunisquat.projectecho.domain.repository.WorldStateRepository

class GameViewModelFactory(
    private val repository: WorldStateRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GameViewModel(repository) as T
}
