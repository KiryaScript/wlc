package com.example.vpn.ui

import android.content.Context
import android.widget.Toast
import java.util.UUID
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vpn.model.VpnProfile
import com.example.vpn.model.VpnProxy
import com.example.vpn.service.MihomoVpnService.ConnectionStatus

// Тёмная хай-тек палитра FlClash темы
val DeepSpaceBackground = Color(0xFF131416) // Глубокий космический черный
val CardBackground = Color(0xFF1F2023)      // Тон контейнеров FlClash
val PrimaryCyan = Color(0xFFB1D1FF)         // Светлый неон для текста и акцентов
val PrimaryPurple = Color(0xFF9061F9)       // Фиолетовый
val AccentGreen = Color(0xFF10B981)         // Успешные статусы (подключено)
val AlertAmber = Color(0xFFFBBF24)          // Тона ожидания (подключение)
val TextWhite = Color(0xFFF3F4F6)           // Высококонтрастный белый
val TextGray = Color(0xFF9CA3AF)            // Читаемый серый
val AccentDeepBlue = Color(0xFF1E3A8A)      // Фон плашек
val AccentDarkBlue = Color(0xFF111827)      // Глубокий синий
val AccentLightBlue = Color(0xFFBFDBFE)     // Акценты кнопок
val DarkestGray = Color(0xFF374151)         // Рамки и разделители

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: Главная, 1: Подписки/Профили, 2: Логи, 3: Настройки
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBackground),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "FlClash Mihomo",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                letterSpacing = (-0.2).sp
                            )
                        )
                        Text(
                            text = "STABLE MODEL • CORE 1.18.1",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryCyan.copy(alpha = 0.8f),
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                },
                actions = {
                    if (activeTab == 0) {
                        IconButton(
                            onClick = {
                                viewModel.measureAllPings()
                                Toast.makeText(context, "Инициирован замер пингов всех прокси", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("refresh_pings_button")
                                .background(CardBackground, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh Pings",
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpaceBackground,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1B1C1E),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkestGray.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Главная") },
                    label = { Text("Главная", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentDarkBlue,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = PrimaryCyan,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.FolderZip, contentDescription = "Профили") },
                    label = { Text("Профили", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentDarkBlue,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = PrimaryCyan,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Terminal, contentDescription = "Логи") },
                    label = { Text("Логи", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentDarkBlue,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = PrimaryCyan,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentDarkBlue,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = PrimaryCyan,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
            }
        },
        containerColor = DeepSpaceBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> DashboardTab(viewModel = viewModel, onAddClick = { showAddSheet = true })
                1 -> ProfilesTab(viewModel = viewModel)
                2 -> LogsTab(viewModel = viewModel)
                3 -> SettingsTab(viewModel = viewModel)
            }
        }
    }

    // Modal Bottom Sheet для ручного добавления новых серверов
    if (showAddSheet) {
        AddServerBottomSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { newProxy ->
                viewModel.addProxy(newProxy)
                showAddSheet = false
            }
        )
    }
}

/**
 * 1. Главная вкладка: Dashboard FlClash Style
 */
