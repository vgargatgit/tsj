import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import ts from "typescript";

import { assertCondition, fail } from "./expect.ts";
import {
  normalizeLineEndings,
  normalizeSnapshotPath,
  readSnapshot,
  stableStringify,
  writeSnapshot,
} from "./snapshot.ts";

export interface Span {
  file: string;
  startLine: number;
  startCol: number;
  endLine: number;
  endCol: number;
}

export interface ParseDiagnostic {
  code: string;
  message: string;
  span: Span;
}

export interface AstNode {
  kind: string;
  span: Span;
  operator?: string;
  tokenText?: string;
  children: AstNode[];
}

export interface ParseResult {
  success: boolean;
  diagnostics: ParseDiagnostic[];
  ast: AstNode | null;
}

type SuiteResult = {
  exitCode: number;
  checkedFiles: number;
  updatedSnapshots: number;
  mismatches: string[];
  unexpectedStatuses: string[];
};

function syntaxKindName(kind: ts.SyntaxKind): string {
  return ts.SyntaxKind[kind] ?? `UnknownKind${kind}`;
}

function scriptKindForPath(filePath: string): ts.ScriptKind {
  if (filePath.endsWith(".tsx")) {
    return ts.ScriptKind.TSX;
  }
  if (filePath.endsWith(".jsx")) {
    return ts.ScriptKind.JSX;
  }
  if (filePath.endsWith(".mts")) {
    return ts.ScriptKind.MTS;
  }
  if (filePath.endsWith(".cts")) {
    return ts.ScriptKind.CTS;
  }
  return ts.ScriptKind.TS;
}

function positionToLineCol(sourceFile: ts.SourceFile, pos: number): { line: number; col: number } {
  const bounded = Math.min(Math.max(pos, 0), sourceFile.end);
  const point = sourceFile.getLineAndCharacterOfPosition(bounded);
  return { line: point.line + 1, col: point.character + 1 };
}

function spanFromOffsets(sourceFile: ts.SourceFile, file: string, start: number, end: number): Span {
  const startPoint = positionToLineCol(sourceFile, start);
  const endPoint = positionToLineCol(sourceFile, Math.max(start, end));
  return {
    file,
    startLine: startPoint.line,
    startCol: startPoint.col,
    endLine: endPoint.line,
    endCol: endPoint.col,
  };
}

function nodeSpan(sourceFile: ts.SourceFile, file: string, node: ts.Node): Span {
  const start = node.getStart(sourceFile, false);
  const end = node.getEnd();
  return spanFromOffsets(sourceFile, file, start, end);
}

function extractOperator(node: ts.Node): string | undefined {
  if (ts.isBinaryExpression(node)) {
    return ts.tokenToString(node.operatorToken.kind) ?? syntaxKindName(node.operatorToken.kind);
  }
  if (ts.isPrefixUnaryExpression(node)) {
    return ts.tokenToString(node.operator) ?? syntaxKindName(node.operator);
  }
  if (ts.isPostfixUnaryExpression(node)) {
    return ts.tokenToString(node.operator) ?? syntaxKindName(node.operator);
  }
  if (ts.isTypeOperatorNode(node)) {
    return ts.tokenToString(node.operator) ?? syntaxKindName(node.operator);
  }
  return undefined;
}

function extractTokenText(sourceFile: ts.SourceFile, node: ts.Node): string | undefined {
  if (ts.isIdentifier(node) || ts.isPrivateIdentifier(node)) {
    return node.text;
  }
  if (
    ts.isStringLiteralLike(node)
    || ts.isNumericLiteral(node)
    || ts.isBigIntLiteral(node)
    || ts.isRegularExpressionLiteral(node)
  ) {
    return node.getText(sourceFile);
  }
  if (node.kind >= ts.SyntaxKind.FirstToken && node.kind <= ts.SyntaxKind.LastToken) {
    return ts.tokenToString(node.kind) ?? node.getText(sourceFile);
  }
  return undefined;
}

