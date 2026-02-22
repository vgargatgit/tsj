import fs from "node:fs";
import path from "node:path";

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stableClone(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((entry) => stableClone(entry));
  }
  if (isPlainObject(value)) {
    const ordered: Record<string, unknown> = {};
    for (const key of Object.keys(value).sort((left, right) => left.localeCompare(right))) {
      ordered[key] = stableClone(value[key]);
    }
    return ordered;
  }
  return value;
}

export function normalizeLineEndings(input: string): string {
  return input.replace(/\r\n/g, "\n");
}

export function normalizeSnapshotPath(inputPath: string): string {
  return normalizeLineEndings(inputPath).replace(/\\/g, "/");
}

export function stableStringify(value: unknown): string {
  return JSON.stringify(stableClone(value), null, 2);
}

export function readSnapshot(snapshotPath: string): string | null {
  if (!fs.existsSync(snapshotPath)) {
    return null;
  }
  return normalizeLineEndings(fs.readFileSync(snapshotPath, "utf8"));
}

export function writeSnapshot(snapshotPath: string, content: string): void {
  const normalized = normalizeLineEndings(content);
  fs.mkdirSync(path.dirname(snapshotPath), { recursive: true });
  fs.writeFileSync(snapshotPath, normalized, "utf8");
}
