package com.example.clausehawk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContractEngine {

    suspend fun analyzeContract(
        text: String,
        customDealbreakers: List<String> = emptyList()
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val trimmed = text.trim()
        val lowerText = trimmed.lowercase()

        if (!isLegitimateContractText(trimmed, lowerText)) {
            return@withContext AnalysisResult(
                riskLevel = RiskLevel.INVALID,
                summaryEn = "Unreadable or non-contract document detected. Please scan an actual legal agreement.",
                summaryHi = "गैर-अनुबंध या अपठनीय दस्तावेज़ पाया गया। कृपया एक वास्तविक कानूनी समझौता स्कैन करें।",
                summaryKn = "ಅಪಠ್ಯ ಅಥವಾ ಒಪ್ಪಂದವಲ್ಲದ ದಾಖಲೆ ಪತ್ತೆಯಾಗಿದೆ. ದಯವಿಟ್ಟು ನಿಜವಾದ ಕಾನೂನು ಒಪ್ಪಂದವನ್ನು ಸ್ಕ್ಯಾನ್ ಮಾಡಿ.",
                redFlags = emptyList(),
                isInvalid = true
            )
        }

        val redFlags = mutableListOf<RedFlag>()
        val deadlines = mutableListOf<DeadlineObligation>()
        val financialExposures = mutableListOf<FinancialExposure>()
        val statutoryVoidabilities = mutableListOf<StatutoryVoidability>()
        val clauseBreakdowns = mutableListOf<ProblemSolutionBreakdown>()
        val ambiguities = mutableListOf<AmbiguityTerm>()
        val dealbreakerMatches = mutableListOf<DealbreakerMatch>()
        val preSigningChecklist = mutableListOf<PreSigningCheckItem>()

        // Clean OCR artifacts: fix word-break hyphens across newlines and normalize spacing
        val normalizedText = trimmed.replace(Regex("(\\w+)-\\s*\\n\\s*(\\w+)"), "$1$2")
        val normLower = normalizedText.lowercase()

        // Helper to find the first keyword actually present in the text
        fun findMatchingKeyword(vararg keywords: String): String? {
            return keywords.firstOrNull { normLower.contains(it.lowercase()) || lowerText.contains(it.lowercase()) }
        }

        val rawSentences = normalizedText.split(Regex("(?<=[.;\\n])\\s+"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

        fun isHeading(s: String): Boolean {
            val cleaned = s.replace(Regex("^\\s*(?:section|clause|\\d+)[\\.\\)]?\\s*", RegexOption.IGNORE_CASE), "").trim()
            val words = cleaned.split(Regex("\\s+"))
            return words.size <= 5 || cleaned.length < 40
        }

        // Helper to extract clean, unbroken quotes without cutting off mid-word or returning lone titles
        fun extractQuote(keyword: String): String {
            val target = keyword.lowercase()
            val matchingIndices = rawSentences.indices.filter { rawSentences[it].lowercase().contains(target) }

            if (matchingIndices.isNotEmpty()) {
                for (idx in matchingIndices) {
                    val s = rawSentences[idx]
                    if (isHeading(s)) {
                        // If heading, check if the subsequent sentence contains the actual clause body
                        if (idx + 1 < rawSentences.size) {
                            val nextS = rawSentences[idx + 1]
                            if (nextS.length >= 20) {
                                return "$s $nextS"
                            }
                        }
                    } else {
                        // Substantive sentence: check if the preceding item was a heading
                        if (idx > 0 && isHeading(rawSentences[idx - 1])) {
                            return "${rawSentences[idx - 1]} $s"
                        }
                        return s
                    }
                }
                return rawSentences[matchingIndices.first()]
            }

            val targetIdx = normLower.indexOf(target)
            val sourceText = if (targetIdx != -1) normalizedText else trimmed
            val searchLower = if (targetIdx != -1) normLower else lowerText
            val idx = searchLower.indexOf(target)

            if (idx == -1) return ""

            // Fallback: Word-boundary-aware expansion
            var start = idx
            while (start > 0 && sourceText[start - 1] != '.' && sourceText[start - 1] != '\n' && (idx - start) < 250) {
                start--
            }
            while (start > 0 && start < sourceText.length && !sourceText[start - 1].isWhitespace() && sourceText[start - 1] != '.' && sourceText[start - 1] != '\n') {
                start--
            }

            var end = idx + target.length
            while (end < sourceText.length && sourceText[end] != '.' && sourceText[end] != '\n' && (end - idx) < 350) {
                end++
            }
            while (end < sourceText.length && !sourceText[end].isWhitespace() && sourceText[end] != '.' && sourceText[end] != '\n') {
                end++
            }

            if (end < sourceText.length && sourceText[end] == '.') {
                end++
            }

            var snippet = sourceText.substring(start, minOf(end, sourceText.length)).replace(Regex("\\s+"), " ").trim()
            if (start > 0 && !snippet.startsWith(".")) snippet = "...$snippet"
            if (end < sourceText.length && !snippet.endsWith(".")) snippet = "$snippet..."
            return snippet
        }

        // Custom Dealbreaker Rules
        for (rule in customDealbreakers) {
            val ruleClean = rule.trim()
            if (ruleClean.isNotBlank() && (lowerText.contains(ruleClean.lowercase()) || normLower.contains(ruleClean.lowercase()))) {
                dealbreakerMatches.add(
                    DealbreakerMatch(
                        ruleKeyword = ruleClean,
                        matchedContext = extractQuote(ruleClean).ifBlank { ruleClean }
                    )
                )
            }
        }

        // Statutory Voidability 1: Non-Compete (Section 27 Indian Contract Act)
        val nonCompeteKw = findMatchingKeyword("non-compete", "restraint of trade", "shall not engage", "not engage in")
        if (nonCompeteKw != null) {
            val quote = extractQuote(nonCompeteKw)
            statutoryVoidabilities.add(
                StatutoryVoidability(
                    actSection = "Section 27, Indian Contract Act 1872",
                    titleEn = "Restraint of Trade / Employment Non-Compete",
                    titleHi = "व्यापार प्रतिबंध / रोजगार गैर-प्रतिस्पर्धा",
                    titleKn = "ವ್ಯಾಪಾರ ನಿರ್ಬಂಧ / ಉದ್ಯೋಗೇತರ ಸ್ಪರ್ಧೆ",
                    legalReasonEn = "Post-employment non-compete clauses are legally void under Indian law.",
                    legalReasonHi = "भारतीय कानून के तहत रोजगार के बाद गैर-प्रतिस्पर्धा की शर्तें कानूनी रूप से अमान्य हैं।",
                    legalReasonKn = "ಭಾರತೀಯ ಕಾನೂನಿನ ಪ್ರಕಾರ ಉದ್ಯೋಗದ ನಂತರದ ಸ್ಪರ್ಧಾತ್ಮಕವಲ್ಲದ ಶರತ್ತುಗಳು ಅಮಾನ್ಯವಾಗಿವೆ.",
                    quoteSnippet = quote
                )
            )
            clauseBreakdowns.add(
                ProblemSolutionBreakdown(
                    originalSnippet = quote,
                    problemEn = "Restricts your lawful right to engage in business, trade, or employment after termination.",
                    problemHi = "समाप्ति के बाद व्यवसाय, व्यापार या रोजगार में संलग्न होने के आपके कानूनी अधिकार को प्रतिबंधित करता है।",
                    problemKn = "ಒಪ್ಪಂದದ ನಂತರ ನಿಮ್ಮ ವೃತ್ತಿ ಅಥವಾ ಉದ್ಯೋಗದ ಹಕ್ಕನ್ನು ನಿರ್ಬಂಧಿಸುತ್ತದೆ.",
                    solutionEn = "Strike down post-term non-compete completely or limit strictly to non-solicitation of active clients.",
                    solutionHi = "गैर-प्रतिस्पर्धा क्लॉज को पूरी तरह से हटाएं या इसे केवल सक्रिय ग्राहकों तक सीमित रखें।",
                    solutionKn = "ಈ ಶರತ್ತನ್ನು ಪೂರ್ಣವಾಗಿ ತೆಗೆದುಹಾಕಿ ಅಥವಾ ಕೇವಲ ಪ್ರಸ್ತುತ ಗ್ರಾಹಕರಿಗೆ ಸೀಮಿತಗೊಳಿಸಿ.",
                    counterOfferDraft = "Proposed Counter-Clause: 'Nothing in this Agreement shall restrict either party from engaging in their trade or business, provided confidential information is maintained.'"
                )
            )
        }

        // Statutory Voidability 2: Restraint of Legal Proceedings (Section 28 Indian Contract Act)
        val jurisdictionKw = findMatchingKeyword("exclusive jurisdiction", "shall not approach court", "waive right", "jurisdiction", "waive")
        if (jurisdictionKw != null) {
            val quote = extractQuote(jurisdictionKw)
            statutoryVoidabilities.add(
                StatutoryVoidability(
                    actSection = "Section 28, Indian Contract Act 1872",
                    titleEn = "Restraint of Legal Proceedings",
                    titleHi = "कानूनी कार्यवाही पर रोक",
                    titleKn = "ಕಾನೂನು ನಡಾವಳಿಗಳ ನಿರ್ಬಂಧ",
                    legalReasonEn = "Clauses restricting statutory court rights or specifying exclusive remote courts are void.",
                    legalReasonHi = "अदालती अधिकारों को प्रतिबंधित करने वाली या दूरस्थ अदालतों का चयन करने वाली धारा अमान्य है।",
                    legalReasonKn = "ನ್ಯಾಯಾಲಯದ ಹಕ್ಕುಗಳನ್ನು ನಿರ್ಬಂಧಿಸುವ ಅಥವಾ ದೂರದ ನ್ಯಾಯಾಲಯವನ್ನು ಕಡ್ಡಾಯಗೊಳಿಸುವ ಶರತ್ತು ಅಮಾನ್ಯವಾಗಿದೆ.",
                    quoteSnippet = quote
                )
            )
        }

        // Vulnerability 3: Unbalanced Indemnity & Liability
        val indemnityKw = findMatchingKeyword("indemnif", "hold harmless", "liable", "indemnity", "liability")
        if (indemnityKw != null) {
            val quote = extractQuote(indemnityKw)
            clauseBreakdowns.add(
                ProblemSolutionBreakdown(
                    originalSnippet = quote,
                    problemEn = "Unbalanced liability clause shifts broad, uncapped financial risk for losses onto you.",
                    problemHi = "असंतुलित दायित्व धारा आपके ऊपर व्यापक, बिना सीमा वाला वित्तीय जोखिम डालती है।",
                    problemKn = "ಅಸಮತೋಲಿತ ಜವಾಬ್ದಾರಿ ಶರತ್ತು ನಿಮ್ಮ ಮೇಲೆ ಅಪರಿಮಿತ ಆರ್ಥಿಕ ಅಪಾಯವನ್ನು ಬೀರುತ್ತದೆ.",
                    solutionEn = "Insert a mutual liability cap limited to total fees paid in the preceding 12 months.",
                    solutionHi = "पिछले 12 महीनों में भुगतान किए गए कुल शुल्क तक सीमित परस्पर देयता सीमा (Liability Cap) शामिल करें।",
                    solutionKn = "ಕಳೆದ 12 ತಿಂಗಳುಗಳಲ್ಲಿ ಪಾವತಿಸಿದ ಒಟ್ಟು ಶುಲ್ಕಕ್ಕೆ ಸೀಮಿತವಾದ ಜವಾಬ್ದಾರಿ ಮಿತಿಯನ್ನು ಸೇರಿಸಿ.",
                    counterOfferDraft = "Proposed Counter-Clause: 'Mutual Liability Cap: Each party's maximum aggregate liability shall be capped at the total fees paid under this Agreement in the preceding 12 months.'"
                )
            )
            redFlags.add(
                RedFlag(
                    titleEn = "Unbalanced Indemnification",
                    titleHi = "असंतुलित क्षतिपूर्ति",
                    titleKn = "ಅಸಮತೋಲಿತ ನಷ್ಟ ಪರಿಹಾರ",
                    descEn = "Unilateral indemnification protecting the counterparty unconditionally.",
                    descHi = "दूसरी पार्टी को बिना शर्त सुरक्षित करने वाला असंतुलित दायित्व।",
                    descKn = "ಇತರ ಪಕ್ಷವನ್ನು ಶರತ್ತಿಲ್ಲದೆ ರಕ್ಷಿಸುವ ಅಸಮತೋಲಿತ ಜವಾಬ್ದಾರಿ.",
                    quoteSnippet = quote
                )
            )
        }

        // Vulnerability 4: Unilateral Termination & Cancellation Rights
        val terminationRegex = Regex(
            "\\b(?:right\\s+to\\s+terminate|may\\s+terminate|entitled\\s+to\\s+terminate|terminate(?:s)?\\s+(?:this\\s+)?agreement|immediate(?:ly)?\\s+terminate|terminate\\s+without\\s+cause|terminate\\s+for\\s+(?:convenience|cause|breach)|cancellation\\s+of\\s+(?:this\\s+)?agreement|termination\\s+for\\s+(?:convenience|cause)|termination\\s+at\\s+will)\\b",
            RegexOption.IGNORE_CASE
        )
        val termMatch = terminationRegex.find(normalizedText) ?: terminationRegex.find(trimmed)
        if (termMatch != null) {
            val quote = extractQuote(termMatch.value).ifBlank { termMatch.value }
            clauseBreakdowns.add(
                ProblemSolutionBreakdown(
                    originalSnippet = quote,
                    problemEn = "Unilateral or immediate termination rights create sudden operational instability.",
                    problemHi = "एकपक्षीय या तत्काल समाप्ति अधिकार अचानक व्यापार जोखिम और अनिश्चितता पैदा करते हैं।",
                    problemKn = "ಏಕಪಕ್ಷೀಯ ರದ್ದತಿ ಹಕ್ಕುಗಳು ತಕ್ಷಣದ ಕಾರ್ಯಚರಣೆಯ ಅಪಾಯವನ್ನು ಉಂಟುಮಾಡುತ್ತವೆ.",
                    solutionEn = "Require mutual 30-day written notice and a mandatory 15-day cure period for default.",
                    solutionHi = "रद्द करने से पहले 30 दिनों का लिखित नोटिस और 15 दिनों की सुधारात्मक अवधि (Cure Period) अनिवार्य करें।",
                    solutionKn = "ರದ್ದುಗೊಳಿಸುವ ಮುನ್ನ ಕಡ್ಡಾಯ 30 ದಿನಗಳ ಬರವಣಿಗೆಯ ನೋಟಿಸ್ ಮತ್ತು 15 ದಿನಗಳ ಕಾಲಾವಕಾಶ ಕೇಳಿ.",
                    counterOfferDraft = "Proposed Counter-Clause: 'Termination Notice: Either party may terminate this Agreement upon thirty (30) days' prior written notice, subject to a 15-day cure period for breach.'"
                )
            )
        }

        // Vulnerability 5: Broad IP Ownership & Work-for-Hire Assignment
        val ipKw = findMatchingKeyword("intellectual property", "work for hire", "assigns all rights", "ownership of deliverables")
        if (ipKw != null) {
            val quote = extractQuote(ipKw)
            clauseBreakdowns.add(
                ProblemSolutionBreakdown(
                    originalSnippet = quote,
                    problemEn = "Broad IP assignment transfers background tools, pre-existing assets, and rights prior to full payment.",
                    problemHi = "व्यापक आईपी असाइनमेंट पूर्ण भुगतान से पहले ही आपके टूल, संपत्ति और अधिकारों को स्थानांतरित कर देता है।",
                    problemKn = "ಪೂರ್ಣ ಪಾವತಿಗೆ ಮೊದಲೇ ನಿಮ್ಮ ಮೂಲ ಉಪಕರಣಗಳು ಮತ್ತು ಹಕ್ಕುಗಳನ್ನು ವರ್ಗಾಯಿಸುತ್ತದೆ.",
                    solutionEn = "Retain background IP tools and stipulate that deliverable IP transfers only upon full payment.",
                    solutionHi = "मूल आईपी टूल्स अपने पास रखें और स्पष्ट करें कि आईपी केवल पूर्ण भुगतान प्राप्त होने पर ही स्थानांतरित होगा।",
                    solutionKn = "ಹಿನ್ನೆಲೆ IP ಅನ್ನು ನಿಮ್ಮಲ್ಲಿಯೇ ಇರಿಸಿಕೊಳ್ಳಿ ಮತ್ತು ಸಂಪೂರ್ಣ ಪಾವತಿಯ ನಂತರವೇ IP ಹಸ್ತಾಂತರವಾಗುತ್ತದೆ ಎಂದು ಸ್ಪಷ್ಟಪಡಿಸಿ.",
                    counterOfferDraft = "Proposed Counter-Clause: 'IP Rights Transfer: Deliverables shall be assigned to Client only upon receipt of full and final payment. Vendor retains all rights to pre-existing background tools and code.'"
                )
            )
        }

        // Vulnerability 6: Unilateral Modifications / Policy Alteration
        val modKw = findMatchingKeyword("reserve the right", "from time to time", "modify", "amend")
        if (modKw != null) {
            val quote = extractQuote(modKw)
            clauseBreakdowns.add(
                ProblemSolutionBreakdown(
                    originalSnippet = quote,
                    problemEn = "Allows the counterparty to alter agreement terms, pricing, or schedules unilaterally without consent.",
                    problemHi = "दूसरी पार्टी को बिना आपकी सहमति के शर्तों, दरों या नीतियों को बदलने की अनुमति देता है।",
                    problemKn = "ನಿಮ್ಮ ಸಮ್ಮತಿಯಿಲ್ಲದೆ ಒಪ್ಪಂದದ ನಿಯಮಗಳು ಮತ್ತು ಬೆಲೆಗಳನ್ನು ಮಾರ್ಪಡಿಸಲು ಅನುಮತಿಸುತ್ತದೆ.",
                    solutionEn = "Require mutual written consent signed by both authorized representatives for all modifications.",
                    solutionHi = "सभी संशोधनों के लिए दोनों पक्षों के हस्ताक्षर वाले लिखित दस्तावेज़ को अनिवार्य करें।",
                    solutionKn = "ಎಲ್ಲಾ ತಿದ್ದುಪಡಿಗಳಿಗೆ ಎರಡೂ ಕಡೆಯವರ ಲಿಖಿತ ಸಹಿಯನ್ನು ಕಡ್ಡಾಯಗೊಳಿಸಿ.",
                    counterOfferDraft = "Proposed Counter-Clause: 'Amendments: No modification or amendment of this Agreement shall be effective unless made in writing and signed by authorized representatives of both parties.'"
                )
            )
        }

        // Vulnerability 7: Auto-Renewal & Lock-in Clauses
        val renewKw = findMatchingKeyword("automatically renew", "auto-renew", "perpetual renewal")
        if (renewKw != null) {
            val quote = extractQuote(renewKw)
            clauseBreakdowns.add(
                ProblemSolutionBreakdown(
                    originalSnippet = quote,
                    problemEn = "Automatic renewal locks you into extended contract terms and financial obligations unless cancelled early.",
                    problemHi = "स्वचालित नवीनीकरण आपको समय सीमा से पहले रद्द न करने पर लंबी वित्तीय देनदारियों में बांध देता है।",
                    problemKn = "ಸ್ವಯಂಚಾಲಿತ ನವೀಕರಣವು ನಿಮ್ಮನ್ನು ಹೆಚ್ಚುವರಿ ಶುಲ್ಕ ಮತ್ತು ಒಪ್ಪಂದದ ಚೌಕಟ್ಟಿನಲ್ಲಿ ಬಂಧಿಸುತ್ತದೆ.",
                    solutionEn = "Require annual explicit opt-in confirmation or a flexible 30-day non-renewal notice window.",
                    solutionHi = "वार्षिक स्पष्ट स्वीकृति (Opt-in) या 30 दिन का आसान गैर-नवीनीकरण नोटिस जोड़ें।",
                    solutionKn = "ವಾರ್ಷಿಕ ಸ್ಪಷ್ಟ ಒಪ್ಪಿಗೆ ಅಥವಾ 30 ದಿನಗಳ ನೋಟಿಸ್ ಮೂಲಕ ರದ್ದುಗೊಳಿಸುವ ಅವಕಾಶವನ್ನು ಸೇರಿಸಿ.",
                    counterOfferDraft = "Proposed Counter-Clause: 'Term Renewal: This Agreement shall renew only upon explicit written agreement of both parties at least 30 days prior to the expiration of the current term.'"
                )
            )
        }

        // Deadlines & Timeframe Obligations
        val durationRegex = Regex("\\b\\d+\\s*(?:days|months|years|weeks|hours)\\b", RegexOption.IGNORE_CASE)
        val durationMatches = durationRegex.findAll(normalizedText).take(4).toList()
        durationMatches.forEach { match ->
            deadlines.add(
                DeadlineObligation(
                    timeframe = match.value,
                    obligationEn = "Required notice period or operational deadline clause.",
                    obligationHi = "आवश्यक नोटिस अवधि या परिचालन समय सीमा।",
                    obligationKn = "ಅಗತ್ಯ ನೋಟಿಸ್ ಅವಧಿ ಅಥವಾ ಸಮಯದ ಜವಾಬ್ದಾರಿ.",
                    quoteSnippet = extractQuote(match.value).ifBlank { match.value }
                )
            )
        }

        // Financial Exposures & Fees (bounded words so 'lecturers. 4' never parses as Rs. 4)
        val amountRegex = Regex(
            "(?:\\b(?:rs\\.?|inr|usd)\\b|[$₹€£])\\s*\\d+(?:,\\d+)*(?:\\.\\d+)?|\\b\\d+\\s*(?:percent|%|per annum)\\b",
            RegexOption.IGNORE_CASE
        )
        val moneyMatches = amountRegex.findAll(normalizedText).take(4).toList()
        moneyMatches.forEach { match ->
            financialExposures.add(
                FinancialExposure(
                    titleEn = "Payment Obligation / Fee Requirement",
                    titleHi = "भुगतान दायित्व / शुल्क आवश्यकता",
                    titleKn = "ಪಾವತಿ ಜವಾಬ್ದಾರಿ / ಶುಲ್ಕದ ಅಗತ್ಯತೆ",
                    amountOrCost = match.value,
                    descriptionEn = "Explicit payment obligation, fee, or financial penalty mentioned in text.",
                    descriptionHi = "अनुबंध में उल्लिखित स्पष्ट भुगतान दायित्व या जुर्माना।",
                    descriptionKn = "ಒಪ್ಪಂದದಲ್ಲಿ ಉಲ್ಲೇಖಿಸಲಾದ ಸ್ಪಷ್ಟ ಪಾವತಿ ಜವಾಬ್ದಾರಿ.",
                    quoteSnippet = extractQuote(match.value).ifBlank { match.value }
                )
            )
        }

        // Financial Penalty or Liquidated Damages (distinguish from casual "interest")
        val financialPenaltyRegex = Regex(
            "\\b(?:late\\s+(?:fee|payment|charge)|accruing\\s+interest|compound\\s+interest|interest\\s+(?:penalty|charge|rate|accrual)|rate\\s+of\\s+interest|interest\\s+of\\s+\\d+%|penalty\\s+(?:fee|charge)?|liquidated\\s+damages)\\b",
            RegexOption.IGNORE_CASE
        )
        val penaltyMatch = financialPenaltyRegex.find(normalizedText) ?: financialPenaltyRegex.find(trimmed)
        if (penaltyMatch != null) {
            val quote = extractQuote(penaltyMatch.value).ifBlank { penaltyMatch.value }
            financialExposures.add(
                FinancialExposure(
                    titleEn = "Uncapped Late Fee & Interest Penalty",
                    titleHi = "अनकैप्ड लेट फी और ब्याज जुर्माना",
                    titleKn = "ಮಿತಿಯಿಲ್ಲದ ತಡವಾದ ಪಾವತಿ ಬಡ್ಡಿ ದಂಡ",
                    amountOrCost = "Variable Penalty",
                    descriptionEn = "Contract specifies cumulative late payment penalties or compounding interest.",
                    descriptionHi = "अनुबंध में बिना अधिकतम सीमा के देय शुल्क या ब्याज निर्दिष्ट है।",
                    descriptionKn = "ಒಪ್ಪಂದವು ಮಿತಿಯಿಲ್ಲದೆ ತಡವಾದ ಪಾವತಿ ದಂಡವನ್ನು ನಿರ್ದಿಷ್ಟಪಡಿಸುತ್ತದೆ.",
                    quoteSnippet = quote
                )
            )
        }

        // Ambiguity & Discretion Clauses
        val ambiguityMap = mapOf(
            "sole discretion" to Triple("Grants absolute unilateral authority to alter terms or determine breach without appeal.", "बिना अपील के शर्तों को बदलने का एकपक्षीय अधिकार देता है।", "ಯಾವುದೇ ಮೇಲ್ಮನವಿಯಿಲ್ಲದೆ ನಿಯಮಗಳನ್ನು ಬದಲಾಯಿಸುವ ಏಕಪಕ್ಷೀಯ ಅಧಿಕಾರ ನೀಡುತ್ತದೆ."),
            "reasonable efforts" to Triple("Vague legal standard that lacks enforceable metrics or performance KPIs.", "अस्पष्ट कानूनी मानक जिसमें लागू करने योग्य प्रदर्शन मेट्रिक्स का अभाव है।", "ಅಳೆಯಬಹುದಾದ ಕಾರ್ಯಕ್ಷಮತೆಯ ಮಾನದಂಡಗಳಿಲ್ಲದ ಅಸ್ಪಷ್ಟ ನಿಯಮ."),
            "from time to time" to Triple("Allows repeated unilateral modification of policies without formal addendums.", "फॉर्मल ऐडेंडम के बिना नीतियों में बार-बार बदलाव की अनुमति देता है।", "ಲಿಖಿತ ಒಪ್ಪಂದವಿಲ್ಲದೆ ನಿಯಮಗಳನ್ನು ಬದಲಾಯಿಸಲು ಅನುಮತಿಸುತ್ತದೆ."),
            "as deemed fit" to Triple("Subjective standard that severely weakens your legal defenses in dispute.", "विषयपरक धारा जो विवाद में आपके कानूनी बचाव को कमजोर करती है।", "ವಿವಾದದ ಸಂದರ್ಭದಲ್ಲಿ ನಿಮ್ಮ ರಕ್ಷಣೆಯನ್ನು ಬಲಹೀನಗೊಳಿಸುತ್ತದೆ.")
        )

        for ((phrase, expTriple) in ambiguityMap) {
            if (lowerText.contains(phrase) || normLower.contains(phrase)) {
                ambiguities.add(
                    AmbiguityTerm(
                        phrase = "\"$phrase\"",
                        explanationEn = expTriple.first,
                        explanationHi = expTriple.second,
                        explanationKn = expTriple.third,
                        quoteSnippet = extractQuote(phrase).ifBlank { phrase }
                    )
                )
            }
        }

        if (redFlags.isEmpty()) {
            redFlags.add(
                RedFlag(
                    titleEn = "Standard Operational Terms Detected",
                    titleHi = "मानक संचालन शर्तें पाई गईं",
                    titleKn = "ಸಾಮಾನ್ಯ ಒಪ್ಪಂದದ ನಿಯಮಗಳು",
                    descEn = "Standard balanced legal provisions identified in document.",
                    descHi = "दस्तावेज़ में मानक संतुलित कानूनी शर्तें पाई गईं।",
                    descKn = "ದಾಖಲೆಯಲ್ಲಿ ಸಮತೋಲಿತ ಸಾಮಾನ್ಯ ನಿಯಮಗಳು ಕಂಡುಬಂದಿವೆ."
                )
            )
        }

        var checkId = 1
        if (clauseBreakdowns.isNotEmpty()) {
            preSigningChecklist.add(
                PreSigningCheckItem(
                    id = checkId++,
                    taskEn = "Negotiate counter-offer amendments for identified high-risk vulnerabilities.",
                    taskHi = "पहचाने गए जोखिमपूर्ण क्लॉज के लिए जवाबी प्रस्ताव संशोधनों पर बातचीत करें।",
                    taskKn = "ಗುರುತಿಸಲಾದ ಅಪಾಯಕಾರಿ ಶರತ್ತುಗಳಿಗೆ ಪ್ರತಿಸಲ್ಲಿಕೆ ತಿದ್ದುಪಡಿಗಳನ್ನು ಚರ್ಚಿಸಿ."
                )
            )
        }
        if (deadlines.isNotEmpty()) {
            preSigningChecklist.add(
                PreSigningCheckItem(
                    id = checkId++,
                    taskEn = "Verify notice period obligations (${deadlines.first().timeframe}).",
                    taskHi = "नोटिस अवधि के दायित्वों की पुष्टि करें।",
                    taskKn = "ನೋಟಿಸ್ ಅವಧಿಯ ಜವಾಬ್ದಾರಿಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
                )
            )
        }
        if (financialExposures.isNotEmpty()) {
            preSigningChecklist.add(
                PreSigningCheckItem(
                    id = checkId++,
                    taskEn = "Confirm all fee caps, payment milestones, and penalty caps.",
                    taskHi = "सभी शुल्क सीमाओं और देय जुर्माने की पुष्टि करें।",
                    taskKn = "ಎಲ್ಲಾ ಶುಲ್ಕ ಮಿತಿಗಳು ಮತ್ತು ಪಾವತಿ ದಂಡಗಳನ್ನು ದೃಢೀಕರಿಸಿ."
                )
            )
        }
        preSigningChecklist.add(
            PreSigningCheckItem(
                id = checkId++,
                taskEn = "Verify all attached schedules, annexures, and exhibits.",
                taskHi = "सभी संलग्न अनुसूचियों और अनुलग्नकों को सत्यापित करें।",
                taskKn = "ಎಲ್ಲಾ ವೇಳಾಪಟ್ಟಿಗಳು ಮತ್ತು ಲಗತ್ತುಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
            )
        )

        val riskLevel = when {
            dealbreakerMatches.isNotEmpty() || statutoryVoidabilities.isNotEmpty() || clauseBreakdowns.isNotEmpty() -> RiskLevel.HIGH
            financialExposures.isNotEmpty() || ambiguities.isNotEmpty() || deadlines.size >= 3 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        AnalysisResult(
            riskLevel = riskLevel,
            summaryEn = when (riskLevel) {
                RiskLevel.HIGH -> "High risk! Critical legal vulnerabilities or statutory voidability issues detected. Resolve proposed counter-clauses before signing."
                RiskLevel.MEDIUM -> "Moderate risk. Review payment obligations, deadlines, and ambiguous discretionary terms."
                RiskLevel.LOW -> "Low risk. Standard agreement with balanced legal terms."
                else -> ""
            },
            summaryHi = when (riskLevel) {
                RiskLevel.HIGH -> "उच्च जोखिम! गंभीर कानूनी कमियां या वैधानिक मुद्दे पाए गए। हस्ताक्षर करने से पहले प्रस्तावित जवाबी धाराओं पर बातचीत करें।"
                RiskLevel.MEDIUM -> "मध्यम जोखिम। भुगतान देनदारियों, समय सीमा और विवेकपूर्ण शर्तों की समीक्षा करें।"
                RiskLevel.LOW -> "कम जोखिम। संतुलित प्रावधानों के साथ मानक समझौता।"
                else -> ""
            },
            summaryKn = when (riskLevel) {
                RiskLevel.HIGH -> "ಹೆಚ್ಚಿನ ಅಪಾಯ! ಗಂಭೀರ ಕಾನೂನು ಲೋಪದೋಷಗಳಿವೆ. ಸಹಿ ಮಾಡುವ ಮುನ್ನ ಸೂಚಿಸಲಾದ ತಿದ್ದುಪಡಿಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
                RiskLevel.MEDIUM -> "ಮಧ್ಯಮ ಅಪಾಯ. ಸಮಯದ ಮಿತಿಗಳು ಮತ್ತು ಪಾವತಿ ಜವಾಬ್ದಾರಿಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
                RiskLevel.LOW -> "ಕಡಿಮೆ ಅಪಾಯ. ಸಮತೋಲಿತ ನಿಯಮಗಳೊಂದಿಗೆ ಸಾಮಾನ್ಯ ಒಪ್ಪಂದ."
                else -> ""
            },
            redFlags = redFlags,
            deadlines = deadlines,
            financialExposures = financialExposures,
            statutoryVoidabilities = statutoryVoidabilities,
            clauseBreakdowns = clauseBreakdowns,
            ambiguities = ambiguities,
            dealbreakerMatches = dealbreakerMatches,
            preSigningChecklist = preSigningChecklist,
            isInvalid = false
        )
    }

    private fun isLegitimateContractText(text: String, lowerText: String): Boolean {
        if (text.length < 25) return false

        // Primary anchors: terms heavily specific to contracts & formal legal documents
        val primaryAnchors = listOf(
            "agreement", "contract", "hereby", "indemnif", "governing law",
            "jurisdiction", "confidentiality", "arbitration", "non-disclosure",
            "severability", "subcontractor", "statutory", "pursuant to",
            "in witness whereof", "hereto", "hereinafter", "whereas",
            "terms and conditions", "terms of service", "lease agreement",
            "employment agreement", "memorandum of understanding",
            "अनुबंध", "समझौता", "ಒಪ್ಪಂದ"
        )
        if (primaryAnchors.any { lowerText.contains(it) }) {
            return true
        }

        // Secondary legal terms: require at least 2 distinct terms
        val secondaryLegalTerms = listOf(
            "party", "parties", "shall", "clause", "liability", "obligation",
            "terminate", "termination", "breach", "warrant", "covenant",
            "damages", "tenant", "landlord", "employer", "employee",
            "vendor", "client", "licensor", "licensee", "counterparty",
            "शर्तें", "पक्ष", "ನಿಯಮಗಳು", "ಶರತ್ತು"
        )
        val secondaryMatches = secondaryLegalTerms.count { lowerText.contains(it) }
        return secondaryMatches >= 2
    }
}