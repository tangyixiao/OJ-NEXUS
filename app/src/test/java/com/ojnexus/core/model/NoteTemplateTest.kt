package com.ojnexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTemplateTest {

    @Test
    fun `default keeps every existing label in reading order`() {
        val template = NoteTemplate.DEFAULT
        assertEquals(NoteSlot.entries.size, template.fields.size)
        assertEquals(
            listOf(
                NoteSlot.KEY_INSIGHT,
                NoteSlot.IMPLEMENTATION,
                NoteSlot.COMPLEXITY,
                NoteSlot.GENERAL,
            ),
            template.fields.map { it.slot },
        )
        assertEquals("KEY INSIGHT", template.labelFor(NoteSlot.KEY_INSIGHT))
        assertEquals("IMPLEMENTATION", template.labelFor(NoteSlot.IMPLEMENTATION))
        assertEquals("COMPLEXITY", template.labelFor(NoteSlot.COMPLEXITY))
        assertEquals("GENERAL NOTES", template.labelFor(NoteSlot.GENERAL))
        assertTrue(template.validate())
    }

    @Test
    fun `preset exposes the phase named fields`() {
        val preset = NoteTemplate.PRESET_ROOT_CAUSE
        assertEquals(listOf("ROOT CAUSE", "KEY INSIGHT", "PITFALL"), preset.fields.map { it.label })
        assertTrue(preset.validate())
    }

    @Test
    fun `validate rejects blank labels duplicate slots and overflow`() {
        assertFalse(NoteTemplate(emptyList<NoteTemplateField>()).validate())
        assertFalse(
            NoteTemplate(
                listOf(NoteTemplateField(NoteSlot.GENERAL, "   ")),
            ).validate(),
        )
        assertFalse(
            NoteTemplate(
                listOf(
                    NoteTemplateField(NoteSlot.GENERAL, "A"),
                    NoteTemplateField(NoteSlot.GENERAL, "B"),
                ),
            ).validate(),
        )
        assertFalse(
            NoteTemplate(
                NoteSlot.entries.map { NoteTemplateField(it, it.name) } +
                    NoteTemplateField(NoteSlot.KEY_INSIGHT, "DUP"),
            ).validate(),
        )
    }

    @Test
    fun `withField replaces in place and appends new slots`() {
        val template = NoteTemplate.DEFAULT
            .withField(NoteTemplateField(NoteSlot.COMPLEXITY, "PITFALL", "WHERE IT BREAKS"))
            .withField(NoteTemplateField(NoteSlot.GENERAL, "REUSE IDEA"))

        assertEquals(
            listOf(NoteSlot.KEY_INSIGHT, NoteSlot.IMPLEMENTATION, NoteSlot.COMPLEXITY, NoteSlot.GENERAL),
            template.fields.map { it.slot },
        )
        assertEquals("PITFALL", template.labelFor(NoteSlot.COMPLEXITY))
        assertEquals("WHERE IT BREAKS", template.hintFor(NoteSlot.COMPLEXITY))
        assertEquals("REUSE IDEA", template.labelFor(NoteSlot.GENERAL))
        assertEquals(4, template.fields.size)
    }

    @Test
    fun `withField cannot grow past the slot count`() {
        val overflow = NoteTemplate.DEFAULT.withField(
            NoteTemplateField(NoteSlot.KEY_INSIGHT, "AGAIN"),
        )
        assertEquals(NoteTemplate.MAX_FIELDS, overflow.fields.size)
    }

    @Test
    fun `without drops a slot but never empties the template`() {
        val shrunk = NoteTemplate.DEFAULT.without(NoteSlot.GENERAL)
        assertEquals(3, shrunk.fields.size)
        assertFalse(shrunk.contains(NoteSlot.GENERAL))

        val single = NoteTemplate(listOf(NoteTemplateField(NoteSlot.GENERAL, "ONLY")))
        assertTrue(single.without(NoteSlot.GENERAL) === single)
    }

    @Test
    fun `reorder follows the requested slots and ignores unknown duplicates`() {
        val template = NoteTemplate.DEFAULT.reorder(
            listOf(NoteSlot.GENERAL, NoteSlot.COMPLEXITY, NoteSlot.COMPLEXITY),
        )
        // Unknown duplicates are ignored; slots not named keep their relative order at the end.
        assertEquals(
            listOf(NoteSlot.GENERAL, NoteSlot.COMPLEXITY, NoteSlot.KEY_INSIGHT, NoteSlot.IMPLEMENTATION),
            template.fields.map { it.slot },
        )
    }

    @Test
    fun `sanitize trims copy dedupes slots and caps the field count`() {
        val dirty = NoteTemplate(
            listOf(
                NoteTemplateField(NoteSlot.GENERAL, "  GENERAL  ", "  hint  "),
                NoteTemplateField(NoteSlot.GENERAL, "DUPLICATE"),
                NoteTemplateField(NoteSlot.KEY_INSIGHT, "KEY"),
            ),
        )
        val clean = dirty.sanitize()
        assertEquals(listOf(NoteSlot.GENERAL, NoteSlot.KEY_INSIGHT), clean.fields.map { it.slot })
        assertEquals("GENERAL", clean.fields.first().label)
        assertEquals("hint", clean.fields.first().hint)
        assertTrue(clean.validate())
    }

    @Test
    fun `editor fields follow template order and carry saved text`() {
        val saved = ProblemNotes(
            problemId = 1L,
            keyInsight = "REVERSE THE ARRAY",
            implementationNotes = "TWO POINTERS",
            complexity = "O(n)",
            general = "",
            updatedAt = 0L,
        )
        val fields = NoteTemplate.PRESET_ROOT_CAUSE.editorFields(saved)
        assertEquals(listOf(NoteSlot.KEY_INSIGHT, NoteSlot.IMPLEMENTATION, NoteSlot.COMPLEXITY), fields.map { it.slot })
        assertEquals("REVERSE THE ARRAY", fields[0].value)
        // The preset renames the slots: KEY_INSIGHT becomes ROOT CAUSE, COMPLEXITY becomes PITFALL.
        assertEquals("ROOT CAUSE", fields[0].label)
        assertEquals("WHY THE PROBLEM IS HARD, IN ONE LINE", fields[0].hint)
        assertEquals("KEY INSIGHT", fields[1].label)
        assertEquals("THE OBSERVATION THAT UNLOCKS THE SOLUTION", fields[1].hint)
        assertEquals("PITFALL", fields[2].label)
        assertEquals("WHERE THIS SOLUTION BREAKS", fields[2].hint)
        assertTrue(fields.all { it.templated })
        // GENERAL is not in the preset and holds no text, so it is not rendered.
        assertFalse(fields.any { it.slot == NoteSlot.GENERAL })
    }

    @Test
    fun `editor fields keep rendering saved text the template no longer lists`() {
        val saved = ProblemNotes(
            problemId = 1L,
            keyInsight = "",
            implementationNotes = "",
            complexity = "",
            general = "KEEP ME",
            updatedAt = 0L,
        )
        val fields = NoteTemplate.PRESET_ROOT_CAUSE.editorFields(saved)
        val orphan = fields.single { it.slot == NoteSlot.GENERAL }

        assertFalse(orphan.templated)
        assertEquals("KEEP ME", orphan.value)
        assertEquals("GENERAL NOTES", orphan.label)
        // Template fields come first, orphaned saved text after.
        assertEquals(
            listOf(NoteSlot.KEY_INSIGHT, NoteSlot.IMPLEMENTATION, NoteSlot.COMPLEXITY, NoteSlot.GENERAL),
            fields.map { it.slot },
        )
    }

    @Test
    fun `editor fields treat blank saved text as empty and null notes as empty`() {
        val saved = ProblemNotes(
            problemId = 1L,
            keyInsight = "   ",
            implementationNotes = "",
            complexity = "",
            general = "",
            updatedAt = 0L,
        )
        val fields = NoteTemplate.DEFAULT.editorFields(saved)
        // A whitespace-only slot is preserved verbatim — trimming is a save-time concern.
        assertEquals("   ", fields.single { it.slot == NoteSlot.KEY_INSIGHT }.value)
        assertTrue(fields.all { it.value.isBlank() })

        val without = NoteTemplate.DEFAULT.editorFields(null)
        assertEquals(NoteTemplate.MAX_FIELDS, without.size)
        assertTrue(without.all { it.value.isEmpty() })
    }

    @Test
    fun `an empty template renders no editor fields`() {
        assertTrue(NoteTemplate(emptyList<NoteTemplateField>()).editorFields(null).isEmpty())
    }

    @Test
    fun `codec round trips the default and preset templates`() {
        listOf(NoteTemplate.DEFAULT, NoteTemplate.PRESET_ROOT_CAUSE).forEach { template ->
            assertEquals(template, NoteTemplateCodec.decode(NoteTemplateCodec.encode(template)))
        }
    }

    @Test
    fun `codec escapes separators inside labels and hints`() {
        val template = NoteTemplate(
            listOf(
                NoteTemplateField(NoteSlot.KEY_INSIGHT, "A:B;C\\D", "hint;with:chars"),
            ),
        )
        val encoded = NoteTemplateCodec.encode(template)
        assertTrue(encoded.contains("\\:"))
        assertTrue(encoded.contains("\\;"))
        assertEquals(template, NoteTemplateCodec.decode(encoded))
    }

    @Test
    fun `codec rejects malformed payloads`() {
        assertNull(NoteTemplateCodec.decode(""))
        assertNull(NoteTemplateCodec.decode("   "))
        assertNull(NoteTemplateCodec.decode("garbage"))
        assertNull(NoteTemplateCodec.decode("KEY_INSIGHT"))
        assertNull(NoteTemplateCodec.decode("KEY_INSIGHT:LABEL"))
        assertNull(NoteTemplateCodec.decode("NOT_A_SLOT:LABEL:HINT"))
        assertNull(NoteTemplateCodec.decode("KEY_INSIGHT:LABEL:HINT;"))
        assertNull(NoteTemplateCodec.decode("KEY_INSIGHT:LABEL:HINT;KEY_INSIGHT:OTHER:HINT"))
        assertNull(NoteTemplateCodec.decode("KEY_INSIGHT:  :HINT"))
    }

    @Test
    fun `codec caps the number of decoded rows`() {
        val fiveRows = NoteSlot.entries.joinToString(";") { "$it:LABEL:HINT" } +
            ";KEY_INSIGHT:LABEL:HINT"
        assertNull(NoteTemplateCodec.decode(fiveRows))
    }
}
