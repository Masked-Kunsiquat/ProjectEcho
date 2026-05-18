package com.github.maskedkunisquat.projectecho

import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.feature.dashboard.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `applyDivineAction queues action consumed on next manual tick`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        try {
            val initialFavor = viewModel.worldState.value.divineFavor
            viewModel.applyDivineAction(DivineAction.CastRain)
            viewModel.triggerTick()
            assertEquals(initialFavor - DivineAction.CastRain.favorCost, viewModel.worldState.value.divineFavor)
            assertEquals(1L, viewModel.worldState.value.worldTimeTick)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `pending action is consumed after one tick - second tick has no action`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        try {
            val initialFavor = viewModel.worldState.value.divineFavor
            viewModel.applyDivineAction(DivineAction.CastRain)
            viewModel.triggerTick()  // consumes action, favor drops by favorCost
            viewModel.triggerTick()  // no pending action, favor unchanged
            assertEquals(initialFavor - DivineAction.CastRain.favorCost, viewModel.worldState.value.divineFavor)
            assertEquals(2L, viewModel.worldState.value.worldTimeTick)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `auto tick fires after 2 second delay`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        try {
            assertEquals(0L, viewModel.worldState.value.worldTimeTick)
            advanceTimeBy(2_001L)
            assertEquals(1L, viewModel.worldState.value.worldTimeTick)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
