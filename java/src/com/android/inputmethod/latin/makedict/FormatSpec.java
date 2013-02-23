/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;

/**
 * Dictionary File Format Specification.
 */
public final class FormatSpec {

    /*
     * Array of Node(FusionDictionary.Node) layout is as follows:
     *
     * g |
     * r | the number of groups, 1 or 2 bytes.
     * o | 1 byte = bbbbbbbb match
     * u |   case 1xxxxxxx => xxxxxxx << 8 + next byte
     * p |   otherwise => bbbbbbbb
     * c |
     * ount
     *
     * g |
     * r | sequence of groups,
     * o | the layout of each group is described below.
     * u |
     * ps
     *
     * f |
     * o | IF SUPPORTS_DYNAMIC_UPDATE (defined in the file header)
     * r |     forward link address, 3byte
     * w | 1 byte = bbbbbbbb match
     * a |   case 1xxxxxxx => -((xxxxxxx << 16) + (next byte << 8) + next byte)
     * r |   otherwise => (xxxxxxx << 16) + (next byte << 8) + next byte
     * d |
     * linkaddress
     */

    /* Node(CharGroup) layout is as follows:
     *   | IF !SUPPORTS_DYNAMIC_UPDATE
     *   |   addressType                         xx     : mask with MASK_GROUP_ADDRESS_TYPE
     *   |                           2 bits, 00 = no children : FLAG_GROUP_ADDRESS_TYPE_NOADDRESS
     * f |                                   01 = 1 byte      : FLAG_GROUP_ADDRESS_TYPE_ONEBYTE
     * l |                                   10 = 2 bytes     : FLAG_GROUP_ADDRESS_TYPE_TWOBYTES
     * a |                                   11 = 3 bytes     : FLAG_GROUP_ADDRESS_TYPE_THREEBYTES
     * g | ELSE
     * s |   is moved ?              2 bits, 11 = no          : FLAG_IS_NOT_MOVED
     *   |                              This must be the same as FLAG_GROUP_ADDRESS_TYPE_THREEBYTES
     *   |                                   01 = yes         : FLAG_IS_MOVED
     *   |                        the new address is stored in the same place as the parent address
     *   |   is deleted?                     10 = yes         : FLAG_IS_DELETED
     *   | has several chars ?         1 bit, 1 = yes, 0 = no   : FLAG_HAS_MULTIPLE_CHARS
     *   | has a terminal ?            1 bit, 1 = yes, 0 = no   : FLAG_IS_TERMINAL
     *   | has shortcut targets ?      1 bit, 1 = yes, 0 = no   : FLAG_HAS_SHORTCUT_TARGETS
     *   | has bigrams ?               1 bit, 1 = yes, 0 = no   : FLAG_HAS_BIGRAMS
     *   | is not a word ?             1 bit, 1 = yes, 0 = no   : FLAG_IS_NOT_A_WORD
     *   | is blacklisted ?            1 bit, 1 = yes, 0 = no   : FLAG_IS_BLACKLISTED
     *
     * p |
     * a | IF SUPPORTS_DYNAMIC_UPDATE (defined in the file header)
     * r |     parent address, 3byte
     * e | 1 byte = bbbbbbbb match
     * n |   case 1xxxxxxx => -((0xxxxxxx << 16) + (next byte << 8) + next byte)
     * t |   otherwise => (bbbbbbbb << 16) + (next byte << 8) + next byte
     * a |
     * ddress
     *
     * c | IF FLAG_HAS_MULTIPLE_CHARS
     * h |   char, char, char, char    n * (1 or 3 bytes) : use CharGroupInfo for i/o helpers
     * a |   end                       1 byte, = 0
     * r | ELSE
     * s |   char                      1 or 3 bytes
     *   | END
     *
     * f |
     * r | IF FLAG_IS_TERMINAL
     * e |   frequency                 1 byte
     * q |
     *
     * c | IF 00 = FLAG_GROUP_ADDRESS_TYPE_NOADDRESS = addressType
     * h |   // nothing
     * i | ELSIF 01 = FLAG_GROUP_ADDRESS_TYPE_ONEBYTE == addressType
     * l |   children address, 1 byte
     * d | ELSIF 10 = FLAG_GROUP_ADDRESS_TYPE_TWOBYTES == addressType
     * r |   children address, 2 bytes
     * e | ELSE // 11 = FLAG_GROUP_ADDRESS_TYPE_THREEBYTES = addressType
     * n |   children address, 3 bytes
     * A | END
     * d
     * dress
     *
     *   | IF FLAG_IS_TERMINAL && FLAG_HAS_SHORTCUT_TARGETS
     *   | shortcut string list
     *   | IF FLAG_IS_TERMINAL && FLAG_HAS_BIGRAMS
     *   | bigrams address list
     *
     * Char format is:
     * 1 byte = bbbbbbbb match
     * case 000xxxxx: xxxxx << 16 + next byte << 8 + next byte
     * else: if 00011111 (= 0x1F) : this is the terminator. This is a relevant choice because
     *       unicode code points range from 0 to 0x10FFFF, so any 3-byte value starting with
     *       00011111 would be outside unicode.
     * else: iso-latin-1 code
     * This allows for the whole unicode range to be encoded, including chars outside of
     * the BMP. Also everything in the iso-latin-1 charset is only 1 byte, except control
     * characters which should never happen anyway (and still work, but take 3 bytes).
     *
     * bigram address list is:
     * <flags> = | hasNext = 1 bit, 1 = yes, 0 = no     : FLAG_ATTRIBUTE_HAS_NEXT
     *           | addressSign = 1 bit,                 : FLAG_ATTRIBUTE_OFFSET_NEGATIVE
     *           |                      1 = must take -address, 0 = must take +address
     *           |                         xx : mask with MASK_ATTRIBUTE_ADDRESS_TYPE
     *           | addressFormat = 2 bits, 00 = unused  : FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE
     *           |                         01 = 1 byte  : FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE
     *           |                         10 = 2 bytes : FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES
     *           |                         11 = 3 bytes : FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES
     *           | 4 bits : frequency         : mask with FLAG_ATTRIBUTE_FREQUENCY
     * <address> | IF (01 == FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE == addressFormat)
     *           |   read 1 byte, add top 4 bits
     *           | ELSIF (10 == FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES == addressFormat)
     *           |   read 2 bytes, add top 4 bits
     *           | ELSE // 11 == FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES == addressFormat
     *           |   read 3 bytes, add top 4 bits
     *           | END
     *           | if (FLAG_ATTRIBUTE_OFFSET_NEGATIVE) then address = -address
     * if (FLAG_ATTRIBUTE_HAS_NEXT) goto bigram_and_shortcut_address_list_is
     *
     * shortcut string list is:
     * <byte size> = GROUP_SHORTCUT_LIST_SIZE_SIZE bytes, big-endian: size of the list, in bytes.
     * <flags>     = | hasNext = 1 bit, 1 = yes, 0 = no : FLAG_ATTRIBUTE_HAS_NEXT
     *               | reserved = 3 bits, must be 0
     *               | 4 bits : frequency : mask with FLAG_ATTRIBUTE_FREQUENCY
     * <shortcut>  = | string of characters at the char format described above, with the terminator
     *               | used to signal the end of the string.
     * if (FLAG_ATTRIBUTE_HAS_NEXT goto flags
     */

