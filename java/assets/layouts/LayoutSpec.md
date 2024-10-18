# Keyboard Layout Format

A lot of concepts are explained through examples in this document, please read through them to
understand different ways of defining layouts.

A good way to learn is also to read through the existing layouts in this repository.

## Starting out

The YAML file's entry point is the [Keyboard](#keyboard) data class. It only requires a name and
rows.

The QWERTY layout is defined like so:
```yaml
name: "QWERTY"
rows:
  - letters: q w e r t y u i o p
  - letters: a s d f g h j k l
  - letters: z x c v b n m
```

The name must be defined first, and letter rows are defined next.

There are many ways to define the same thing. Use which one makes most sense for your use case. All rows in the below example are functionally identical:
```yaml
name: Different examples
rows:
  # Basic definition as string
  - letters: q w e r t
  
  # Basic definition as string (quoting is necessary if you use some symbols)
  - letters: "q w e r t"

  # Definition as inline list
  - letters: ["q", "w", "e", "r", "t"]

  # Definition as multiline list, and unicode escapes
  - letters:
      # U+0071: LATIN SMALL LETTER Q
      - "\u0071"
      # U+0077: LATIN SMALL LETTER W
      - "\u0077"
      # U+0065: LATIN SMALL LETTER E
      - "\u0065"
      # U+0072: LATIN SMALL LETTER R
      - "\u0072"
      # U+0074: LATIN SMALL LETTER T
      - "\u0074"

  # Defining base, explicit case, and mixing randomly
  - letters:
      - {type: base, spec: q}
      - {type: case, normal: w, shifted: W}
      - e
      - "\u0072"
      - type: case
        normal:
          type: base
          spec: "t|t" # pipe symbol is keyspec syntax: label|code
          code: 0x74  # though you can set code explicitly too
        shifted: {type: base, spec: T}
```

Defining layout-specific moreKeys can also be done in multiple ways:
```yaml
name: MoreKeys examples
rows:
  # List syntax: [primarykey, ...morekeys]
  - letters:
      - [a, ą]
      - b
      - [c, č]
      - [d] # For consistency you can also specify a key without morekeys like this
      - [e, ė, ę]

  # You can also do it inline at the cost of readability
  - letters: [[a, ą], b, [c, č], d, [e, ė, ę]]

  # Explicit BaseKey syntax
  - letters:
      - {type: base, spec: "a", moreKeys: "ą"}
      - {type: base, spec: b}
      - type: base
        spec: "c"
        moreKeys: "č"
      - "d"
      - type: base
        spec: "e"
        moreKeys: ["ę", "ė"]

  # You can use list syntax anywhere that expects a Key, such as `case`
  - letters:
      - type: case
        normal: [a, ą]
        shifted: [A, Ą]
      - b
      - {type: case, normal: [c, č], shifted: [C, Č]}
      - d
      - [e, ę, ė]
```

These shortcuts for defining things are explained more in the shortcuts section. In practice you
should avoid randomly mixing different ways of defining keys, you should stick to something that
keeps the file readable and understandable.

With RTL languages it's advised to use unicode escapes in order to keep the file easily editable.
It's good practice to include a comment explaining each unicode character:
```yaml
name: Arabic
rows:
  - letters:
      # U+0636: "ض" ARABIC LETTER DAD
      - "\u0636"
      # U+0635: "ص" ARABIC LETTER SAD
      - "\u0635"
      # U+062B: "ث" ARABIC LETTER THEH
      - "\u062B"
      # U+0642: "ق" ARABIC LETTER QAF
      # U+06A8: "ڨ" ARABIC LETTER QAF WITH THREE DOTS ABOVE
      - ["\u0642", "\u06A8"]
```


### Number row and bottom row

A default number row and bottom row will be added if they are not explicitly defined. You can
override them with custom ones for more advanced layouts:

```yaml
name: "PC QWERTY Example"
numberRowMode: AlwaysEnabled
rows:
  - numbers: "` 1 2 3 4 5 6 7 8 9 0 - ="
  - letters: "q w e r t y u i o p [ ] \\"
  - letters: "a s d f g h j k l ; ' $delete"
  - letters: "$shift z x c v b n m , . / $shift"
  - bottom:  "$symbols $space $enter"