function serializeNode(sourceFile: ts.SourceFile, file: string, node: ts.Node): AstNode {
  const children: AstNode[] = [];
  node.forEachChild((child) => {
    children.push(serializeNode(sourceFile, file, child));
  });

  const snapshot: AstNode = {
    kind: syntaxKindName(node.kind),
    span: nodeSpan(sourceFile, file, node),
    children,
  };

  const operator = extractOperator(node);
  if (operator !== undefined) {
    snapshot.operator = operator;
  }

  const tokenText = extractTokenText(sourceFile, node);
  if (tokenText !== undefined) {
    snapshot.tokenText = tokenText;
  }

  return snapshot;
}

function compareDiagnostics(left: ParseDiagnostic, right: ParseDiagnostic): number {
  if (left.span.file !== right.span.file) {
    return left.span.file.localeCompare(right.span.file);
  }
  if (left.span.startLine !== right.span.startLine) {
    return left.span.startLine - right.span.startLine;
  }
  if (left.span.startCol !== right.span.startCol) {
    return left.span.startCol - right.span.startCol;
  }
  if (left.span.endLine !== right.span.endLine) {
    return left.span.endLine - right.span.endLine;
  }
  if (left.span.endCol !== right.span.endCol) {
    return left.span.endCol - right.span.endCol;
  }
  return left.code.localeCompare(right.code);
}

export function parseSourceText(filePath: string, sourceText: string): ParseResult {
  const normalizedFile = normalizeSnapshotPath(filePath);
  const normalizedSource = normalizeLineEndings(sourceText);
  const sourceFile = ts.createSourceFile(
    normalizedFile,
    normalizedSource,
    ts.ScriptTarget.Latest,
    true,
    scriptKindForPath(normalizedFile)
  );

  const diagnostics: ParseDiagnostic[] = sourceFile.parseDiagnostics
    .map((diagnostic) => {
      const start = diagnostic.start ?? 0;
      const length = diagnostic.length ?? 0;
      const span = spanFromOffsets(sourceFile, normalizedFile, start, start + Math.max(length, 0));
      return {
        code: `TS${diagnostic.code}`,
        message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
        span,
      };
    })
    .sort(compareDiagnostics);

  if (diagnostics.length > 0) {
    return {
      success: false,
      diagnostics,
      ast: null,
    };
  }

  return {
    success: true,
    diagnostics,
    ast: serializeNode(sourceFile, normalizedFile, sourceFile),
  };
}

export function parseFile(filePath: string, repoRoot: string): ParseResult {
  const absolute = path.resolve(filePath);
  const sourceText = fs.readFileSync(absolute, "utf8");
  const relativeFile = normalizeSnapshotPath(path.relative(repoRoot, absolute));
  return parseSourceText(relativeFile, sourceText);
}

export function serializeAst(ast: AstNode | null): string {
  return `${stableStringify(ast)}\n`;
}

function serializeDiagnostics(diagnostics: ParseDiagnostic[]): string {
  return `${stableStringify({ diagnostics })}\n`;
}

function snapshotStem(filePath: string): string {
  const base = path.basename(filePath);
  if (base.endsWith(".d.ts")) {
    return base.slice(0, -5);
  }
  return base.replace(/\.(tsx|ts)$/u, "");
}

function listTypeScriptFiles(directory: string): string[] {
  if (!fs.existsSync(directory)) {
    return [];
  }
  const entries = fs.readdirSync(directory, { withFileTypes: true })
    .sort((left, right) => left.name.localeCompare(right.name));
  const files: string[] = [];
  for (const entry of entries) {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...listTypeScriptFiles(absolute));
      continue;
    }
    if (entry.isFile() && (entry.name.endsWith(".ts") || entry.name.endsWith(".tsx"))) {
      files.push(absolute);
    }
  }
  return files;
}

