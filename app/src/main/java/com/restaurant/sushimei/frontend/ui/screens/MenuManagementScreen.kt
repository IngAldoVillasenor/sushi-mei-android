package com.restaurant.sushimei.frontend.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.restaurant.sushimei.frontend.data.model.MenuItem
import com.restaurant.sushimei.frontend.ui.menu.MenuManagementViewModel
import java.util.UUID

// Paleta de colores por categoría (generativa)
private val categoryColors = listOf(
    Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF1E88E5),
    Color(0xFF00897B), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF6D4C41), Color(0xFF00ACC1), Color(0xFF5E35B1)
)

private fun colorForCategory(category: String): Color {
    val idx = category.hashCode().let { if (it < 0) -it else it } % categoryColors.size
    return categoryColors[idx]
}

@Composable
fun MenuManagementScreen(
    viewModel: MenuManagementViewModel = run {
        val context = LocalContext.current
        viewModel(factory = MenuManagementViewModel.factory(context))
    }
) {
    val products      by viewModel.filteredProducts.collectAsState()
    val categories    by viewModel.categories.collectAsState()
    val searchQuery   by viewModel.searchQuery.collectAsState()
    val selectedCat   by viewModel.selectedCategory.collectAsState()
    val selectedItem  by viewModel.selectedProduct.collectAsState()
    val isSaving      by viewModel.isSaving.collectAsState()
    val saveSuccess   by viewModel.saveSuccess.collectAsState()

    var activeScreen by remember { mutableStateOf("menu") } // "menu", "tags", "config_builder", "promotions_list", "promotion_editor"
    var configTargetId by remember { mutableStateOf<String?>(null) }
    var promotionTargetToEdit by remember { mutableStateOf<com.restaurant.sushimei.frontend.data.model.Promotion?>(null) }

    if (activeScreen == "tags") {
        val context = LocalContext.current
        val tagsViewModel: com.restaurant.sushimei.frontend.ui.admin.tags.AdminTagsViewModel = viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val mockRepo = com.restaurant.sushimei.frontend.data.repository.MockMenuRepository(context)
                    return com.restaurant.sushimei.frontend.ui.admin.tags.AdminTagsViewModel(mockRepo) as T
                }
            }
        )
        com.restaurant.sushimei.frontend.ui.admin.tags.AdminTagsScreen(
            viewModel = tagsViewModel,
            onBack = { activeScreen = "menu" }
        )
        return
    }

    if (activeScreen == "config_builder" && configTargetId != null) {
        val context = LocalContext.current
        val configViewModel: com.restaurant.sushimei.frontend.ui.admin.configurator.ConfigurationBuilderViewModel = viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val mockRepo = com.restaurant.sushimei.frontend.data.repository.MockMenuRepository(context)
                    return com.restaurant.sushimei.frontend.ui.admin.configurator.ConfigurationBuilderViewModel(mockRepo) as T
                }
            }
        )
        com.restaurant.sushimei.frontend.ui.admin.configurator.ConfigurationBuilderScreen(
            menuItemId = configTargetId!!,
            viewModel = configViewModel,
            onBack = { activeScreen = "menu" }
        )
        return
    }

    if (activeScreen == "promotions_list" || activeScreen == "promotion_editor") {
        val context = LocalContext.current
        val promosViewModel: com.restaurant.sushimei.frontend.ui.admin.promotions.PromotionsViewModel = viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val mockPromoRepo = com.restaurant.sushimei.frontend.data.local.providePromotionRepository(context)
                    return com.restaurant.sushimei.frontend.ui.admin.promotions.PromotionsViewModel(mockPromoRepo) as T
                }
            }
        )

        if (activeScreen == "promotions_list") {
            com.restaurant.sushimei.frontend.ui.admin.promotions.PromotionsListScreen(
                viewModel = promosViewModel,
                onBack = { activeScreen = "menu" },
                onEditPromotion = { promotion ->
                    promotionTargetToEdit = promotion
                    activeScreen = "promotion_editor"
                }
            )
        } else {
            com.restaurant.sushimei.frontend.ui.admin.promotions.PromotionEditorScreen(
                promotion = promotionTargetToEdit,
                viewModel = promosViewModel,
                onBack = { activeScreen = "promotions_list" }
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Panel izquierdo: catálogo ──────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(if (selectedItem != null) 0.42f else 1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gestión de Menú",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${products.size} productos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { activeScreen = "promotions_list" }) {
                        Text("Promociones")
                    }
                    TextButton(onClick = { activeScreen = "tags" }) {
                        Text("Gestión de Tags")
                    }
                    FloatingActionButton(
                        onClick = { viewModel.newProduct() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nuevo producto")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Búsqueda
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Buscar producto…") },
                leadingIcon  = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
                shape      = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Chips de categoría
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCat == null,
                        onClick  = { viewModel.onCategorySelected(null) },
                        label    = { Text("Todos") }
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick  = { viewModel.onCategorySelected(if (selectedCat == cat) null else cat) },
                        label    = { Text(cat) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorForCategory(cat).copy(alpha = 0.2f),
                            selectedLabelColor     = colorForCategory(cat)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lista de productos
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { item ->
                    ProductListItem(
                        item       = item,
                        isSelected = selectedItem?.id == item.id,
                        onClick    = { viewModel.selectProduct(item) },
                        onToggle   = { activo -> viewModel.toggleActive(item, activo) }
                    )
                }
            }
        }

        // ── Panel derecho: formulario ──────────────────────────────────────
        AnimatedVisibility(
            visible = selectedItem != null,
            enter   = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit    = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            selectedItem?.let { item ->
                ProductFormPanel(
                    item       = item,
                    isSaving   = isSaving,
                    saveSuccess = saveSuccess,
                    onSave     = { viewModel.saveProduct(it) },
                    onDismiss  = { viewModel.clearSelection() },
                    onAcknowledge = { viewModel.acknowledgeSaveSuccess() },
                    onOpenConfigurator = {
                        configTargetId = item.id
                        activeScreen = "config_builder"
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProductListItem
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProductListItem(
    item: MenuItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val catColor = colorForCategory(item.categoria)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Nombre + categoría
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = item.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (!isSelected && false) Color.Gray  // placeholder lógica inactivos
                        else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(catColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text  = item.categoria,
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Precio
            Text(
                text  = "$${String.format("%.2f", item.precio)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Toggle activo/inactivo
            Switch(
                checked         = true,  // siempre true aquí porque filtramos por activo
                onCheckedChange = { onToggle(it) },
                modifier        = Modifier.height(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProductFormPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProductFormPanel(
    item: MenuItem,
    isSaving: Boolean,
    saveSuccess: Boolean,
    onSave: (MenuItem) -> Unit,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onOpenConfigurator: () -> Unit
) {
    var nombre      by remember(item.id) { mutableStateOf(item.nombre) }
    var precio      by remember(item.id) { mutableStateOf(if (item.precio > 0) item.precio.toString() else "") }
    var categoria   by remember(item.id) { mutableStateOf(item.categoria) }
    var emoji       by remember(item.id) { mutableStateOf(item.emoji) }
    var descripcion by remember(item.id) { mutableStateOf(item.descripcion) }

    val isNew = item.nombre.isBlank()

    // Feedback de éxito
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(1500)
            onAcknowledge()
        }
    }

    Column(
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight()
            .padding(20.dp)
    ) {
        // Header del panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isNew) "Nuevo producto" else "Editar producto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                // Emoji + preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji.ifBlank { "🍣" }, fontSize = 30.sp)
                    }
                    OutlinedTextField(
                        value         = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label         = { Text("Emoji") },
                        singleLine    = true,
                        modifier      = Modifier.width(120.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value         = nombre,
                    onValueChange = { nombre = it },
                    label         = { Text("Nombre del producto *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = nombre.isBlank()
                )
            }

            item {
                OutlinedTextField(
                    value         = precio,
                    onValueChange = { precio = it },
                    label         = { Text("Precio *") },
                    prefix        = { Text("$") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = precio.toDoubleOrNull() == null || (precio.toDoubleOrNull() ?: 0.0) <= 0
                )
            }

            item {
                OutlinedTextField(
                    value         = categoria,
                    onValueChange = { categoria = it },
                    label         = { Text("Categoría *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = categoria.isBlank()
                )
            }

            item {
                OutlinedTextField(
                    value         = descripcion,
                    onValueChange = { descripcion = it },
                    label         = { Text("Descripción (opcional)") },
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth()
                )
            }

            item {
                // Nota API
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 El precio aquí es local. El backend usará el ID del producto para obtener su precio canónico al procesar órdenes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isNew) {
            OutlinedButton(
                onClick = onOpenConfigurator,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configurar Opciones (Fase 6A2)")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Botones de acción
        val canSave = nombre.isNotBlank() &&
                categoria.isNotBlank() &&
                (precio.toDoubleOrNull() ?: 0.0) > 0

        AnimatedVisibility(visible = saveSuccess) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E7D32).copy(alpha = 0.12f))
                    .padding(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                Spacer(Modifier.width(8.dp))
                Text("Guardado correctamente", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    val precioDouble = precio.toDoubleOrNull() ?: return@Button
                    onSave(
                        item.copy(
                            nombre      = nombre.trim(),
                            precio      = precioDouble,
                            categoria   = categoria.trim(),
                            emoji       = emoji.ifBlank { "🍣" },
                            descripcion = descripcion.trim()
                        )
                    )
                },
                enabled  = canSave && !isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isNew) "✅ Crear" else "💾 Guardar")
                }
            }
        }
    }
}
