package io.github.sspanak.tt9.languages;

import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import io.github.sspanak.tt9.BuildConfig;
import io.github.sspanak.tt9.util.AssetFile;
import io.github.sspanak.tt9.util.Logger;

public class LanguageDefinition {
	private static final String LOG_TAG = LanguageDefinition.class.getSimpleName();

	private static final String LANGUAGES_DIR = "languages";
	/** Mapp med en .yml per språk (t.ex. Swedish.yml). */
	private static final String DEFINITIONS_DIR = LANGUAGES_DIR + "/definitions";
	/** Bakåtkompatibilitet: sammanslagen fil om mappen saknas/är tom. */
	private static final String DEFINITIONS_FILE = LANGUAGES_DIR + "/definitions.yml";
	private static final String YAML_SEPARATOR = "---";

	public String abcString = "";
	public String currency = "";
	public String dictionaryFile = "";
	public boolean filterBySounds = false;
	public boolean hasABC = true;
	public boolean hasSpaceBetweenWords = true;
	public boolean hasUpperCase = true;
	public String iconABC = "";
	public String iconT9 = "";
	public boolean isTranscribed = false;
	public final ArrayList<ArrayList<String>> layout = new ArrayList<>();
	public String locale = "";
	public String name = "";
	public String ngramFile = "";
	@NonNull public final HashMap<Integer, String> numerals = new HashMap<>();

	private boolean inLayout = false;

	/**
	 * Converts YAML definitions to LanguageDefinition objects. All properties in the YAML are
	 * considered optional, so the LanguageDefinition defaults will be used when some property is omitted.
	 * Had to write all this, because the only usable library, SnakeYAML, works fine on Android 10+,
	 * but causes crashes on older devices.
	 */
	private LanguageDefinition() {}

	/**
	 * Returns all language definitions from assets.
	 * <p>
	 * Preferred: {@code languages/definitions/*.yml} (one file per language).<br>
	 * Fallback: {@code languages/definitions.yml} with {@code ---} between languages.
	 */
	public static ArrayList<LanguageDefinition> getAll(AssetManager assets) {
		ArrayList<LanguageDefinition> definitions = new ArrayList<>();

		// 1) languages/definitions/*.yml
		String[] files = null;
		try {
			files = assets.list(DEFINITIONS_DIR);
		} catch (IOException e) {
			Logger.e(LOG_TAG, "Failed listing '" + DEFINITIONS_DIR + "'. " + e.getMessage());
		}

		if (files != null && files.length > 0) {
			Arrays.sort(files);
			for (String name : files) {
				if (name == null || !name.endsWith(".yml")) {
					continue;
				}
				String path = DEFINITIONS_DIR + "/" + name;
				String[] lines = readAssetLines(assets, path);
				if (lines.length == 0) {
					continue;
				}
				definitions.addAll(parseDefinitionBlocks(lines));
			}
		}

		// 2) Fallback: languages/definitions.yml
		if (definitions.isEmpty()) {
			String[] lines = readAssetLines(assets, DEFINITIONS_FILE);
			if (lines.length > 0) {
				definitions.addAll(parseDefinitionBlocks(lines));
			}
		}

		Logger.d("tt9.LanguageCollection", "Found " + definitions.size() + " languages");
		return definitions;
	}

	/**
	 * Parses one or more definition blocks from YAML lines.
	 * Blocks are separated by {@code ---}; a single-language file is one block.
	 */
	private static ArrayList<LanguageDefinition> parseDefinitionBlocks(String[] lines) {
		ArrayList<LanguageDefinition> out = new ArrayList<>();
		LanguageDefinition definition = new LanguageDefinition();
		boolean hasContent = false;

		for (String raw : lines) {
			if (raw == null) {
				continue;
			}
			// Strip end-of-line comments
			String line = raw.replaceFirst("\\s*#.*$", "").trim();
			if (line.isEmpty()) {
				continue;
			}

			if (YAML_SEPARATOR.equals(line)) {
				if (hasContent) {
					out.add(definition);
				}
				definition = new LanguageDefinition();
				hasContent = false;
				continue;
			}

			if (!definition.setLayoutEntry(line)) {
				definition.setProperty(line);
			}
			hasContent = true;
		}

		if (hasContent) {
			out.add(definition);
		}
		return out;
	}

