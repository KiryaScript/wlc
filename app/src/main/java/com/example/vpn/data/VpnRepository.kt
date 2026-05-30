package com.example.vpn.data

import com.example.vpn.core.MihomoConfigGenerator
import com.example.vpn.model.VpnProfile
import com.example.vpn.model.VpnProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Репозиторий для управления прокси-серверами, профилями подписей и работы с конфигурацией Mihomo.
 */
class VpnRepository {

    private val _proxies = MutableStateFlow<List<VpnProxy>>(emptyList())
    val proxies: StateFlow<List<VpnProxy>> = _proxies.asStateFlow()

    private val _selectedProxy = MutableStateFlow<VpnProxy?>(null)
    val selectedProxy: StateFlow<VpnProxy?> = _selectedProxy.asStateFlow()

    // FlClash-Style Profiles / Subscriptions
    private val _profiles = MutableStateFlow<List<VpnProfile>>(emptyList())
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<VpnProfile?>(null)
    val selectedProfile: StateFlow<VpnProfile?> = _selectedProfile.asStateFlow()

    // Внутреннее хранилище прокси для каждого профиля по его ID
    private val profileProxies = mutableMapOf<String, MutableList<VpnProxy>>()

    init {
        // Загрузка дефолтного встроенного профиля демо-серверов
        loadDemoProfile()
    }

    private fun loadDemoProfile() {
        val demoId = "default_demo"
        val demoProfile = VpnProfile(
            id = demoId,
            name = "Встроенные Серверы",
            sourceUrl = null,
            proxiesCount = 5,
            isSelected = true
        )

        val demoServers = mutableListOf(
            VpnProxy.VlessReality(
                name = "VLESS Reality (Frankfurt)" ,
                server = "de-vless.mihomo.net",
                port = 443,
                uuid = "6cf36070-dfd3-48bd-b765-ab5cb3ad3865",
                publicKey = "g6g_bL4V_G7uR0M-rXf3eT7yS6h-wE1_q9A5cX8bUjE=",
                serverName = "images.google.com",
                shortId = "abc123fa70"
            ),
            VpnProxy.Hysteria2(
                name = "Hysteria 2 (Helsinki BBR)",
                server = "fi-hy2.mihomo.net",
                port = 8443,
                auth = "mihomo_fast_token_99",
                sni = "test-speed.helsinki.fi"
            ),
            VpnProxy.WireGuard(
                name = "WireGuard (Amsterdam Cloud)",
                server = "nl-wg.mihomo.net",
                port = 51820,
                privateKey = "aGku...MmhfMGZfd3RfZmFzdF9rZXlfMTI=",
                publicKey = "d2dHeDRmR3RreXNfYm9vX3NoYXJlX3VpZDg4"
            ),
            VpnProxy.Shadowsocks(
                name = "Shadowsocks (Tokyo AEAD)",
                server = "jp-ss.mihomo.net",
                port = 8531,
                cipher = "2022-blake3-aes-128-gcm",
                password = "Base64PasswordKeyForMihomoSecureSS="
            ),
            VpnProxy.Socks5(
                name = "SOCKS5 Proxy (Singapore Core)",
                server = "sg-socks.mihomo.net",
                port = 1080,
                username = "socks_user",
                password = "singapore_secret"
            )
        )

        profileProxies[demoId] = demoServers
        _profiles.value = listOf(demoProfile)
        _selectedProfile.value = demoProfile

        _proxies.value = demoServers
        _selectedProxy.value = demoServers.firstOrNull()
    }

    /**
     * Добавить новую подписку (профиль)
     */
    fun addProfile(name: String, sourceUrl: String?, proxiesList: List<VpnProxy>): VpnProfile {
        val newId = UUID.randomUUID().toString()
        val profile = VpnProfile(
            id = newId,
            name = name,
            sourceUrl = sourceUrl,
            proxiesCount = proxiesList.size,
            isSelected = false
        )

        profileProxies[newId] = proxiesList.toMutableList()

        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles.add(profile)
        _profiles.value = currentProfiles

        // Если это первый дополнительный профиль, его можно активировать сразу
        if (currentProfiles.size == 2) {
            selectProfile(newId)
        }

        return profile
    }