@Composable
fun DashboardTab(
    viewModel: VpnViewModel,
    onAddClick: () -> Unit
) {
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val selectedProxy by viewModel.selectedProxy.collectAsState()
    val proxies by viewModel.proxies.collectAsState()
    val dlSpeed by viewModel.downloadSpeed.collectAsState()
    val ulSpeed by viewModel.uploadSpeed.collectAsState()
    val totalIn by viewModel.totalBytesIn.collectAsState()
    val totalOut by viewModel.totalBytesOut.collectAsState()
    val pings by viewModel.serverPings.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Физический щит подключения FlClash
        ConnectionDashboard(
            status = vpnStatus,
            activeProxyName = selectedProxy?.name ?: "Прокси не выбран",
            onClick = {
                var currentContext = context
                while (currentContext is android.content.ContextWrapper) {
                    if (currentContext is com.example.MainActivity) {
                        break
                    }
                    currentContext = currentContext.baseContext
                }
                val mainActivity = currentContext as? com.example.MainActivity
                if (mainActivity != null) {
                    mainActivity.toggleConnectionWithChecks()
                } else {
                    viewModel.toggleConnection(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Спидометры
        SpeedometerSection(
            dlSpeed = dlSpeed,
            ulSpeed = ulSpeed,
            totalIn = totalIn,
            totalOut = totalOut
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Активный баннер
        ActiveServerBanner(selectedProxy, pings[selectedProxy?.id ?: ""])

        Spacer(modifier = Modifier.height(24.dp))

        // Заголовок списка прокси групп
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Конфигурации (${proxies.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            )
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardBackground,
                    contentColor = PrimaryCyan
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_proxy_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Proxy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Сервер", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Proxy Group: GLOBAL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkestGray.copy(alpha = 0.7f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROXY GROUP: GLOBAL",
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Core Active Proxies",
                        color = TextGray.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }

                Divider(color = DarkestGray.copy(alpha = 0.5f), thickness = 1.dp)

                if (proxies.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Выбранный профиль пуст.\nДобавьте прокси или заведите подписку.",
                            color = TextGray,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column {
                        proxies.forEachIndexed { index, proxy ->
                            ServerRowItem(
                                proxy = proxy,
                                isSelected = selectedProxy?.id == proxy.id,
                                pingValue = pings[proxy.id],
                                onSelect = { viewModel.selectProxy(proxy) },
                                onDelete = { viewModel.deleteProxy(proxy.id) },
                                onPingRefresh = { viewModel.measurePing(proxy) }
                            )
                            if (index < proxies.size - 1) {
                                Divider(color = DarkestGray.copy(alpha = 0.3f), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 2. Вкладка "Профили": Управление зашифрованными подписками и ключами (FlClash Style)
 */
@Composable
fun ProfilesTab(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val isFetchingSub by viewModel.isFetchingSub.collectAsState()

    var inputName by remember { mutableStateOf("") }
    var inputUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Карточка добавления / Импорта подписки
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PrimaryCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "ИМПОРТ КЛЮЧА ИЛИ ПОДПИСКИ",
                    color = PrimaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Название подписки (Опционально)") },
                    colors = getTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Ссылка / Ключ (hy2, vless, ss, socks5)") },
                    placeholder = { Text("hy2://... или https://domen.com/sub/id") },
                    colors = getTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (inputUrl.isBlank()) {
                            Toast.makeText(context, "Пожалуйста, введите URL подписки или ключ", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.importSubscription(context, inputName, inputUrl) { success ->
                            if (success) {
                                inputName = ""
                                inputUrl = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = AccentDarkBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isFetchingSub
                ) {
                    if (isFetchingSub) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccentDarkBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ИМПОРТ...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ИМПОРТИРОВАТЬ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Заголовок списка профилей
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Inventory2, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Доступные профили (${profiles.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Список профилей
        profiles.forEach { profile ->
            val isSelected = selectedProfile?.id == profile.id
            val borderTint = if (isSelected) PrimaryCyan else DarkestGray.copy(alpha = 0.5f)
            val bgTint = if (isSelected) CardBackground.copy(alpha = 0.95f) else CardBackground.copy(alpha = 0.6f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { viewModel.selectProfile(profile.id) }
                    .border(1.dp, borderTint, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = bgTint),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (isSelected) {
                        // Декоративный левый цветной элемент как в FlClash
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(95.dp)
                                .background(PrimaryCyan)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .padding(start = if (isSelected) 8.dp else 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) PrimaryCyan else TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                val sourceLabel = if (profile.sourceUrl != null) {
                                    profile.sourceUrl
                                } else {
                                    "Локальный импорт / Ручные сервера"
                                }
                                Text(
                                    text = sourceLabel,
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(DarkestGray, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${profile.proxiesCount} серверов",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = DarkestGray.copy(alpha = 0.3f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val format = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
                            val dateText = format.format(java.util.Date(profile.lastUpdated))
                            Text(
                                text = "Обновлено: $dateText",
                                fontSize = 10.sp,
                                color = TextGray.copy(alpha = 0.8f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (profile.sourceUrl != null) {
                                    IconButton(
                                        onClick = { viewModel.refreshSubscription(context, profile) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(DarkestGray, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Обновить подписку",
                                            tint = PrimaryCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                if (profile.id != "default_demo") {
                                    IconButton(
                                        onClick = { viewModel.deleteProfile(profile.id) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(DarkestGray, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Удалить подписку",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * 3. Вкладка "Логи": Многофункциональный живой консольный вывод Mihomo Core (FlClash)
 */
@Composable
fun LogsTab(viewModel: VpnViewModel) {
    val logs by viewModel.logs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Заголовки панели управления консолями
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Консоль телеметрии",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                )
            }

            Button(
                onClick = { viewModel.clearLogs() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkestGray,
                    contentColor = TextWhite
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Очистить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Окно Терминала
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color(0xFF2E2F30), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E10)),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Протокол логов пуст.",
                        color = TextGray.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { log ->
                        val color = when {
                            log.contains("[SUCCESS]") || log.contains("[SYSTEM]") -> AccentGreen
                            log.contains("[FAILED]") || log.contains("timeout") -> Color(0xFFEF4444)
                            log.contains("[FETCH]") -> AlertAmber
                            log.contains("[RULE]") -> PrimaryPurple
                            else -> TextWhite
                        }
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = color,
                            lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 4. Вкладка "Настройки": Конфигурационные режимы, DNS и разработчик (FlClash)
 */
@Composable
fun SettingsTab(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val tunnelMode by viewModel.tunnelMode.collectAsState()
    val dnsServer by viewModel.dnsServer.collectAsState()

    var customDnsInput by remember { mutableStateOf(dnsServer) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkestGray.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "ТУННЕЛЬНЫЙ РЕЖИМ (TUNNEL MODE)",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                val modes = listOf("Rule (Пакетный)", "Global (Глобальный)", "Direct (Прямой)")
                modes.forEach { mode ->
                    val isSelected = tunnelMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setTunnelMode(mode) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode,
                            color = if (isSelected) PrimaryCyan else TextWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setTunnelMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrimaryCyan,
                                unselectedColor = TextGray
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Секция DNS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkestGray.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "DNS НАСТРОЙКИ (CORE DNS RESOLVER)",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = customDnsInput,
                    onValueChange = { customDnsInput = it },
                    label = { Text("Системный DNS сервера") },
                    colors = getTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (customDnsInput.isNotBlank()) {
                            viewModel.setDnsServer(customDnsInput)
                            Toast.makeText(context, "Системный DNS ядра переопределен", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = AccentDarkBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Применить DNS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Информация о разработчике и ядре
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkestGray.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "СИСТЕМНЫЕ МЕТАДАННЫЕ",
                    color = PrimaryCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Статус TUN", color = TextGray, fontSize = 13.sp)
                    Text("Активен (gvisor)", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Маршрутизация", color = TextGray, fontSize = 13.sp)
                    Text("Auto-Route (SYSTEM-controlled)", color = TextWhite, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ядро (Core Build)", color = TextGray, fontSize = 13.sp)
                    Text("Mihomo Core v1.18.1 Meta", color = TextWhite, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Интерфейс приложения", color = TextGray, fontSize = 13.sp)
                    Text("FlClash Stable Pro Edition", color = PrimaryCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Кнопка управления VPN подключением во главе панели управления.
 */
@Composable
fun ConnectionDashboard(
    status: ConnectionStatus,
    activeProxyName: String,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ConnectionStatus.CONNECTING) 1.15f else if (status == ConnectionStatus.CONNECTED) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val (glowColor, statusText) = when (status) {
        ConnectionStatus.DISCONNECTED -> Pair(Color.Transparent, "Подключить")
        ConnectionStatus.CONNECTING -> Pair(AlertAmber, "Соединение...")
        ConnectionStatus.CONNECTED -> Pair(AccentLightBlue, "Защищено")
        ConnectionStatus.DISCONNECTING -> Pair(PrimaryPurple, "Отключение...")
    }

    var secondsConnected by remember { mutableStateOf(0L) }
    LaunchedEffect(status) {
        if (status == ConnectionStatus.CONNECTED) {
            val startTime = System.currentTimeMillis() - (secondsConnected * 1000)
            while (true) {
                secondsConnected = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        } else {
            secondsConnected = 0L
        }
    }

    val hours = secondsConnected / 3600
    val minutes = (secondsConnected % 3600) / 60
    val seconds = secondsConnected % 60
    val durationText = if (status == ConnectionStatus.CONNECTED) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        "--:--:--"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(32.dp), clip = false)
            .border(1.dp, DarkestGray.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // TUN Mode Badge in the top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier
                        .background(AccentDeepBlue, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "TUN MODE",
                        color = AccentLightBlue,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .drawBehind {
                            if (status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING) {
                                drawCircle(
                                    color = glowColor.copy(alpha = 0.15f),
                                    radius = size.minDimension / 1.7f * pulseScale
                                )
                                drawCircle(
                                    color = glowColor.copy(alpha = 0.08f),
                                    radius = size.minDimension / 1.4f * pulseScale
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val isConnectedOrConnecting = status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING
                    val circleBorderColor = if (isConnectedOrConnecting) AccentDeepBlue else DarkestGray.copy(alpha = 0.5f)
                    val circleBgColor = if (isConnectedOrConnecting) AccentDarkBlue else Color(0xFF333538)
                    val iconTint = if (isConnectedOrConnecting) AccentLightBlue else TextGray

                    // Central Circle Button
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(circleBgColor)
                            .border(4.dp, circleBorderColor, CircleShape)
                            .clickable(onClick = onClick)
                            .testTag("connection_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Connection",
                            tint = iconTint,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Dot overlap
                    if (isConnectedOrConnecting) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .clip(CircleShape)
                                .background(PrimaryCyan)
                                .border(4.dp, CardBackground, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentDarkBlue)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = TextWhite,
                        letterSpacing = 0.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextGray,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = activeProxyName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextGray,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Секция спидометров
 */
@Composable
fun SpeedometerSection(
    dlSpeed: Long,
    ulSpeed: Long,
    totalIn: Long,
    totalOut: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SpeedCard(
            title = "Вход. скорость",
            speedBytes = dlSpeed,
            totalBytes = totalIn,
            isDownload = true,
            modifier = Modifier.weight(1f)
        )
        SpeedCard(
            title = "Исх. скорость",
            speedBytes = ulSpeed,
            totalBytes = totalOut,
            isDownload = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SpeedCard(
    title: String,
    speedBytes: Long,
    totalBytes: Long,
    isDownload: Boolean,
    modifier: Modifier = Modifier
) {
    val speedStr = formatSpeed(speedBytes)
    val totalStr = formatTotalBytes(totalBytes)

    val speedParts = speedStr.split(" ")
    val value = speedParts.getOrNull(0) ?: "0"
    val unit = speedParts.getOrNull(1) ?: "B/s"

    Card(
        modifier = modifier
            .border(1.dp, DarkestGray.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isDownload) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = unit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextGray,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Всего: $totalStr",
                fontSize = 11.sp,
                color = TextGray.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Быстрый инфо баннер активной прокси
 */
@Composable
fun ActiveServerBanner(proxy: VpnProxy?, ping: Int?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PrimaryCyan.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2633)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28364F)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getFlagEmoji(proxy?.name ?: "GLOBAL"),
                    fontSize = 20.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Активный сервер",
                    fontSize = 11.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = proxy?.name ?: "Сервер не выбран",
                    fontSize = 14.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (proxy != null) {
                PingIndicator(ping = ping)
            }
        }
    }
}

/**
 * Ряд конкретного прокси в списке
 */
@Composable
fun ServerRowItem(
    proxy: VpnProxy,
    isSelected: Boolean,
    pingValue: Int?,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onPingRefresh: () -> Unit
) {
    val itemBg = if (isSelected) Color(0xFF282B30) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBg)
            .clickable(onClick = onSelect)
            .drawBehind {
                if (isSelected) {
                    drawRect(
                        color = PrimaryCyan,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)
                    )
                }
            }
            .testTag("server_item_${proxy.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Флаг
            val flagBgColor = if (isSelected) AccentDeepBlue else DarkestGray
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(flagBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getFlagEmoji(proxy.name),
                    fontSize = 18.sp
                )
            }

            // Название, протокол и описание URI
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = proxy.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) TextWhite else TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Бэйдж протокола
                    Box(
                        modifier = Modifier
                            .background(DarkestGray, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = getProtocolLabel(proxy).uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val detailText = when (proxy) {
                    is VpnProxy.VlessReality -> "vless://${proxy.server}:${proxy.port} @ reality"
                    is VpnProxy.Hysteria2 -> "hysteria2://${proxy.server}:${proxy.port} @ hys2"
                    is VpnProxy.WireGuard -> "wg://ip_local:${proxy.ipLocal} @ wg"
                    is VpnProxy.Shadowsocks -> "ss://${proxy.server}:${proxy.port} @ static"
                    is VpnProxy.Socks5 -> "socks5://${proxy.server}:${proxy.port} @ socks5"
                    is VpnProxy.Trojan -> "trojan://${proxy.server}:${proxy.port} @ trojan"
                    is VpnProxy.Vmess -> "vmess://${proxy.server}:${proxy.port} @ vmess"
                }

                Text(
                    text = detailText,
                    fontSize = 10.sp,
                    color = TextGray.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Пинг замер по клику
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPingRefresh)
                    .padding(4.dp)
            ) {
                PingIndicator(ping = pingValue)
            }

            // Кнопка удаления сервера
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete Server",
                    tint = TextGray.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PingIndicator(ping: Int?) {
    when {
        ping == null -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = PrimaryCyan,
                strokeWidth = 1.5.dp
            )
        }
        ping < 0 -> {
            Text(
                text = "timeout",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Red,
                fontFamily = FontFamily.Monospace
            )
        }
        else -> {
            val color = when {
                ping < 60 -> AccentGreen
                ping < 140 -> AlertAmber
                else -> Color(0xFFEF4444)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = "$ping ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Модальное окно добавления нового сервера с динамическим отображением полей под каждый протокол
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (VpnProxy) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableStateOf(0) } // 0: VLESS, 1: Hy2, 2: WG, 3: Shadowsocks, 4: SOCKS5
    val tabs = listOf("VLESS", "Hysteria 2", "WireGuard", "Shadowsocks", "SOCKS5")

    // Общие поля
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var portString by remember { mutableStateOf("") }

    // VLESS Reality
    var uuid by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var shortId by remember { mutableStateOf("") }

    // Hysteria 2
    var auth by remember { mutableStateOf("") }

    // WireGuard
    var wgPrivate by remember { mutableStateOf("") }
    var wgPublic by remember { mutableStateOf("") }
    var wgLocalIp by remember { mutableStateOf("10.0.0.2") }
    var wgDns by remember { mutableStateOf("1.1.1.1") }

    // Shadowsocks & Socks5
    var cipher by remember { mutableStateOf("2022-blake3-aes-128-gcm") }
    var password by remember { mutableStateOf("") }
    var socksUser by remember { mutableStateOf("") }

    var pswdVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepSpaceBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Добавить конфигурацию",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Вкладки выбора протокола
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DeepSpaceBackground,
                contentColor = PrimaryCyan,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = PrimaryCyan,
                        unselectedContentColor = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Общие текстовые поля
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                colors = getTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_name"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Хост / IP") },
                    colors = getTextFieldColors(),
                    modifier = Modifier
                        .weight(1.8f)
                        .testTag("input_host"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = portString,
                    onValueChange = { portString = it },
                    label = { Text("Порт") },
                    colors = getTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_port"),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = CardBackground, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Уникальные поля в зависимости от формата
            when (selectedTab) {
                0 -> { // VLESS + Reality
                    OutlinedTextField(
                        value = uuid,
                        onValueChange = { uuid = it },
                        label = { Text("UUID пользователя") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vless_uuid"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = publicKey,
                        onValueChange = { publicKey = it },
                        label = { Text("Public Key (Reality)") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vless_pubkey"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text("SNI (Server Name)") },
                            colors = getTextFieldColors(),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("vless_sni"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = shortId,
                            onValueChange = { shortId = it },
                            label = { Text("Short ID") },
                            colors = getTextFieldColors(),
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("vless_shortid"),
                            singleLine = true
                        )
                    }
                }
                1 -> { // Hysteria 2
                    OutlinedTextField(
                        value = auth,
                        onValueChange = { auth = it },
                        label = { Text("Auth String / Токен") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hy2_auth"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("SNI (Сертификат / Заголовок)") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hy2_sni"),
                        singleLine = true
                    )
                }
                2 -> { // WireGuard
                    OutlinedTextField(
                        value = wgPrivate,
                        onValueChange = { wgPrivate = it },
                        label = { Text("Private Key клиента") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wg_private"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = wgPublic,
                        onValueChange = { wgPublic = it },
                        label = { Text("Public Key сервера") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wg_public"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = wgLocalIp,
                            onValueChange = { wgLocalIp = it },
                            label = { Text("Локальный IP Tunnel") },
                            colors = getTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wg_local_ip"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = wgDns,
                            onValueChange = { wgDns = it },
                            label = { Text("DNS") },
                            colors = getTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wg_dns"),
                            singleLine = true
                        )
                    }
                }
                3 -> { // Shadowsocks
                    OutlinedTextField(
                        value = cipher,
                        onValueChange = { cipher = it },
                        label = { Text("Шифр (Cipher)") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ss_cipher"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль соединения") },
                        colors = getTextFieldColors(),
                        visualTransformation = if (pswdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pswdVisible = !pswdVisible }) {
                                Icon(
                                    imageVector = if (pswdVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password",
                                    tint = PrimaryCyan
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ss_password"),
                        singleLine = true
                    )
                }
                4 -> { // SOCKS5
                    OutlinedTextField(
                        value = socksUser,
                        onValueChange = { socksUser = it },
                        label = { Text("Логин (Опционально)") },
                        colors = getTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("socks_user"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль (Опционально)") },
                        colors = getTextFieldColors(),
                        visualTransformation = if (pswdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pswdVisible = !pswdVisible }) {
                                Icon(
                                    imageVector = if (pswdVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password",
                                    tint = PrimaryCyan
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("socks_password"),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Кнопка сохранения нового прокси
            Button(
                onClick = {
                    val port = portString.toIntOrNull()
                    if (name.isBlank() || server.isBlank() || port == null) {
                        return@Button
                    }
                    val newProxy = when (selectedTab) {
                        0 -> VpnProxy.VlessReality(
                            name = name,
                            server = server,
                            port = port,
                            uuid = uuid.ifBlank { UUID.randomUUID().toString() },
                            publicKey = publicKey.ifBlank { "dummy-reality-key" },
                            serverName = sni.ifBlank { "google.com" },
                            shortId = shortId.ifBlank { "sh01d" }
                        )
                        1 -> VpnProxy.Hysteria2(
                            name = name,
                            server = server,
                            port = port,
                            auth = auth.ifBlank { "dummy-auth-key" },
                            sni = sni.ifBlank { server }
                        )
                        2 -> VpnProxy.WireGuard(
                            name = name,
                            server = server,
                            port = port,
                            privateKey = wgPrivate.ifBlank { "dummy-private-key" },
                            publicKey = wgPublic.ifBlank { "dummy-public-key" },
                            ipLocal = wgLocalIp.ifBlank { "10.0.0.2/32" },
                            dns = wgDns.ifBlank { "1.1.1.1" }
                        )
                        3 -> VpnProxy.Shadowsocks(
                            name = name,
                            server = server,
                            port = port,
                            cipher = cipher.ifBlank { "2022-blake3-aes-128-gcm" },
                            password = password.ifBlank { "dummy-password" }
                        )
                        else -> VpnProxy.Socks5(
                            name = name,
                            server = server,
                            port = port,
                            username = socksUser.ifEmpty { null },
                            password = password.ifEmpty { null }
                        )
                    }
                    onAdd(newProxy)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_proxy_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = AccentDarkBlue
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "СОХРАНИТЬ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// Помощники дизайна
@Composable
fun getTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryCyan,
    unfocusedBorderColor = DarkestGray,
    focusedLabelColor = PrimaryCyan,
    unfocusedLabelColor = TextGray,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    cursorColor = PrimaryCyan,
    focusedPlaceholderColor = TextGray.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = TextGray.copy(alpha = 0.6f)
)

fun getProtocolBadgeBrush(proxy: VpnProxy): Brush {
    val colors = when (proxy) {
        is VpnProxy.VlessReality -> listOf(Color(0xFF00FFCC), Color(0xFF00FF99))
        is VpnProxy.Hysteria2 -> listOf(Color(0xFFFFB703), Color(0xFFFF9F1C))
        is VpnProxy.WireGuard -> listOf(Color(0xFF8338EC), Color(0xFF3A86C8))
        is VpnProxy.Shadowsocks -> listOf(Color(0xFFE63946), Color(0xFFF15BB5))
        is VpnProxy.Socks5 -> listOf(Color(0xFF90E0EF), Color(0xFF00B4D8))
        is VpnProxy.Trojan -> listOf(Color(0xFFFF0D57), Color(0xFFFF4081))
        is VpnProxy.Vmess -> listOf(Color(0xFF0077B6), Color(0xFF03045E))
    }
    return Brush.horizontalGradient(colors)
}

fun getProtocolLabel(proxy: VpnProxy): String {
    return when (proxy) {
        is VpnProxy.VlessReality -> "VLESS"
        is VpnProxy.Hysteria2 -> "HY2"
        is VpnProxy.WireGuard -> "WIREGUARD"
        is VpnProxy.Shadowsocks -> "SS"
        is VpnProxy.Socks5 -> "SOCKS5"
        is VpnProxy.Trojan -> "TROJAN"
        is VpnProxy.Vmess -> "VMESS"
    }
}

// Утилиты форматирования
fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format("%.1f KB/s", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB/s", mb)
}

fun formatTotalBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

fun getFlagEmoji(name: String): String {
    val upper = name.uppercase()
    return when {
        upper.contains("US") || upper.contains("USA") || upper.contains("UNITED STATES") || upper.contains("AMERICA") -> "🇺🇸"
        upper.contains("RU") || upper.contains("RUSSIA") || upper.contains("MOSCOW") -> "🇷🇺"
        upper.contains("DE") || upper.contains("GERMANY") || upper.contains("FRANKFURT") -> "🇩🇪"
        upper.contains("NL") || upper.contains("NETHERLANDS") || upper.contains("AMSTERDAM") -> "🇳🇱"
        upper.contains("SG") || upper.contains("SINGAPORE") -> "🇸🇬"
        upper.contains("HK") || upper.contains("HONG") || upper.contains("HONGKONG") -> "🇭🇰"
        upper.contains("JP") || upper.contains("JAPAN") || upper.contains("TOKYO") -> "🇯🇵"
        upper.contains("GB") || upper.contains("UK") || upper.contains("LONDON") || upper.contains("ENGLAND") -> "🇬🇧"
        upper.contains("FR") || upper.contains("FRANCE") || upper.contains("PARIS") -> "🇫🇷"
        upper.contains("FI") || upper.contains("FINLAND") || upper.contains("HELSINKI") -> "🇫🇮"
        upper.contains("CH") || upper.contains("SWITZERLAND") -> "🇨🇭"
        upper.contains("SE") || upper.contains("SWEDEN") -> "🇸🇪"
        else -> "🌐"
    }
}
