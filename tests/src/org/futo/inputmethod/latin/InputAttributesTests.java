package org.futo.inputmethod.latin;

import android.os.LocaleList;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputAttributesTests {

    @Test
    public void testHintLocales() {
        final EditorInfo editorInfo = new EditorInfo();
        final LocaleList localeList = new LocaleList(new Locale("en", "US"), new Locale("ja", "JP"));
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
        editorInfo.hintLocales = localeList;

        final InputAttributes inputAttributes = new InputAttributes(editorInfo, false, "");

        assertEquals(localeList, inputAttributes.mHintLocales);
    }

    @Test
    public void testHintLocalesIgnoredForNonTextFields() {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = InputType.TYPE_CLASS_NUMBER;
        editorInfo.hintLocales = new LocaleList(new Locale("en", "US"));

        final InputAttributes inputAttributes = new InputAttributes(editorInfo, false, "");

        assertNull(inputAttributes.mHintLocales);
    }

    @Test
    public void testHintLocalesNullEditorInfo() {
        final InputAttributes inputAttributes = new InputAttributes(null, false, "");

        assertNull(inputAttributes.mHintLocales);
    }
}
