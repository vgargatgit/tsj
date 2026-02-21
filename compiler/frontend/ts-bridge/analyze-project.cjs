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

const JAVA_IMPORT_PREFIX = 'java:';
const JAVA_CLASS_NAME_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$/;
const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/;

function diagnosticAtNode(ts, sourceFile, node, code, message) {
  let line = null;
  let column = null;
  if (sourceFile && node) {
    const start = node.getStart(sourceFile, false);
    const position = sourceFile.getLineAndCharacterOfPosition(start);
    line = position.line + 1;
    column = position.character + 1;
  }
  return {
    code,
    category: 'Error',
    message,
    filePath: sourceFile ? normalizeFilePath(sourceFile.fileName) : null,
    line,
    column
  };
}

function collectInteropBindings(ts, sourceFile) {
  const bindings = [];
  const diagnostics = [];

  function visit(node) {
    if (ts.isImportDeclaration(node) && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
      const specifier = node.moduleSpecifier.text;
      if (specifier.startsWith(JAVA_IMPORT_PREFIX)) {
        const className = specifier.slice(JAVA_IMPORT_PREFIX.length);
        if (!JAVA_CLASS_NAME_PATTERN.test(className)) {
          diagnostics.push(
            diagnosticAtNode(
              ts,
              sourceFile,
              node.moduleSpecifier,
              'TSJ26-INTEROP-MODULE-SPECIFIER',
              'Invalid Java interop specifier. Expected `java:<fully.qualified.ClassName>`.'
            )
          );
          return;
        }

        const importClause = node.importClause;
        const namedBindings = importClause && importClause.namedBindings;
        if (!importClause || importClause.name || !namedBindings || !ts.isNamedImports(namedBindings)) {
          diagnostics.push(
            diagnosticAtNode(
              ts,
              sourceFile,
              node,
              'TSJ26-INTEROP-SYNTAX',
              'Java interop imports must use named imports, for example `import { max } from \"java:java.lang.Math\"`.'
            )
          );
          return;
        }

        if (namedBindings.elements.length === 0) {
          diagnostics.push(
            diagnosticAtNode(
              ts,
              sourceFile,
              namedBindings,
              'TSJ26-INTEROP-SYNTAX',
              'Java interop imports must declare at least one named binding.'
            )
          );
          return;
        }

        for (const element of namedBindings.elements) {
          const importedName = element.propertyName ? element.propertyName.text : element.name.text;
          const localName = element.name.text;
          if (!IDENTIFIER_PATTERN.test(importedName) || !IDENTIFIER_PATTERN.test(localName)) {
            diagnostics.push(
              diagnosticAtNode(
                ts,
                sourceFile,
                element,
                'TSJ26-INTEROP-BINDING',
                `Invalid Java interop binding \`${importedName} as ${localName}\`.`
              )
            );
            continue;
          }
          const start = element.getStart(sourceFile, false);
          const position = sourceFile.getLineAndCharacterOfPosition(start);
          bindings.push({
            filePath: normalizeFilePath(sourceFile.fileName),
            line: position.line + 1,
            column: position.character + 1,
            className,
            importedName,
            localName
          });
        }
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return { bindings, diagnostics };
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
  const interopBindings = [];
  for (const sourceFile of program.getSourceFiles()) {
    if (sourceFile.isDeclarationFile || !isProjectSourceFile(sourceFile.fileName, projectRoot)) {
      continue;
    }
    const interopResult = collectInteropBindings(ts, sourceFile);
    interopBindings.push(...interopResult.bindings);
    diagnostics.push(...interopResult.diagnostics);
  }

  const output = {
    tsconfigPath,
    sourceFiles,
    diagnostics,
    interopBindings
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
