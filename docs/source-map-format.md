# TSJ Source Map Format (v0.1)

TSJ emits a line-oriented source map for each generated JVM entry class.

## File Location

- Compile output path:
  - `<out>/classes/dev/tsj/generated/<ProgramClass>.tsj.map`
- Artifact metadata key:
  - `sourceMapFile` in `program.tsj.properties`

## File Format

- UTF-8 text file.
- First line is a header:
  - `TSJ-SOURCE-MAP\t1`
- Each subsequent line maps one generated Java source line to one TypeScript source location:
  - `<javaLine>\t<sourcePath>\t<sourceLine>\t<sourceColumn>`

Example:

```text
TSJ-SOURCE-MAP	1
48	/mnt/d/coding/tsj/app/main.ts	12	3
49	/mnt/d/coding/tsj/app/main.ts	13	3
```

## Escaping

`sourcePath` uses escaped sequences:

- `\\` for backslash
- `\t` for tab
- `\n` for newline
- `\r` for carriage return

## Runtime Usage

- `tsj run --ts-stacktrace` reads this map and rewrites generated-class frames into TypeScript coordinates.
- Mapping is best-effort and currently line-based.
