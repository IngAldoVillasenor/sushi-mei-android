package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.restaurant.sushimei.frontend.data.model.ConfiguredProduct
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.data.model.FulfillmentType
import com.restaurant.sushimei.frontend.data.model.PaymentMethod
import com.restaurant.sushimei.frontend.data.model.Promotion
import com.restaurant.sushimei.frontend.data.model.PromotionBenefit
import com.restaurant.sushimei.frontend.data.local.provideMenuRepository
import com.restaurant.sushimei.frontend.data.local.provideManualPosOrderRepository
import com.restaurant.sushimei.frontend.data.local.providePromotionRepository
import com.restaurant.sushimei.frontend.data.local.PosTicketPrintTracker
import com.restaurant.sushimei.frontend.data.api.NetworkModule
import com.restaurant.sushimei.frontend.PrintService
import com.restaurant.sushimei.frontend.ui.pos.PosViewModel
import com.restaurant.sushimei.frontend.ui.pos.PosUiState
import com.restaurant.sushimei.frontend.ui.pos.CheckoutState
import com.restaurant.sushimei.frontend.ui.pos.QuoteState
import java.math.BigDecimal
import java.util.Locale
import com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorScreen
import com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PromotionConfigurationFlow(
    val promotion: Promotion,
    val menuItem: MenuItem,
    val purchasedProduct: ConfiguredProduct? = null,
    val rewardProducts: List<ConfiguredProduct> = emptyList()
)

private sealed interface PosPrintState {
    data object Idle : PosPrintState
    data object Printing : PosPrintState
    data object Printed : PosPrintState
    data class Failed(val message: String) : PosPrintState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen() {
    val context = LocalContext.current
    val printTracker = remember { PosTicketPrintTracker(context) }
    val menuRepository = remember { provideMenuRepository(context) }
    val viewModel: PosViewModel = viewModel(
        factory = PosViewModel.factory(
            menuRepository = menuRepository,
            manualPosOrderRepository = provideManualPosOrderRepository(context),
            promotionRepository = providePromotionRepository(context)
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stateSuccess = uiState as? PosUiState.Success

    val selectedCategory = stateSuccess?.selectedCategory
    val cart = stateSuccess?.currentCart ?: emptyList()
    val quoteState = stateSuccess?.quoteState ?: QuoteState.Idle
    val checkoutState = stateSuccess?.checkoutState ?: CheckoutState.Idle
    val pricingPreview = if (quoteState is QuoteState.Valid) quoteState.preview else com.restaurant.sushimei.frontend.data.model.OrderPricingPreview(BigDecimal.ZERO, emptyList(), emptyList(), BigDecimal.ZERO)

    val filteredMenuItems = stateSuccess?.filteredProducts ?: emptyList()
    val categories = stateSuccess?.categories ?: listOf("Todos")
    val activePromotions = stateSuccess?.activePromotions ?: emptyList()
    val promotionLoadError = stateSuccess?.promotionLoadError

    var showSuccessDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var selectedPromotion by remember { mutableStateOf<Promotion?>(null) }
    var promotionConfigurationFlow by remember { mutableStateOf<PromotionConfigurationFlow?>(null) }
    var configuringItemId by remember { mutableStateOf<Long?>(null) }
    var printState by remember { mutableStateOf<PosPrintState>(PosPrintState.Idle) }
    var printRetry by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refreshActivePromotions()
    }

    LaunchedEffect(checkoutState) {
        if (checkoutState is CheckoutState.Success) {
            showCheckoutDialog = false
            showSuccessDialog = true
        }
    }

    LaunchedEffect(checkoutState, printRetry) {
        val success = checkoutState as? CheckoutState.Success ?: return@LaunchedEffect
        val order = success.response
        if (printTracker.wasPrinted(order.requestId)) {
            printState = PosPrintState.Printed
            return@LaunchedEffect
        }

        printState = PosPrintState.Printing
        try {
            val detailResponse = NetworkModule.sushiMeiApi.getOperationalOrderDetail(order.id)
            val detail = detailResponse.body()
            if (!detailResponse.isSuccessful || detail == null) {
                printState = PosPrintState.Failed("No se pudo cargar el ticket confirmado.")
                return@LaunchedEffect
            }

            val printed = withContext(Dispatchers.IO) {
                PrintService(context).printOperationalTicket(detail)
            }
            if (printed) {
                printTracker.markPrinted(order.requestId)
                printState = PosPrintState.Printed
            } else {
                printState = PosPrintState.Failed("Revisa Bluetooth, el permiso y la impresora emparejada.")
            }
        } catch (_: Exception) {
            printState = PosPrintState.Failed("No se pudo imprimir el ticket. Revisa la conexión e intenta de nuevo.")
        }
    }

    if (showSuccessDialog && checkoutState is CheckoutState.Success) {
        AlertDialog(
            onDismissRequest = {
                if (printState != PosPrintState.Printing) {
                    showSuccessDialog = false
                    viewModel.resetCheckoutState()
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "¡Orden Confirmada!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = buildString {
                        append("Orden #${checkoutState.response.id} procesada y enviada a Cocinando.\n")
                        append("Total: $${String.format(Locale.US, "%.2f", checkoutState.response.total)} MXN\n\n")
                        append(
                            when (val currentPrintState = printState) {
                                PosPrintState.Idle, PosPrintState.Printing -> "Imprimiendo ticket…"
                                PosPrintState.Printed -> "Ticket impreso."
                                is PosPrintState.Failed -> currentPrintState.message
                            }
                        )
                    },
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (printState is PosPrintState.Failed) {
                            printRetry += 1
                        } else {
                            showSuccessDialog = false
                            viewModel.resetCheckoutState()
                        }
                    },
                    enabled = printState != PosPrintState.Printing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(if (printState is PosPrintState.Failed) "Reintentar impresión" else "Aceptar")
                }
            },
            dismissButton = {
                if (printState is PosPrintState.Failed) {
                    TextButton(onClick = {
                        showSuccessDialog = false
                        viewModel.resetCheckoutState()
                    }) {
                        Text("Cerrar")
                    }
                }
            }
        )
    }

