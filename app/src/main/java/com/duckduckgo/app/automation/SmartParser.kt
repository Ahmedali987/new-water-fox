/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.automation

object SmartParser {

    // ---------------------------------------------------------------------
    // Regex patterns
    // ---------------------------------------------------------------------

    // Email — standard
    private val emailRegex = Regex(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
    )

    // Phone — international-friendly, 8–15 digits with optional separators
    private val phoneRegex = Regex(
        "(?:\\+\\d{1,3}[\\s\\-.]?)?(?:\\(\\d{1,4}\\)[\\s\\-.]?)?\\d{2,4}[\\s\\-.]?\\d{3,4}[\\s\\-.]?\\d{3,4}",
    )

    // Dates — dd/mm/yyyy, dd-mm-yyyy, dd.mm.yyyy, yyyy-mm-dd, dd mon yyyy
    private val dateRegex = Regex(
        "\\b(?:\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}|\\d{4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}|\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4})\\b",
        RegexOption.IGNORE_CASE,
    )

    // URL
    private val urlRegex = Regex(
        "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        RegexOption.IGNORE_CASE,
    )

    // Username / handle
    private val handleRegex = Regex("(?<![a-zA-Z0-9_])@([a-zA-Z0-9_.]{3,32})")

    // Currency amounts
    private val currencyRegex = Regex(
        "(?:[$₹€£¥]\\s?\\d{1,3}(?:[,.]\\d{3})*(?:\\.\\d{1,2})?|\\b\\d{1,3}(?:[,.]\\d{3})*(?:\\.\\d{1,2})?\\s?(?:USD|INR|EUR|GBP|JPY|AUD|CAD))",
        RegexOption.IGNORE_CASE,
    )

    // Long numeric ID (6+ digits, not a phone/date)
    private val idRegex = Regex("\\b\\d{6,}\\b")

    // Pincode / ZIP
    private val pincodeRegex = Regex("\\b(?:\\d{5}|\\d{6}|[A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)\\b")

    // ---------------------------------------------------------------------
    // Structured output
    // ---------------------------------------------------------------------

    data class ParsedData(
        val emails: List<String> = emptyList(),
        val phones: List<String> = emptyList(),
        val dates: List<String> = emptyList(),
        val names: List<String> = emptyList(),
        val firstNames: List<String> = emptyList(),
        val lastNames: List<String> = emptyList(),
        val addresses: List<String> = emptyList(),
        val cities: List<String> = emptyList(),
        val states: List<String> = emptyList(),
        val countries: List<String> = emptyList(),
        val zipCodes: List<String> = emptyList(),
        val urls: List<String> = emptyList(),
        val usernames: List<String> = emptyList(),
        val currencies: List<String> = emptyList(),
        val ids: List<String> = emptyList(),
        val companyNames: List<String> = emptyList(),
        val jobTitles: List<String> = emptyList(),
        val genders: List<String> = emptyList(),
        val miscText: List<String> = emptyList(),
    ) {

        /** Flat list of (fieldKey, value) pairs ready for the vault. */
        val all: List<Pair<String, String>>
            get() = buildList {
                emails.forEach { add("email" to it) }
                phones.forEach { add("phone" to it) }
                dates.forEach { add("dob" to it) }
                names.forEach { add("fullName" to it) }
                firstNames.forEach { add("firstName" to it) }
                lastNames.forEach { add("lastName" to it) }
                addresses.forEach { add("address" to it) }
                cities.forEach { add("city" to it) }
                states.forEach { add("state" to it) }
                countries.forEach { add("country" to it) }
                zipCodes.forEach { add("zip" to it) }
                urls.forEach { add("website" to it) }
                usernames.forEach { add("username" to it) }
                currencies.forEach { add("amount" to it) }
                ids.forEach { add("id" to it) }
                companyNames.forEach { add("company" to it) }
                jobTitles.forEach { add("jobTitle" to it) }
                genders.forEach { add("gender" to it) }
                miscText.forEach { add("text" to it) }
            }

        val isEmpty: Boolean
            get() = all.isEmpty()

        val totalCount: Int
            get() = all.size
    }

    // ---------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------

