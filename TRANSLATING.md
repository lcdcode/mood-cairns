# Translating Mood Cairns

Thank you for helping! Mood Cairns is fully offline, so every translation ships
inside the app. All user-facing text lives in one file per language, and you only
need a text editor to translate it.

- English source: `app/src/main/res/values/strings.xml`
- Translations: `app/src/main/res/values-<language>/strings.xml`

A partial translation is fine. Any string you have not translated yet falls back
to English, one string at a time, so a language can ship and grow gradually.

## Translating strings

1. Copy `values/strings.xml` into your language's folder (see "Adding a new
   language" if it does not exist yet), or open the existing file.
2. Translate the text between the tags. Never change the `name="..."` part.
3. Leave out anything marked `translatable="false"` (the app name, for example).
4. Read the `<!-- comments -->` above a string: they explain context, such as
   where it appears or what a placeholder holds.

### Placeholders

`%1$s`, `%2$d` and similar are filled in by the app (a name, a number, a date).

- Keep every placeholder, with the same number and letter.
- You may move them around: `%2$s` can come before `%1$s` if your grammar needs
  that. The numbers say which value goes where, not the order.
- `%1$d` is a whole number and `%1$s` is text. Changing one into the other crashes
  the app, and the unit tests reject it.

### Plurals

Text with a count uses `<plurals>`. English has two forms, `one` and `other`, but
your language may need different ones: `zero`, `one`, `two`, `few`, `many`,
`other`. Provide every form your language uses (see the
[CLDR plural rules](https://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html)):

```xml
<plurals name="charts_summary_raw">
    <item quantity="one">%1$d wpis · surowe wartości dzienne</item>
    <item quantity="few">%1$d wpisy · surowe wartości dzienne</item>
    <item quantity="many">%1$d wpisów · surowe wartości dzienne</item>
    <item quantity="other">%1$d wpisu · surowe wartości dzienne</item>
</plurals>
```

A `one` form may drop the number if that reads more naturally ("a second"
instead of "1 second"), but it must not add a placeholder English does not have.

### Special characters

These follow Android's string resource rules:

| Write   | To get                                   |
|---------|------------------------------------------|
| `\'`    | an apostrophe: `Don\'t`                  |
| `\"`    | a double quote                           |
| `&amp;` | an ampersand                             |
| `\n`    | a line break (only where English has one) |

The About strings contain `&lt;b&gt;...&lt;/b&gt;` (bold) and
`&lt;a href=...&gt;...&lt;/a&gt;` (a link). Keep those tags as written and
translate only the text between them. Keep `common_list_separator` in its
surrounding quotes, which preserve its spaces.

Keep any `formatted="false"` attribute. It marks a string with a literal `%`
(like "100%") that is not a placeholder; without it, a translation such as
"100% es" is misread as a format code ("% e") and fails the lint check.

### Built-in names

The `seed_*` strings are the names of the built-in scales, tags, and reminder
windows. Keep them short: they appear on small chips and chart legends. A tag
like "Content" means the feeling (contented), not "contents". Check the comments.

### Dates, times, and numbers

You do not translate these. The app formats dates, times, decimals, and
percentages with your language's own conventions automatically.

## Checking your translation

1. Build a debug APK (see [README.md](README.md#building-for-yourself)).
2. In the app, open Settings → Language and pick your language. The app restarts
   its screen in that language without changing the rest of your phone.
3. Look for text that is cut off or still in English.
4. Run the unit tests, which check placeholders and the language lists:

   ```
   ./gradlew :app:testDebugUnitTest
   ```

## Adding a new language

Four files must agree. `SupportedLocalesTest` fails if one is missed.

1. **Strings.** Create `app/src/main/res/values-<qualifier>/strings.xml`. The
   qualifier is the language code, with a region or script written Android's way:

   | Language                  | Language tag | Folder                |
   |---------------------------|--------------|-----------------------|
   | German                    | `de`         | `values-de`           |
   | Brazilian Portuguese      | `pt-BR`      | `values-pt-rBR`       |
   | Chinese (Simplified)      | `zh-Hans`    | `values-b+zh+Hans`    |

2. **`app/src/main/res/xml/locale_config.xml`.** Add `<locale android:name="de" />`
   using the language tag. This list controls which languages are packaged into
   the app and offered in Android 13+'s per-app language settings.
3. **`app/src/debug/res/xml/locale_config.xml`.** Add the same line. Debug builds
   use this copy, which also lists the `en-XA` test pseudolocale.
4. **`app/src/main/java/com/lcdcode/moodcairns/settings/SupportedLocales.kt`.**
   Add the tag to `tags`. This is the in-app picker's list, in display order.

Then run the unit tests and open a pull request.

### Store listing (optional)

F-Droid shows a translated description when it finds one. Copy
`fastlane/metadata/android/en-US/` to a folder for your language and translate it.
Use just the language code (for example `es/`) when the translation suits every
region, or add a region (`pt-BR/`) when it does not:

- `title.txt`: at most 50 characters
- `short_description.txt`: at most 80 characters
- `full_description.txt`

Skip `images/` (the English screenshots are used) and `changelogs/`
(release notes are written in English).

## Notes for maintainers

- **Adding a string:** add it to `values/strings.xml` only. Other languages show
  the English text until translated.
- **Changing English wording:** if the meaning changes, give the string a new
  `name`, so outdated translations are not shown with the new meaning. Fixing a
  typo can keep the name.
- **Built-in names:** never edit the stored names in `data/db/Seed.kt`. Existing
  installs and backups recognize built-in rows by those exact English names. To
  change what users see, edit the `seed_*` string instead.
- **New screens:** use string resources from the start, and add the file to
  `EXTRACTED_FILES` in `HardcodedUiTextTest`, which fails the build on literal
  UI text in listed files.
- **Pseudolocale:** debug builds offer "English (XA)" in the language picker. It
  shows every resource string accented and padded, so any plain, unaccented text
  on screen was missed by extraction.