    if (showCheckoutDialog && stateSuccess != null) {
        CheckoutDialog(
            viewModel = viewModel,
            uiState = stateSuccess,
            onDismiss = { showCheckoutDialog = false }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // LADO IZQUIERDO: 70% Catálogo de Productos
        // ==========================================
        Column(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🍣 Punto de Venta",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${filteredMenuItems.size} productos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ActivePromotionsSection(
                promotions = activePromotions,
                errorMessage = promotionLoadError,
                onPromotionClick = { selectedPromotion = it },
                onRetry = viewModel::refreshActivePromotions
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                items(categories) { category ->
                    val isSelected = (selectedCategory ?: "Todos") == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category) },
                        label = {
                            Text(
                                text = category,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMenuItems, key = { it.id }) { item ->
                    val cartQuantity = cart.filter { it.menuItemId == item.id }.sumOf { it.quantity }
                    MenuItemCard(
                        menuItem = item,
                        cartQuantity = cartQuantity,
                        onAddToCart = {
                            if (item.requiresConfiguration) {
                                configuringItemId = item.id
                            } else {
                                viewModel.addToCart(item)
                            }
                        }
                    )
                }
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )

        // ==========================================
        // LADO DERECHO: 30% Ticket en Vivo
        // ==========================================
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Orden Actual",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (cart.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearCart() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Vaciar Carrito",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

            if (cart.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🛒", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "El carrito está vacío",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Toca un producto para agregar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cart, key = { it.id }) { configuredProduct ->
                        CartItemRow(
                            configuredProduct = configuredProduct,
                            onIncrement = {
                                viewModel.incrementCartItem(configuredProduct) {
                                    configuringItemId = configuredProduct.menuItemId
                                }
                            },
                            onDecrement = { viewModel.removeFromCart(configuredProduct) },
                            onDelete = { viewModel.deleteFromCart(configuredProduct) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            val isCobrarEnabled = cart.isNotEmpty() && quoteState is QuoteState.Valid && checkoutState !is CheckoutState.Loading

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Subtotal:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", pricingPreview.subtotal)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    pricingPreview.adjustments.forEach { adjustment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = adjustment.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC62828)
                            )
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", adjustment.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                    }

                    if (pricingPreview.adjustments.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (quoteState is QuoteState.Error) {
                        Text(
                            text = quoteState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cotización Total:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", pricingPreview.total)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Button(
                onClick = { showCheckoutDialog = true },
                enabled = isCobrarEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PointOfSale,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Cobrar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    selectedPromotion?.let { promotion ->
        PromotionItemPickerDialog(
            promotion = promotion,
            eligibleProducts = viewModel.eligibleProducts(promotion),
            onDismiss = { selectedPromotion = null },
            onSelect = { item ->
                selectedPromotion = null
                if (item.requiresConfiguration) {
                    promotionConfigurationFlow = PromotionConfigurationFlow(
                        promotion = promotion,
                        menuItem = item
                    )
                } else {
                    viewModel.addPromotionBundle(promotion, item)
                }
            }
        )
    }

    promotionConfigurationFlow?.let { flow ->
        val bogo = flow.promotion.benefit as? PromotionBenefit.BuyXGetYSameItem
        val configuringReward = flow.purchasedProduct != null && bogo != null
        val rewardOrdinal = flow.rewardProducts.size + 1
        val contextLabel = if (configuringReward) {
            "${flow.promotion.name}: configura el roll gratis $rewardOrdinal/${bogo?.rewardQuantity}"
        } else if (bogo != null) {
            "${flow.promotion.name}: configura el roll comprado"
        } else {
            "${flow.promotion.name}: configura el roll de la promoción"
        }
        val actionLabel = when {
            configuringReward -> "Guardar roll gratis"
            bogo != null -> "Continuar al roll gratis"
            else -> "Agregar promoción"
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { promotionConfigurationFlow = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.9f)
                    .clip(MaterialTheme.shapes.large),
                color = MaterialTheme.colorScheme.background
            ) {
                val stageKey = if (configuringReward) "reward-$rewardOrdinal" else "purchase"
                val configViewModel: ConfiguratorViewModel = viewModel(
                    key = "promotion-${flow.promotion.id}-${flow.menuItem.id}-$stageKey",
                    factory = ConfiguratorViewModel.factory(menuRepository)
                )

                ConfiguratorScreen(
                    menuItemId = flow.menuItem.id,
                    viewModel = configViewModel,
                    onDismiss = { promotionConfigurationFlow = null },
                    contextLabel = contextLabel,
                    actionLabel = actionLabel,
                    onAddToCart = { configuredProduct ->
                        if (flow.purchasedProduct == null) {
                            if (bogo == null || bogo.rewardQuantity == 0) {
                                viewModel.addPromotionBundle(
                                    promotion = flow.promotion,
                                    menuItem = flow.menuItem,
                                    purchasedProduct = configuredProduct
                                )
                                promotionConfigurationFlow = null
                            } else {
                                promotionConfigurationFlow = flow.copy(purchasedProduct = configuredProduct)
                            }
                        } else {
                            val rewards = flow.rewardProducts + configuredProduct
                            if (rewards.size >= bogo!!.rewardQuantity) {
                                viewModel.addPromotionBundle(
                                    promotion = flow.promotion,
                                    menuItem = flow.menuItem,
                                    purchasedProduct = flow.purchasedProduct,
                                    rewardProducts = rewards
                                )
                                promotionConfigurationFlow = null
                            } else {
                                promotionConfigurationFlow = flow.copy(rewardProducts = rewards)
                            }
                        }
                    }
                )
            }
        }
    }

    configuringItemId?.let { itemId ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { configuringItemId = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.9f)
                    .clip(MaterialTheme.shapes.large),
                color = MaterialTheme.colorScheme.background
            ) {
                val configViewModel: com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorViewModel =
                    viewModel(
                        key = "pos-configurator-$itemId",
                        factory = com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorViewModel.factory(menuRepository)
                    )

                com.restaurant.sushimei.frontend.ui.pos.configurator.ConfiguratorScreen(
                    menuItemId = itemId,
                    viewModel = configViewModel,
                    onDismiss = { configuringItemId = null },
                    onAddToCart = { configuredProduct ->
                        viewModel.addConfiguredProduct(configuredProduct)
                        configuringItemId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ActivePromotionsSection(
    promotions: List<Promotion>,
    errorMessage: String?,
    onPromotionClick: (Promotion) -> Unit,
    onRetry: () -> Unit
) {
    if (promotions.isEmpty() && errorMessage == null) return

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = "Promociones",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (errorMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(promotions, key = { it.id }) { promotion ->
                    Card(
                        onClick = { onPromotionClick(promotion) },
                        modifier = Modifier.width(230.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = promotion.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = promotionBenefitLabel(promotion),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = promotionDayLabel(promotion),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionItemPickerDialog(
    promotion: Promotion,
    eligibleProducts: List<MenuItem>,
    onDismiss: () -> Unit,
    onSelect: (MenuItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(promotion.name) },
        text = {
            Column {
                Text(
                    text = "Selecciona el roll real para esta promoción. El backend confirmará la regla y el precio.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (eligibleProducts.isEmpty()) {
                    Text(
                        text = "No hay productos elegibles disponibles en el catálogo.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(eligibleProducts, key = { it.id }) { item ->
                            Card(
                                onClick = { onSelect(item) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.nombre, fontWeight = FontWeight.Bold)
                                        if (item.requiresConfiguration) {
                                            Text(
                                                "Requiere configuración",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text("$${String.format(Locale.US, "%.2f", item.precio)}")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun promotionBenefitLabel(promotion: Promotion): String {
    return when (val benefit = promotion.benefit) {
        is PromotionBenefit.FixedUnitPrice -> "$${benefit.amount} por roll"
        is PromotionBenefit.BuyXGetYSameItem ->
            "Compra ${benefit.buyQuantity}, recibe ${benefit.rewardQuantity} gratis"
    }
}

private fun promotionDayLabel(promotion: Promotion): String {
    val names = mapOf(
        1 to "Lunes",
        2 to "Martes",
        3 to "Miércoles",
        4 to "Jueves",
        5 to "Viernes",
        6 to "Sábado",
        7 to "Domingo"
    )
    return promotion.schedule.daysOfWeek.sorted().joinToString(", ") { names[it] ?: "Día $it" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutDialog(
    viewModel: PosViewModel,
    uiState: PosUiState.Success,
    onDismiss: () -> Unit
) {
    val total = if (uiState.quoteState is QuoteState.Valid) uiState.quoteState.preview.total else BigDecimal.ZERO

    // Derived values directly from UI state
    val type = uiState.fulfillmentType
    val method = uiState.paymentMethod
    val pickup = uiState.pickupName ?: ""
    val address = uiState.deliveryAddress ?: ""
    val denom = uiState.cashDenomination?.toString() ?: ""
    val isLoading = uiState.checkoutState is CheckoutState.Loading

    // Client side validation matching ViewModel
    val isPickupValid = type != FulfillmentType.PICKUP || (pickup.isNotBlank() && pickup.trim().length in 2..120)
    val isDeliveryValid = type != FulfillmentType.DELIVERY || (address.isNotBlank() && address.trim().length in 5..500)
    val isCashValid = method != PaymentMethod.CASH || (uiState.cashDenomination != null && uiState.cashDenomination > BigDecimal.ZERO)
    val isCardValid = method != PaymentMethod.CARD || type == FulfillmentType.PICKUP
    val isValid = isPickupValid && isDeliveryValid && isCashValid && isCardValid

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Confirmar Orden") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.checkoutState is CheckoutState.Error) {
                    Text(
                        text = uiState.checkoutState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Text("Método de Entrega", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == FulfillmentType.PICKUP,
                        onClick = { viewModel.updateFulfillmentType(FulfillmentType.PICKUP) },
                        label = { Text("Para Llevar") },
                        enabled = !isLoading
                    )
                    FilterChip(
                        selected = type == FulfillmentType.DELIVERY,
                        onClick = { viewModel.updateFulfillmentType(FulfillmentType.DELIVERY) },
                        label = { Text("A Domicilio") },
                        enabled = !isLoading
                    )
                }

                if (type == FulfillmentType.PICKUP) {
                    OutlinedTextField(
                        value = pickup,
                        onValueChange = { viewModel.updatePickupName(it) },
                        label = { Text("Nombre de quien recoge") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        enabled = !isLoading
                    )
                } else {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { viewModel.updateDeliveryAddress(it) },
                        label = { Text("Dirección de entrega") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        enabled = !isLoading
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Método de Pago", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = method == PaymentMethod.CASH,
                        onClick = { viewModel.updatePaymentMethod(PaymentMethod.CASH) },
                        label = { Text("Efectivo") },
                        enabled = !isLoading
                    )
                    if (type == FulfillmentType.PICKUP) {
                        FilterChip(
                            selected = method == PaymentMethod.CARD,
                            onClick = { viewModel.updatePaymentMethod(PaymentMethod.CARD) },
                            label = { Text("Tarjeta") },
                            enabled = !isLoading
                        )
                    }
                    FilterChip(
                        selected = method == PaymentMethod.TRANSFER,
                        onClick = { viewModel.updatePaymentMethod(PaymentMethod.TRANSFER) },
                        label = { Text("Transferencia") },
                        enabled = !isLoading
                    )
                }

                if (method == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = denom,
                        onValueChange = {
                            val bd = it.toBigDecimalOrNull()
                            viewModel.updateCashDenomination(bd)
                        },
                        label = { Text("Denominación (Efectivo a pagar)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        enabled = !isLoading
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Total Estimado: $${String.format(Locale.US, "%.2f", total)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.cobrarOrden() },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Confirmar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun MenuItemCard(
    menuItem: MenuItem,
    cartQuantity: Int,
    onAddToCart: () -> Unit
) {
    Card(
        onClick = onAddToCart,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = menuItem.emoji,
                        fontSize = 36.sp
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val priceText = if (menuItem.requiresConfiguration && menuItem.pricingMode == com.restaurant.sushimei.frontend.data.model.ItemPricingMode.SELECTION_SUM) {
                            "Según selección"
                        } else {
                            "$${String.format(Locale.US, "%.2f", menuItem.precio)}"
                        }
                        Text(
                            text = priceText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = menuItem.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = menuItem.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(36.dp)
                )
            }

            if (cartQuantity > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "x$cartQuantity",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    configuredProduct: ConfiguredProduct,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = configuredProduct.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (configuredProduct.promotionSelection == null) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", configuredProduct.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Total cotizado abajo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            configuredProduct.promotionSelection?.let { selection ->
                Text(
                    text = selection.promotionName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val promotionSelection = configuredProduct.promotionSelection
                Text(
                    text = if (promotionSelection == null) {
                        "$${String.format(Locale.US, "%.2f", configuredProduct.unitTotal)} c/u"
                    } else {
                        "Compra ${configuredProduct.quantity} · ${promotionSelection.rewardConfigurations.size} gratis"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (promotionSelection == null) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Disminuir",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "${configuredProduct.quantity}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = onIncrement,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aumentar",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
