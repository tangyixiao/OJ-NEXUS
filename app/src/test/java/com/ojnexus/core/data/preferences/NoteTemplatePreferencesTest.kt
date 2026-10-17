package com.ojnexus.core.data.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.model.NoteSlot
import com.ojnexus.core.model.NoteTemplate
import com.ojnexus.core.model.NoteTemplateCodec
import com.ojnexus.core.model.NoteTemplateField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteTemplatePreferencesTest {

    private fun repository() =
        UserPreferencesRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun `note template round trips through the data store`() = runBlocking {
        val repository = repository()
        repository.setNoteTemplate(NoteTemplate.PRESET_ROOT_CAUSE)
        assertEquals(NoteTemplate.PRESET_ROOT_CAUSE, repository.noteTemplate.first())

        repository.setNoteTemplate(NoteTemplate.DEFAULT)
        assertEquals(NoteTemplate.DEFAULT, repository.noteTemplate.first())
    }

    @Test
    fun `a stored template survives a reordered field list`() = runBlocking {
        val repository = repository()
        val template = NoteTemplate(
            listOf(
                NoteTemplateField(NoteSlot.COMPLEXITY, "COMPLEXITY", "big-O"),
                NoteTemplateField(NoteSlot.KEY_INSIGHT, "KEY INSIGHT"),
            ),
        )
        repository.setNoteTemplate(template)
        val stored = repository.noteTemplate.first()
        assertEquals(listOf(NoteSlot.COMPLEXITY, NoteSlot.KEY_INSIGHT), stored.fields.map { it.slot })
    }

    @Test
    fun `a corrupt stored value falls back to the default template`() = runBlocking {
        val repository = repository()
        repository.setNoteTemplate(NoteTemplate.PRESET_ROOT_CAUSE)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.userPreferencesDataStore.edit {
            it[stringPreferencesKey(NOTE_TEMPLATE_KEY)] = "GARBAGE:not:a:slot"
        }
        assertEquals(
            "GARBAGE:not:a:slot",
            context.userPreferencesDataStore.data
                .first()[stringPreferencesKey(NOTE_TEMPLATE_KEY)],
        )
        assertEquals(NoteTemplate.DEFAULT, repository.noteTemplate.first())
    }

    @Test
    fun `an invalid template is refused and leaves the previous value intact`() = runBlocking {
        val repository = repository()
        repository.setNoteTemplate(NoteTemplate.PRESET_ROOT_CAUSE)
        val invalid = NoteTemplate(emptyList<NoteTemplateField>())
        assertFalse(repository.setNoteTemplate(invalid))
        assertEquals(NoteTemplate.PRESET_ROOT_CAUSE, repository.noteTemplate.first())

        val duplicateSlots = NoteTemplate(
            listOf(
                NoteTemplateField(NoteSlot.GENERAL, "A"),
                NoteTemplateField(NoteSlot.GENERAL, "B"),
            ),
        )
        assertFalse(repository.setNoteTemplate(duplicateSlots))
        assertEquals(NoteTemplate.PRESET_ROOT_CAUSE, repository.noteTemplate.first())
    }

    @Test
    fun `an escaped payload decodes back to the same template`() = runBlocking {
        val template = NoteTemplate(
            listOf(NoteTemplateField(NoteSlot.KEY_INSIGHT, "A:B;C", "hint;with:chars")),
        )
        assertEquals(
            template,
            NoteTemplateCodec.decode(NoteTemplateCodec.encode(template)),
        )
    }
}
