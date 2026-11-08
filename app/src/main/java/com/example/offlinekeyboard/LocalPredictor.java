package com.example.offlinekeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A compact, fully on-device dictionary and n-gram predictor. */
public class LocalPredictor {
    private final Context appContext;
    private final SharedPreferences preferences;
    private final Map<String, Integer> heWords = new HashMap<>();
    private final Map<String, Integer> enWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> bigrams = new HashMap<>();

    public LocalPredictor(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences("local_language_data", Context.MODE_PRIVATE);
        loadWordList("he_words.txt", heWords);
        loadWordList("en_words.txt", enWords);
        loadBigrams();
    }

    private void loadWordList(String asset, Map<String, Integer> target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                appContext.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                // Dictionary rows must contain exactly: one word + one frequency.
                // Multi-word phrases are handled by the bigram table instead.
                if (parts.length != 2 || parts[0].isEmpty()) continue;
                int frequency;
                try { frequency = Integer.parseInt(parts[1]); }
                catch (NumberFormatException ignored) { continue; }
                String word = parts[0].toLowerCase(Locale.ROOT);
                Integer oldFrequency = target.get(word);
                if (oldFrequency == null || frequency > oldFrequency) target.put(word, frequency);
            }
        } catch (Exception ignored) {
            // The keyboard remains usable even if an optional dictionary asset is missing.
        }
    }

    private void loadBigrams() {
        addBigram("אני", "רוצה", 500); addBigram("אני", "צריך", 280);
        addBigram("אני", "יודע", 260); addBigram("אני", "יכול", 240);
        addBigram("אני", "לא", 420); addBigram("אני", "הולך", 180);
        addBigram("מה", "שלומך", 320); addBigram("מה", "קרה", 280);
        addBigram("מה", "אתה", 250); addBigram("מה", "זה", 240);
        addBigram("זה", "לא", 380); addBigram("זה", "היה", 260);
        addBigram("זה", "נראה", 210); addBigram("זה", "יכול", 180);
        addBigram("של", "המערכת", 180); addBigram("של", "המשתמש", 160);
        addBigram("בוקר", "טוב", 600); addBigram("ערב", "טוב", 600);
        addBigram("שלום", "רב", 450); addBigram("תודה", "רבה", 500);
        addBigram("תודה", "על", 250); addBigram("אין", "בעיה", 450);
        addBigram("אני", "מנסה", 180); addBigram("אני", "מחפש", 180);
        addBigram("אני", "בונה", 160); addBigram("אני", "עובד", 160);
        addBigram("אפשר", "לקבל", 300); addBigram("אפשר", "לעשות", 280);
        addBigram("איך", "אפשר", 300); addBigram("איך", "עושים", 220);
        addBigram("איך", "לבנות", 180); addBigram("יש", "לי", 330);
        addBigram("יש", "אפשרות", 260); addBigram("אין", "לי", 260);
        addBigram("כדי", "לבדוק", 180); addBigram("כדי", "לראות", 180);
        addBigram("with", "the", 600); addBigram("how", "to", 600);
        addBigram("I", "am", 500); addBigram("I", "want", 450);
        addBigram("I", "need", 400); addBigram("thank", "you", 600);
    }

    private void addBigram(String first, String next, int frequency) {
        String key = first.toLowerCase(Locale.ROOT);
        String value = next.toLowerCase(Locale.ROOT);
        Map<String, Integer> following = bigrams.get(key);
        if (following == null) {
            following = new HashMap<>();
            bigrams.put(key, following);
        }
        following.put(value, frequency);
    }

    public synchronized List<String> suggest(String prefix, String previousWord, boolean english) {
        Map<String, Integer> dictionary = english ? enWords : heWords;
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        String previous = previousWord == null ? "" : previousWord.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> results = new LinkedHashSet<>();

        if (normalizedPrefix.isEmpty()) {
            Map<String, Integer> nextWords = new HashMap<>();
            Map<String, Integer> known = bigrams.get(previous);
            if (known != null) nextWords.putAll(known);

            String learnedPrefix = "b:" + previous + ":";
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                if (entry.getKey().startsWith(learnedPrefix) && entry.getValue() instanceof Integer) {
                    String word = entry.getKey().substring(learnedPrefix.length());
                    Integer existing = nextWords.get(word);
                    int current = existing == null ? 0 : existing;
                    nextWords.put(word, current + (Integer) entry.getValue() * 120);
                }
            }
            List<Map.Entry<String, Integer>> rankedNext = new ArrayList<>(nextWords.entrySet());
            Collections.sort(rankedNext, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String, Integer> item : rankedNext) {
                if (dictionary.containsKey(item.getKey()) || (english && isEnglishWord(item.getKey())) || (!english && isHebrewWord(item.getKey()))) {
                    results.add(item.getKey());
                    if (results.size() == 3) return new ArrayList<>(results);
                }
            }
        }

        Map<String, Integer> vocabulary = new HashMap<>(dictionary);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!entry.getKey().startsWith("w:") || !(entry.getValue() instanceof Integer)) continue;
            String learnedWord = entry.getKey().substring(2);
            boolean rightLanguage = english ? isEnglishWord(learnedWord) : isHebrewWord(learnedWord);
            if (rightLanguage && !vocabulary.containsKey(learnedWord)) vocabulary.put(learnedWord, 1);
        }

        List<Map.Entry<String, Integer>> matches = new ArrayList<>();
        for (Map.Entry<String, Integer> item : vocabulary.entrySet()) {
            if (normalizedPrefix.isEmpty() || item.getKey().startsWith(normalizedPrefix)) {
                matches.add(item);
            }
        }
        Collections.sort(matches, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                int leftScore = left.getValue() + preferences.getInt("w:" + left.getKey(), 0) * 100;
                int rightScore = right.getValue() + preferences.getInt("w:" + right.getKey(), 0) * 100;
                return Integer.compare(rightScore, leftScore);
            }
        });
        for (Map.Entry<String, Integer> item : matches) {
            results.add(item.getKey());
            if (results.size() == 3) break;
        }
        return new ArrayList<>(results);
    }

    private boolean isEnglishWord(String word) {
        return !word.isEmpty() && word.matches("[a-z]+(\\'[a-z]+)?");
    }

    private boolean isHebrewWord(String word) {
        return !word.isEmpty() && word.matches("[\\u0590-\\u05FF]+");
    }

    public synchronized void learn(String word, String previousWord) {
        if (word == null || word.length() < 2 || word.length() > 24) return;
        String cleanWord = word.toLowerCase(Locale.ROOT);
        if (!cleanWord.matches("[\\p{L}\\p{M}]+")) return;
        SharedPreferences.Editor editor = preferences.edit();
        String wordKey = "w:" + cleanWord;
        editor.putInt(wordKey, Math.min(100, preferences.getInt(wordKey, 0) + 1));
        if (previousWord != null && previousWord.length() > 0 && previousWord.length() < 24) {
            String previous = previousWord.toLowerCase(Locale.ROOT);
            if (previous.matches("[\\p{L}\\p{M}]+")) {
                String bigramKey = "b:" + previous + ":" + cleanWord;
                editor.putInt(bigramKey, Math.min(100, preferences.getInt(bigramKey, 0) + 1));
            }
        }
        editor.apply();
    }
}
