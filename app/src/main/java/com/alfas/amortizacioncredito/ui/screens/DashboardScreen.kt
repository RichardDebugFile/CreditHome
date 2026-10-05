package com.alfas.amortizacioncredito.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.domain.model.ClientLoanStatus
import com.alfas.amortizacioncredito.domain.model.DashboardClientSummary
import java.io.File
import java.util.Locale
import com.alfas.amortizacioncredito.ui.AmortizationUiState
import com.alfas.amortizacioncredito.ui.theme.*

private val dashboardCurrencyFormat = ThreadLocal.withInitial {
    DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US))
}

private fun formatMoney(amount: Double): String {
    return dashboardCurrencyFormat.get()?.format(amount) ?: "$$amount"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: AmortizationUiState,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onNavigateToSimulator: (ClientProfile?) -> Unit,
    onOpenClientList: () -> Unit,
    onOpenNewClient: () -> Unit,
    onOpenBusinessProfile: () -> Unit,
    onOpenBackupDialog: () -> Unit,
    onOpenSignatureVault: () -> Unit = {},
    onOpenPaymentAssistant: () -> Unit = {},
    onExportExcel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = state.dashboardStats
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<ClientLoanStatus?>(null) }
    val unreadNotificationsCount = remember(state.notifications) { state.notifications.count { !it.isRead } }

    val logoBitmap = remember(state.businessProfile.logoPath) {
        val path = state.businessProfile.logoPath
        if (path.isNotBlank() && File(path).exists()) {
            try {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, boundsOptions)
                var sampleSize = 1
                while (boundsOptions.outWidth / sampleSize > 128 || boundsOptions.outHeight / sampleSize > 128) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(path, decodeOptions)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val clientsById = remember(state.allClients) {
        state.allClients.associateBy { it.id }
    }

    val filteredClients = remember(stats.clientSummaries, searchQuery, selectedFilter) {
        stats.clientSummaries.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.documentOrPhone.contains(searchQuery, ignoreCase = true)
            val matchesFilter = selectedFilter == null || item.status == selectedFilter
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón circular Asistente de Pagos Inteligente (solo ícono)
                FloatingActionButton(
                    onClick = onOpenPaymentAssistant,
                    containerColor = GreenSuccess,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Asistente de Pagos",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Botón circular Nuevo Crédito (solo ícono)
                FloatingActionButton(
                    onClick = { onNavigateToSimulator(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nuevo Crédito",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap,
                                contentDescription = "Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.alfas.amortizacioncredito.R.drawable.ic_credithome_logo),
                                contentDescription = "CreditHome Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = if (state.businessProfile.businessName.isNotBlank()) state.businessProfile.businessName else "CreditHome",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Panel de Control Financiero",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú principal")
                    }
                },
                actions = {
                    // Botón de Notificaciones (Campanita con badge)
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(
                                        containerColor = RedError,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (unreadNotificationsCount > 99) "99+" else "$unreadNotificationsCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (unreadNotificationsCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Centro de Notificaciones",
                                tint = if (unreadNotificationsCount > 0) AmberWarningDark else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Botón Asistente Inteligente de Pagos
                    IconButton(onClick = onOpenPaymentAssistant) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Asistente de Pagos",
                            tint = GreenSuccess
                        )
                    }

                    IconButton(onClick = onOpenBackupDialog) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Copia de Seguridad",
                            tint = CreditHomeBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 1. TARJETA PRINCIPAL: BALANCE GLOBAL DE CARTERA
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BALANCE GENERAL DE CARTERA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", stats.collectionRate)}% Recaudado",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Total Financiado",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatMoney(stats.totalFinanced),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Cobrado",
                                    fontSize = 11.sp,
                                    color = GreenSuccess
                                )
                                Text(
                                    text = formatMoney(stats.totalCollected),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GreenSuccess
                                )
                            }
                        }

                        // Barra de progreso de recaudación
                        LinearProgressIndicator(
                            progress = { stats.collectionRate / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saldo Pendiente por Cobrar:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatMoney(stats.totalPendingBalance),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                        }
                    }
                }
            }

            // 2. INDICADORES RÁPIDOS (KPI CARDS)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiStatCard(
                        title = "Clientes",
                        value = "${stats.totalClients}",
                        icon = Icons.Default.Groups,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )

                    KpiStatCard(
                        title = "En Mora",
                        value = "${stats.overdueClientsCount}",
                        icon = Icons.Default.WarningAmber,
                        iconTint = if (stats.overdueClientsCount > 0) RedErrorLight else CreditHomeEmerald,
                        containerColor = if (stats.overdueClientsCount > 0) RedErrorLight.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        borderColor = if (stats.overdueClientsCount > 0) RedErrorLight.copy(alpha = 0.35f) else null,
                        modifier = Modifier.weight(1f)
                    )

                    KpiStatCard(
                        title = "Mes Actual",
                        value = "${stats.dueThisMonthCount}",
                        icon = Icons.Default.CalendarMonth,
                        iconTint = AmberAccent,
                        containerColor = if (stats.dueThisMonthCount > 0) AmberAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        borderColor = if (stats.dueThisMonthCount > 0) AmberAccent.copy(alpha = 0.35f) else null,
                        modifier = Modifier.weight(1f)
                    )

                    KpiStatCard(
                        title = "Liquidados",
                        value = "${stats.fullyPaidClientsCount}",
                        icon = Icons.Default.CheckCircle,
                        iconTint = CreditHomeEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. BOTONERA DE ACCIONES RÁPIDAS
            item {
                Text(
                    text = "ACCIONES RÁPIDAS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        title = "Simulador Libre",
                        subtitle = "Cotizar crédito",
                        icon = Icons.Default.Calculate,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToSimulator(null) },
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionTile(
                        title = "Nuevo Cliente",
                        subtitle = "Crear crédito",
                        icon = Icons.Default.PersonAdd,
                        iconColor = GreenSuccess,
                        onClick = onOpenNewClient,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        title = "Directorio",
                        subtitle = "${stats.totalClients} clientes",
                        icon = Icons.Default.FolderShared,
                        iconColor = PurpleAccent,
                        onClick = onOpenClientList,
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionTile(
                        title = "Mi Perfil / Logo",
                        subtitle = "Datos del emisor",
                        icon = Icons.Default.Storefront,
                        iconColor = SkyBlueAction,
                        onClick = onOpenBusinessProfile,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        title = "Bóveda de Firmas",
                        subtitle = "Táctil, .p12 y fotos",
                        icon = Icons.Default.Draw,
                        iconColor = PurpleLight,
                        onClick = onOpenSignatureVault,
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionTile(
                        title = "Copia Nube",
                        subtitle = "Respaldar datos",
                        icon = Icons.Default.CloudSync,
                        iconColor = TealAccent,
                        onClick = onOpenBackupDialog,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. SECCIÓN: CARTERA Y COBRANZA
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ESTADO DE COBRANZAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${filteredClients.size} de ${stats.totalClients} cliente(s)",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { onNavigateToSimulator(null) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuevo", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onExportExcel,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Buscador rápido de cliente
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nombre o cédula...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Chips de Filtro
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("Todos (${stats.totalClients})", fontSize = 10.5.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == ClientLoanStatus.EN_MORA,
                        onClick = { selectedFilter = if (selectedFilter == ClientLoanStatus.EN_MORA) null else ClientLoanStatus.EN_MORA },
                        label = { Text("🔴 En Mora (${stats.overdueClientsCount})", fontSize = 10.5.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == ClientLoanStatus.VENCE_PRONTO,
                        onClick = { selectedFilter = if (selectedFilter == ClientLoanStatus.VENCE_PRONTO) null else ClientLoanStatus.VENCE_PRONTO },
                        label = { Text("🟠 Vence este mes (${stats.dueThisMonthCount})", fontSize = 10.5.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == ClientLoanStatus.AL_DIA,
                        onClick = { selectedFilter = if (selectedFilter == ClientLoanStatus.AL_DIA) null else ClientLoanStatus.AL_DIA },
                        label = { Text("🟢 Al día", fontSize = 10.5.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == ClientLoanStatus.LIQUIDADO,
                        onClick = { selectedFilter = if (selectedFilter == ClientLoanStatus.LIQUIDADO) null else ClientLoanStatus.LIQUIDADO },
                        label = { Text("🎉 Liquidados (${stats.fullyPaidClientsCount})", fontSize = 10.5.sp) }
                    )
                }
            }

            if (filteredClients.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text(
                                text = if (stats.totalClients == 0) "Aún no tienes clientes registrados" else "No se encontraron clientes con este filtro",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onNavigateToSimulator(null) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Registrar / Simular Nuevo Crédito", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(
                    items = filteredClients,
                    key = { it.id },
                    contentType = { "client_card" }
                ) { clientSummary ->
                    val clientObj = clientsById[clientSummary.id]
                    DashboardClientCard(
                        summary = clientSummary,
                        onClick = {
                            if (clientObj != null) {
                                onNavigateToSimulator(clientObj)
                            }
                        }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.alfas.amortizacioncredito.R.drawable.ic_credithome_logo),
                            contentDescription = "CreditHome Logo",
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CreditHome",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sistema Profesional de Crédito y Cobranza",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
fun KpiStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    borderColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        border = borderColor?.let { BorderStroke(1.dp, it) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.height(60.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = subtitle, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun DashboardClientCard(
    summary: DashboardClientSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (summary.status) {
                ClientLoanStatus.EN_MORA -> RedBadgeBorder
                ClientLoanStatus.VENCE_PRONTO -> AmberBadgeBorderLight
                ClientLoanStatus.LIQUIDADO -> GreenBadgeBorderLight
                ClientLoanStatus.AL_DIA -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.documentOrPhone.isNotBlank()) {
                        Text(
                            text = summary.documentOrPhone,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Badge de Estado
                val (badgeText, badgeBg, badgeTextColor) = when (summary.status) {
                    ClientLoanStatus.EN_MORA -> Triple("🔴 Cuota en Mora", RedBadgeBg, RedBadgeText)
                    ClientLoanStatus.VENCE_PRONTO -> Triple("🟠 Vence este Mes", AmberBadgeBg, AmberBadgeText)
                    ClientLoanStatus.LIQUIDADO -> Triple("🎉 Liquidado 100%", GreenBadgeBg, GreenBadgeText)
                    ClientLoanStatus.AL_DIA -> Triple("🟢 Al día", SkyBadgeBg, SkyBadgeText)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }

            // Datos financieros del cliente
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Financiado", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(summary.totalFinanced), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pagado (${summary.paidMonths}/${summary.totalMonths})", fontSize = 9.5.sp, color = GreenSuccess)
                    Text(formatMoney(summary.totalPaid), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Saldo Restante", fontSize = 9.5.sp, color = AmberWarningDark)
                    Text(formatMoney(summary.remainingBalance), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AmberWarningDark)
                }
            }

            // Barra de avance
            LinearProgressIndicator(
                progress = { summary.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = if (summary.status == ClientLoanStatus.LIQUIDADO) GreenSuccess else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Próximo Vencimiento / Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (summary.status != ClientLoanStatus.LIQUIDADO && summary.nextDueDate.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = if (summary.status == ClientLoanStatus.EN_MORA) RedError else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cuota prox: ${summary.nextDueDate} (${formatMoney(summary.nextPaymentAmount)})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (summary.status == ClientLoanStatus.EN_MORA) RedError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (summary.status == ClientLoanStatus.LIQUIDADO) {
                    Text("Crédito cancelado en su totalidad", fontSize = 10.sp, color = GreenSuccess, fontWeight = FontWeight.Medium)
                } else {
                    Text("Sin fecha definida", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Text(
                    text = "Ver Tabla →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