```

There can at most be one number row, and one bottom row. If they are explicitly defined in the
layout file, then the number row must be at the top and bottom row must be at the bottom.
Between 1 and 8 letter rows are permitted.

### Template keys (automatic shift and backspace)
The keyboard parser automatically prepends `$shift` and appends `$delete` to the final letter row
when all of the following conditions are met:
1. No `bottom` row is explicitly defined
2. None of the letter rows explicitly set `$shift` or `$delete`

What this means is that to customize the location of shift and delete, or to disable the shift key
in your layout, you just need to specify `$shift` and/or `$delete` somewhere, and the automatic
insertion will be disabled.  If you specify an explicit bottom row, you will need to manually specify
them as well.

In this example, the backspace (`$delete`) is relocated to the second row instead of its usual place in the third row:
```yaml
name: "Alphabet"
rows:
  - letters: a b c d e f g h i j
  - letters: k l m n o p q r s $delete    # Backspace on this row instead of bottom row
  - letters: $shift t u v w x y z $shift  # Two shift keys
  - bottom:  $symbols $space $enter       # No comma or period
```


You can use the following template keys:

* `$shift` - shift key
* `$delete` - backspace key
* `$space` - spacebar
* `$enter` - enter/search/next/prev/go key
* `$symbols` - switch to symbols menu
* `$alphabet` - switch back to alphabet (only used in symbols layout)
* `$action` - action key, user-defined, may be empty
* `$number` - switch to numpad mode
* `$contextual` - contextual key, `/` for URLs and date fields, `@` for email fields, `:` for time fields
* `$zwnj` - Zero-width non-joiner key, and zero-width joiner key in morekeys.
* `$gap` - regular-width gap
* `$alt0` - switch to alt page 0, or back from alt page 0
* `$alt1` - switch to alt page 1, or back from alt page 1
* `$alt2` - switch to alt page 2, or back from alt page 2

---

### List<Key> and Key definition shortcuts

There are a few shortcuts to help make it easier to define layouts. These are documented in this section.

#### `Key` as a YAML string

An example of a full BaseKey definition:
```yaml
name: example
rows:
  - letters:
      - type: base
        spec: "a"
```

This shortcut allows you to define the exact same thing with just
```yaml
name: example
rows:
  - letters:
      - a
```

#### `Key` as a YAML list

An example of a full BaseKey definition with moreKeys:
```yaml
name: example
rows:
  - letters:
      - type: base
        spec: "a"
        moreKeys: "ayy,ahh"
```

This shortcut allows you to define the exact same thing with just
```yaml
name: example
rows:
  - letters:
      - [a, ayy, ahh]
```

The first element is treated as the spec, and all subsequent elements are turned into moreKeys.

#### `List<Key>` as a YAML string

An example of a List<Key> definition, using the string shortcut for defining `Key`:
```yaml
name: example
rows:
  - letters: [a, b, c]
```

This shortcut allows you to define the exact same thing with just
```yaml
name: example
rows:
  - letters: a b c
  # or - letters: "a b c", necessary for yaml parsing if you use some special symbols
```


#### Key specs
Key specs work the same way they do in AOSP keyboard. For example you can define a differing label from its code, without needing to use the long format.
```yaml
name: "Arabic example"
rows:
  - letters:
    - " \u0654◌|\u0654" # The label will appear as ٔ◌ but it will only type the Hamza mark, without the ◌ circle
