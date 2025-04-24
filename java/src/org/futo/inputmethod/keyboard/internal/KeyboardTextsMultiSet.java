package org.futo.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeyboardTextsMultiSet extends KeyboardTextsSet {
    private final List<KeyboardTextsSet> extra = new ArrayList<>();

    private final Locale baseLocale;

    public KeyboardTextsMultiSet(final Context context, final Locale base, final List<Locale> extra) {
        baseLocale = base;
        setLocale(base, context);

        for(final Locale locale : extra) {
            final KeyboardTextsSet extraSet = new KeyboardTextsSet();
            extraSet.setLocale(locale, context);
            this.extra.add(extraSet);
        }
    }

    @Override
    public void setLocale(final Locale locale, final Context context) {
        if(locale != baseLocale) throw new IllegalStateException();
        super.setLocale(locale, context);
    }

    @Override
    public void setLocale(final Locale locale, final Resources res, final String resourcePackageName) {
        if(locale != baseLocale) throw new IllegalStateException();
        super.setLocale(locale, res, resourcePackageName);
    }

    @Override
    public String getText(final String name) {
        if(name.startsWith("morekeys_")) {
            final ArrayList<String> texts = new ArrayList<>();
            texts.add(super.getText(name));
            for(KeyboardTextsSet extra : extra) {
                final String extraText = extra.getText(name);
                if(!texts.contains(extraText)) texts.add(extraText);
            }

            return String.join(",", texts);
        } else {
            return super.getText(name);
        }
    }
}
