package org.futo.inputmethod.latin;

import android.os.LocaleList;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InputAttributesTests {

    @Test
    public void testHintLocales() {
        final EditorInfo editorInfo = mock(EditorInfo.class);
        final LocaleList localeList = new LocaleList(new Locale("en", "US"), new Locale("ja", "JP"));
        when(editorInfo.getHintLocales()).thenReturn(localeList);

        final InputAttributes inputAttributes = new InputAttributes(editorInfo, false, "");

        assertEquals(localeList, inputAttributes.mHintLocales);
    }
}