    fun parse(rawText: String): ParsedData {
        if (rawText.isBlank()) return ParsedData()

        val text = rawText.replace("\r\n", "\n")
        val lower = text.lowercase()

        val emails = distinct(emailRegex.findAll(text).map { it.value.trim() })

        // Phones: filter to plausible numbers, drop date-likes and short ids
        val phones = distinct(
            phoneRegex.findAll(text)
                .map { it.value.trim() }
                .filter { digitsOnly(it).length in 8..15 }
                .filterNot { it.matches(Regex("\\d{4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}")) },
        )

        val dates = distinct(dateRegex.findAll(text).map { it.value.trim() })
        val urls = distinct(urlRegex.findAll(text).map { it.value.trim() })
        val handles = distinct(handleRegex.findAll(text).map { it.groupValues[1].trim() })
        val currencies = distinct(currencyRegex.findAll(text).map { it.value.trim() })
        val zips = distinct(pincodeRegex.findAll(text).map { it.value.trim() })
        val ids = distinct(idRegex.findAll(text).map { it.value.trim() })
            .filterNot { it in phones.map { p -> digitsOnly(p) } }
            .take(10)

        val names = extractNames(text)
        val firstNames = names.map { it.split(" ").firstOrNull() ?: it }.distinct()
        val lastNames = names.mapNotNull { it.split(" ").drop(1).joinToString(" ").ifBlank { null } }.distinct()

        val addresses = extractAddresses(text)
        val cities = extractCities(text)
        val states = extractStates(text)
        val countries = extractCountries(text)
        val companies = extractCompanies(text)
        val jobTitles = extractJobTitles(text)
        val genders = extractGenders(text)

        val miscText = extractMisc(text)

        return ParsedData(
            emails = emails,
            phones = phones,
            dates = dates,
            names = names,
            firstNames = firstNames,
            lastNames = lastNames,
            addresses = addresses,
            cities = cities,
            states = states,
            countries = countries,
            zipCodes = zips,
            urls = urls,
            usernames = handles,
            currencies = currencies,
            ids = ids,
            companyNames = companies,
            jobTitles = jobTitles,
            genders = genders,
            miscText = miscText,
        )
    }

    // ---------------------------------------------------------------------
    // Extractors
    // ---------------------------------------------------------------------

    private fun extractNames(text: String): List<String> {
        val results = mutableListOf<String>()

        // "My name is X", "I am X", "this is X"
        val patterns = listOf(
            Regex("(?i)(?:my name is|i am|i'm|this is|name[\\s:=\\-]+)\\s*([A-Z][a-z]{1,20}(?:\\s+[A-Z][a-z]{1,20}){0,3})"),
            Regex("(?i)(?:mr|mrs|ms|miss|dr|prof)\\.?\\s+([A-Z][a-z]{1,20}(?:\\s+[A-Z][a-z]{1,20}){0,3})"),
            Regex("(?m)^([A-Z][a-z]{1,20}(?:\\s+[A-Z][a-z]{1,20}){1,3})$"),
        )

        patterns.forEach { regex ->
            regex.findAll(text).forEach { match ->
                val name = match.groupValues[1].trim()
                if (name.length in 3..50 && !isCommonWord(name)) results.add(name)
            }
        }
        return results.distinct().take(20)
    }

    private fun extractAddresses(text: String): List<String> {
        val keywords = listOf(
            "street", "st.", "avenue", "ave", "road", "rd", "lane", "ln",
            "block", "sector", "nagar", "colony", "apartment", "apt",
            "floor", "building", "bldg", "house", "flat", "plot", "phase",
        )
        val results = mutableListOf<String>()
        text.split("\n", ";").forEach { line ->
            val lower = line.lowercase()
            if (keywords.any { lower.contains(it) } && line.trim().length in 10..200) {
                results.add(line.trim())
            }
        }
        return results.distinct().take(10)
    }