    static final int VERSION_1_MAGIC_NUMBER = 0x78B1;
    public static final int VERSION_2_MAGIC_NUMBER = 0x9BC13AFE;
    static final int MINIMUM_SUPPORTED_VERSION = 1;
    static final int MAXIMUM_SUPPORTED_VERSION = 3;
    static final int NOT_A_VERSION_NUMBER = -1;
    static final int FIRST_VERSION_WITH_HEADER_SIZE = 2;
    static final int FIRST_VERSION_WITH_DYNAMIC_UPDATE = 3;

    // These options need to be the same numeric values as the one in the native reading code.
    static final int GERMAN_UMLAUT_PROCESSING_FLAG = 0x1;
    // TODO: Make the native reading code read this variable.
    static final int SUPPORTS_DYNAMIC_UPDATE = 0x2;
    static final int FRENCH_LIGATURE_PROCESSING_FLAG = 0x4;
    static final int CONTAINS_BIGRAMS_FLAG = 0x8;

    // TODO: Make this value adaptative to content data, store it in the header, and
    // use it in the reading code.
    static final int MAX_WORD_LENGTH = Constants.Dictionary.MAX_WORD_LENGTH;

    static final int PARENT_ADDRESS_SIZE = 3;
    static final int FORWARD_LINK_ADDRESS_SIZE = 3;

    // These flags are used only in the static dictionary.
    static final int MASK_GROUP_ADDRESS_TYPE = 0xC0;
    static final int FLAG_GROUP_ADDRESS_TYPE_NOADDRESS = 0x00;
    static final int FLAG_GROUP_ADDRESS_TYPE_ONEBYTE = 0x40;
    static final int FLAG_GROUP_ADDRESS_TYPE_TWOBYTES = 0x80;
    static final int FLAG_GROUP_ADDRESS_TYPE_THREEBYTES = 0xC0;

    static final int FLAG_HAS_MULTIPLE_CHARS = 0x20;

    static final int FLAG_IS_TERMINAL = 0x10;
    static final int FLAG_HAS_SHORTCUT_TARGETS = 0x08;
    static final int FLAG_HAS_BIGRAMS = 0x04;
    static final int FLAG_IS_NOT_A_WORD = 0x02;
    static final int FLAG_IS_BLACKLISTED = 0x01;

