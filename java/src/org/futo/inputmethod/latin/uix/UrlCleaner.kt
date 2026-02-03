package org.futo.inputmethod.latin.uix

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
* URL cleaning provider from the clearURLs ruleset
*
* @property urlPattern Regex pattern to match URLs this provider applies to
* @property completeProvider If `true`, entire URL should be blocked (pixels/beacons)
* @property rules List of regex patterns matching query parameter NAMES to remove (e.g. "utm_source")
* @property rawRules List of regex patterns applied to the URL path itself, not query params
* @property referralMarketing Additional query parameter patterns to remove, for affiliate/referrals
* @property exceptions List of regex patterns for URLs that should NOT be cleaned
* */
@Serializable
data class ClearUrlsProvider (
    val urlPattern: String,
    val completeProvider: Boolean = false,
    val rules: List<String> = emptyList(),
    val rawRules: List<String> = emptyList(),
    val referralMarketing: List<String> = emptyList(),
    val exceptions: List<String> = emptyList()
)

@Serializable
data class ClearUrlsRules(
    val providers: Map<String, ClearUrlsProvider>
)


/**
 * Singleton utility for removing tracking parameters from URLs.
 *
 * Uses the ClearURLs ruleset from `assets/url-rules.json` to identify and strip
 * tracking parameters (utm_*, fbclid, etc.) and referral marketing tags from URLs.
 *
 * Must call [init] with a [Context] before using [cleanUrl].
 *
 * Example usage:
 * ```
 * UrlCleaner.init(context)
 * val cleaned = UrlCleaner.cleanUrl("https://example.com?utm_source=twitter&id=123")
 * // Result: "https://example.com?id=123"
 * ```
 *
 * @see ClearUrlsProvider for the rule format
 * @see ClearUrlsRules for the JSON structure
 */
object UrlCleaner {
    // Helper class to hold pre-compiled regex
    // Based on /assets/url-rules.json from clearURLs rules
    private data class CompiledProvider(
        val name: String,
        val urlPattern: Regex,
        val rules: List<Regex>,
        val referralMarketing: List<Regex>,
        val rawRules: List<Regex>,
        val exceptions: List<Regex>
    )

    // Cached data for performance
    private var rules: ClearUrlsRules? = null;
    private var compiledProviders: List<CompiledProvider>? = null;

    public fun init(context: Context) {
        if (rules != null) return;

        // Read JSON from assets
        val jsonString = context.assets.open("url-rules.json")
                                .bufferedReader()
                                .use { it.readText() };

        // Parse JSON into data class
        val json = Json { ignoreUnknownKeys = true };
        rules = json.decodeFromString<ClearUrlsRules>(jsonString);

        // Pre-compile all regex for better performance
        // NOTE: !! here should be safe since we already checked if rules != null
        compiledProviders = rules!!.providers.map { (name, provider) ->
            CompiledProvider(
                name = name,
                urlPattern = provider.urlPattern.toRegex(RegexOption.IGNORE_CASE),
                rules = provider.rules.map {it.toRegex(RegexOption.IGNORE_CASE)},
                referralMarketing = provider.referralMarketing.map { it.toRegex(RegexOption.IGNORE_CASE) },
                rawRules = provider.rawRules.map {it.toRegex(RegexOption.IGNORE_CASE)},
                exceptions = provider.exceptions.map {it.toRegex(RegexOption.IGNORE_CASE)}
            )
        };
    }

    fun cleanUrl(text: String): String {
        // Early exit, not initialized
        if(compiledProviders == null) return text;

        // Only clean if the text is a single URL (with optional surrounding whitespaces)
        val trimmed = text.trim();
        val urlRegex = """https?://\S+""".toRegex();

        if (!trimmed.matches(urlRegex)) return text;

        return cleanSingleUrl(trimmed);
    }

    private fun cleanSingleUrl(url: String): String {
        // Find all matching providers
        val matchingProviders = compiledProviders?.filter {
            it.urlPattern.containsMatchIn(url)
        } ?: emptyList()

        // Prefer specific providers over globalRules
        val provider = matchingProviders.find { it.name != "globalRules" }
            ?: matchingProviders.find { it.name == "globalRules" }
            ?: return url

        // Check for exceptions, if found skip cleaning
        if (provider.exceptions.any { it.containsMatchIn((url)) })
            return url

        // Finally parse into parts
        val uri = Uri.parse(url);

        // Split query params and filter out trackers
        val queryParams = uri.queryParameterNames;
        val cleanParams = mutableListOf<Pair<String, String>>();

        for(paramName in queryParams) {
            val shouldRemove = provider.rules.any { it.matches(paramName) }
                            || provider.referralMarketing.any { it.matches(paramName) };

            if (!shouldRemove) {
                // We keep this param
                val value = uri.getQueryParameter(paramName);
                if (value != null)
                    cleanParams.add(paramName to value);
            }
        }

        // Rebuild URL without tracking
        val builder = uri.buildUpon().clearQuery();
        for ((name, value) in cleanParams) {
            builder.appendQueryParameter(name, value);
        }

        var cleanedUrl = builder.build().toString();

        // Apply rawRules
        for (rawRule in provider.rawRules) {
            cleanedUrl = rawRule.replace(cleanedUrl, "");
        }

        return cleanedUrl;
    }
}
