package com.ojnexus.core.model

/**
 * Which stored note column a template field writes to. The four slots are the four free-text
 * columns of the `problem_notes` table — a template never adds storage, it only labels and
 * orders it.
 */
enum class NoteSlot {
    KEY_INSIGHT,
    IMPLEMENTATION,
    COMPLEXITY,
    GENERAL,
}

/** One editable field of a note template: the storage slot plus its user-visible copy. */
data class NoteTemplateField(
    val slot: NoteSlot,
    val label: String,
    val hint: String = "",
)

/**
 * The user's note layout. Fields are rendered in list order; [MAX_FIELDS] equals the number
 * of storage slots, so every field owns exactly one column.
 */
data class NoteTemplate(val fields: List<NoteTemplateField>) {

    /** True when every label is non-blank, slots are unique, and the cap is respected. */
    fun validate(): Boolean {
        if (fields.isEmpty() || fields.size > MAX_FIELDS) return false
        val slots = mutableSetOf<NoteSlot>()
        return fields.all { field ->
            field.label.isNotBlank() && slots.add(field.slot)
        }
    }

    fun labelFor(slot: NoteSlot): String? =
        fields.firstOrNull { it.slot == slot }?.label?.takeIf { it.isNotBlank() }

    fun hintFor(slot: NoteSlot): String? =
        fields.firstOrNull { it.slot == slot }?.hint?.takeIf { it.isNotBlank() }

    fun contains(slot: NoteSlot): Boolean = fields.any { it.slot == slot }

    /** Adds a field, or replaces the existing field for the same slot in place. */
    fun withField(field: NoteTemplateField): NoteTemplate {
        val index = fields.indexOfFirst { it.slot == field.slot }
        val next = if (index >= 0) {
            fields.toMutableList().apply { set(index, field) }
        } else {
            fields + field
        }
        return copy(fields = next.take(MAX_FIELDS))
    }

    /** Removes a slot's field. Never empties the template — the editor always has a field. */
    fun without(slot: NoteSlot): NoteTemplate {
        if (fields.size <= 1) return this
        return copy(fields = fields.filterNot { it.slot == slot })
    }

    /** Reorders to [slots]; unknown slots are dropped and duplicates ignored. */
    fun reorder(slots: List<NoteSlot>): NoteTemplate {
        val ordered = mutableListOf<NoteTemplateField>()
        val seen = mutableSetOf<NoteSlot>()
        slots.forEach { slot ->
            if (seen.add(slot)) {
                fields.firstOrNull { it.slot == slot }?.let(ordered::add)
            }
        }
        fields.forEach { field -> if (seen.add(field.slot)) ordered.add(field) }
        return copy(fields = ordered)
    }

    /** Trims copy, drops duplicate slots (first wins) and enforces the cap. */
    fun sanitize(): NoteTemplate = copy(
        fields = fields
            .map { it.copy(label = it.label.trim(), hint = it.hint.trim()) }
            .distinctBy { it.slot }
            .take(MAX_FIELDS),
    )

    companion object {
        const val MAX_FIELDS = 4

        /**
         * The shipped layout: the four existing fields with today's labels. Defaulting to the
         * labels users already see keeps saved notes meaningful — the ROOT CAUSE preset is a
         * choice, not a silent relabel.
         */
        val DEFAULT: NoteTemplate = NoteTemplate(
            fields = listOf(
                NoteTemplateField(NoteSlot.KEY_INSIGHT, DEFAULT_LABEL_KEY_INSIGHT),
                NoteTemplateField(NoteSlot.IMPLEMENTATION, DEFAULT_LABEL_IMPLEMENTATION),
                NoteTemplateField(NoteSlot.COMPLEXITY, DEFAULT_LABEL_COMPLEXITY),
                NoteTemplateField(NoteSlot.GENERAL, DEFAULT_LABEL_GENERAL),
            ),
        )

        /** The phase-named preset: ROOT CAUSE / KEY INSIGHT / PITFALL. */
        val PRESET_ROOT_CAUSE: NoteTemplate = NoteTemplate(
            fields = listOf(
                NoteTemplateField(
                    slot = NoteSlot.KEY_INSIGHT,
                    label = "ROOT CAUSE",
                    hint = "WHY THE PROBLEM IS HARD, IN ONE LINE",
                ),
                NoteTemplateField(
                    slot = NoteSlot.IMPLEMENTATION,
                    label = "KEY INSIGHT",
                    hint = "THE OBSERVATION THAT UNLOCKS THE SOLUTION",
                ),
                NoteTemplateField(
                    slot = NoteSlot.COMPLEXITY,
                    label = "PITFALL",
                    hint = "WHERE THIS SOLUTION BREAKS",
                ),
            ),
        )

        const val DEFAULT_LABEL_KEY_INSIGHT = "KEY INSIGHT"
        const val DEFAULT_LABEL_IMPLEMENTATION = "IMPLEMENTATION"
        const val DEFAULT_LABEL_COMPLEXITY = "COMPLEXITY"
        const val DEFAULT_LABEL_GENERAL = "GENERAL NOTES"

        /** The label a slot falls back to when the template no longer lists it. */
        fun defaultLabelFor(slot: NoteSlot): String = when (slot) {
            NoteSlot.KEY_INSIGHT -> DEFAULT_LABEL_KEY_INSIGHT
            NoteSlot.IMPLEMENTATION -> DEFAULT_LABEL_IMPLEMENTATION
            NoteSlot.COMPLEXITY -> DEFAULT_LABEL_COMPLEXITY
            NoteSlot.GENERAL -> DEFAULT_LABEL_GENERAL
        }
    }
}