```


### Automatic moreKeys

moreKeys refers to the extra keys that are accessible by long-pressing a key. The layout engine
automatically adds a bunch of moreKeys to keys by default - symbols, actions, numbers, and accented
letters are added based on the key's location and user settings.

You can customize the behavior of automatic moreKeys by setting `moreKeyMode` in attributes. For
example, to disable all automatic moreKeys, you can include this near the top of your layout
definition:

```yaml
attributes: {moreKeyMode: OnlyExplicit}
```

moreKey modes are documented in the [More Key Mode](#more-key-mode) section of API reference.

### Attribute system

Attributes can be defined in multiple places: the keyboard itself, a specific row, or an individual
key. Attributes which are unset get inherited in a particular order. See the
[Key Attributes](#key-attributes) section of the API reference for all attributes and defaults.

### Different key types

The `base` key is fit for most cases, but `case` is also useful when you need the key to change
based on shift state. See the [Keys](#keys) section of the API reference for all key types.

---




# API Reference
## Keyboard
### Overview

The `Keyboard` data class represents a keyboard layout definition, serving as the entry point for layout YAML files.

### Properties

#### `name`

* **Description**: The human-readable name of the layout. If the layout is for a specific language, this should be written in the relevant language.
* **Type**: `String`
* **Optional**: No

#### `rows`

* **Description**: The rows defined for the layout. Defining the number row, bottom row, or the functional keys (shift/backspace) is optional here. If they are missing, defaults will automatically be added to `effectiveRows`.
* **Type**: `List<`[`Row`](#row)`>`
* **Optional**: No

#### `languages`

* **Description**: (optional) List of languages this layout is intended for. It will be displayed as an option for the specified languages.
* **Type**: `List<String>`
* **Optional**: Yes
* **Default Value**: empty list

#### `description`

* **Description**: (optional) A human-readable description of the layout. Authorship/origin information may be added here. This is intended to be displayed to the user when they are selecting layouts.
* **Type**: `String`
* **Optional**: Yes
* **Default Value**: empty string

#### `layoutSetOverrides`

* **Description**: (optional) Override the symbols layout or other layouts for this layout set.
* **Type**: [`LayoutSetOverrides`](#layout-set-overrides)
* **Optional**: Yes
* **Default Value**: A default instance of `LayoutSetOverrides`

#### `numberRowMode`

* **Description**: (optional) Whether the number row should be user-configurable, always displayed, or never.
* **Type**: [`NumberRowMode`](#numberrowmode-1)
* **Optional**: Yes
* **Default Value**: `UserConfigurable`

#### `bottomRowHeightMode`

* **Description**: (optional) Whether the bottom row should always maintain a consistent height, or whether it should grow and shrink.
* **Type**: [`BottomRowHeightMode`](#bottomrowheightmode-1)
* **Optional**: Yes
* **Default Value**: `Fixed`

#### `bottomRowWidthMode`

* **Description**: (optional) Whether the bottom row should follow key widths of other rows, or should maintain separate widths for consistency.
* **Type**: [`BottomRowWidthMode`](#bottomrowwidthmode-1)
* **Optional**: Yes
* **Default Value**: `SeparateFunctional`

#### `attributes`

* **Description**: (optional) Default attributes to use for all rows
* **Type**: [`KeyAttributes`](#key-attributes)
* **Optional**: Yes
* **Default Value**: A default instance of `KeyAttributes`

#### `overrideWidths`

* **Description**: (optional) Definitions of custom key widths. Values are between 0.0 and 1.0, with 1.0 representing 100% of the keyboard width.
* **Type**: `Map<`[`KeyWidth`](#key-widths)`, Float>``
* **Optional**: Yes
* **Default Value**: empty map

#### `rowHeightMode`

