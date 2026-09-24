package com.ojnexus.judge.luogu.open

/**
 * Language identifiers accepted by the Luogu Open Platform judge.
 * Keep the wire identifier separate from the compact label shown in the editor.
 */
data class LuoguLanguageOption(
    val id: String,
    val label: String,
)

object LuoguLanguages {
    const val DEFAULT_ID = "cxx/14/gcc"

    val options: List<LuoguLanguageOption> = listOf(
        LuoguLanguageOption("c/99/gcc", "C99"),
        LuoguLanguageOption("cxx/98/gcc", "C++98"),
        LuoguLanguageOption(DEFAULT_ID, "C++14"),
        LuoguLanguageOption("cxx/17/gcc", "C++17"),
        LuoguLanguageOption("cxx/20/gcc", "C++20"),
        LuoguLanguageOption("python3/c", "Python 3"),
        LuoguLanguageOption("python3/py", "PyPy 3"),
        LuoguLanguageOption("java/8", "Java 8"),
        LuoguLanguageOption("kotlin/jvm", "Kotlin"),
        LuoguLanguageOption("go", "Go"),
        LuoguLanguageOption("rust/rustc", "Rust"),
        LuoguLanguageOption("pascal/fpc", "Pascal"),
        LuoguLanguageOption("haskell/ghc", "Haskell"),
        LuoguLanguageOption("js/node/lts", "Node.js"),
        LuoguLanguageOption("php", "PHP"),
        LuoguLanguageOption("ruby", "Ruby"),
        LuoguLanguageOption("perl", "Perl"),
        LuoguLanguageOption("scala", "Scala"),
    )
}
