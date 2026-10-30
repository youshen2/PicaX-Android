package moye.picax.model

/** The Swift client's tokenizer, precedence, union and combination semantics. */
object SearchExpression {
    data class Clause(val terms: List<String>) {
        fun keyword(platform: Platform): String = if(terms.size == 1) terms.first() else terms.joinToString(" ") { term ->
            if(platform in listOf(Platform.nhentai, Platform.eHentai) && term.any { it.isWhitespace() } && '"' !in term) "\"$term\"" else term
        }
    }
    private enum class Kind { Term, Union, And, Open, Close }
    private data class Token(val kind: Kind, val value: String)
    private fun tokens(input: String): List<Token> {
        val result = mutableListOf<Token>()
        var start = 0
        var quoted = false
        var escaped = false
        fun term(end: Int) { input.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { result += Token(Kind.Term, it) } }
        input.forEachIndexed { index, char ->
            if(escaped) { escaped = false; return@forEachIndexed }
            if(quoted && char == '\\') { escaped = true; return@forEachIndexed }
            if(char == '"') { quoted = !quoted; return@forEachIndexed }
            if(quoted) return@forEachIndexed
            val kind = when(char) { '/' -> Kind.Union; '&' -> Kind.And; '(', '（' -> Kind.Open; ')', '）' -> Kind.Close; else -> null }
            if(kind != null) { term(index); result += Token(kind, char.toString()); start = index + 1 }
        }
        term(input.length)
        return result
    }
    fun clauses(input: String): List<Clause> {
        val operands = mutableListOf<List<Clause>>()
        val operators = mutableListOf<Kind>()
        var needsOperand = true
        fun emptyIfNeeded() { if(needsOperand) operands += emptyList<Clause>() }
        fun precedence(kind: Kind) = if(kind == Kind.And) 2 else 1
        fun reduce() {
            val operation = operators.removeAt(operators.lastIndex)
            require(operation != Kind.Open && operands.size >= 2) { "搜索关键词的括号未配对" }
            val right = operands.removeAt(operands.lastIndex)
            val left = operands.removeAt(operands.lastIndex)
            val result = mutableListOf<Clause>()
            val seen = mutableSetOf<Set<String>>()
            fun append(clause: Clause) {
                if(!seen.add(clause.terms.toSet())) return
                require(result.size < 256) { "搜索组合超过 256 组，请减少 / 分支" }
                result += clause
            }
            if(operation == Kind.Union || left.isEmpty() || right.isEmpty()) (left + right).forEach(::append)
            else left.forEach { a -> right.forEach { b -> append(Clause((a.terms + b.terms).distinct())) } }
            operands += result
        }
        for(token in tokens(input)) when(token.kind) {
            Kind.Term -> {
                require(needsOperand) { "关键词与括号分组之间请使用 / 或 & 连接" }
                operands += listOf(Clause(listOf(token.value))); needsOperand = false
            }
            Kind.Open -> {
                require(needsOperand) { "关键词与括号分组之间请使用 / 或 & 连接" }
                operators += Kind.Open
            }
            Kind.Close -> {
                emptyIfNeeded()
                while(operators.isNotEmpty() && operators.last() != Kind.Open) reduce()
                require(operators.lastOrNull() == Kind.Open) { "搜索关键词的括号未配对" }
                operators.removeAt(operators.lastIndex); needsOperand = false
            }
            Kind.Union, Kind.And -> {
                emptyIfNeeded()
                while(operators.isNotEmpty() && operators.last() != Kind.Open && precedence(operators.last()) >= precedence(token.kind)) reduce()
                operators += token.kind; needsOperand = true
            }
        }
        emptyIfNeeded()
        while(operators.isNotEmpty()) reduce()
        return operands.lastOrNull().orEmpty()
    }
    fun expand(input: String) = clauses(input).map { it.keyword(Platform.picacg) }
}