    // These flags are used only in the dynamic dictionary.
    static final int MASK_MOVE_AND_DELETE_FLAG = 0xC0;
    static final int FIXED_BIT_OF_DYNAMIC_UPDATE_MOVE = 0x40;
    static final int FLAG_IS_MOVED = 0x00 | FIXED_BIT_OF_DYNAMIC_UPDATE_MOVE;
    static final int FLAG_IS_NOT_MOVED = 0x80 | FIXED_BIT_OF_DYNAMIC_UPDATE_MOVE;
    static final int FLAG_IS_DELETED = 0x80;

    static final int FLAG_ATTRIBUTE_HAS_NEXT = 0x80;
    static final int FLAG_ATTRIBUTE_OFFSET_NEGATIVE = 0x40;
    static final int MASK_ATTRIBUTE_ADDRESS_TYPE = 0x30;
    static final int FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE = 0x10;
    static final int FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES = 0x20;
    static final int FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES = 0x30;
    static final int FLAG_ATTRIBUTE_FREQUENCY = 0x0F;

    static final int GROUP_CHARACTERS_TERMINATOR = 0x1F;

    static final int GROUP_TERMINATOR_SIZE = 1;
    static final int GROUP_FLAGS_SIZE = 1;
    static final int GROUP_FREQUENCY_SIZE = 1;
    static final int GROUP_MAX_ADDRESS_SIZE = 3;
    static final int GROUP_ATTRIBUTE_FLAGS_SIZE = 1;
    static final int GROUP_ATTRIBUTE_MAX_ADDRESS_SIZE = 3;
    static final int GROUP_SHORTCUT_LIST_SIZE_SIZE = 2;

    static final int NO_CHILDREN_ADDRESS = Integer.MIN_VALUE;
    static final int NO_PARENT_ADDRESS = 0;
    static final int NO_FORWARD_LINK_ADDRESS = 0;
    static final int INVALID_CHARACTER = -1;

    static final int MAX_CHARGROUPS_FOR_ONE_BYTE_CHARGROUP_COUNT = 0x7F; // 127
    static final int MAX_CHARGROUPS_IN_A_NODE = 0x7FFF; // 32767
    static final int MAX_BIGRAMS_IN_A_GROUP = 10000;

    static final int MAX_TERMINAL_FREQUENCY = 255;
    static final int MAX_BIGRAM_FREQUENCY = 15;

    public static final int SHORTCUT_WHITELIST_FREQUENCY = 15;

    // This option needs to be the same numeric value as the one in binary_format.h.
    static final int NOT_VALID_WORD = -99;
    static final int SIGNED_CHILDREN_ADDRESS_SIZE = 3;

    /**
     * Options about file format.
     */
    public static final class FormatOptions {
        public final int mVersion;
        public final boolean mSupportsDynamicUpdate;
        public FormatOptions(final int version) {
            this(version, false);
        }
        public FormatOptions(final int version, final boolean supportsDynamicUpdate) {
            mVersion = version;
            if (version < FIRST_VERSION_WITH_DYNAMIC_UPDATE && supportsDynamicUpdate) {
                throw new RuntimeException("Dynamic updates are only supported with versions "
                        + FIRST_VERSION_WITH_DYNAMIC_UPDATE + " and ulterior.");
            }
            mSupportsDynamicUpdate = supportsDynamicUpdate;
        }
    }

    /**
     * Class representing file header.
     */
    public static final class FileHeader {
        public final int mHeaderSize;
        public final DictionaryOptions mDictionaryOptions;
        public final FormatOptions mFormatOptions;
        private static final String DICTIONARY_VERSION_ATTRIBUTE = "version";
        private static final String DICTIONARY_LOCALE_ATTRIBUTE = "locale";
        private static final String DICTIONARY_ID_ATTRIBUTE = "dictionary";
        private static final String DICTIONARY_DESCRIPTION_ATTRIBUTE = "description";
        public FileHeader(final int headerSize, final DictionaryOptions dictionaryOptions,
                final FormatOptions formatOptions) {
            mHeaderSize = headerSize;
            mDictionaryOptions = dictionaryOptions;
            mFormatOptions = formatOptions;
        }

        // Helper method to get the locale as a String
        public String getLocaleString() {
            return mDictionaryOptions.mAttributes.get(FileHeader.DICTIONARY_LOCALE_ATTRIBUTE);
        }

        // Helper method to get the version String
        public String getVersion() {
            return mDictionaryOptions.mAttributes.get(FileHeader.DICTIONARY_VERSION_ATTRIBUTE);
        }

        // Helper method to get the dictionary ID as a String
        public String getId() {
            return mDictionaryOptions.mAttributes.get(FileHeader.DICTIONARY_ID_ATTRIBUTE);
        }

        // Helper method to get the description
        public String getDescription() {
            // TODO: Right now each dictionary file comes with a description in its own language.
            // It will display as is no matter the device's locale. It should be internationalized.
            return mDictionaryOptions.mAttributes.get(FileHeader.DICTIONARY_DESCRIPTION_ATTRIBUTE);
        }
    }

    private FormatSpec() {
        // This utility class is not publicly instantiable.
    }
}