* **Description**: (optional) Whether or not rows should fill the vertical space, or have vertical gaps added.
* **Type**: [`RowHeightMode`](#rowheightmode-1)
* **Optional**: Yes
* **Default Value**: `ClampHeight`

#### `useZWNJKey`

* **Description**: (optional) Whether or not the ZWNJ key should be shown in place of the contextual key.
* **Type**: `Boolean`
* **Optional**: Yes
* **Default Value**: `false`

#### `minimumFunctionalKeyWidth`

* **Description**: (optional) Minimum width for functional keys.
* **Type**: `Float`
* **Optional**: Yes
* **Default Value**: 0.125f

#### `minimumBottomRowFunctionalKeyWidth`

* **Description**: (optional) Minimum width for functional keys in the bottom row.
* **Type**: `Float`
* **Optional**: Yes
* **Default Value**: 0.15f

#### `altPages`

* **Description**: (optional) Alternative pages for this layout, use in conjunction with `$alt0`, `$alt1`, `$alt2`.
* **Type**: `List<List<`[`Row`](#row)`>>`
* **Optional**: Yes
* **Default Value**: empty list

## Row

### Overview

The `Row` data class represents a single row in a keyboard layout. It allows for customization of various aspects, including the type of keys, row height, splittability, and behavior with respect to the number row.

One of `numbers`, `letters` or `bottom` must be defined.

#### `numbers`

*   **Description**: Defines this as a number row.
*   **Type**: `List<Key>?`
*   **Optional**: One of `numbers`, `letters` or `bottom` must be defined.
*   **Default Value**: `null`
*   **Behavior**:
    *   If defined, will be treated as a number row by default.
    *   Number rows are given grow keys, no background, and smaller height by default.

#### `letters`

*   **Description**: Defines this as a letters row.
*   **Type**: `List<Key>?`
*   **Optional**: One of `numbers`, `letters` or `bottom` must be defined.
*   **Default Value**: `null`
*   **Behavior**:
    *   If defined, will be treated as a letters row by default.
    *   Letter rows are splittable by default.

#### `bottom`

*   **Description**: Defines this as a bottom row.
*   **Type**: `List<Key>?`
*   **Optional**: One of `numbers`, `letters` or `bottom` must be defined.
*   **Default Value**: `null`
*   **Behavior**:
    *   If defined, this is a bottom row. Bottom row should typically contain: `$symbols , $action $space $contextual . $enter`

#### `rowHeight`

*   **Description**: The height multiplier for this row.
*   **Type**: `Double`
*   **Optional**: Yes
*   **Default Value**: `1.0` for letters and bottom rows, `0.8` for number rows.
*   **Behavior**:
    *   Can be used to customize the row height with respect to other rows.

#### `splittable`

*   **Description**: Whether this row is splittable.
*   **Type**: `Boolean`
*   **Optional**: Yes
*   **Default Value**: `true` if defined as a letters row, `false` otherwise
*   **Behavior**:
    *   If true, allows the row to be split when the user prefers a split layout (e.g. landscape)

#### `numRowMode`

*   **Description**: How this row should behave with respect to the number row.
*   **Type**: [`RowNumberRowMode`](#rownumberrowmode)
*   **Optional**: Yes
*   **Default Value**: `Default`
*   **Behavior**:
    *   Can be customized to control when the row is displayed in relation to the number row.

#### `attributes`

*   **Description**: Default key attributes for this row.
*   **Type**: [`KeyAttributes`](#key-attributes)
*   **Optional**: Yes
*   **Default Value**: a default set of attributes
*   **Behavior**:
    *   Allows customization of key styles, widths, and other attributes.


## Layout Set Overrides
### Overview

The `LayoutSetOverrides` data class represents a set of overrides for specific layouts.
All layouts currently leave this default.

### Properties

#### `symbols`
*   **Description**: The layout used when the keyboard is in symbols mode.
*   **Type**: `String`
*   **Default Value**: `"symbols"`

#### `symbolsShifted`
*   **Description**: The layout used when the keyboard is in shifted symbols mode.
*   **Type**: `String`
*   **Default Value**: `"symbols_shift"`

#### `number`
*   **Description**: The layout used when the keyboard is in numpad mode.
*   **Type**: `String`
*   **Default Value**: `"number"`

#### `numberShifted`
*   **Description**: The layout used when the keyboard is in shifted numpad mode (this is currently unused).
*   **Type**: `String`
*   **Default Value**: `"number_shift"`

#### `phone`
*   **Description**: The layout used when the keyboard is in phone mode.
*   **Type**: `String`
*   **Default Value**: `"phone"`

#### `phoneShifted`
*   **Description**: The layout used when the keyboard is in shifted phone mode.
*   **Type**: `String`
*   **Default Value**: `"phone_shift"`

## Label Flags

### Overview

The `LabelFlags` data class represents flags for customizing the behavior of key labels on a key.

### Properties

#### `alignHintLabelToBottom`

*   **Description**: Aligns the hint label to the bottom of the key.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `alignIconToBottom`

*   **Description**: Aligns icon to the bottom of the key.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `alignLabelOffCenter`

*   **Description**: Aligns label off-center in the key.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `hasHintLabel`

*   **Description**: Indicates whether the key has a hint label.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `followKeyLabelRatio`

*   **Description**: Follows the key label ratio for text size.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `followKeyLetterRatio`

*   **Description**: Follows the key letter ratio for text size.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `followKeyLargeLetterRatio`

*   **Description**: Follows the key large letter ratio for text size.
*   **Type**: `Boolean`
*   **Default Value**: `false`

#### `autoXScale`

*   **Description**: Enables automatic x-axis scaling to fit label within key.
*   **Type**: `Boolean`
*   **Default Value**: `false`


## Key Attributes
### Overview

The `KeyAttributes` data class represents various attributes for keys in a keyboard layout. These attributes control how keys are displayed and behave.

### Properties

#### `width`

*   **Description**: The width token for the key.
*   **Type**: [`KeyWidth?`](#key-widths)
*   **DefaultKeyAttributes Value**: `Regular`
*   **Behavior**:
    *   Specifies the width of the key using a width token.

#### `style`

*   **Description**: The visual style (background) for the key.
*   **Type**: [`KeyVisualStyle?`](#key-visual-style)
*   **DefaultKeyAttributes Value**: `Normal`
*   **Behavior**:
    *   Defines the background appearance of the key.

#### `anchored`

*   **Description**: Whether or not to anchor the key to the edges.
*   **Type**: `Boolean?`
*   **DefaultKeyAttributes Value**: `false`
*   **Behavior**:
    *   If true, anchors the key to the edges, adding padding as needed.

#### `showPopup`

*   **Description**: Whether or not to show the popup indicator when the key is pressed.
*   **Type**: `Boolean?`
*   **DefaultKeyAttributes Value**: `true`
*   **Behavior**:
    *   If true, displays a popup indicator for letters, but this may be undesirable for functional or large keys.

#### `moreKeyMode`

*   **Description**: Which moreKeys to add automatically.
*   **Type**: [`MoreKeyMode?`](#more-key-mode)
*   **getEffectiveAttributes Default Value**: `All` if the row is a letter or bottom row, and the effective width is Regular, else `OnlyFromLetter`
*   **Behavior**:
    *   Specifies the mode for adding more keys automatically.

#### `useKeySpecShortcut`

*   **Description**: Whether or not to use keyspec shortcuts.
*   **Type**: `Boolean?`
*   **DefaultKeyAttributes Value**: `true`
*   **Behavior**:
    *   If true, enables automatic conversion of certain characters using keyspec shortcuts.

#### `longPressEnabled`

*   **Description**: Whether or not longpress is enabled for the key.
*   **Type**: `Boolean?`
*   **DefaultKeyAttributes Value**: `false`
*   **Behavior**:
    *   If true, enables long press functionality for the key.

#### `labelFlags`

*   **Description**: Label flags for how the key's label (and its hint) should be presented.
*   **Type**: [`LabelFlags?`](#label-flags)
*   **DefaultKeyAttributes Value**: `{ autoXScale: true }`
*   **Behavior**:
    *   Specifies how to display the key's label and hint.

#### `repeatableEnabled`

*   **Description**: Whether or not the key is repeatable, intended for backspace or arrow keys.
*   **Type**: `Boolean?`
*   **DefaultKeyAttributes Value**: `false`
*   **Behavior**:
    *   If true, enables repeat functionality for the key.

#### `shiftable`

*   **Description**: Whether or not the key is automatically shiftable.
*   **Type**: `Boolean?`
*   **DefaultKeyAttributes Value**: `true`
*   **Behavior**:
    *   If true, the code and label automatically becomes uppercased when the layout is shifted. If this is not desired, this can be set to false. Shift behavior can be customized by using a CaseSelector.

### Inheritance

The attributes are inherited in the following order:

1.  `Key.attributes`
2.  `Row.attributes`
3.  `Keyboard.attributes`
4.  `DefaultKeyAttributes`



# Keys

## Base
### Overview
The `BaseKey` data class is the main one used to define keys in a keyboard layout. It contains various attributes and settings for customization.

Explicit definition in yaml is done with `type: base`.

You can automatically define a BaseKey with default settings by simply specifying the spec in place of the BaseKey object:
```yaml
# Instead of
  - letters:
     - type: base
       spec: "a"

# You can do the same thing like this
  - letters:
      - "a"

# But customizing attributes requires the long-form definition
  - letters:
      - type: base
        spec: "a"
        attributes: { style: NoBackground }
```

### Properties

#### `spec`
*   **Description**: [AOSP key spec](#key-spec).
*   **Type**: `String`
*   **Behavior**:
    *   Specifies the key specification, which can contain custom label, code, icon, or output text.

#### `attributes`
*   **Description**: Attributes for this key.
*   **Type**: [`KeyAttributes`](#key-attributes)
*   **Default Value**: blank
*   **Behavior**:
    *   Specifies various attributes for the key, such as width, style, and more.
    *   Values defined here supersede any other values inherited from row, keyboard, or default attributes.

#### `moreKeys`
*   **Description**: More keys for this key.
*   **Type**: `List<String>`
*   **Default Value**: empty list
*   **Behavior**:
    *   Specifies more keys that should be added.
    *   Can be defined as a list or comma-separated string in YAML (e.g. `a,b,c`)

#### `hint`
*   **Description**: Hint for the key.
*   **Type**: `String?`
*   **Default Value**: `null`
*   **Behavior**:
    *   If set, overrides a default hint from the value of moreKeys.
    *   Specifies a custom hint for the key.

### Key Spec

Each key specification is one of the following:
- Label optionally followed by keyOutputText (keyLabel|keyOutputText).
- Label optionally followed by code point (keyLabel|!code/code_name).
- Icon followed by keyOutputText (!icon/icon_name|keyOutputText).
- Icon followed by code point (!icon/icon_name|!code/code_name).

Label and keyOutputText are one of the following:
- Literal string.
- Label reference represented by (!text/label_name), see {@link KeyboardTextsSet}.
- String resource reference represented by (!text/resource_name), see {@link KeyboardTextsSet}.

Icon is represented by (!icon/icon_name), see {@link KeyboardIconsSet}.

Code is one of the following:
- Code point presented by hexadecimal string prefixed with "0x"
- Code reference represented by (!code/code_name), see {@link KeyboardCodesSet}.

Special character, comma ',' backslash '\', and bar '|' can be escaped by '\' character.
Note that the '\' is also parsed by XML parser and {@link MoreKeySpec#splitKeySpecs(String)}
as well.


## Case Selector
### Overview
The `CaseSelector` data class represents a case selector key in a keyboard layout. It allows specifying different types of keys depending on when the layout is shifted or not.

Defined in yaml using `type: case`

Note: You can specify a different type of Key in different cases. For example, normal can be a base key, and shifted can be a gap.

### Properties

#### `normal`
*   **Description**: Key to use normally. Required.
*   **Type**: `Key`
*   **Behavior**:
    *   Specifies the key to use when the layout is not shifted.

#### `shifted`
*   **Description**: Key to use when shifted.
*   **Type**: `Key`
*   **Default Value**: same as `normal`
*   **Behavior**:
    *   Specifies the key to use when the layout is shifted.

#### `shiftedManually`
*   **Description**: Key to use when shifted, excluding automatic shift.
*   **Type**: `Key`
*   **Default Value**: same as `shifted`
*   **Behavior**:
    *   Specifies the key to use when the layout is shifted manually.

#### `shiftLocked`
*   **Description**: Key to use when shift locked (caps lock).
*   **Type**: `Key`
*   **Default Value**: same as `shiftedManually`
*   **Behavior**:
    *   Specifies the key to use when the shift lock is enabled.

#### `symbols`
*   **Description**: Key to use when in symbols layout.
*   **Type**: `Key`
*   **Default Value**: same as `normal`
*   **Behavior**:
    *   Specifies the key to use when the layout is in symbols mode.

#### `symbolsShifted`
*   **Description**: Key to use when in symbols layout and shifted.
*   **Type**: `Key`
*   **Default Value**: same as `normal`
*   **Behavior**:
    *   Specifies the key to use when the layout is in symbols mode and shifted.


## Gap
### Overview
The `GapKey` data class represents a gap in the keyboard layout.
Instead of a key, a gap will be placed in its place.

Defined in yaml using `type: gap`, or just use the `$gap` shortcut.

### Properties

#### `attributes`
*   **Description**: Attributes for this key. This is mainly useful for setting the width.
*   **Type**: `KeyAttributes`
*   **Default Value**: blank


## Enter
### Overview
The `EnterKey` data class represents an enter key. Its icon and moreKeys will depend on the input field.

Defined in yaml using `type: enter`

This is not intended to be used in layouts, instead please use the `$enter` shortcut when possible.

### Properties

#### `attributes`
*   **Description**: Attributes for this key.
*   **Type**: `KeyAttributes`
*   **Default Value**: blank


## Action
### Overview
The `ActionKey` data class represents the user-configurable action key. If an action key is not set,
then the key will be skipped.

Defined in yaml using `type: action`

This is not intended to be used in layouts, instead please use the `$action` shortcut when possible.

### Properties

#### `attributes`
*   **Description**: Attributes for this key.
*   **Type**: `KeyAttributes`
*   **Default Value**: blank


## Contextual
### Overview
The `ContextualKey` data class represents a contextual key. It's shown only in specific keyboard layouts as defined here:
```kotlin
        KeyboardId.MODE_EMAIL    to BaseKey(spec = "@", attributes = attributes),
        KeyboardId.MODE_URL      to BaseKey(spec = "/", attributes = attributes),
        KeyboardId.MODE_DATETIME to BaseKey(spec = "/", moreKeys = listOf(":"), attributes = attributes),
        KeyboardId.MODE_DATE     to BaseKey(spec = "/", attributes = attributes),
        KeyboardId.MODE_TIME     to BaseKey(spec = ":", attributes = attributes),
```
Defined in yaml using `type: contextual`

This is not intended to be used in layouts, instead please use the `$contextual` shortcut when possible.

### Properties

#### `attributes`
*   **Description**: Attributes for this key.
*   **Type**: `KeyAttributes`
*   **Default Value**: blank


--- --- ---

# Enums

## Key Widths

The `KeyWidth` enum represents various width tokens for keys in a keyboard layout. It simplifies the process of specifying key widths by using tokens instead of explicit percentages.

#### `Regular`
*   **Description**: Regular key width, used for normal letters.
*   **Behavior**:
    *   Calculated based on the total keyboard width and maximum number of keys in a row.
    *   Consistent across the entire keyboard except in some cases (bottom row, split layout, or rows with functional keys).

#### `FunctionalKey`
*   **Description**: Functional key width, used for functional keys like Shift, Backspace, Enter, and Symbols.
*   **Behavior**:
    *   Has a minimum width specified by the [`minimumFunctionalKeyWidth`](#minimumfunctionalkeywidth) property.
    *   May be larger than the minimum if there is available space.

#### `Grow`
*   **Description**: Grow width, used for keys that take up remaining space divided evenly among all grow keys in the row.
*   **Behavior**:
    *   Currently not supported in splittable rows.
    *   Can complicate width calculation for functional keys and others.
    *   Mainly used for spacebar.

#### `Custom1`, `Custom2`, `Custom3`, `Custom4`
*   **Description**: Custom width tokens, defined in the [`overrideWidths`](#overridewidths) property of the keyboard configuration.
*   **Behavior**:
    *   Values are between 0.0 and 1.0.
    *   Used to specify custom widths for specific keys or layouts.

## Key Visual Style
The `KeyVisualStyle` enum represents the visual style of a key in a keyboard layout. It affects the background of the key based on user theme settings.

#### `Normal`
*   **Description**: Uses a normal key background, intended for all letters.
*   **Behavior**: This may be a rounded rectangle or no background depending on user settings.

#### `NoBackground`
*   **Description**: Uses no key background, intended for number row numbers.
*   **Behavior**: No background will be shown for this key.

#### `Functional`
*   **Description**: Uses a slightly darker colored background, intended for functional keys (backspace, etc).
*   **Behavior**: A more visually distinct background is used, it may be dimmer or more saturated.

#### `StickyOff`
*   **Description**: Intended for Shift when it's not shiftlocked.
*   **Behavior**: Specifies a background for Shift when it's not shiftlocked.

#### `StickyOn`
*   **Description**: Intended for Shift to indicate it's shiftlocked. Uses a more bright background.
*   **Behavior**: Specifies a brighter background for Shift when it's shiftlocked.

#### `Action`
*   **Description**: Uses a bright fully rounded background, normally used for the enter key.
*   **Behavior**: Specifies a bright, fully rounded background for the enter key.

#### `Spacebar`
*   **Description**: Depending on the key borders setting, this is either the same as Normal (key borders enabled) or a fully rounded rectangle (key borders disabled).
*   **Behavior**: Specifies a background for the spacebar key, which may be the same as Normal or a fully rounded rectangle depending on key borders settings.

#### `MoreKey`
*   **Description**: Visual style for moreKeys. Not intended for use in layout definitions.
*   **Behavior**: Specifies a background for more keys.


## More Key Mode
The `MoreKeyMode` enum specifies which morekeys can be automatically added to the key.

#### `All`
*   **Description**: Insert all possible automatic moreKeys
*   **Behavior**:
    *   Inserts moreKeys from keyspec shortcut.
    *   Inserts moreKeys for numbers, symbols, actions, and language letters based on user settings and based on the key's coordinate.
    *   Counts towards keyCoordinate.

#### `OnlyFromLetter`
*   **Description**: Only automatically insert morekeys from keyspec shortcut or language-related accents
*   **Behavior**:
    *   Inserts moreKeys from keyspec shortcut, and for language letters based on user settings.
    *   Does not count towards keyCoordinate.

#### `OnlyExplicit`
*   **Description**: Do not automatically insert any morekeys.
*   **Behavior**:
    *   Does not count towards keyCoordinate
    *   Only moreKeys explicitly defined for the key are included.

## `BottomRowHeightMode`
Represents the height mode of the bottom row in a layout.

#### `Fixed`
*   **Description**: The bottom row height is fixed.

#### `Flexible`
*   **Description**: The bottom row height can vary based on row count.


## `BottomRowWidthMode`

Represents the key width mode of keys in the bottom row.

#### `SeparateFunctional`
*   **Description**: The functional keys (symbols, enter) are at least [`minimumBottomFunctionalKeyWidth`](#minimumbottomrowfunctionalkeywidth) wide.

#### `Identical`
*   **Description**: The functional keys (symbols, enter) follow the functional width from other rows.


## `RowHeightMode`

Represents the height mode of rows in this layout.

#### `ClampHeight`
*   **Description**: The row height is clamped to a reasonable height, row spacing is added to fill the space.

#### `FillHeight`
*   **Description**: The row height fills available height.


## `NumberRowMode`

Represents the behavior of the number row in a layout.

#### `UserConfigurable`
*   **Description**: The number row can be enabled or disabled by the user in typing settings.

#### `AlwaysEnabled`
*   **Description**: The number row is always enabled and visible.

#### `AlwaysDisabled`
*   **Description**: The number row is never enabled or visible.


## `RowNumberRowMode`

Represents the behavior of a row's visibility with respect to the number row in a layout.

#### `Default`
*   **Description**: The row is unaffected by the state of the number row

#### `Filler`
*   **Description**: This row only displays when the number row is active.

#### `Hideable`
*   **Description**: This row only displays when the number row is inactive.
