'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

function loadTypeScript() {
  try {
    return require('typescript');
  } catch (error) {
    const globalCandidates = findGlobalTypeScriptCandidates();
    for (const candidate of globalCandidates) {
      if (fs.existsSync(candidate)) {
        try {
          return require(candidate);
        } catch (candidateError) {
          // Keep trying fallback candidates.
        }
      }
    }
    const message = [
      'Unable to resolve the `typescript` package.',
      'Install it in the workspace root (`npm i -D typescript`) or make `tsc` available globally.'
    ].join(' ');
    throw new Error(message);
  }
}

function findGlobalTypeScriptCandidates() {
  try {
    const tscPath = execFileSync('which', ['tsc'], { encoding: 'utf8' }).trim();
    if (!tscPath) {
      return [];
    }
    const realTscPath = fs.realpathSync(tscPath);
    return [
      path.resolve(realTscPath, '..', '..', 'lib', 'typescript.js'),
      path.resolve(realTscPath, '..', 'lib', 'typescript.js')
    ];
  } catch (error) {
    return [];
  }
}

function normalizeFilePath(filePath) {
  return path.resolve(filePath);
}

function countTypedNodes(ts, checker, sourceFile) {
  let nodeCount = 0;
  let typedNodeCount = 0;

  function visit(node) {
    nodeCount += 1;
    try {
      const type = checker.getTypeAtLocation(node);
      if (type) {
        typedNodeCount += 1;
      }
    } catch (error) {
      // Type resolution can fail on invalid or synthetic nodes; count continues.
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return { nodeCount, typedNodeCount };
}

function categoryName(ts, category) {
  return ts.DiagnosticCategory[category] || 'Unknown';
}

function diagnosticToJson(ts, diagnostic) {
  const json = {
    code: `TS${diagnostic.code}`,
    category: categoryName(ts, diagnostic.category),
    message: ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'),
    filePath: null,
    line: null,
    column: null
  };

  if (diagnostic.file && typeof diagnostic.start === 'number') {
    const position = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
    json.filePath = normalizeFilePath(diagnostic.file.fileName);
    json.line = position.line + 1;
    json.column = position.character + 1;
  }

  return json;
}

function isProjectSourceFile(filePath, projectRoot) {
  const normalized = normalizeFilePath(filePath);
  return normalized.startsWith(projectRoot + path.sep) || normalized === projectRoot;
}

function main() {
  const ts = loadTypeScript();
  const tsconfigArg = process.argv[2];
  if (!tsconfigArg) {
    throw new Error('Usage: node analyze-project.cjs <tsconfig-path>');
  }

  const tsconfigPath = normalizeFilePath(tsconfigArg);
  if (!fs.existsSync(tsconfigPath)) {
    throw new Error(`tsconfig file not found: ${tsconfigPath}`);
  }

  const readResult = ts.readConfigFile(tsconfigPath, ts.sys.readFile);
  if (readResult.error) {
    const output = {
      tsconfigPath,
      sourceFiles: [],
      diagnostics: [diagnosticToJson(ts, readResult.error)]
    };
    process.stdout.write(JSON.stringify(output));
    process.exit(0);
  }

  const parseResult = ts.parseJsonConfigFileContent(
    readResult.config,
    ts.sys,
    path.dirname(tsconfigPath),
    undefined,
    tsconfigPath
  );

  const program = ts.createProgram({
    rootNames: parseResult.fileNames,
    options: parseResult.options,
    projectReferences: parseResult.projectReferences
  });
  const checker = program.getTypeChecker();

  const projectRoot = normalizeFilePath(path.dirname(tsconfigPath));
  const sourceFiles = program
    .getSourceFiles()
    .filter((sourceFile) => !sourceFile.isDeclarationFile)
    .filter((sourceFile) => isProjectSourceFile(sourceFile.fileName, projectRoot))
    .map((sourceFile) => {
      const counts = countTypedNodes(ts, checker, sourceFile);
      return {
        path: normalizeFilePath(sourceFile.fileName),
        nodeCount: counts.nodeCount,
        typedNodeCount: counts.typedNodeCount
      };
    })
    .sort((a, b) => a.path.localeCompare(b.path));

  const diagnostics = [
    ...parseResult.errors,
    ...ts.getPreEmitDiagnostics(program)
  ].map((diagnostic) => diagnosticToJson(ts, diagnostic));

  const output = {
    tsconfigPath,
    sourceFiles,
    diagnostics
  };

  process.stdout.write(JSON.stringify(output));
}

try {
  main();
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(message + '\n');
  process.exit(1);
}
