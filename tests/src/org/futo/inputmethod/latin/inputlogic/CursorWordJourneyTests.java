package org.futo.inputmethod.latin.inputlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.uix.TextEditPopupActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Run on an isolated device with the FUTO test IME already selected and suggestions enabled. */
@RunWith(AndroidJUnit4.class)
public class CursorWordJourneyTests {
    @Test
    public void softwareKeysSuggestTheEntireWordAfterEditingAtTheCaret() {
        assumeTrue("Requires an isolated device with the FUTO test IME selected",
                "true".equals(InstrumentationRegistry.getArguments().getString("cursorWordJourney")));
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);
        final UiAutomation automation = instrumentation.getUiAutomation();
        final AccessibilityServiceInfo serviceInfo = automation.getServiceInfo();
        final int originalFlags = serviceInfo.flags;
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(serviceInfo);
        final Intent intent = new Intent(instrumentation.getTargetContext(), TextEditPopupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            instrumentation.getTargetContext().startActivity(intent);
            final String delete = instrumentation.getTargetContext().getString(R.string.spoken_description_delete);
            awaitImeNode(automation, node -> delete.equals(description(node)), "FUTO Delete key");
            final String hint = instrumentation.getTargetContext().getString(R.string.settings_try_typing_here);
            final AccessibilityNodeInfo editor = awaitNode(automation,
                    AccessibilityWindowInfo.TYPE_APPLICATION,
                    node -> node.isEditable() && (hint.contentEquals(text(node))
                            || hint.contentEquals(node.getHintText() == null ? "" : node.getHintText())),
                    "empty FUTO test editor");
            final Bundle newText = new Bundle();
            newText.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "fork");
            assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, newText));
            final Bundle caret = new Bundle();
            caret.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 3);
            caret.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 3);
            assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, caret));
            awaitSuggestion(automation, "fork");

            press(device, awaitImeNode(automation, node -> delete.equals(description(node)),
                    "FUTO Delete key"));
            assertEditor(automation, "fok", 2);
            awaitSuggestion(automation, "fok");

            press(device, awaitImeNode(automation, node -> "n".equals(description(node)
                    .toLowerCase(Locale.ROOT)), "FUTO n key"));
            assertEditor(automation, "fonk", 3);
            awaitSuggestion(automation, "fonk");

            final String shift = instrumentation.getTargetContext().getString(R.string.spoken_description_shift);
            press(device, awaitImeNode(automation, node -> shift.equals(description(node)), "FUTO Shift key"));
            final String capitalI = instrumentation.getTargetContext().getString(R.string.spoken_letter_0049);
            press(device, awaitImeNode(automation, node -> capitalI.equals(description(node)), "FUTO I key"));
            assertEditor(automation, "fonIk", 4);
            awaitSuggestion(automation, "fonIk");
        } finally {
            device.pressBack();
            serviceInfo.flags = originalFlags;
            automation.setServiceInfo(serviceInfo);
        }
    }

    private static void assertEditor(final UiAutomation automation, final String text, final int caret) {
        final AccessibilityNodeInfo editor = awaitNode(automation, AccessibilityWindowInfo.TYPE_APPLICATION,
                node -> node.isEditable() && text.contentEquals(text(node)), "editor text '" + text + "'");
        assertEquals(caret, editor.getTextSelectionStart());
        assertEquals(caret, editor.getTextSelectionEnd());
    }

    private static CharSequence text(final AccessibilityNodeInfo node) {
        return node.getText() == null ? "" : node.getText();
    }

    private static String description(final AccessibilityNodeInfo node) {
        return node.getContentDescription() == null ? "" : node.getContentDescription().toString();
    }

    private static void press(final UiDevice device, final AccessibilityNodeInfo node) {
        final Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        assertTrue(device.click(bounds.centerX(), bounds.centerY()));
    }

    private static void awaitSuggestion(final UiAutomation automation, final String word) {
        final String verbatim = '"' + word + '"';
        // A keyboard relayout can leave cached virtual nodes from the previous action bar.
        automation.setServiceInfo(automation.getServiceInfo());
        try {
            awaitImeNode(automation, node -> verbatim.equals(description(node))
                    || word.equals(description(node)) || word.contentEquals(text(node)),
                    "FUTO suggestion '" + word + "'");
        } catch (AssertionError failure) {
            final TreeSet<String> labels = new TreeSet<>();
            for (final AccessibilityWindowInfo window : automation.getWindows()) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
                findNode(window.getRoot(), node -> {
                    if (node.isVisibleToUser() && description(node).length() > 1) labels.add(description(node));
                    return false;
                });
            }
            Log.i("CursorWordJourney", "Visible keyboard labels for " + word + ": " + labels);
            throw failure;
        }
    }

    private static AccessibilityNodeInfo awaitImeNode(final UiAutomation automation,
            final Predicate<AccessibilityNodeInfo> match, final String expected) {
        return awaitNode(automation, AccessibilityWindowInfo.TYPE_INPUT_METHOD, match, expected);
    }

    private static AccessibilityNodeInfo awaitNode(final UiAutomation automation, final int windowType,
            final Predicate<AccessibilityNodeInfo> match, final String expected) {
        final long end = SystemClock.uptimeMillis() + 5_000;
        do {
            for (final AccessibilityWindowInfo window : automation.getWindows()) {
                if (window.getType() != windowType) continue;
                final AccessibilityNodeInfo node = findNode(window.getRoot(), match);
                if (node != null) return node;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("Not shown: " + expected);
    }

    private static AccessibilityNodeInfo findNode(final AccessibilityNodeInfo node,
            final Predicate<AccessibilityNodeInfo> match) {
        if (node == null) return null;
        if (node.isVisibleToUser() && node.refresh() && match.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            final AccessibilityNodeInfo found = findNode(node.getChild(i), match);
            if (found != null) return found;
        }
        return null;
    }
}