    /**
     * Удалить профиль подписки
     */
    fun deleteProfile(profileId: String) {
        if (profileId == "default_demo") return // Встроенный демо-профиль удалить нельзя

        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles.removeAll { it.id == profileId }
        _profiles.value = currentProfiles

        profileProxies.remove(profileId)

        // Если удалили активный профиль, переключаемся на дефолтный
        if (_selectedProfile.value?.id == profileId) {
            selectProfile("default_demo")
        }
    }

    /**
     * Выставить активный профиль подписки
     */
    fun selectProfile(profileId: String) {
        val mappedProfiles = _profiles.value.map {
            it.copy(isSelected = (it.id == profileId))
        }
        _profiles.value = mappedProfiles
        val activeProj = mappedProfiles.find { it.isSelected }
        _selectedProfile.value = activeProj

        // Загружаем прокси этого профиля в реактивный StateFlow
        val profileServers = profileProxies[profileId] ?: mutableListOf()
        _proxies.value = profileServers
        _selectedProxy.value = profileServers.firstOrNull()
    }

    /**
     * Обновить список прокси определенного профиля (например при обновлении подписки)
     */
    fun updateProfileProxies(profileId: String, newProxies: List<VpnProxy>) {
        profileProxies[profileId] = newProxies.toMutableList()

        // Обновляем счетчик серверов в профиле
        val updatedProfiles = _profiles.value.map {
            if (it.id == profileId) {
                it.copy(proxiesCount = newProxies.size, lastUpdated = System.currentTimeMillis())
            } else {
                it
            }
        }
        _profiles.value = updatedProfiles

        // Если это текущий активный профиль — обновляем и proxies StateFlow
        if (_selectedProfile.value?.id == profileId) {
            _selectedProfile.value = updatedProfiles.find { it.id == profileId }
            _proxies.value = newProxies
            _selectedProxy.value = newProxies.firstOrNull()
        }
    }

    fun selectProxy(proxy: VpnProxy) {
        _selectedProxy.value = proxy
    }

    /**
     * Добавить прокси в текущий выбранный профиль (ручное добавление)
     */
    fun addProxy(proxy: VpnProxy) {
        val activeProfileId = _selectedProfile.value?.id ?: return
        val currentProfileServers = profileProxies[activeProfileId] ?: mutableListOf()
        currentProfileServers.add(proxy)

        // Обновляем список proxies в Flow
        _proxies.value = currentProfileServers.toList()

        // Обновляем счетчик прокси в профиле
        val updatedProfiles = _profiles.value.map {
            if (it.id == activeProfileId) {
                it.copy(proxiesCount = currentProfileServers.size)
            } else {
                it
            }
        }
        _profiles.value = updatedProfiles

        if (_selectedProxy.value == null) {
            _selectedProxy.value = proxy
        }
    }

    /**
     * Удалить конкретную прокси из активного профиля
     */
    fun deleteProxy(proxyId: String) {
        val activeProfileId = _selectedProfile.value?.id ?: return
        val currentProfileServers = profileProxies[activeProfileId] ?: mutableListOf()
        currentProfileServers.removeAll { it.id == proxyId }

        _proxies.value = currentProfileServers.toList()

        // Обновляем счетчик прокси в профиле
        val updatedProfiles = _profiles.value.map {
            if (it.id == activeProfileId) {
                it.copy(proxiesCount = currentProfileServers.size)
            } else {
                it
            }
        }
        _profiles.value = updatedProfiles

        if (_selectedProxy.value?.id == proxyId) {
            _selectedProxy.value = currentProfileServers.firstOrNull()
        }
    }

    /**
     * Генерирует итоговый конфигурационный файл YAML на основе выбранного прокси
     */
    fun getActiveConfigContent(dnsServer: String = "1.1.1.1"): String {
        val selected = _selectedProxy.value ?: return ""
        return MihomoConfigGenerator.generateConfig(_proxies.value, selected, dnsServer)
    }
}