/** One rendered editor row: the slot, its display copy, and the text it edits. */
data class NoteEditorField(
    val slot: NoteSlot,
    val label: String,
    val hint: String,
    val value: String,
    /** True when the template lists this slot; false when it renders only to expose saved text. */
    val templated: Boolean,
)

/**
 * The fields the editor renders for [saved]:
 *
 * 1. template fields, in template order;
 * 2. then every remaining slot that carries saved text, in slot ordinal order — saved text is
 *    never hidden by a template edit, because Local First forbids data loss.
 *
 * An empty template renders nothing so the UI can show its empty state.
 */
fun NoteTemplate.editorFields(saved: ProblemNotes?): List<NoteEditorField> {
    if (fields.isEmpty()) return emptyList()
    val text = saved?.let { notes ->
        mapOf(
            NoteSlot.KEY_INSIGHT to notes.keyInsight,
            NoteSlot.IMPLEMENTATION to notes.implementationNotes,
            NoteSlot.COMPLEXITY to notes.complexity,
            NoteSlot.GENERAL to notes.general,
        )
    } ?: emptyMap()
    val ordered = fields.map { field ->
        NoteEditorField(
            slot = field.slot,
            label = field.label,
            hint = field.hint,
            value = text[field.slot] ?: "",
            templated = true,
        )
    }
    val orphans = NoteSlot.entries
        .filter { slot -> !contains(slot) && !text[slot].isNullOrBlank() }
        .map { slot ->
            NoteEditorField(
                slot = slot,
                label = NoteTemplate.defaultLabelFor(slot),
                hint = "",
                value = text[slot].orEmpty(),
                templated = false,
            )
        }
    return ordered + orphans
}

/**
 * Compact persistence format for [NoteTemplate]: `slot:label:hint` rows joined by `;`, with
 * `\` escaping `:`, `;` and `\` inside labels and hints. Four short rows do not need a JSON
 * dependency, and a decoder that returns null instead of throwing keeps a corrupt preference
 * from breaking the editor.
 */
object NoteTemplateCodec {

    private const val ROW_SEPARATOR = ';'
    private const val FIELD_SEPARATOR = ':'
    private const val ESCAPE = '\\'

    fun encode(template: NoteTemplate): String =
        template.fields.joinToString(ROW_SEPARATOR.toString()) { field ->
            listOf(
                field.slot.name,
                escape(field.label),
                escape(field.hint),
            ).joinToString(FIELD_SEPARATOR.toString())
        }

    /** Returns null for anything malformed; callers fall back to `NoteTemplate.DEFAULT`. */
    fun decode(raw: String): NoteTemplate? {
        if (raw.isBlank()) return null
        val rows = splitEscaped(raw, ROW_SEPARATOR)
        if (rows.size > NoteTemplate.MAX_FIELDS) return null
        val fields = mutableListOf<NoteTemplateField>()
        val seen = mutableSetOf<NoteSlot>()
        rows.forEach { row ->
            if (row.isEmpty()) return null
            val parts = splitEscaped(row, FIELD_SEPARATOR)
            if (parts.size != 3) return null
            val slot = NoteSlot.entries.firstOrNull { it.name == parts[0] } ?: return null
            if (!seen.add(slot)) return null
            fields.add(
                NoteTemplateField(
                    slot = slot,
                    label = unescape(parts[1]),
                    hint = unescape(parts[2]),
                ),
            )
        }
        if (fields.isEmpty()) return null
        return NoteTemplate(fields).takeIf { it.validate() }
    }

    private fun escape(value: String): String = buildString(value.length + 4) {
        value.forEach { char ->
            when (char) {
                ROW_SEPARATOR, FIELD_SEPARATOR, ESCAPE -> append(ESCAPE)
            }
            append(char)
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> {
                    append(char)
                    escaped = false
                }
                char == ESCAPE -> escaped = true
                else -> append(char)
            }
        }
    }

    /**
     * Splits on unescaped [separator] while keeping escape sequences intact, so a nested
     * split can still tell `A\:B` from `A` and `B`. Callers run [unescape] on each part
     * once every level has been split.
     */
    private fun splitEscaped(value: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> {
                    current.append(ESCAPE)
                    current.append(char)
                    escaped = false
                }
                char == ESCAPE -> escaped = true
                char == separator -> {
                    parts.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts.add(current.toString())
        return parts
    }
}