function verifySnapshot(
  snapshotPath: string,
  actual: string,
  update: boolean,
  mismatches: string[],
  updatedCountRef: { count: number }
): void {
  const expected = readSnapshot(snapshotPath);
  if (expected === null || normalizeLineEndings(expected) !== normalizeLineEndings(actual)) {
    if (update) {
      writeSnapshot(snapshotPath, actual);
      updatedCountRef.count += 1;
      return;
    }
    mismatches.push(snapshotPath);
  }
}

function requireFlagArgs(args: string[]): { update: boolean } {
  const allowed = new Set(["--update"]);
  for (const arg of args) {
    if (!allowed.has(arg)) {
      fail(`Unknown argument: ${arg}. Supported args: --update`);
    }
  }
  return { update: args.includes("--update") };
}

export function runParseHarness(rootDir: string, args: string[]): SuiteResult {
  const { update } = requireFlagArgs(args);

  const okDir = path.join(rootDir, "src", "ok");
  const errDir = path.join(rootDir, "src", "err");
  const expectedOkDir = path.join(rootDir, "fixtures", "expected", "ok");
  const expectedErrDir = path.join(rootDir, "fixtures", "expected", "err");

  const okFiles = listTypeScriptFiles(okDir);
  const errFiles = listTypeScriptFiles(errDir);
  assertCondition(okFiles.length > 0, "No files found in src/ok.");
  assertCondition(errFiles.length > 0, "No files found in src/err.");

  const mismatches: string[] = [];
  const unexpectedStatuses: string[] = [];
  const updated = { count: 0 };

  for (const filePath of okFiles) {
    const relative = normalizeSnapshotPath(path.relative(rootDir, filePath));
    const result = parseFile(filePath, rootDir);
    if (!result.success) {
      unexpectedStatuses.push(`${relative}: expected parse success but found diagnostics.`);
      continue;
    }
    const expectedPath = path.join(expectedOkDir, `${snapshotStem(relative)}.ast.json`);
    verifySnapshot(expectedPath, serializeAst(result.ast), update, mismatches, updated);
  }

  for (const filePath of errFiles) {
    const relative = normalizeSnapshotPath(path.relative(rootDir, filePath));
    const result = parseFile(filePath, rootDir);
    if (result.success) {
      unexpectedStatuses.push(`${relative}: expected parse failure but parsing succeeded.`);
      continue;
    }
    const expectedPath = path.join(expectedErrDir, `${snapshotStem(relative)}.diag.json`);
    verifySnapshot(expectedPath, serializeDiagnostics(result.diagnostics), update, mismatches, updated);
  }

  let exitCode = 0;
  if (unexpectedStatuses.length > 0) {
    exitCode = 2;
  } else if (mismatches.length > 0) {
    exitCode = 1;
  }

  return {
    exitCode,
    checkedFiles: okFiles.length + errFiles.length,
    updatedSnapshots: updated.count,
    mismatches,
    unexpectedStatuses,
  };
}

function logSuiteResult(result: SuiteResult): void {
  if (result.unexpectedStatuses.length > 0) {
    console.error("Unexpected parse statuses:");
    for (const item of result.unexpectedStatuses) {
      console.error(`- ${item}`);
    }
  }
  if (result.mismatches.length > 0) {
    console.error("Snapshot mismatches:");
    for (const item of result.mismatches) {
      console.error(`- ${normalizeSnapshotPath(item)}`);
    }
  }

  console.log(
    stableStringify({
      checkedFiles: result.checkedFiles,
      updatedSnapshots: result.updatedSnapshots,
      mismatches: result.mismatches.length,
      unexpectedStatuses: result.unexpectedStatuses.length,
      exitCode: result.exitCode,
    })
  );
}

function detectRootDir(): string {
  const harnessFile = fileURLToPath(import.meta.url);
  const harnessDir = path.dirname(harnessFile);
  return path.resolve(harnessDir, "..", "..");
}

export function main(rawArgs: string[]): number {
  try {
    const rootDir = detectRootDir();
    const result = runParseHarness(rootDir, rawArgs);
    logSuiteResult(result);
    return result.exitCode;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`Harness internal error: ${message}`);
    return 3;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
