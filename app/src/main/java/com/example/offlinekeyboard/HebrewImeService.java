package com.example.offlinekeyboard;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HebrewImeService extends InputMethodService {
    private static final int BG = Color.rgb(231, 236, 243);
    private static final int KEY_BG = Color.rgb(255, 255, 255);
    private static final int SPECIAL_BG = Color.rgb(202, 215, 231);
    private static final int TEXT = Color.rgb(32, 46, 64);
    private static final int ACCENT = Color.rgb(49, 94, 155);

    private LocalPredictor predictor;
    private LinearLayout keyboardRoot;
    private LinearLayout suggestionsRow;
    private boolean englishMode = false;
    private boolean symbolsMode = false;
    private boolean shiftOnce = false;
    private boolean predictionAllowed = true;
    private boolean learningAllowed = true;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        predictor = new LocalPredictor(this);
    }

    @Override
    public View onCreateInputView() {
        keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setGravity(Gravity.CENTER_HORIZONTAL);
        keyboardRoot.setPadding(dp(4), dp(5), dp(4), dp(7));
        keyboardRoot.setBackgroundColor(BG);
        keyboardRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        buildKeyboard();
        return keyboardRoot;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        int inputType = attribute == null ? EditorInfo.TYPE_CLASS_TEXT : attribute.inputType;
        int inputClass = inputType & EditorInfo.TYPE_MASK_CLASS;
        int variation = inputType & EditorInfo.TYPE_MASK_VARIATION;

        boolean password = variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean specialText = variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER;

        int flags = inputType & EditorInfo.TYPE_MASK_FLAGS;
        boolean explicitlyNoSuggestions = (flags & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        predictionAllowed = inputClass == EditorInfo.TYPE_CLASS_TEXT && !password && !specialText && !explicitlyNoSuggestions;
        learningAllowed = predictionAllowed && (
                variation == EditorInfo.TYPE_TEXT_VARIATION_NORMAL
                || variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                || variation == EditorInfo.TYPE_TEXT_VARIATION_LONG_MESSAGE);
        if (inputClass == EditorInfo.TYPE_CLASS_NUMBER || inputClass == EditorInfo.TYPE_CLASS_PHONE
                || inputClass == EditorInfo.TYPE_CLASS_DATETIME) {
            predictionAllowed = false;
            learningAllowed = false;
        }
        shiftOnce = false;
        if (keyboardRoot != null) {
            buildKeyboard();
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshSuggestions();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        refreshSuggestions();
    }

    private void buildKeyboard() {
        if (keyboardRoot == null) return;
        keyboardRoot.removeAllViews();

        suggestionsRow = new LinearLayout(this);
        suggestionsRow.setOrientation(LinearLayout.HORIZONTAL);
        suggestionsRow.setGravity(Gravity.CENTER_VERTICAL);
        suggestionsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams candidateLp = new LinearLayout.LayoutParams(-1, dp(43));
        candidateLp.setMargins(dp(1), 0, dp(1), dp(4));
        keyboardRoot.addView(suggestionsRow, candidateLp);

        if (symbolsMode) {
            addLetterRow("1234567890");
            addLetterRow("@#$%&*-+/");
            addLetterRow("()[]{}!?=");
        } else if (englishMode) {
            addLetterRow(shiftOnce ? "QWERTYUIOP" : "qwertyuiop");
            addLetterRow(shiftOnce ? "ASDFGHJKL" : "asdfghjkl");
            addEnglishThirdRow();
        } else {
            addLetterRow("קראטוןםפ");
            addLetterRow("שדגכעיחלךף");
            addLetterRow("זסבהנמצתץ");
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        keyboardRoot.addView(bottom, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpecialKey(bottom, "?123", 1.12f, SPECIAL_BG, () -> {
            symbolsMode = !symbolsMode;
            buildKeyboard();
        });
        addSpecialKey(bottom, englishMode ? "עברית" : "EN", 1.12f, SPECIAL_BG, () -> {
            englishMode = !englishMode;
            symbolsMode = false;
            shiftOnce = false;
            buildKeyboard();
        });
        addSpecialKey(bottom, ",", 0.78f, KEY_BG, () -> commit(","));
        addSpecialKey(bottom, "רווח", 3.7f, KEY_BG, this::handleSpace);
        addSpecialKey(bottom, ".", 0.78f, KEY_BG, () -> commit("."));
        addSpecialKey(bottom, "⌫", 1.15f, SPECIAL_BG, this::handleBackspace);
        addSpecialKey(bottom, "↵", 1.15f, ACCENT, this::handleEnter);
        refreshSuggestions();
    }

    private void addLetterRow(String letters) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection((englishMode || symbolsMode) ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(48));
        rowLp.setMargins(0, dp(2), 0, dp(2));
        keyboardRoot.addView(row, rowLp);

        for (int i = 0; i < letters.length(); i++) {
            final String value = String.valueOf(letters.charAt(i));
            TextView key = keyView(value, KEY_BG, TEXT, 21);
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(0, -1, 1f);
            keyLp.setMargins(dp(2), dp(1), dp(2), dp(1));
            row.addView(key, keyLp);
            key.setOnClickListener(v -> {
                boolean resetShift = englishMode && shiftOnce && !symbolsMode;
                if (resetShift) shiftOnce = false;
                commit(value);
                if (resetShift) buildKeyboard();
            });
        }
    }

    private void addEnglishThirdRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(48));
        rowLp.setMargins(0, dp(2), 0, dp(2));
        keyboardRoot.addView(row, rowLp);

        TextView shift = keyView("⇧", SPECIAL_BG, TEXT, 20);
        LinearLayout.LayoutParams shiftLp = new LinearLayout.LayoutParams(0, -1, 1.25f);
        shiftLp.setMargins(dp(2), dp(1), dp(2), dp(1));
        row.addView(shift, shiftLp);
        shift.setOnClickListener(v -> {
            shiftOnce = !shiftOnce;
            buildKeyboard();
        });

        String letters = shiftOnce ? "ZXCVBNM" : "zxcvbnm";
        for (int i = 0; i < letters.length(); i++) {
            final String value = String.valueOf(letters.charAt(i));
            TextView key = keyView(value, KEY_BG, TEXT, 21);
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(0, -1, 1f);
            keyLp.setMargins(dp(2), dp(1), dp(2), dp(1));
            row.addView(key, keyLp);
            key.setOnClickListener(v -> {
                boolean resetShift = shiftOnce;
                if (resetShift) shiftOnce = false;
                commit(value);
                if (resetShift) buildKeyboard();
            });
        }
    }

    private void addSpecialKey(LinearLayout parent, String label, float weight, int color, Runnable action) {
        int textColor = color == ACCENT ? Color.WHITE : TEXT;
        TextView key = keyView(label, color, textColor, label.length() > 3 ? 12 : 17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        lp.setMargins(dp(2), dp(1), dp(2), dp(1));
        parent.addView(key, lp);
        key.setOnClickListener(v -> action.run());
    }

    private TextView keyView(String label, int backgroundColor, int textColor, int textSize) {
        TextView key = new TextView(this);
        key.setText(label);
        key.setTextColor(textColor);
        key.setTextSize(textSize);
        key.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        key.setGravity(Gravity.CENTER);
        key.setSingleLine(true);
        key.setBackground(makeRounded(backgroundColor, 7));
        key.setElevation(dp(1));
        return key;
    }

    private GradientDrawable makeRounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void commit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(text, 1);
        refreshSuggestions();
    }

    private void handleSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (learningAllowed) {
            String before = safeBeforeCursor(160);
            String current = lastWord(before);
            if (!current.isEmpty()) {
                String prior = lastWord(before.substring(0, Math.max(0, before.length() - current.length())));
                predictor.learn(current, prior);
            }
        }
        ic.commitText(" ", 1);
        refreshSuggestions();
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
        refreshSuggestions();
    }

    private void handleEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
                && sendDefaultEditorAction(true)) {
            return;
        }
        ic.commitText("\n", 1);
        refreshSuggestions();
    }

    private String safeBeforeCursor(int length) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence value = ic.getTextBeforeCursor(length, 0);
        return value == null ? "" : value.toString();
    }

    private String currentWordPrefix(String text) {
        if (text == null || text.isEmpty()) return "";
        char last = text.charAt(text.length() - 1);
        // A space or punctuation means the previous word is complete and we are predicting the next one.
        if (!Character.isLetter(last) && !isHebrewMark(last)) return "";
        return lastWord(text);
    }

    private String lastWord(String text) {
        if (text == null || text.isEmpty()) return "";
        int end = text.length();
        while (end > 0 && !Character.isLetter(text.charAt(end - 1)) && !isHebrewMark(text.charAt(end - 1))) end--;
        int start = end;
        while (start > 0 && (Character.isLetter(text.charAt(start - 1)) || isHebrewMark(text.charAt(start - 1)))) start--;
        return text.substring(start, end);
    }

    private boolean isHebrewMark(char c) {
        return c >= '\u0591' && c <= '\u05C7';
    }

    private void refreshSuggestions() {
        if (suggestionsRow == null) return;
        suggestionsRow.removeAllViews();
        List<String> suggestions = new ArrayList<>();
        if (predictionAllowed && predictor != null) {
            String before = safeBeforeCursor(160);
            String prefix = currentWordPrefix(before);
            String previous = "";
            if (!prefix.isEmpty()) {
                int index = before.lastIndexOf(prefix);
                previous = index > 0 ? lastWord(before.substring(0, index)) : "";
            } else {
                previous = lastWord(before);
            }
            suggestions = predictor.suggest(prefix, previous, englishMode);
        }

        for (int i = 0; i < 3; i++) {
            final String suggestion = i < suggestions.size() ? suggestions.get(i) : "";
            TextView candidate = new TextView(this);
            candidate.setText(suggestion);
            candidate.setTextSize(15);
            candidate.setTextColor(suggestion.isEmpty() ? Color.TRANSPARENT : TEXT);
            candidate.setGravity(Gravity.CENTER);
            candidate.setSingleLine(true);
            candidate.setBackground(makeRounded(suggestion.isEmpty() ? BG : KEY_BG, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            suggestionsRow.addView(candidate, lp);
            if (!suggestion.isEmpty()) {
                candidate.setOnClickListener(v -> acceptSuggestion(suggestion));
            }
        }
    }

    private void acceptSuggestion(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || word == null || word.isEmpty()) return;
        String prefix = currentWordPrefix(safeBeforeCursor(160));
        if (!prefix.isEmpty()) {
            ic.deleteSurroundingText(prefix.length(), 0);
        }
        ic.commitText(word + " ", 1);
        refreshSuggestions();
    }
}