	private static String[] readAssetLines(AssetManager assets, String path) {
		try {
			BufferedReader reader = new AssetFile(assets, path).getReader();
			StringBuilder contents = new StringBuilder();
			char[] buffer = new char[10000];
			int read;

			while ((read = reader.read(buffer)) != -1) {
				contents.append(buffer, 0, read);
			}
			reader.close();

			return contents.toString().split("\n", -1);
		} catch (IOException e) {
			Logger.e(LOG_TAG, "Failed reading language definitions from: '" + path + "'. " + e.getMessage());
			return new String[0];
		}
	}

	/**
	 * Normalizes a YAML boolean to a Java boolean.
	 */
	private boolean parseYamlBoolean(@Nullable String value) {
		if (value == null) {
			return false;
		}

		return switch (value.toLowerCase()) {
			case "true", "on", "yes", "y" -> true;
			default -> false;
		};
	}

	/**
	 * Sets a property based on the key-value pair in a YAML line. If the key does not match any
	 * property, the line is ignored.
	 */
	private void setProperty(@NonNull String line) {
		int colonIndex = line.indexOf(':');
		if (colonIndex == -1) {
			return;
		}

		String key = line.substring(0, colonIndex).trim();
		String value = (colonIndex + 1 < line.length()) ? line.substring(colonIndex + 1).trim() : "";

		switch (key) {
			case "abcString":
				abcString = value;
				return;
			case "currency":
				currency = value;
				return;
			case "dictionaryFile":
				// t.ex. sv-utf8.csv → sv-utf8.zip när DICTIONARY_EXTENSION = "zip"
				dictionaryFile = value.replaceFirst("\\.\\w+$", "." + BuildConfig.DICTIONARY_EXTENSION);
				return;
			case "filterBySounds":
				filterBySounds = parseYamlBoolean(value);
				return;
			case "hasABC":
				hasABC = parseYamlBoolean(value);
				return;
			case "hasSpaceBetweenWords":
				hasSpaceBetweenWords = parseYamlBoolean(value);
				return;
			case "hasUpperCase":
				hasUpperCase = parseYamlBoolean(value);
				return;
			case "iconABC":
				iconABC = value;
				return;
			case "iconT9":
				iconT9 = value;
				return;
			case "sounds":
				isTranscribed = true;
				return;
			case "locale":
				locale = value;
				return;
			case "name":
				name = value;
				return;
			case "numerals":
				setNumerals(value);
				return;
			case "ngramFile":
				ngramFile = value;
				return;
		}
	}

	/**
	 * Builds the key layout line by line. Returns true when a layout entry is successfully set.
	 */
	private boolean setLayoutEntry(@NonNull String line) {
		if (!inLayout) {
			return inLayout = "layout:".equals(line);
		}

		ArrayList<String> layoutEntry = parseList(line);
		if (layoutEntry == null) {
			inLayout = false;
		} else {
			layout.add(layoutEntry);
			return true;
		}

		return false;
	}

	private void setNumerals(@NonNull String yamlList) {
		ArrayList<String> numberList = parseList(yamlList);
		if (numberList == null || numberList.size() != 10) {
			return;
		}

		for (int i = 0; i < 10; i++) {
			numerals.put(i, numberList.get(i));
		}
	}

	/**
	 * Validates a YAML line as an array and returns the character list to be assigned to a given key (a layout entry).
	 * If the YAML line is invalid, NULL will be returned.
	 */
	@Nullable
	private ArrayList<String> parseList(@NonNull String yamlLine) {
		int start = yamlLine.indexOf('[');
		int end = yamlLine.indexOf(']');
		if (start == -1 || end == -1 || start >= end) {
			return null;
		}

		String entryTxt = yamlLine.substring(start + 1, end).replace(" ", "");

		ArrayList<String> entry = new ArrayList<>();
		int last = 0, len = entryTxt.length();

		for (int i = 0; i < len; i++) {
			if (entryTxt.charAt(i) == ',') {
				entry.add(entryTxt.substring(last, i));
				last = i + 1;
			}
		}

		if (last < len) {
			entry.add(entryTxt.substring(last));
		}

		return entry;
	}

	public String getDictionaryFile() {
		return LANGUAGES_DIR + "/dictionaries/" + dictionaryFile;
	}

	public String getNgramsFile() {
		return LANGUAGES_DIR + "/ngrams/" + ngramFile;
	}
}
