package org.futo.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeyboardTextsMultiSet extends KeyboardTextsSet {
    private KeyboardTextsSet base = new KeyboardTextsSet();
    private List<KeyboardTextsSet> extra = new ArrayList<>();

    private Locale baseLocale;

    public KeyboardTextsMultiSet(Context context, Locale base, List<Locale> extra) {
        this.base = new KeyboardTextsSet();
        this.base.setLocale(base, context);
        this.baseLocale = base;

        for(Locale locale : extra) {
            KeyboardTextsSet extraSet = new KeyboardTextsSet();
            extraSet.setLocale(locale, context);
            this.extra.add(extraSet);
        }
    }

    @Override
    public void setLocale(Locale locale, Context context) {
        if(locale != baseLocale) throw new IllegalStateException();
    }

    @Override
    public void setLocale(Locale locale, Resources res, String resourcePackageName) {
        if(locale != baseLocale) throw new IllegalStateException();
    }

    @Override
    public String getText(String name) {
        // TODO: Blacklist certain ones like period key, comma key, since they break it. Should disable duplication too
        if(name.startsWith("morekeys_")) {
            StringBuilder combined = new StringBuilder(base.getText(name));
            for(KeyboardTextsSet extra : this.extra) {
                combined.append(",").append(extra.getText(name));
            }
            return combined.toString();
        } else {
            return base.getText(name);
        }
    }
}