    private fun extractCities(text: String): List<String> {
        val known = listOf(
            "mumbai", "delhi", "bangalore", "bengaluru", "hyderabad", "chennai",
            "kolkata", "pune", "ahmedabad", "jaipur", "lucknow", "kanpur",
            "nagpur", "indore", "bhopal", "surat", "kochi", "cochin",
            "new york", "london", "paris", "tokyo", "dubai", "singapore",
            "los angeles", "chicago", "toronto", "sydney", "berlin",
        )
        val found = mutableListOf<String>()
        val lower = text.lowercase()
        known.forEach { city ->
            if (lower.contains(city)) found.add(city.replaceFirstChar { it.uppercase() })
        }
        return found.distinct()
    }

    private fun extractStates(text: String): List<String> {
        val known = listOf(
            "maharashtra", "karnataka", "tamil nadu", "delhi", "gujarat",
            "rajasthan", "uttar pradesh", "west bengal", "kerala", "telangana",
            "andhra pradesh", "punjab", "haryana", "bihar", "madhya pradesh",
            "california", "texas", "florida", "new york", "washington",
        )
        val found = mutableListOf<String>()
        val lower = text.lowercase()
        known.forEach { state ->
            if (lower.contains(state)) found.add(state.replaceFirstChar { it.uppercase() })
        }
        return found.distinct()
    }

    private fun extractCountries(text: String): List<String> {
        val known = listOf(
            "india", "usa", "united states", "united kingdom", "canada",
            "australia", "germany", "france", "japan", "china", "brazil",
            "singapore", "uae", "united arab emirates", "saudi arabia",
            "pakistan", "bangladesh", "sri lanka", "nepal", "russia",
        )
        val found = mutableListOf<String>()
        val lower = text.lowercase()
        known.forEach { country ->
            if (lower.contains(country)) found.add(country.replaceFirstChar { it.uppercase() })
        }
        return found.distinct()
    }

    private fun extractCompanies(text: String): List<String> {
        val patterns = listOf(
            Regex("(?i)(?:company|worked at|employed at|organization)[\\s:=\\-]+([A-Z][\\w&.\\- ]{2,40})"),
            Regex("\\b([A-Z][\\w\\-]*(?:\\s+[A-Z][\\w\\-]*)*\\s+(?:Inc|LLC|Ltd|Pvt|Corp|Corporation|Limited|GmbH|Pte))\\.?\\b"),
        )
        val results = mutableListOf<String>()
        patterns.forEach { regex ->
            regex.findAll(text).forEach { results.add(it.groupValues[1].trim()) }
        }
        return results.distinct().take(10)
    }

    private fun extractJobTitles(text: String): List<String> {
        val titles = listOf(
            "engineer", "developer", "manager", "designer", "analyst",
            "consultant", "architect", "scientist", "director", "ceo",
            "cto", "founder", "intern", "associate", "executive",
            "accountant", "teacher", "doctor", "nurse", "lawyer",
        )
        val results = mutableListOf<String>()
        text.split("\n").forEach { line ->
            val lower = line.lowercase()
            titles.forEach { t ->
                if (lower.contains(t) && line.length < 80) {
                    results.add(line.trim())
                }
            }
        }
        return results.distinct().take(10)
    }

    private fun extractGenders(text: String): List<String> {
        val results = mutableListOf<String>()
        val lower = text.lowercase()
        if (Regex("\\b(male|man|boy)\\b").containsMatchIn(lower)) results.add("Male")
        if (Regex("\\b(female|woman|girl)\\b").containsMatchIn(lower)) results.add("Female")
        if (Regex("\\b(other|non-binary|nonbinary)\\b").containsMatchIn(lower)) results.add("Other")
        return results
    }

    private fun extractMisc(text: String): List<String> {
        // Any non-empty line that wasn't captured by stronger extractors
        return text.lines()
            .map { it.trim() }
            .filter { it.length in 3..80 }
            .filterNot { it.contains("@") || it.matches(Regex(".*\\d{6,}.*")) }
            .distinct()
            .take(20)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun <T> distinct(seq: Sequence<T>): List<T> = seq.distinct().toList()

    private fun digitsOnly(s: String): String = s.filter { it.isDigit() }

    private fun isCommonWord(name: String): Boolean {
        val common = setOf(
            "the", "and", "for", "with", "from", "this", "that", "have",
            "will", "your", "about", "which", "there", "their", "what",
        )
        return name.lowercase() in common
    }
}