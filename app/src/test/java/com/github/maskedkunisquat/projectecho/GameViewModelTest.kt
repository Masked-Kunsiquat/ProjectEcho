package com.github.maskedkunisquat.projectecho

import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.repository.WorldStateRepository
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

private class FakeWorldStateRepository : WorldStateRepository {
    override suspend fun save(state: WorldState) = Unit
    override suspend fun load(): WorldState? = null
}

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
        val viewModel = GameViewModel(FakeWorldStateRepository(), testDispatcher)
        try {
            val initialFavor = viewModel.worldState.value.divineFavor
            viewModel.applyDivineAction(DivineAction.CastRain)
            viewModel.triggerTick()
            // CastRain costs 10; devotion stays ≥ 40 so +1 regen fires on the same tick
            assertEquals(initialFavor - DivineAction.CastRain.favorCost + 1, viewModel.worldState.value.divineFavor)
            assertEquals(1L, viewModel.worldState.value.worldTimeTick)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `pending action is consumed after one tick - second tick has no action`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(FakeWorldStateRepository(), testDispatcher)
        try {
            val initialFavor = viewModel.worldState.value.divineFavor
            viewModel.applyDivineAction(DivineAction.CastRain)
            viewModel.triggerTick()  // consumes action: -10 favor, +1 regen
            viewModel.triggerTick()  // no action: +1 regen only (proves CastRain was consumed once)
            // Net: -10 (one cost) + 2 (two regen ticks)
            assertEquals(initialFavor - DivineAction.CastRain.favorCost + 2, viewModel.worldState.value.divineFavor)
            assertEquals(2L, viewModel.worldState.value.worldTimeTick)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `auto tick fires after 2 second delay`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(FakeWorldStateRepository(), testDispatcher)
        try {
            assertEquals(0L, viewModel.worldState.value.worldTimeTick)
            advanceTimeBy(2_001L)
            assertEquals(1L, viewModel.worldState.value.worldTimeTick)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}