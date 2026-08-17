package com.restaurant.sushimei.frontend.ui.admin.promotions

import com.restaurant.sushimei.frontend.data.api.ApiException
import com.restaurant.sushimei.frontend.data.model.Promotion
import com.restaurant.sushimei.frontend.data.model.PromotionBenefit
import com.restaurant.sushimei.frontend.data.model.PromotionSchedule
import com.restaurant.sushimei.frontend.data.model.PromotionTarget
import com.restaurant.sushimei.frontend.data.model.PromotionTargetType
import com.restaurant.sushimei.frontend.data.repository.IPromotionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PromotionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: IPromotionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches all promotion definitions from repository`() = runTest {
        val monday = fixedPricePromotion(id = 1L, name = "Lunes \$69")
        val thursday = bogoPromotion(id = 2L, name = "Jueves 2x1")
        coEvery { repository.getPromotions() } returns listOf(monday, thursday)

        val viewModel = PromotionsViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getPromotions() }
        assertEquals(listOf(monday, thursday), viewModel.uiState.value.promotions)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `saving existing promotion updates visible list with server response`() = runTest {
        val original = fixedPricePromotion(id = 1L, name = "Lunes \$69")
        val edited = original.copy(active = false)
        val saved = edited.copy(version = 2L)
        coEvery { repository.getPromotions() } returns listOf(original)
        coEvery { repository.updatePromotion(edited) } returns saved
        val viewModel = PromotionsViewModel(repository)
        advanceUntilIdle()

        viewModel.savePromotion(edited)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updatePromotion(edited) }
        assertEquals(listOf(saved), viewModel.uiState.value.promotions)
        assertTrue(viewModel.uiState.value.saveSuccess)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `saving promotion with zero id creates and adds server definition`() = runTest {
        val existing = fixedPricePromotion(id = 1L, name = "Lunes \$69")
        val draft = bogoPromotion(id = 0L, name = "Jueves 2x1")
        val created = draft.copy(id = 2L, version = 0L)
        coEvery { repository.getPromotions() } returns listOf(existing)
        coEvery { repository.createPromotion(draft) } returns created
        val viewModel = PromotionsViewModel(repository)
        advanceUntilIdle()

        viewModel.savePromotion(draft)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.createPromotion(draft) }
        assertEquals(listOf(existing, created), viewModel.uiState.value.promotions)
    }

    @Test
    fun `load failure exposes actionable error instead of empty state`() = runTest {
        coEvery { repository.getPromotions() } throws IllegalStateException("sin conexión")

        val viewModel = PromotionsViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            "Error al cargar promociones: sin conexión",
            viewModel.uiState.value.errorMessage
        )
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.promotions.isEmpty())
    }

    @Test
    fun `schedule conflict keeps server explanation and diagnostic reference`() = runTest {
        val monday = fixedPricePromotion(id = 1L, name = "Lunes \$69")
        coEvery { repository.getPromotions() } returns listOf(monday)
        coEvery { repository.updatePromotion(monday) } throws ApiException(
            code = "PROMOTION_SCHEDULE_CONFLICT",
            message = "Otra promoción activa coincide en días y productos.",
            httpStatus = 409,
            requestId = "abcd1234-1111-2222-3333-444444444444"
        )
        val viewModel = PromotionsViewModel(repository)
        advanceUntilIdle()

        viewModel.savePromotion(monday)
        advanceUntilIdle()

        assertEquals(
            "Otra promoción activa coincide en días y productos. (Ref. abcd1234)",
            viewModel.uiState.value.errorMessage
        )
        assertFalse(viewModel.uiState.value.saveSuccess)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    private fun fixedPricePromotion(id: Long, name: String) = Promotion(
        id = id,
        name = name,
        active = true,
        priority = 100,
        schedule = PromotionSchedule(daysOfWeek = setOf(1), allDay = true),
        targets = listOf(PromotionTarget(PromotionTargetType.TAG, 10L, "Rollos")),
        benefit = PromotionBenefit.FixedUnitPrice(BigDecimal("69.00")),
        version = 1L
    )

    private fun bogoPromotion(id: Long, name: String) = Promotion(
        id = id,
        name = name,
        active = true,
        priority = 90,
        schedule = PromotionSchedule(daysOfWeek = setOf(4), allDay = true),
        targets = listOf(PromotionTarget(PromotionTargetType.TAG, 10L, "Rollos")),
        benefit = PromotionBenefit.BuyXGetY(type = "BUY_X_GET_Y_SAME_ITEM",
            buyQuantity = 1,
            rewardQuantity = 1,
            repeat = true
        ),
        version = 1L
    )
}
