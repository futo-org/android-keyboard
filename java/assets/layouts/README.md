# Keyboard Layout definitions

The app will search for `*.yml` and `*.yaml` files here recursively. The directories are currently not namespaced, so please ensure there are no two files with the same name.

This document specifies the format for defining keyboard layouts.

A lot of concepts are explained through examples in this document, please read through them to understand ways of defining things.

**NOTE: This spec is not yet finalized and some things are subject to change. Some differences currently exist between this document and the actual API.**

## Practical use

Built-in layouts should be added to `java/assets/layouts`

The YAML file describes the `Keyboard` class, it only requires a name and rows.

### General file layout and simple examples

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
        moreKeys: "ę,ė"

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

These shortcuts for defining things are explained more in the shortcuts section. In practice you should not randomly mix different ways of defining things, you should stick to something that keeps the file easy to understand.

With RTL languages it's advised to use unicode escapes in order to keep the file easily editable. It's good practice to include a comment explaining each unicode character:
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

A default number row and bottom row will be added if they are not explicitly defined. You can override them with custom ones for more advanced layouts:
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

There can at most be one number row, and one bottom row. If they are explicitly defined in the layout file, then the number row must be at the top and bottom row must be at the bottom. Between 1 and 8 letter rows are permitted.

### Template keys (automatic shift and backspace)
The keyboard parser automatically inserts a `$shift` at the start of the final letters row, and a `$delete` at the end, if the following two conditions are met:

1. `bottom` row is not explicitly defined
2. neither `$shift` nor `$delete` are explicitly set in the final letters row

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


(Experimental, may not be final) To make a double-wide functional key you can put two of them consecutively:
```yaml
name: "Weird QWERTY"
rows:
  - letters: "$delete $delete q w e r t y u i"   # 2x wide delete key at the top left
  - letters: "a s d f g h j k l o"
  - letters: "$shift z x c v b n m p $shift"
  - bottom:  "$symbols , $space . $enter $enter" # 2x wide enter key
```

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
  - letters: a b c   # a.k.a "a b c"
```


#### Key specs
Key specs work the same way they do in AOSP keyboard. For example you can define a differing label from its code, without needing to use the long format.
```yaml
name: "Arabic example"
rows:
  - letters:
    - " \u0654◌|\u0654" # The label will appear as ٔ◌ but it will only type the Hamza mark, without the ◌ circle
```

---

## Type reference

### Keyboard
#### name: `String`
Describes the name of the keyboard layout. If this is a layout for a specific language, this should be written localized to that language. For example, "Lietuvių QWERTY klaviatūra" instead of "Lithuanian QWERTY Keyboard".

#### rows: `List<Row>`
List of rows to be included in the layout.

#### symbolsLayout: `String` (optional)
Which symbols layout to use. Normally this should be left to the default "symbols" layout.

#### symbolsShiftLayout: `string` (optional)
Which shifted symbols layout to use. Normally this should be left to the default "symbols_shift" layout.

#### numberRowMode: `NumberRowMode` (optional)
This can be set to one of the following:

* `UserConfigurable` - settings number row toggle will toggle this layout's number row
* `AlwaysEnabled` - this layout's number row is always enabled
* `AlwaysDisabled` - this layout's number row is always disabled

#### script: `Script` (optional) = Latin
This can be set to one of the following:

* `Arabic`
* `Armenian`
* `Bengali`
* `Cyrillic`
* `Devanagari`
* `Georgian`
* `Greek`
* `Hebrew`
* `Kannada`
* `Khmer`
* `Lao`
* `Latin`
* `Malayalam`
* `Myanmar`
* `Sinhala`
* `Tamil`
* `Telugu`
* `Thai`

---

### Row
#### (numbers|letters|bottom): `List<Key>`
When defining a row, at most one of `numbers`, `letters`, or `bottom` can be set. This is mainly done as a yaml shorthand for defining the row type. The row type also has some effects on the keys. Number rows hide key backgrounds by default, and have a shorter height. They are also unsplittable, like action rows.

For most cases, you only need to define `letters` rows. The default numbers and bottom rows are added automatically.

An example of defining a QWERTY layout:
```yaml
name: "QWERTY"
rows:
  - letters: q w e r t y u i o p
  - letters: a s d f g h j k l
  - letters: z x c v b n m
```

If your layout is more complex, you can override all rows:
```yaml
name: "DVORAK"
rows:
  - letters: "$delete $delete ' p y f g c r l"
  - letters: "a o e u i d h t n s"
  - letters: "$shift q j k x b m w v z"
  - bottom: "$symbols , $space . $enter $enter"
```

#### rowHeight: `Double` (optional) = `1.0`

#### splittable: `Boolean` (optional) = `true`

#### fillerRowForNumberRow: `Boolean` (optional) = `false`
When set, the row will only appear if the number row is active in the primary layout. Mainly used for symbol layouts.

---

### KeyAttributes
#### width: `KeyWidth` (optional)
Can be set to one of the following:

* `Regular` - default regular key width
* `FunctionalKey` - width used for enter, backspace, etc
* `Grow` - fills all empty space in the row, used for spacebar and number row
* `Custom1` - if used, override width for `Custom1` must be defined in the keyboard
* `Custom2` - if used, override width for `Custom2` must be defined in the keyboard
* `Custom3` - if used, override width for `Custom3` must be defined in the keyboard

#### style: `KeyVisualStyle` (optional)
Defines mainly the background for the key. Valid values:

* `Normal` - default visual style
* `NoBackground` - no background, used for number row
* `Functional` - darker key, used for shift and backspace
* `Action` - rounded bright key, used for enter
* `Spacebar` - used for spacebar
* `StickyOff`
* `StickyOn`

#### showPopup: `Boolean` (optional)
Whether or not to show a popup to indicate the key was tapped

#### moreKeyMode: `MoreKeyMode` (optional)
Declares how more keys should be inserted automatically. Valid values:

* `All` - add all automatic morekeys
* `OnlyFromKeyspec` - add only for matching keyspec shortcut
* ``

### Key (`type: base`)
#### spec: `String`
This is the key spec. Usually it should just be a letter.

Text references are permitted, such as `!text/keyspec_q`

You can set a custom icon and code here as well: `!icon/action_paste|!code/action_paste`

#### code: `Int` (optional)
Override the code for this key

#### icon: `String` (optional)
Custom icon for the key. For example, can be `action_settings`.

#### shiftedCode: `Int`
#### anchored: `Boolean`
#### showPopup: `Boolean`
#### moreKeys: `String`
#### longPressEnabled: `Boolean`
#### repeatableEnabled: `Boolean`

### Key (`type: case`)
#### normal: `Key`
#### shifted: `Key` = normal
#### automaticShifted: `Key` = shifted
#### manualShifted: `Key` = shifted
#### shiftLocked: `Key` = shifted
#### shiftLockShifted: `Key` = shiftLocked
#### symbols: `Key` = normal
#### symbolsShifted: `Key` = normal
