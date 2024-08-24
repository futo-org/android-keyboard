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

# API Reference

TODO