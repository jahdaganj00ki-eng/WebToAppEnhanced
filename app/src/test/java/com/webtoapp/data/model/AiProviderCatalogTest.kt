package com.webtoapp.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.webtoapp.core.ai.AiApiClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiProviderCatalogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val client = AiApiClient(context)

    @Test
    fun `every provider category is capped at three entries`() {
        // Issue: the AI settings catalog had grown to 32 providers. The trim policy caps
        // each catalog category at three well-known entries. International was expanded to
        // ten entries with headroom for two more, so its cap is raised to 12; all other
        // non-CUSTOM categories stay at three.
        val counts = AiProvider.entries.groupBy { it.category }.mapValues { (_, v) -> v.size }
        counts.forEach { (category, count) ->
            if (category != ProviderCategory.CUSTOM) {
                assertThat(count).isAtMost(12)
            }
        }
        assertThat(counts[ProviderCategory.RECOMMENDED]).isAtMost(3)
        assertThat(counts[ProviderCategory.INTERNATIONAL]).isAtMost(12)
        assertThat(counts[ProviderCategory.AGGREGATOR]).isAtMost(3)
        assertThat(counts[ProviderCategory.CHINESE]).isAtMost(3)
    }

    @Test
    fun `removed providers expose their legacy base urls for migration`() {
        // GROQ is an active provider again and no longer in REMOVED_PROVIDER_BASE_URLS;
        // its legacy base was migrated to CUSTOM (see AiConfig.REMOVED_PROVIDER_BASE_URLS).
        assertThat(AiProvider.REMOVED_PROVIDER_BASE_URLS).doesNotContainKey("GROQ")
        assertThat(AiProvider.REMOVED_PROVIDER_BASE_URLS).doesNotContainKey("OPENAI")
    }

    @Test
    fun `international providers carry the expected provider shape`() {
        val international = AiProvider.entries.filter { it.category == ProviderCategory.INTERNATIONAL }
        val expected = mapOf(
            AiProvider.GROQ to "https://api.groq.com/openai/v1",
            AiProvider.NVIDIA_NIM to "https://integrate.api.nvidia.com/v1",
            AiProvider.CLEAN_APIS to "https://cleanapis.com/v1",
            AiProvider.APINEX_BOND to "https://api.apinex.bond/v1",
            AiProvider.RADOX_AI to "https://radoxai.com/v1",
            AiProvider.KILO_GATEWAY to "https://ai-gateway.kilo.ai/api/v1",
            AiProvider.KIOS_API to "https://kiosapi.com/v1/",
        )
        assertThat(international.map { it.name }).containsExactlyElementsIn(expected.keys.map { it.name })
        for ((provider, baseUrl) in expected) {
            assertThat(provider.baseUrl).isEqualTo(baseUrl)
            assertThat(provider.modelsEndpoint).isEqualTo("/v1/models")
            assertThat(provider.requiresApiKey).isTrue()
            assertThat(provider.category).isEqualTo(ProviderCategory.INTERNATIONAL)
        }
        val sixProbed = listOf(AiProvider.GROQ, AiProvider.NVIDIA_NIM, AiProvider.CLEAN_APIS,
            AiProvider.APINEX_BOND, AiProvider.RADOX_AI, AiProvider.KILO_GATEWAY)
        for (provider in sixProbed) {
            assertThat(provider.apiKeyUrl).isNotBlank()
        }
        assertThat(AiProvider.KIOS_API.apiKeyUrl).isEmpty()
    }

    @Test
    fun `buildApiUrl joins base and endpoint without duplicated path segments`() {
        assertThat(client.buildApiUrl("https://cleanapis.com/v1", "/v1/models"))
            .isEqualTo("https://cleanapis.com/v1/models")
        assertThat(client.buildApiUrl("https://kiosapi.com/v1/", "/v1/models"))
            .isEqualTo("https://kiosapi.com/v1/models")
        assertThat(client.buildApiUrl("https://ai-gateway.kilo.ai/api/v1", "/v1/chat/completions"))
            .isEqualTo("https://ai-gateway.kilo.ai/api/v1/chat/completions")
        assertThat(client.buildApiUrl("https://api.groq.com/openai/v1", "/v1/chat/completions"))
            .isEqualTo("https://api.groq.com/openai/v1/chat/completions")
        // No regression for bare host bases (OpenAI etc.).
        assertThat(client.buildApiUrl("https://api.openai.com", "/v1/chat/completions"))
            .isEqualTo("https://api.openai.com/v1/chat/completions")
    }

    @Test
    fun `custom chat endpoint follows the selected api format`() {
        fun config(format: ApiFormat, override: String? = null) = ApiKeyConfig(
            provider = AiProvider.CUSTOM,
            apiKey = "sk-test",
            baseUrl = "https://api.example.com",
            customChatEndpoint = override,
            apiFormat = format
        )

        assertThat(config(ApiFormat.OPENAI_COMPATIBLE).getEffectiveChatEndpoint())
            .isEqualTo("/v1/chat/completions")
        assertThat(config(ApiFormat.ANTHROPIC).getEffectiveChatEndpoint())
            .isEqualTo("/v1/messages")
        assertThat(config(ApiFormat.OPENAI_RESPONSES).getEffectiveChatEndpoint())
            .isEqualTo("/v1/responses")
        // An explicit endpoint override always wins over the format default.
        assertThat(config(ApiFormat.ANTHROPIC, override = "/api/messages").getEffectiveChatEndpoint())
            .isEqualTo("/api/messages")
    }

    @Test
    fun `glm chat endpoint is the v4 completions path`() {
        val config = ApiKeyConfig(provider = AiProvider.GLM, apiKey = "sk-test")
        assertThat(config.getEffectiveModelsEndpoint()).isEqualTo("/v4/models")
        assertThat(config.getEffectiveChatEndpoint()).isEqualTo("/v4/chat/completions")
        assertThat(config.copy(customChatEndpoint = "/custom/chat").getEffectiveChatEndpoint())
            .isEqualTo("/custom/chat")
    }

    @Test
    fun `api format display names carry the wire endpoints`() {
        assertThat(ApiFormat.ANTHROPIC.displayName).contains("/v1/messages")
        assertThat(ApiFormat.OPENAI_COMPATIBLE.displayName).contains("/chat/completions")
        assertThat(ApiFormat.OPENAI_RESPONSES.displayName).contains("/responses")
    }
}
