'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const SCHEMA_VERSION = 'tsj-backend-token-v1';
const KEYWORDS = new Set([
  'function', 'const', 'let', 'var', 'if', 'else', 'while', 'return',
  'true', 'false', 'null', 'for', 'export', 'import', 'from',
  'class', 'extends', 'this', 'super', 'new', 'undefined',
  'async', 'await', 'throw', 'delete', 'break', 'continue', 'do',
  'declare', 'using'
]);
let syntheticNameCounter = 0;

function loadTypeScript() {
  try {
    return require('typescript');
  } catch (error) {
    const globalCandidates = findGlobalTypeScriptCandidates();
    for (const candidate of globalCandidates) {
      if (!fs.existsSync(candidate)) {
        continue;
      }
      try {
        return require(candidate);
      } catch (candidateError) {
        // Continue trying other candidates.
      }
    }
    throw new Error(
      'Unable to resolve the `typescript` package. Install it in the workspace root (`npm i -D typescript`).'
    );
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

function tokenTypeName(ts, tokenKind, tokenText) {
  if (tokenKind >= ts.SyntaxKind.FirstKeyword && tokenKind <= ts.SyntaxKind.LastKeyword) {
    return KEYWORDS.has(tokenText) ? 'KEYWORD' : 'IDENTIFIER';
  }
  if (tokenKind === ts.SyntaxKind.Identifier) {
    return KEYWORDS.has(tokenText) ? 'KEYWORD' : 'IDENTIFIER';
  }
  if (tokenKind === ts.SyntaxKind.NumericLiteral || tokenKind === ts.SyntaxKind.BigIntLiteral) {
    return 'NUMBER';
  }
  if (tokenKind === ts.SyntaxKind.StringLiteral || tokenKind === ts.SyntaxKind.NoSubstitutionTemplateLiteral) {
    return 'STRING';
  }
  return 'SYMBOL';
}

function tokenTextValue(ts, scanner, tokenKind, tokenText) {
  if (tokenKind === ts.SyntaxKind.BigIntLiteral) {
    return tokenText;
  }
  if (tokenKind === ts.SyntaxKind.StringLiteral || tokenKind === ts.SyntaxKind.NoSubstitutionTemplateLiteral) {
    const value = scanner.getTokenValue();
    if (typeof value === 'string') {
      return value;
    }
    if (tokenText.length >= 2) {
      return tokenText.substring(1, tokenText.length - 1);
    }
  }
  return tokenText;
}

function diagnosticToJson(ts, sourceFile, diagnostic) {
  const position = sourceFile.getLineAndCharacterOfPosition(diagnostic.start || 0);
  return {
    code: `TS${diagnostic.code}`,
    message: ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'),
    line: position.line + 1,
    column: position.character + 1
  };
}

class NormalizationError extends Error {}

function unsupported(message) {
  throw new NormalizationError(message);
}

function hasModifier(ts, node, kind) {
  return !!node.modifiers && node.modifiers.some((modifier) => modifier.kind === kind);
}

function nodeLocation(sourceFile, node) {
  const start = node.getStart(sourceFile, false);
  const position = sourceFile.getLineAndCharacterOfPosition(start);
  return {
    line: position.line + 1,
    column: position.character + 1
  };
}

function withLocation(sourceFile, node, payload) {
  const loc = nodeLocation(sourceFile, node);
  return {
    ...payload,
    line: loc.line,
    column: loc.column
  };
}

function withSyntheticLocation(line, column, payload) {
  return {
    ...payload,
    line,
    column
  };
}

function undefinedLiteral(line, column) {
  return withSyntheticLocation(line, column, { kind: 'UndefinedLiteral' });
}

function cloneNormalized(value) {
  return JSON.parse(JSON.stringify(value));
}

function rewriteValue(value, rewriteNode) {
  if (Array.isArray(value)) {
    return value.map((entry) => rewriteValue(entry, rewriteNode));
  }
  if (!value || typeof value !== 'object') {
    return value;
  }
  const rewritten = {};
  for (const [key, entry] of Object.entries(value)) {
    rewritten[key] = rewriteValue(entry, rewriteNode);
  }
  if (typeof rewritten.kind === 'string') {
    return rewriteNode(rewritten);
  }
  return rewritten;
}

function rewriteThisExpressions(value, replacementExpression) {
  if (Array.isArray(value)) {
    return value.map((entry) => rewriteThisExpressions(entry, replacementExpression));
  }
  if (!value || typeof value !== 'object') {
    return value;
  }
  if (value.kind === 'ThisExpression') {
    return cloneNormalized(replacementExpression);
  }
  const rewritten = {};
  for (const [key, entry] of Object.entries(value)) {
    rewritten[key] = rewriteThisExpressions(entry, replacementExpression);
  }
  return rewritten;
}

function nextSyntheticName(prefix) {
  syntheticNameCounter += 1;
  return `${prefix}_${syntheticNameCounter}`;
}

function normalizeBlockOrSingleStatement(ts, sourceFile, statement) {
  if (ts.isBlock(statement)) {
    return statement.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner));
  }
  return normalizeStatement(ts, sourceFile, statement);
}

function rewriteCurrentLoopContinueStatements(statements, replaceContinue, loopLabel = null) {
  return statements.flatMap((statement) => {
    if (statement.kind === 'ContinueStatement') {
      const continueLabel = typeof statement.label === 'string' ? statement.label : null;
      const matchesCurrentLoop = continueLabel === null
        ? loopLabel === null
        : continueLabel === loopLabel;
      return matchesCurrentLoop ? replaceContinue(statement) : [statement];
    }
    if (statement.kind === 'WhileStatement') {
      if (loopLabel !== null) {
        return [{
          ...statement,
          body: rewriteCurrentLoopContinueStatements(statement.body, replaceContinue, loopLabel)
        }];
      }
      return [statement];
    }
    if (statement.kind === 'IfStatement') {
      return [{
        ...statement,
        thenBlock: rewriteCurrentLoopContinueStatements(statement.thenBlock, replaceContinue, loopLabel),
        elseBlock: rewriteCurrentLoopContinueStatements(statement.elseBlock, replaceContinue, loopLabel)
      }];
    }
    if (statement.kind === 'TryStatement') {
      return [{
        ...statement,
        tryBlock: rewriteCurrentLoopContinueStatements(statement.tryBlock, replaceContinue, loopLabel),
        catchBlock: rewriteCurrentLoopContinueStatements(statement.catchBlock, replaceContinue, loopLabel),
        finallyBlock: rewriteCurrentLoopContinueStatements(statement.finallyBlock, replaceContinue, loopLabel)
      }];
    }
    return [statement];
  });
}

function letLoopCaptureNames(ts, initializer) {
  if (!initializer || !ts.isVariableDeclarationList(initializer)) {
    return [];
  }
  if ((initializer.flags & ts.NodeFlags.Let) === 0) {
    return [];
  }
  const names = [];
  for (const declaration of initializer.declarations) {
    if (ts.isIdentifier(declaration.name)) {
      names.push(declaration.name.text);
    }
  }
  return names;
}

function wrapFunctionExpressionForLoopCapture(functionExpression, captureNames, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(line, column, {
      kind: 'FunctionExpression',
      parameters: [...captureNames],
      body: [
        withSyntheticLocation(line, column, {
          kind: 'ReturnStatement',
          expression: cloneNormalized(functionExpression)
        })
      ],
      async: false,
      thisMode: 'LEXICAL'
    }),
    arguments: captureNames.map((name) => withSyntheticLocation(line, column, {
      kind: 'VariableExpression',
      name
    }))
  });
}

function rewriteLoopClosureCaptureExpression(expression, captureNames) {
  if (!expression || typeof expression !== 'object' || !expression.kind) {
    return expression;
  }
  switch (expression.kind) {
    case 'UnaryExpression':
      return {
        ...expression,
        expression: rewriteLoopClosureCaptureExpression(expression.expression, captureNames)
      };
    case 'YieldExpression':
      return {
        ...expression,
        expression: rewriteLoopClosureCaptureExpression(expression.expression, captureNames)
      };
    case 'BinaryExpression':
      return {
        ...expression,
        left: rewriteLoopClosureCaptureExpression(expression.left, captureNames),
        right: rewriteLoopClosureCaptureExpression(expression.right, captureNames)
      };
    case 'AssignmentExpression':
      return {
        ...expression,
        target: rewriteLoopClosureCaptureExpression(expression.target, captureNames),
        expression: rewriteLoopClosureCaptureExpression(expression.expression, captureNames)
      };
    case 'ConditionalExpression':
      return {
        ...expression,
        condition: rewriteLoopClosureCaptureExpression(expression.condition, captureNames),
        whenTrue: rewriteLoopClosureCaptureExpression(expression.whenTrue, captureNames),
        whenFalse: rewriteLoopClosureCaptureExpression(expression.whenFalse, captureNames)
      };
    case 'CallExpression':
      return {
        ...expression,
        callee: rewriteLoopClosureCaptureExpression(expression.callee, captureNames),
        arguments: expression.arguments.map((argument) => rewriteLoopClosureCaptureExpression(argument, captureNames))
      };
    case 'OptionalCallExpression':
      return {
        ...expression,
        callee: rewriteLoopClosureCaptureExpression(expression.callee, captureNames),
        arguments: expression.arguments.map((argument) => rewriteLoopClosureCaptureExpression(argument, captureNames))
      };
    case 'MemberAccessExpression':
      return {
        ...expression,
        receiver: rewriteLoopClosureCaptureExpression(expression.receiver, captureNames)
      };
    case 'OptionalMemberAccessExpression':
      return {
        ...expression,
        receiver: rewriteLoopClosureCaptureExpression(expression.receiver, captureNames)
      };
    case 'NewExpression':
      return {
        ...expression,
        constructor: rewriteLoopClosureCaptureExpression(expression.constructor, captureNames),
        arguments: expression.arguments.map((argument) => rewriteLoopClosureCaptureExpression(argument, captureNames))
      };
    case 'ArrayLiteralExpression':
      return {
        ...expression,
        elements: expression.elements.map((element) => rewriteLoopClosureCaptureExpression(element, captureNames))
      };
    case 'ObjectLiteralExpression':
      return {
        ...expression,
        entries: expression.entries.map((entry) => ({
          ...entry,
          value: rewriteLoopClosureCaptureExpression(entry.value, captureNames)
        }))
      };
    case 'FunctionExpression': {
      const rewrittenFunction = {
        ...expression,
        body: rewriteLoopClosureCaptureStatements(expression.body, captureNames)
      };
      return wrapFunctionExpressionForLoopCapture(
        rewrittenFunction,
        captureNames,
        expression.line,
        expression.column
      );
    }
    default:
      return expression;
  }
}

function rewriteLoopClosureCaptureStatement(statement, captureNames) {
  if (!statement || typeof statement !== 'object' || !statement.kind) {
    return statement;
  }
  switch (statement.kind) {
    case 'VariableDeclaration':
      return {
        ...statement,
        expression: rewriteLoopClosureCaptureExpression(statement.expression, captureNames)
      };
    case 'AssignmentStatement':
      return {
        ...statement,
        target: rewriteLoopClosureCaptureExpression(statement.target, captureNames),
        expression: rewriteLoopClosureCaptureExpression(statement.expression, captureNames)
      };
    case 'FunctionDeclarationStatement':
      return {
        ...statement,
        declaration: {
          ...statement.declaration,
          body: rewriteLoopClosureCaptureStatements(statement.declaration.body, captureNames)
        }
      };
    case 'ClassDeclarationStatement':
      return {
        ...statement,
        declaration: {
          ...statement.declaration,
          constructorMethod: statement.declaration.constructorMethod
            ? {
              ...statement.declaration.constructorMethod,
              body: rewriteLoopClosureCaptureStatements(statement.declaration.constructorMethod.body, captureNames)
            }
            : null,
          methods: statement.declaration.methods.map((method) => ({
            ...method,
            body: rewriteLoopClosureCaptureStatements(method.body, captureNames)
          }))
        }
      };
    case 'LabeledStatement':
      return {
        ...statement,
        statement: rewriteLoopClosureCaptureStatement(statement.statement, captureNames)
      };
    case 'IfStatement':
      return {
        ...statement,
        condition: rewriteLoopClosureCaptureExpression(statement.condition, captureNames),
        thenBlock: rewriteLoopClosureCaptureStatements(statement.thenBlock, captureNames),
        elseBlock: rewriteLoopClosureCaptureStatements(statement.elseBlock, captureNames)
      };
    case 'WhileStatement':
      return {
        ...statement,
        condition: rewriteLoopClosureCaptureExpression(statement.condition, captureNames),
        body: rewriteLoopClosureCaptureStatements(statement.body, captureNames)
      };
    case 'TryStatement':
      return {
        ...statement,
        tryBlock: rewriteLoopClosureCaptureStatements(statement.tryBlock, captureNames),
        catchBlock: rewriteLoopClosureCaptureStatements(statement.catchBlock, captureNames),
        finallyBlock: rewriteLoopClosureCaptureStatements(statement.finallyBlock, captureNames)
      };
    case 'SuperCallStatement':
      return {
        ...statement,
        arguments: statement.arguments.map((argument) => rewriteLoopClosureCaptureExpression(argument, captureNames))
      };
    case 'ReturnStatement':
      return {
        ...statement,
        expression: rewriteLoopClosureCaptureExpression(statement.expression, captureNames)
      };
    case 'ThrowStatement':
      return {
        ...statement,
        expression: rewriteLoopClosureCaptureExpression(statement.expression, captureNames)
      };
    case 'ConsoleLogStatement':
      return {
        ...statement,
        expression: rewriteLoopClosureCaptureExpression(statement.expression, captureNames)
      };
    case 'ExpressionStatement':
      return {
        ...statement,
        expression: rewriteLoopClosureCaptureExpression(statement.expression, captureNames)
      };
    default:
      return statement;
  }
}

function rewriteLoopClosureCaptureStatements(statements, captureNames) {
  return statements.map((statement) => rewriteLoopClosureCaptureStatement(statement, captureNames));
}

function createLoopExitGuard(line, column, condition) {
  return withSyntheticLocation(line, column, {
    kind: 'IfStatement',
    condition: withSyntheticLocation(line, column, {
      kind: 'UnaryExpression',
      operator: '!',
      expression: cloneNormalized(condition)
    }),
    thenBlock: [
      withSyntheticLocation(line, column, { kind: 'BreakStatement' })
    ],
    elseBlock: []
  });
}

function normalizeForInitializer(ts, sourceFile, initializer) {
  if (!initializer) {
    return null;
  }
  if (ts.isVariableDeclarationList(initializer)) {
    const statements = [];
    for (const declaration of initializer.declarations) {
      if (ts.isObjectBindingPattern(declaration.name) || ts.isArrayBindingPattern(declaration.name)) {
        statements.push(...normalizeDestructuringVariableDeclaration(
          ts,
          sourceFile,
          declaration
        ));
        continue;
      }
      if (!ts.isIdentifier(declaration.name)) {
        unsupported('For-loop declaration initializers require identifier bindings or destructuring in normalizedProgram.');
      }
      const initializerExpression = declaration.initializer
        ? normalizeExpression(ts, sourceFile, declaration.initializer)
        : withLocation(sourceFile, declaration, { kind: 'UndefinedLiteral' });
      statements.push(withLocation(sourceFile, declaration, {
        kind: 'VariableDeclaration',
        name: declaration.name.text,
        expression: initializerExpression
      }));
    }
    return statements;
  }
  return [normalizeExpressionAsStatement(ts, sourceFile, initializer)];
}

function normalizeForStatement(ts, sourceFile, statement, loopLabel = null) {
  const statementLoc = nodeLocation(sourceFile, statement);
  const captureNames = letLoopCaptureNames(ts, statement.initializer);
  const initStatements = normalizeForInitializer(ts, sourceFile, statement.initializer);
  const condition = statement.condition
    ? normalizeExpression(ts, sourceFile, statement.condition)
    : withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true });
  const updateStatement = statement.incrementor
    ? normalizeExpressionAsStatement(ts, sourceFile, statement.incrementor)
    : null;
  let body = normalizeBlockOrSingleStatement(ts, sourceFile, statement.statement);
  if (captureNames.length > 0) {
    body = rewriteLoopClosureCaptureStatements(body, captureNames);
  }

  if (updateStatement !== null) {
    body = rewriteCurrentLoopContinueStatements(body, (continueStatement) => ([
      cloneNormalized(updateStatement),
      continueStatement
    ]), loopLabel);
    body.push(cloneNormalized(updateStatement));
  }

  const whileStatement = withLocation(sourceFile, statement, {
    kind: 'WhileStatement',
    condition,
    body
  });

  if (initStatements === null) {
    return whileStatement;
  }
  return withLocation(sourceFile, statement, {
    kind: 'IfStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true }),
    thenBlock: [...initStatements, whileStatement],
    elseBlock: []
  });
}

function syntheticCallExpression(name, args, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(line, column, {
      kind: 'VariableExpression',
      name
    }),
    arguments: args
  });
}

function normalizeForIterationBinding(ts, sourceFile, initializer, valueExpression, line, column) {
  if (ts.isVariableDeclarationList(initializer)) {
    if (initializer.declarations.length !== 1) {
      unsupported('for...of/for...in declarations support exactly one binding in normalizedProgram.');
    }
    const declaration = initializer.declarations[0];
    if (declaration.initializer) {
      unsupported('for...of/for...in declaration initializers are unsupported in normalizedProgram.');
    }
    if (ts.isIdentifier(declaration.name)) {
      return [withSyntheticLocation(line, column, {
        kind: 'VariableDeclaration',
        name: declaration.name.text,
        expression: cloneNormalized(valueExpression)
      })];
    }
    if (ts.isObjectBindingPattern(declaration.name) || ts.isArrayBindingPattern(declaration.name)) {
      return expandBindingNameToStatements(
        ts,
        sourceFile,
        declaration.name,
        cloneNormalized(valueExpression),
        'declare',
        line,
        column
      );
    }
    unsupported('Unsupported for...of/for...in declaration binding in normalizedProgram.');
  }
  return expandAssignmentTargetToStatements(
    ts,
    sourceFile,
    initializer,
    cloneNormalized(valueExpression),
    line,
    column
  );
}

function normalizeForOfOrInStatement(ts, sourceFile, statement, loopLabel = null) {
  const forOfStatement = ts.isForOfStatement(statement);
  const awaitForOf = forOfStatement && statement.awaitModifier;
  const statementLoc = nodeLocation(sourceFile, statement);
  const valuesName = nextSyntheticName(forOfStatement ? 'forOfValues' : 'forInKeys');
  const indexName = nextSyntheticName('forIndex');

  const valuesExpression = variableExpression(valuesName, statementLoc.line, statementLoc.column);
  const indexExpression = variableExpression(indexName, statementLoc.line, statementLoc.column);

  const collectionHelperName = forOfStatement
    ? '__tsj_for_of_values'
    : '__tsj_for_in_keys';
  const collectionExpression = syntheticCallExpression(
    collectionHelperName,
    [normalizeExpression(ts, sourceFile, statement.expression)],
    statementLoc.line,
    statementLoc.column
  );
  let currentValueExpression = syntheticCallExpression(
    '__tsj_index_read',
    [cloneNormalized(valuesExpression), cloneNormalized(indexExpression)],
    statementLoc.line,
    statementLoc.column
  );
  if (awaitForOf) {
    currentValueExpression = withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'AwaitExpression',
      expression: currentValueExpression
    });
  }

  const iterationBindingStatements = normalizeForIterationBinding(
    ts,
    sourceFile,
    statement.initializer,
    currentValueExpression,
    statementLoc.line,
    statementLoc.column
  );
  let body = [
    ...iterationBindingStatements,
    ...normalizeBlockOrSingleStatement(ts, sourceFile, statement.statement)
  ];
  const updateStatement = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'AssignmentStatement',
    target: cloneNormalized(indexExpression),
    expression: withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'BinaryExpression',
      left: cloneNormalized(indexExpression),
      operator: '+',
      right: withSyntheticLocation(statementLoc.line, statementLoc.column, {
        kind: 'NumberLiteral',
        text: '1'
      })
    })
  });
  body = rewriteCurrentLoopContinueStatements(body, (continueStatement) => ([
    cloneNormalized(updateStatement),
    continueStatement
  ]), loopLabel);
  body.push(cloneNormalized(updateStatement));

  const loopCondition = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'BinaryExpression',
    left: cloneNormalized(indexExpression),
    operator: '<',
    right: memberAccessExpression(valuesExpression, 'length', statementLoc.line, statementLoc.column)
  });
  const whileStatement = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'WhileStatement',
    condition: loopCondition,
    body
  });

  return withLocation(sourceFile, statement, {
    kind: 'IfStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true }),
    thenBlock: [
      withSyntheticLocation(statementLoc.line, statementLoc.column, {
        kind: 'VariableDeclaration',
        name: valuesName,
        expression: collectionExpression
      }),
      withSyntheticLocation(statementLoc.line, statementLoc.column, {
        kind: 'VariableDeclaration',
        name: indexName,
        expression: withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'NumberLiteral',
          text: '0'
        })
      }),
      whileStatement
    ],
    elseBlock: []
  });
}

function normalizeDoStatement(ts, sourceFile, statement, loopLabel = null) {
  const statementLoc = nodeLocation(sourceFile, statement);
  const condition = normalizeExpression(ts, sourceFile, statement.expression);
  let body = normalizeBlockOrSingleStatement(ts, sourceFile, statement.statement);
  body = rewriteCurrentLoopContinueStatements(body, (continueStatement) => ([
    createLoopExitGuard(continueStatement.line, continueStatement.column, condition),
    continueStatement
  ]), loopLabel);
  body.push(createLoopExitGuard(statementLoc.line, statementLoc.column, condition));
  return withLocation(sourceFile, statement, {
    kind: 'WhileStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'BooleanLiteral',
      value: true
    }),
    body
  });
}

function wrapLabeledLoopLowering(sourceFile, labeledStatement, label, loweredStatement) {
  const labeledNode = (loopStatement) => withLocation(sourceFile, labeledStatement, {
    kind: 'LabeledStatement',
    label,
    statement: loopStatement
  });
  if (loweredStatement.kind === 'WhileStatement') {
    return [labeledNode(loweredStatement)];
  }
  if (loweredStatement.kind === 'IfStatement'
    && loweredStatement.condition
    && loweredStatement.condition.kind === 'BooleanLiteral'
    && loweredStatement.condition.value === true
    && Array.isArray(loweredStatement.thenBlock)
    && Array.isArray(loweredStatement.elseBlock)
    && loweredStatement.elseBlock.length === 0
    && loweredStatement.thenBlock.length > 0) {
    const maybeLoop = loweredStatement.thenBlock[loweredStatement.thenBlock.length - 1];
    if (maybeLoop && maybeLoop.kind === 'WhileStatement') {
      const preludes = loweredStatement.thenBlock.slice(0, loweredStatement.thenBlock.length - 1);
      const labeledLoc = nodeLocation(sourceFile, labeledStatement);
      return [withSyntheticLocation(labeledLoc.line, labeledLoc.column, {
        kind: 'IfStatement',
        condition: withSyntheticLocation(labeledLoc.line, labeledLoc.column, {
          kind: 'BooleanLiteral',
          value: true
        }),
        thenBlock: [...preludes, labeledNode(maybeLoop)],
        elseBlock: []
      })];
    }
  }
  unsupported('Labeled loop lowering requires while-backed loop form in normalizedProgram.');
}

function normalizeExpressionAsStatement(ts, sourceFile, expressionNode) {
  if (ts.isBinaryExpression(expressionNode) && expressionNode.operatorToken.kind === ts.SyntaxKind.EqualsToken) {
    return withLocation(sourceFile, expressionNode, {
      kind: 'AssignmentStatement',
      target: normalizeExpression(ts, sourceFile, expressionNode.left),
      expression: normalizeExpression(ts, sourceFile, expressionNode.right)
    });
  }
  return withLocation(sourceFile, expressionNode, {
    kind: 'ExpressionStatement',
    expression: normalizeExpression(ts, sourceFile, expressionNode)
  });
}

function emptyTryClauseNoopStatement(line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'ExpressionStatement',
    expression: undefinedLiteral(line, column)
  });
}

function normalizeSwitchClauseBody(ts, sourceFile, clause) {
  return clause.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner));
}

function normalizeSwitchStatement(ts, sourceFile, statement) {
  const statementLoc = nodeLocation(sourceFile, statement);
  const switchValueName = nextSyntheticName('__tsj_switch_value');
  const switchIndexName = nextSyntheticName('__tsj_switch_index');
  const switchValueExpression = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'VariableExpression',
    name: switchValueName
  });
  const switchIndexExpression = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'VariableExpression',
    name: switchIndexName
  });
  const minusOne = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'NumberLiteral',
    text: '-1'
  });
  const switchIndexUnset = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'BinaryExpression',
    left: cloneNormalized(switchIndexExpression),
    operator: '===',
    right: cloneNormalized(minusOne)
  });

  const switchValueDeclaration = withLocation(sourceFile, statement, {
    kind: 'VariableDeclaration',
    name: switchValueName,
    expression: normalizeExpression(ts, sourceFile, statement.expression)
  });
  const switchIndexDeclaration = withLocation(sourceFile, statement, {
    kind: 'VariableDeclaration',
    name: switchIndexName,
    expression: cloneNormalized(minusOne)
  });

  const clauseDispatchStatements = [];
  const clauseBodies = [];
  let defaultClauseIndex = -1;
  let seenDefault = false;
  let clauseIndex = 0;

  for (const clause of statement.caseBlock.clauses) {
    if (ts.isCaseClause(clause)) {
      const clauseLoc = nodeLocation(sourceFile, clause);
      const matchCondition = withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
        kind: 'IfStatement',
        condition: withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
          kind: 'BinaryExpression',
          left: cloneNormalized(switchIndexUnset),
          operator: '&&',
          right: withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
            kind: 'BinaryExpression',
            left: cloneNormalized(switchValueExpression),
            operator: '===',
            right: normalizeExpression(ts, sourceFile, clause.expression)
          })
        }),
        thenBlock: [withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
          kind: 'AssignmentStatement',
          target: cloneNormalized(switchIndexExpression),
          expression: withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
            kind: 'NumberLiteral',
            text: String(clauseIndex)
          })
        })],
        elseBlock: []
      });
      clauseDispatchStatements.push(matchCondition);
      clauseBodies.push({
        clauseIndex,
        line: clauseLoc.line,
        column: clauseLoc.column,
        body: normalizeSwitchClauseBody(ts, sourceFile, clause)
      });
      clauseIndex += 1;
      continue;
    }
    if (seenDefault) {
      unsupported('Switch statements in TSJ-59a subset support at most one default clause.');
    }
    seenDefault = true;
    const clauseLoc = nodeLocation(sourceFile, clause);
    defaultClauseIndex = clauseIndex;
    clauseBodies.push({
      clauseIndex,
      line: clauseLoc.line,
      column: clauseLoc.column,
      body: normalizeSwitchClauseBody(ts, sourceFile, clause)
    });
    clauseIndex += 1;
  }

  if (defaultClauseIndex >= 0) {
    clauseDispatchStatements.push(withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'IfStatement',
      condition: cloneNormalized(switchIndexUnset),
      thenBlock: [withSyntheticLocation(statementLoc.line, statementLoc.column, {
        kind: 'AssignmentStatement',
        target: cloneNormalized(switchIndexExpression),
        expression: withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'NumberLiteral',
          text: String(defaultClauseIndex)
        })
      })],
      elseBlock: []
    }));
  }

  const loopBody = [];
  for (const clauseBody of clauseBodies) {
    const thenBlock = [...clauseBody.body];
    const lastThen = thenBlock.length > 0 ? thenBlock[thenBlock.length - 1] : null;
    const clauseTerminates = !!lastThen && (
      lastThen.kind === 'ReturnStatement'
      || lastThen.kind === 'ThrowStatement'
      || lastThen.kind === 'BreakStatement'
      || lastThen.kind === 'ContinueStatement'
    );
    if (!clauseTerminates) {
      thenBlock.push(withSyntheticLocation(clauseBody.line, clauseBody.column, {
        kind: 'AssignmentStatement',
        target: cloneNormalized(switchIndexExpression),
        expression: withSyntheticLocation(clauseBody.line, clauseBody.column, {
          kind: 'NumberLiteral',
          text: String(clauseBody.clauseIndex + 1)
        })
      }));
      thenBlock.push(withSyntheticLocation(clauseBody.line, clauseBody.column, {
        kind: 'ContinueStatement',
        label: null
      }));
    }
    loopBody.push(withSyntheticLocation(clauseBody.line, clauseBody.column, {
      kind: 'IfStatement',
      condition: withSyntheticLocation(clauseBody.line, clauseBody.column, {
        kind: 'BinaryExpression',
        left: cloneNormalized(switchIndexExpression),
        operator: '===',
        right: withSyntheticLocation(clauseBody.line, clauseBody.column, {
          kind: 'NumberLiteral',
          text: String(clauseBody.clauseIndex)
        })
      }),
      thenBlock,
      elseBlock: []
    }));
  }
  loopBody.push(withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BreakStatement', label: null }));

  const dispatchLoop = withLocation(sourceFile, statement, {
    kind: 'WhileStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true }),
    body: loopBody
  });

  return withLocation(sourceFile, statement, {
    kind: 'IfStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true }),
    thenBlock: [switchValueDeclaration, switchIndexDeclaration, ...clauseDispatchStatements, dispatchLoop],
    elseBlock: []
  });
}

function variableExpression(name, line, column) {
  return withSyntheticLocation(line, column, { kind: 'VariableExpression', name });
}

function memberAccessExpression(receiver, member, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'MemberAccessExpression',
    receiver: cloneNormalized(receiver),
    member
  });
}

function stringLiteralExpression(text, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'StringLiteral',
    text
  });
}

function propertyNameText(ts, sourceFile, propertyName) {
  if (ts.isIdentifier(propertyName) || ts.isStringLiteral(propertyName) || ts.isNumericLiteral(propertyName)) {
    return propertyName.text;
  }
  if (ts.isComputedPropertyName(propertyName)) {
    const unwrapped = unwrapParenthesizedExpression(ts, propertyName.expression);
    if (
      ts.isIdentifier(unwrapped)
      || ts.isStringLiteral(unwrapped)
      || ts.isNoSubstitutionTemplateLiteral(unwrapped)
      || ts.isNumericLiteral(unwrapped)
    ) {
      return unwrapped.text;
    }
    if (ts.isPropertyAccessExpression(unwrapped) || ts.isElementAccessExpression(unwrapped)) {
      return unwrapped.getText(sourceFile);
    }
    unsupported('Computed property names in normalizedProgram require stable identifier/string/numeric/symbol-like forms.');
  }
  unsupported('Destructuring supports only identifier/string/numeric property names in normalizedProgram.');
}

function objectLiteralKeyExpression(ts, sourceFile, propertyName) {
  const keyLoc = nodeLocation(sourceFile, propertyName);
  if (ts.isIdentifier(propertyName) || ts.isStringLiteral(propertyName) || ts.isNumericLiteral(propertyName)) {
    return stringLiteralExpression(propertyName.text, keyLoc.line, keyLoc.column);
  }
  if (ts.isComputedPropertyName(propertyName)) {
    return normalizeExpression(ts, sourceFile, unwrapParenthesizedExpression(ts, propertyName.expression));
  }
  unsupported('Unsupported object literal property name in normalizedProgram.');
}

function unwrapParenthesizedExpression(ts, expression) {
  let current = expression;
  while (ts.isParenthesizedExpression(current)) {
    current = current.expression;
  }
  return current;
}

function expandBindingNameToStatements(ts, sourceFile, bindingName, sourceExpression, mode, line, column) {
  if (ts.isIdentifier(bindingName)) {
    if (mode === 'declare') {
      return [withSyntheticLocation(line, column, {
        kind: 'VariableDeclaration',
        name: bindingName.text,
        expression: cloneNormalized(sourceExpression)
      })];
    }
    return [withSyntheticLocation(line, column, {
      kind: 'AssignmentStatement',
      target: variableExpression(bindingName.text, line, column),
      expression: cloneNormalized(sourceExpression)
    })];
  }
  if (ts.isObjectBindingPattern(bindingName)) {
    const statements = [];
    for (const element of bindingName.elements) {
      if (element.dotDotDotToken) {
        unsupported('Object rest destructuring is unsupported in normalizedProgram.');
      }
      let key;
      if (element.propertyName) {
        key = propertyNameText(ts, sourceFile, element.propertyName);
      } else if (ts.isIdentifier(element.name)) {
        key = element.name.text;
      } else {
        unsupported('Object destructuring requires explicit property names for nested bindings.');
      }
      const valueExpression = memberAccessExpression(sourceExpression, key, line, column);
      const resolvedValueExpression = element.initializer
        ? createDestructuringDefaultExpression(
          ts,
          sourceFile,
          valueExpression,
          element.initializer,
          line,
          column
        )
        : valueExpression;
      statements.push(...expandBindingNameToStatements(
        ts,
        sourceFile,
        element.name,
        resolvedValueExpression,
        mode,
        line,
        column
      ));
    }
    return statements;
  }
  if (ts.isArrayBindingPattern(bindingName)) {
    const statements = [];
    let index = 0;
    for (const element of bindingName.elements) {
      if (ts.isOmittedExpression(element)) {
        index += 1;
        continue;
      }
      if (!ts.isBindingElement(element)) {
        unsupported('Unsupported array destructuring element in normalizedProgram.');
      }
      if (element.dotDotDotToken) {
        const restExpression = createArrayRestExpression(sourceExpression, index, line, column);
        statements.push(...expandBindingNameToStatements(
          ts,
          sourceFile,
          element.name,
          restExpression,
          mode,
          line,
          column
        ));
        break;
      }
      const valueExpression = memberAccessExpression(sourceExpression, String(index), line, column);
      const resolvedValueExpression = element.initializer
        ? createDestructuringDefaultExpression(
          ts,
          sourceFile,
          valueExpression,
          element.initializer,
          line,
          column
        )
        : valueExpression;
      statements.push(...expandBindingNameToStatements(
        ts,
        sourceFile,
        element.name,
        resolvedValueExpression,
        mode,
        line,
        column
      ));
      index += 1;
    }
    return statements;
  }
  unsupported('Unsupported binding pattern in normalizedProgram.');
}

function normalizeDestructuringVariableDeclaration(ts, sourceFile, declaration) {
  if (!declaration.initializer) {
    unsupported('Destructuring declarations in normalizedProgram require initializers.');
  }
  const declarationLoc = nodeLocation(sourceFile, declaration);
  const tempName = nextSyntheticName('destruct');
  const tempExpression = variableExpression(tempName, declarationLoc.line, declarationLoc.column);
  const statements = [
    withSyntheticLocation(declarationLoc.line, declarationLoc.column, {
      kind: 'VariableDeclaration',
      name: tempName,
      expression: normalizeExpression(ts, sourceFile, declaration.initializer)
    })
  ];
  statements.push(...expandBindingNameToStatements(
    ts,
    sourceFile,
    declaration.name,
    tempExpression,
    'declare',
    declarationLoc.line,
    declarationLoc.column
  ));
  return statements;
}

function expandAssignmentTargetToStatements(ts, sourceFile, targetExpression, sourceExpression, line, column) {
  if (ts.isParenthesizedExpression(targetExpression)) {
    return expandAssignmentTargetToStatements(ts, sourceFile, targetExpression.expression, sourceExpression, line, column);
  }
  if (ts.isIdentifier(targetExpression) || ts.isPropertyAccessExpression(targetExpression)) {
    return [withSyntheticLocation(line, column, {
      kind: 'AssignmentStatement',
      target: normalizeExpression(ts, sourceFile, targetExpression),
      expression: cloneNormalized(sourceExpression)
    })];
  }
  if (ts.isArrayLiteralExpression(targetExpression)) {
    const statements = [];
    let index = 0;
    for (const element of targetExpression.elements) {
      if (ts.isOmittedExpression(element)) {
        index += 1;
        continue;
      }
      if (ts.isSpreadElement(element)) {
        unsupported('Array rest assignment is unsupported in normalizedProgram.');
      }
      const valueExpression = memberAccessExpression(sourceExpression, String(index), line, column);
      statements.push(...expandAssignmentTargetToStatements(
        ts,
        sourceFile,
        element,
        valueExpression,
        line,
        column
      ));
      index += 1;
    }
    return statements;
  }
  if (ts.isObjectLiteralExpression(targetExpression)) {
    const statements = [];
    for (const property of targetExpression.properties) {
      if (ts.isSpreadAssignment(property)) {
        unsupported('Object rest assignment is unsupported in normalizedProgram.');
      }
      if (ts.isShorthandPropertyAssignment(property)) {
        const valueExpression = memberAccessExpression(sourceExpression, property.name.text, line, column);
        statements.push(...expandAssignmentTargetToStatements(
          ts,
          sourceFile,
          property.name,
          valueExpression,
          line,
          column
        ));
        continue;
      }
      if (ts.isPropertyAssignment(property)) {
        const key = propertyNameText(ts, sourceFile, property.name);
        const valueExpression = memberAccessExpression(sourceExpression, key, line, column);
        statements.push(...expandAssignmentTargetToStatements(
          ts,
          sourceFile,
          property.initializer,
          valueExpression,
          line,
          column
        ));
        continue;
      }
      unsupported('Unsupported object assignment target in normalizedProgram.');
    }
    return statements;
  }
  unsupported('Unsupported destructuring assignment target in normalizedProgram.');
}

function normalizeDestructuringAssignmentStatement(ts, sourceFile, statement, binaryExpression) {
  const statementLoc = nodeLocation(sourceFile, statement);
  const tempName = nextSyntheticName('destructAssign');
  const tempExpression = variableExpression(tempName, statementLoc.line, statementLoc.column);
  const statements = [
    withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'VariableDeclaration',
      name: tempName,
      expression: normalizeExpression(ts, sourceFile, binaryExpression.right)
    })
  ];
  statements.push(...expandAssignmentTargetToStatements(
    ts,
    sourceFile,
    binaryExpression.left,
    tempExpression,
    statementLoc.line,
    statementLoc.column
  ));
  return statements;
}

function createDefaultParameterStatement(parameterName, initializerExpression, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'IfStatement',
    condition: withSyntheticLocation(line, column, {
      kind: 'BinaryExpression',
      left: variableExpression(parameterName, line, column),
      operator: '===',
      right: withSyntheticLocation(line, column, { kind: 'UndefinedLiteral' })
    }),
    thenBlock: [
      withSyntheticLocation(line, column, {
        kind: 'AssignmentStatement',
        target: variableExpression(parameterName, line, column),
        expression: initializerExpression
      })
    ],
    elseBlock: []
  });
}

function createRestArgsExpression(startIndex, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(line, column, {
      kind: 'VariableExpression',
      name: '__tsj_rest_args'
    }),
    arguments: [
      withSyntheticLocation(line, column, {
        kind: 'NumberLiteral',
        text: String(startIndex)
      })
    ]
  });
}

function createArrayRestExpression(sourceExpression, startIndex, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(line, column, {
      kind: 'VariableExpression',
      name: '__tsj_array_rest'
    }),
    arguments: [
      cloneNormalized(sourceExpression),
      withSyntheticLocation(line, column, {
        kind: 'NumberLiteral',
        text: String(startIndex)
      })
    ]
  });
}

function createDestructuringDefaultExpression(ts, sourceFile, valueExpression, initializer, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'ConditionalExpression',
    condition: withSyntheticLocation(line, column, {
      kind: 'BinaryExpression',
      left: cloneNormalized(valueExpression),
      operator: '===',
      right: withSyntheticLocation(line, column, { kind: 'UndefinedLiteral' })
    }),
    whenTrue: normalizeExpression(ts, sourceFile, initializer),
    whenFalse: cloneNormalized(valueExpression)
  });
}

function normalizeParameters(ts, sourceFile, parameters) {
  const names = [];
  const prologue = [];
  let sawRestParameter = false;
  for (const parameter of parameters) {
    if (sawRestParameter) {
      unsupported('Rest parameter must be the final parameter in normalizedProgram.');
    }
    if (parameter.dotDotDotToken) {
      sawRestParameter = true;
      if (parameter.initializer) {
        unsupported('Rest parameters cannot declare default initializers in normalizedProgram.');
      }
      if (!ts.isIdentifier(parameter.name)) {
        unsupported('Rest parameters must be identifiers in normalizedProgram.');
      }
      const parameterLoc = nodeLocation(sourceFile, parameter);
      prologue.push(withSyntheticLocation(parameterLoc.line, parameterLoc.column, {
        kind: 'VariableDeclaration',
        name: parameter.name.text,
        expression: createRestArgsExpression(names.length, parameterLoc.line, parameterLoc.column)
      }));
      continue;
    }
    if (ts.isIdentifier(parameter.name)) {
      names.push(parameter.name.text);
      if (parameter.initializer) {
        const parameterLoc = nodeLocation(sourceFile, parameter);
        prologue.push(createDefaultParameterStatement(
          parameter.name.text,
          normalizeExpression(ts, sourceFile, parameter.initializer),
          parameterLoc.line,
          parameterLoc.column
        ));
      }
      continue;
    }
    if (ts.isObjectBindingPattern(parameter.name) || ts.isArrayBindingPattern(parameter.name)) {
      const parameterLoc = nodeLocation(sourceFile, parameter);
      const syntheticName = nextSyntheticName('param');
      names.push(syntheticName);
      const sourceExpression = variableExpression(syntheticName, parameterLoc.line, parameterLoc.column);
      if (parameter.initializer) {
        prologue.push(createDefaultParameterStatement(
          syntheticName,
          normalizeExpression(ts, sourceFile, parameter.initializer),
          parameterLoc.line,
          parameterLoc.column
        ));
      }
      prologue.push(...expandBindingNameToStatements(
        ts,
        sourceFile,
        parameter.name,
        sourceExpression,
        'declare',
        parameterLoc.line,
        parameterLoc.column
      ));
      continue;
    }
    unsupported('Unsupported parameter form in normalizedProgram.');
  }
  return { names, prologue };
}

function arrayLiteralFromElements(elements, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'ArrayLiteralExpression',
    elements
  });
}

function objectLiteralFromEntries(entries, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'ObjectLiteralExpression',
    entries
  });
}

function normalizeObjectMethodValue(ts, sourceFile, property) {
  if (!property.body) {
    unsupported('Object methods in normalizedProgram require bodies.');
  }
  if (property.asteriskToken) {
    unsupported('Generator object methods are unsupported in normalizedProgram.');
  }
  if (property.questionToken) {
    unsupported('Optional object methods are unsupported in normalizedProgram.');
  }
  const normalizedParameters = normalizeParameters(ts, sourceFile, property.parameters);
  return withLocation(sourceFile, property, {
    kind: 'FunctionExpression',
    parameters: normalizedParameters.names,
    body: [
      ...normalizedParameters.prologue,
      ...property.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
    ],
    async: hasModifier(ts, property, ts.SyntaxKind.AsyncKeyword),
    thisMode: 'DYNAMIC'
  });
}

function normalizeObjectAccessorValue(ts, sourceFile, property) {
  if (!property.body) {
    unsupported('Object accessors in normalizedProgram require bodies.');
  }
  if (property.questionToken) {
    unsupported('Optional object accessors are unsupported in normalizedProgram.');
  }
  if (hasModifier(ts, property, ts.SyntaxKind.AsyncKeyword)) {
    unsupported('Async object accessors are unsupported in normalizedProgram.');
  }
  const normalizedParameters = normalizeParameters(ts, sourceFile, property.parameters);
  return withLocation(sourceFile, property, {
    kind: 'FunctionExpression',
    parameters: normalizedParameters.names,
    body: [
      ...normalizedParameters.prologue,
      ...property.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
    ],
    async: false,
    thisMode: 'DYNAMIC'
  });
}

function createObjectDynamicWriteExpression(objectExpression, keyExpression, valueExpression, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(line, column, {
      kind: 'VariableExpression',
      name: '__tsj_set_dynamic_key'
    }),
    arguments: [
      cloneNormalized(objectExpression),
      cloneNormalized(keyExpression),
      cloneNormalized(valueExpression)
    ]
  });
}

function createObjectAccessorDefineExpression(
  objectExpression,
  keyExpression,
  getterExpression,
  setterExpression,
  line,
  column
) {
  return withSyntheticLocation(line, column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(line, column, {
      kind: 'VariableExpression',
      name: '__tsj_define_accessor'
    }),
    arguments: [
      cloneNormalized(objectExpression),
      cloneNormalized(keyExpression),
      cloneNormalized(getterExpression),
      cloneNormalized(setterExpression)
    ]
  });
}

function spreadSegmentsFromArrayElements(ts, sourceFile, elements, line, column) {
  const segments = [];
  const chunk = [];
  let hasSpread = false;
  for (const element of elements) {
    if (ts.isSpreadElement(element)) {
      hasSpread = true;
      if (chunk.length > 0) {
        segments.push(arrayLiteralFromElements(chunk.splice(0), line, column));
      }
      segments.push(normalizeExpression(ts, sourceFile, element.expression));
      continue;
    }
    if (ts.isOmittedExpression(element)) {
      unsupported('Array holes are unsupported in normalizedProgram.');
    }
    chunk.push(normalizeExpression(ts, sourceFile, element));
  }
  if (chunk.length > 0) {
    segments.push(arrayLiteralFromElements(chunk.splice(0), line, column));
  }
  return hasSpread ? segments : null;
}

function spreadSegmentsFromObjectProperties(ts, sourceFile, properties, line, column) {
  const segments = [];
  const chunk = [];
  let hasSpread = false;
  for (const property of properties) {
    if (ts.isSpreadAssignment(property)) {
      hasSpread = true;
      if (chunk.length > 0) {
        segments.push(objectLiteralFromEntries(chunk.splice(0), line, column));
      }
      segments.push(normalizeExpression(ts, sourceFile, property.expression));
      continue;
    }
    if (ts.isPropertyAssignment(property)) {
      chunk.push({
        key: propertyNameText(ts, sourceFile, property.name),
        value: normalizeExpression(ts, sourceFile, property.initializer)
      });
      continue;
    }
    if (ts.isShorthandPropertyAssignment(property)) {
      chunk.push({
        key: property.name.text,
        value: withLocation(sourceFile, property.name, { kind: 'VariableExpression', name: property.name.text })
      });
      continue;
    }
    if (ts.isMethodDeclaration(property)) {
      chunk.push({
        key: propertyNameText(ts, sourceFile, property.name),
        value: normalizeObjectMethodValue(ts, sourceFile, property)
      });
      continue;
    }
    unsupported('Unsupported object literal member in normalizedProgram.');
  }
  if (chunk.length > 0) {
    segments.push(objectLiteralFromEntries(chunk.splice(0), line, column));
  }
  return hasSpread ? segments : null;
}

function spreadSegmentsFromCallArguments(ts, sourceFile, args, line, column) {
  const segments = [];
  const chunk = [];
  let hasSpread = false;
  for (const argument of args) {
    if (ts.isSpreadElement(argument)) {
      hasSpread = true;
      if (chunk.length > 0) {
        segments.push(arrayLiteralFromElements(chunk.splice(0), line, column));
      }
      segments.push(normalizeExpression(ts, sourceFile, argument.expression));
      continue;
    }
    chunk.push(normalizeExpression(ts, sourceFile, argument));
  }
  if (chunk.length > 0) {
    segments.push(arrayLiteralFromElements(chunk.splice(0), line, column));
  }
  return hasSpread ? segments : null;
}

function ensureNoUnsupportedClassMemberModifiers(ts, member) {
  if (!member.modifiers || member.modifiers.length === 0) {
    return;
  }
  for (const modifier of member.modifiers) {
    if (
      modifier.kind === ts.SyntaxKind.PublicKeyword
      || modifier.kind === ts.SyntaxKind.PrivateKeyword
      || modifier.kind === ts.SyntaxKind.ProtectedKeyword
      || modifier.kind === ts.SyntaxKind.StaticKeyword
      || modifier.kind === ts.SyntaxKind.ReadonlyKeyword
      || modifier.kind === ts.SyntaxKind.AbstractKeyword
      || modifier.kind === ts.SyntaxKind.OverrideKeyword
      || modifier.kind === ts.SyntaxKind.AccessorKeyword
      || modifier.kind === ts.SyntaxKind.Decorator
    ) {
      continue;
    }
    unsupported(`Unsupported class member modifier in normalizedProgram: ${ts.SyntaxKind[modifier.kind]}`);
  }
}

function manglePrivateName(name) {
  const sanitized = name.startsWith('#') ? name.substring(1) : name;
  return `__tsj_private_${sanitized}`;
}

function classMemberKeySpec(ts, sourceFile, nameNode) {
  if (ts.isIdentifier(nameNode)) {
    const keyLoc = nodeLocation(sourceFile, nameNode);
    return {
      line: keyLoc.line,
      column: keyLoc.column,
      literalKey: nameNode.text,
      keyExpression: null
    };
  }
  if (ts.isPrivateIdentifier(nameNode)) {
    const keyLoc = nodeLocation(sourceFile, nameNode);
    return {
      line: keyLoc.line,
      column: keyLoc.column,
      literalKey: manglePrivateName(nameNode.text),
      keyExpression: null
    };
  }
  if (ts.isStringLiteral(nameNode) || ts.isNoSubstitutionTemplateLiteral(nameNode)) {
    const keyLoc = nodeLocation(sourceFile, nameNode);
    return {
      line: keyLoc.line,
      column: keyLoc.column,
      literalKey: nameNode.text,
      keyExpression: null
    };
  }
  if (ts.isNumericLiteral(nameNode)) {
    const keyLoc = nodeLocation(sourceFile, nameNode);
    return {
      line: keyLoc.line,
      column: keyLoc.column,
      literalKey: nameNode.text,
      keyExpression: null
    };
  }
  if (ts.isComputedPropertyName(nameNode)) {
    const keyLoc = nodeLocation(sourceFile, nameNode);
    const unwrappedExpression = unwrapParenthesizedExpression(ts, nameNode.expression);
    if (ts.isStringLiteral(unwrappedExpression) || ts.isNoSubstitutionTemplateLiteral(unwrappedExpression)) {
      return {
        line: keyLoc.line,
        column: keyLoc.column,
        literalKey: unwrappedExpression.text,
        keyExpression: null
      };
    }
    if (ts.isNumericLiteral(unwrappedExpression)) {
      return {
        line: keyLoc.line,
        column: keyLoc.column,
        literalKey: unwrappedExpression.text,
        keyExpression: null
      };
    }
    return {
      line: keyLoc.line,
      column: keyLoc.column,
      literalKey: null,
      keyExpression: normalizeExpression(ts, sourceFile, unwrappedExpression)
    };
  }
  unsupported('Unsupported class member name form in normalizedProgram.');
}

function createClassPropertyWriteStatement(targetExpression, keySpec, valueExpression, line, column) {
  if (keySpec.keyExpression !== null) {
    return withSyntheticLocation(line, column, {
      kind: 'ExpressionStatement',
      expression: withSyntheticLocation(line, column, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(line, column, {
          kind: 'VariableExpression',
          name: '__tsj_set_dynamic_key'
        }),
        arguments: [
          cloneNormalized(targetExpression),
          cloneNormalized(keySpec.keyExpression),
          cloneNormalized(valueExpression)
        ]
      })
    });
  }
  return withSyntheticLocation(line, column, {
    kind: 'AssignmentStatement',
    target: memberAccessExpression(targetExpression, keySpec.literalKey, line, column),
    expression: cloneNormalized(valueExpression)
  });
}

function decoratorNodes(ts, node) {
  if (typeof ts.canHaveDecorators === 'function' && ts.canHaveDecorators(node) && typeof ts.getDecorators === 'function') {
    const decorators = ts.getDecorators(node);
    return decorators ? [...decorators] : [];
  }
  if (Array.isArray(node.decorators)) {
    return [...node.decorators];
  }
  if (Array.isArray(node.modifiers)) {
    return node.modifiers.filter((modifier) => modifier.kind === ts.SyntaxKind.Decorator);
  }
  return [];
}

function collectBindingNamesFromPattern(ts, pattern, names) {
  if (ts.isIdentifier(pattern)) {
    names.add(pattern.text);
    return;
  }
  if (ts.isObjectBindingPattern(pattern) || ts.isArrayBindingPattern(pattern)) {
    for (const element of pattern.elements) {
      if (ts.isBindingElement(element)) {
        collectBindingNamesFromPattern(ts, element.name, names);
      }
    }
  }
}

function collectTopLevelBindingNames(ts, sourceFile) {
  const names = new Set();
  for (const statement of sourceFile.statements) {
    if (ts.isFunctionDeclaration(statement) && statement.name && ts.isIdentifier(statement.name)) {
      names.add(statement.name.text);
      continue;
    }
    if (ts.isClassDeclaration(statement) && statement.name && ts.isIdentifier(statement.name)) {
      names.add(statement.name.text);
      continue;
    }
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        collectBindingNamesFromPattern(ts, declaration.name, names);
      }
      continue;
    }
    if (ts.isImportDeclaration(statement) && statement.importClause && !statement.importClause.isTypeOnly) {
      if (statement.importClause.name && ts.isIdentifier(statement.importClause.name)) {
        names.add(statement.importClause.name.text);
      }
      if (statement.importClause.namedBindings) {
        if (ts.isNamespaceImport(statement.importClause.namedBindings)) {
          names.add(statement.importClause.namedBindings.name.text);
        }
        if (ts.isNamedImports(statement.importClause.namedBindings)) {
          for (const element of statement.importClause.namedBindings.elements) {
            names.add(element.name.text);
          }
        }
      }
      continue;
    }
    if (ts.isImportEqualsDeclaration && ts.isImportEqualsDeclaration(statement) && ts.isIdentifier(statement.name)) {
      names.add(statement.name.text);
    }
  }
  return names;
}

function shouldLowerDecorator(ts, decoratorNode, knownBindings) {
  const decoratorExpression = decoratorNode.expression ? decoratorNode.expression : decoratorNode;
  if (ts.isIdentifier(decoratorExpression)) {
    return knownBindings.has(decoratorExpression.text);
  }
  if (ts.isCallExpression(decoratorExpression) && ts.isIdentifier(decoratorExpression.expression)) {
    return knownBindings.has(decoratorExpression.expression.text);
  }
  return false;
}

function functionTypeCheckExpression(expression, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'BinaryExpression',
    left: withSyntheticLocation(line, column, {
      kind: 'UnaryExpression',
      operator: 'typeof',
      expression: cloneNormalized(expression)
    }),
    operator: '===',
    right: withSyntheticLocation(line, column, {
      kind: 'StringLiteral',
      text: 'function'
    })
  });
}

function normalizeDecoratorRuntimeValue(ts, sourceFile, decoratorNode) {
  const decoratorExpression = decoratorNode.expression ? decoratorNode.expression : decoratorNode;
  const decoratorLoc = nodeLocation(sourceFile, decoratorNode);
  if (ts.isCallExpression(decoratorExpression)) {
    const factoryName = nextSyntheticName('decoratorFactory');
    const factoryExpression = variableExpression(factoryName, decoratorLoc.line, decoratorLoc.column);
    return withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'CallExpression',
      callee: withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
        kind: 'FunctionExpression',
        parameters: [],
        body: [
          withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
            kind: 'VariableDeclaration',
            name: factoryName,
            expression: normalizeExpression(ts, sourceFile, decoratorExpression.expression)
          }),
          withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
            kind: 'IfStatement',
            condition: functionTypeCheckExpression(factoryExpression, decoratorLoc.line, decoratorLoc.column),
            thenBlock: [withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
              kind: 'ReturnStatement',
              expression: withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
                kind: 'CallExpression',
                callee: cloneNormalized(factoryExpression),
                arguments: decoratorExpression.arguments.map((argument) => normalizeExpression(ts, sourceFile, argument))
              })
            })],
            elseBlock: [withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
              kind: 'ReturnStatement',
              expression: undefinedLiteral(decoratorLoc.line, decoratorLoc.column)
            })]
          })
        ],
        async: false,
        generator: false,
        thisMode: 'LEXICAL'
      }),
      arguments: []
    });
  }
  return normalizeExpression(ts, sourceFile, decoratorExpression);
}

function classMemberDecoratorKeyExpression(keySpec) {
  if (keySpec.keyExpression !== null) {
    return cloneNormalized(keySpec.keyExpression);
  }
  return stringLiteralExpression(keySpec.literalKey, keySpec.line, keySpec.column);
}

function createClassDecoratorApplicationStatements(
  ts,
  sourceFile,
  decorators,
  knownBindings,
  classReference,
  line,
  column
) {
  const statements = [];
  const activeDecorators = decorators.filter((decorator) => shouldLowerDecorator(ts, decorator, knownBindings));
  for (const decorator of [...activeDecorators].reverse()) {
    const decoratorLoc = nodeLocation(sourceFile, decorator);
    const decoratorName = nextSyntheticName('classDecorator');
    const decoratorExpression = variableExpression(decoratorName, decoratorLoc.line, decoratorLoc.column);
    const resultName = nextSyntheticName('classDecoratorResult');
    const resultExpression = variableExpression(resultName, decoratorLoc.line, decoratorLoc.column);
    statements.push(withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'VariableDeclaration',
      name: decoratorName,
      expression: normalizeDecoratorRuntimeValue(ts, sourceFile, decorator)
    }));
    statements.push(withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'IfStatement',
      condition: functionTypeCheckExpression(decoratorExpression, decoratorLoc.line, decoratorLoc.column),
      thenBlock: [
        withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
          kind: 'VariableDeclaration',
          name: resultName,
          expression: withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
            kind: 'CallExpression',
            callee: cloneNormalized(decoratorExpression),
            arguments: [cloneNormalized(classReference)]
          })
        }),
        withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
          kind: 'IfStatement',
          condition: cloneNormalized(resultExpression),
          thenBlock: [withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
            kind: 'AssignmentStatement',
            target: cloneNormalized(classReference),
            expression: cloneNormalized(resultExpression)
          })],
          elseBlock: []
        })
      ],
      elseBlock: []
    }));
  }
  return statements;
}

function createMethodDecoratorApplicationStatements(
  ts,
  sourceFile,
  decorators,
  knownBindings,
  targetExpression,
  keySpec,
  line,
  column
) {
  const statements = [];
  const targetName = nextSyntheticName('decoratorTarget');
  const keyName = nextSyntheticName('decoratorKey');
  const descriptorName = nextSyntheticName('decoratorDescriptor');
  const targetVariable = variableExpression(targetName, line, column);
  const keyVariable = variableExpression(keyName, line, column);
  const descriptorVariable = variableExpression(descriptorName, line, column);

  statements.push(withSyntheticLocation(line, column, {
    kind: 'VariableDeclaration',
    name: targetName,
    expression: cloneNormalized(targetExpression)
  }));
  statements.push(withSyntheticLocation(line, column, {
    kind: 'VariableDeclaration',
    name: keyName,
    expression: classMemberDecoratorKeyExpression(keySpec)
  }));
  statements.push(withSyntheticLocation(line, column, {
    kind: 'VariableDeclaration',
    name: descriptorName,
    expression: withSyntheticLocation(line, column, {
      kind: 'CallExpression',
      callee: memberAccessExpression(variableExpression('Object', line, column), 'getOwnPropertyDescriptor', line, column),
      arguments: [cloneNormalized(targetVariable), cloneNormalized(keyVariable)]
    })
  }));

  const activeDecorators = decorators.filter((decorator) => shouldLowerDecorator(ts, decorator, knownBindings));
  for (const decorator of [...activeDecorators].reverse()) {
    const decoratorLoc = nodeLocation(sourceFile, decorator);
    const decoratorName = nextSyntheticName('methodDecorator');
    const decoratorExpression = variableExpression(decoratorName, decoratorLoc.line, decoratorLoc.column);
    const resultName = nextSyntheticName('methodDecoratorResult');
    const resultExpression = variableExpression(resultName, decoratorLoc.line, decoratorLoc.column);
    statements.push(withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'VariableDeclaration',
      name: decoratorName,
      expression: normalizeDecoratorRuntimeValue(ts, sourceFile, decorator)
    }));
    statements.push(withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'IfStatement',
      condition: functionTypeCheckExpression(decoratorExpression, decoratorLoc.line, decoratorLoc.column),
      thenBlock: [
        withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
          kind: 'VariableDeclaration',
          name: resultName,
          expression: withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
            kind: 'CallExpression',
            callee: cloneNormalized(decoratorExpression),
            arguments: [
              cloneNormalized(targetVariable),
              cloneNormalized(keyVariable),
              cloneNormalized(descriptorVariable)
            ]
          })
        }),
        withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
          kind: 'AssignmentStatement',
          target: cloneNormalized(descriptorVariable),
          expression: withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
            kind: 'BinaryExpression',
            left: cloneNormalized(resultExpression),
            operator: '||',
            right: cloneNormalized(descriptorVariable)
          })
        })
      ],
      elseBlock: []
    }));
  }

  statements.push(withSyntheticLocation(line, column, {
    kind: 'IfStatement',
    condition: cloneNormalized(descriptorVariable),
    thenBlock: [withSyntheticLocation(line, column, {
      kind: 'ExpressionStatement',
      expression: withSyntheticLocation(line, column, {
        kind: 'CallExpression',
        callee: memberAccessExpression(variableExpression('Object', line, column), 'defineProperty', line, column),
        arguments: [
          cloneNormalized(targetVariable),
          cloneNormalized(keyVariable),
          cloneNormalized(descriptorVariable)
        ]
      })
    })],
    elseBlock: []
  }));

  return statements;
}

function createPropertyDecoratorApplicationStatements(
  ts,
  sourceFile,
  decorators,
  knownBindings,
  targetExpression,
  keySpec,
  line,
  column
) {
  const statements = [];
  const targetName = nextSyntheticName('decoratorTarget');
  const keyName = nextSyntheticName('decoratorKey');
  const targetVariable = variableExpression(targetName, line, column);
  const keyVariable = variableExpression(keyName, line, column);

  statements.push(withSyntheticLocation(line, column, {
    kind: 'VariableDeclaration',
    name: targetName,
    expression: cloneNormalized(targetExpression)
  }));
  statements.push(withSyntheticLocation(line, column, {
    kind: 'VariableDeclaration',
    name: keyName,
    expression: classMemberDecoratorKeyExpression(keySpec)
  }));

  const activeDecorators = decorators.filter((decorator) => shouldLowerDecorator(ts, decorator, knownBindings));
  for (const decorator of [...activeDecorators].reverse()) {
    const decoratorLoc = nodeLocation(sourceFile, decorator);
    const decoratorName = nextSyntheticName('propertyDecorator');
    const decoratorExpression = variableExpression(decoratorName, decoratorLoc.line, decoratorLoc.column);
    statements.push(withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'VariableDeclaration',
      name: decoratorName,
      expression: normalizeDecoratorRuntimeValue(ts, sourceFile, decorator)
    }));
    statements.push(withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
      kind: 'IfStatement',
      condition: functionTypeCheckExpression(decoratorExpression, decoratorLoc.line, decoratorLoc.column),
      thenBlock: [withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
        kind: 'ExpressionStatement',
        expression: withSyntheticLocation(decoratorLoc.line, decoratorLoc.column, {
          kind: 'CallExpression',
          callee: cloneNormalized(decoratorExpression),
          arguments: [cloneNormalized(targetVariable), cloneNormalized(keyVariable)]
        })
      })],
      elseBlock: []
    }));
  }

  return statements;
}

function normalizeClassMethodDefinition(ts, sourceFile, method) {
  ensureNoUnsupportedClassMemberModifiers(ts, method);
  if (!method.body) {
    unsupported('Class methods in normalizedProgram require bodies.');
  }
  if (method.asteriskToken) {
    unsupported('Generator class methods are unsupported in normalizedProgram.');
  }
  if (method.questionToken) {
    unsupported('Optional class methods are unsupported in normalizedProgram.');
  }
  const normalizedParameters = normalizeParameters(ts, sourceFile, method.parameters);
  return {
    parameters: normalizedParameters.names,
    body: [
      ...normalizedParameters.prologue,
      ...method.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
    ],
    async: hasModifier(ts, method, ts.SyntaxKind.AsyncKeyword)
  };
}

function classMethodFunctionExpression(methodDefinition, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'FunctionExpression',
    parameters: methodDefinition.parameters,
    body: methodDefinition.body.map((statement) => cloneNormalized(statement)),
    async: methodDefinition.async,
    thisMode: 'DYNAMIC'
  });
}

function hasParameterPropertyModifier(ts, parameter) {
  if (!parameter.modifiers || parameter.modifiers.length === 0) {
    return false;
  }
  return parameter.modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.PublicKeyword
    || modifier.kind === ts.SyntaxKind.PrivateKeyword
    || modifier.kind === ts.SyntaxKind.ProtectedKeyword
    || modifier.kind === ts.SyntaxKind.ReadonlyKeyword);
}

function constructorParameterPropertyAssignments(ts, sourceFile, parameters, fieldNames) {
  const assignments = [];
  for (const parameter of parameters) {
    if (!hasParameterPropertyModifier(ts, parameter)) {
      continue;
    }
    if (!ts.isIdentifier(parameter.name)) {
      unsupported('Constructor parameter properties in normalizedProgram require identifier names.');
    }
    const paramLoc = nodeLocation(sourceFile, parameter);
    const propertyName = parameter.name.text;
    if (!fieldNames.includes(propertyName)) {
      fieldNames.push(propertyName);
    }
    assignments.push(createClassPropertyWriteStatement(
      withSyntheticLocation(paramLoc.line, paramLoc.column, { kind: 'ThisExpression' }),
      {
        line: paramLoc.line,
        column: paramLoc.column,
        literalKey: propertyName,
        keyExpression: null
      },
      variableExpression(propertyName, paramLoc.line, paramLoc.column),
      paramLoc.line,
      paramLoc.column
    ));
  }
  return assignments;
}

function injectConstructorParameterPropertyAssignments(bodyStatements, parameterPropertyAssignments) {
  if (parameterPropertyAssignments.length === 0) {
    return bodyStatements;
  }
  let insertIndex = 0;
  while (insertIndex < bodyStatements.length && bodyStatements[insertIndex].kind === 'SuperCallStatement') {
    insertIndex += 1;
  }
  return [
    ...bodyStatements.slice(0, insertIndex),
    ...parameterPropertyAssignments.map((statementNode) => cloneNormalized(statementNode)),
    ...bodyStatements.slice(insertIndex)
  ];
}

function containsThisKeyword(ts, node) {
  let contains = false;
  function visit(current) {
    if (contains) {
      return;
    }
    if (current.kind === ts.SyntaxKind.ThisKeyword) {
      contains = true;
      return;
    }
    ts.forEachChild(current, visit);
  }
  visit(node);
  return contains;
}

function normalizeClassDeclaration(ts, sourceFile, statement) {
  let classNameText = null;
  if (!statement.name) {
    classNameText = nextSyntheticName('classExpr');
  } else if (ts.isIdentifier(statement.name)) {
    classNameText = statement.name.text;
  } else {
    unsupported('Class declarations in normalizedProgram require identifier names.');
  }

  let superClassName = null;
  if (statement.heritageClauses && statement.heritageClauses.length > 0) {
    for (const clause of statement.heritageClauses) {
      if (clause.token === ts.SyntaxKind.ExtendsKeyword) {
        if (clause.types.length !== 1) {
          unsupported('Class extends clause in normalizedProgram supports exactly one base type.');
        }
        const baseType = clause.types[0];
        if (!ts.isIdentifier(baseType.expression)) {
          unsupported('Class extends clause must reference identifier base type in normalizedProgram.');
        }
        superClassName = baseType.expression.text;
        continue;
      }
      if (clause.token === ts.SyntaxKind.ImplementsKeyword) {
        continue;
      }
      unsupported('Unsupported heritage clause in normalizedProgram.');
    }
  }

  const classLoc = nodeLocation(sourceFile, statement);
  const classReference = variableExpression(classNameText, classLoc.line, classLoc.column);
  const prototypeReference = memberAccessExpression(classReference, 'prototype', classLoc.line, classLoc.column);
  const knownDecoratorBindings = collectTopLevelBindingNames(ts, sourceFile);
  const classDecorators = decoratorNodes(ts, statement);

  let constructorMethod = null;
  const methods = [];
  const fieldNames = [];
  const instanceFieldInitializers = [];
  const postClassStatements = [];
  for (const member of statement.members) {
    if (ts.isPropertyDeclaration(member)) {
      ensureNoUnsupportedClassMemberModifiers(ts, member);
      const keySpec = classMemberKeySpec(ts, sourceFile, member.name);
      const memberLoc = nodeLocation(sourceFile, member);
      const memberDecorators = decoratorNodes(ts, member);
      const initializerExpression = member.initializer
        ? normalizeExpression(ts, sourceFile, member.initializer)
        : withSyntheticLocation(memberLoc.line, memberLoc.column, { kind: 'UndefinedLiteral' });
      const staticField = hasModifier(ts, member, ts.SyntaxKind.StaticKeyword);
      const assignmentStatement = createClassPropertyWriteStatement(
        staticField
          ? classReference
          : withSyntheticLocation(memberLoc.line, memberLoc.column, { kind: 'ThisExpression' }),
        keySpec,
        initializerExpression,
        memberLoc.line,
        memberLoc.column
      );
      if (staticField) {
        postClassStatements.push(assignmentStatement);
      } else {
        instanceFieldInitializers.push(assignmentStatement);
        if (keySpec.keyExpression === null) {
          fieldNames.push(keySpec.literalKey);
        }
      }
      if (memberDecorators.length > 0) {
        postClassStatements.push(...createPropertyDecoratorApplicationStatements(
          ts,
          sourceFile,
          memberDecorators,
          knownDecoratorBindings,
          staticField ? classReference : prototypeReference,
          keySpec,
          memberLoc.line,
          memberLoc.column
        ));
      }
      continue;
    }
    if (ts.isConstructorDeclaration(member)) {
      ensureNoUnsupportedClassMemberModifiers(ts, member);
      if (constructorMethod !== null) {
        unsupported('Duplicate class constructor declarations are unsupported in normalizedProgram.');
      }
      if (!member.body) {
        unsupported('Class constructors in normalizedProgram require bodies.');
      }
      const normalizedParameters = normalizeParameters(ts, sourceFile, member.parameters);
      const parameterPropertyAssignments = constructorParameterPropertyAssignments(
        ts,
        sourceFile,
        member.parameters,
        fieldNames
      );
      const normalizedBody = member.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner));
      constructorMethod = {
        name: 'constructor',
        parameters: normalizedParameters.names,
        body: [
          ...normalizedParameters.prologue,
          ...injectConstructorParameterPropertyAssignments(normalizedBody, parameterPropertyAssignments)
        ],
        async: false
      };
      continue;
    }
    if (ts.isMethodDeclaration(member)) {
      if (!member.body) {
        continue;
      }
      const methodLoc = nodeLocation(sourceFile, member);
      const keySpec = classMemberKeySpec(ts, sourceFile, member.name);
      const methodDefinition = normalizeClassMethodDefinition(ts, sourceFile, member);
      const staticMethod = hasModifier(ts, member, ts.SyntaxKind.StaticKeyword);
      if (staticMethod) {
        postClassStatements.push(createClassPropertyWriteStatement(
          classReference,
          keySpec,
          classMethodFunctionExpression(methodDefinition, methodLoc.line, methodLoc.column),
          methodLoc.line,
          methodLoc.column
        ));
      } else if (keySpec.keyExpression === null) {
        methods.push({
          name: keySpec.literalKey,
          parameters: methodDefinition.parameters,
          body: methodDefinition.body,
          async: methodDefinition.async
        });
      } else {
        postClassStatements.push(createClassPropertyWriteStatement(
          prototypeReference,
          keySpec,
          classMethodFunctionExpression(methodDefinition, methodLoc.line, methodLoc.column),
          methodLoc.line,
          methodLoc.column
        ));
      }
      const memberDecorators = decoratorNodes(ts, member);
      if (memberDecorators.length > 0) {
        postClassStatements.push(...createMethodDecoratorApplicationStatements(
          ts,
          sourceFile,
          memberDecorators,
          knownDecoratorBindings,
          staticMethod ? classReference : prototypeReference,
          keySpec,
          methodLoc.line,
          methodLoc.column
        ));
      }
      continue;
    }
    if (ts.isGetAccessorDeclaration(member) || ts.isSetAccessorDeclaration(member)) {
      ensureNoUnsupportedClassMemberModifiers(ts, member);
      if (!member.body) {
        continue;
      }
      if (hasModifier(ts, member, ts.SyntaxKind.AsyncKeyword)) {
        unsupported('Async class accessors are unsupported in normalizedProgram.');
      }
      const keySpec = classMemberKeySpec(ts, sourceFile, member.name);
      const memberLoc = nodeLocation(sourceFile, member);
      const targetExpression = hasModifier(ts, member, ts.SyntaxKind.StaticKeyword)
        ? classReference
        : prototypeReference;
      const keyExpression = keySpec.keyExpression !== null
        ? keySpec.keyExpression
        : stringLiteralExpression(keySpec.literalKey, keySpec.line, keySpec.column);
      const accessorValue = normalizeObjectAccessorValue(ts, sourceFile, member);
      const getterExpression = ts.isGetAccessorDeclaration(member)
        ? accessorValue
        : undefinedLiteral(memberLoc.line, memberLoc.column);
      const setterExpression = ts.isSetAccessorDeclaration(member)
        ? accessorValue
        : undefinedLiteral(memberLoc.line, memberLoc.column);
      postClassStatements.push(withSyntheticLocation(memberLoc.line, memberLoc.column, {
        kind: 'ExpressionStatement',
        expression: createObjectAccessorDefineExpression(
          targetExpression,
          keyExpression,
          getterExpression,
          setterExpression,
          memberLoc.line,
          memberLoc.column
        )
      }));
      const memberDecorators = decoratorNodes(ts, member);
      if (memberDecorators.length > 0) {
        postClassStatements.push(...createMethodDecoratorApplicationStatements(
          ts,
          sourceFile,
          memberDecorators,
          knownDecoratorBindings,
          targetExpression,
          keySpec,
          memberLoc.line,
          memberLoc.column
        ));
      }
      continue;
    }
    if (ts.isClassStaticBlockDeclaration && ts.isClassStaticBlockDeclaration(member)) {
      const normalizedStatements = member.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner));
      const rewrittenStatements = rewriteThisExpressions(normalizedStatements, classReference);
      postClassStatements.push(...rewrittenStatements);
      continue;
    }
    unsupported(`Unsupported class member in normalizedProgram: ${ts.SyntaxKind[member.kind]}`);
  }

  if (instanceFieldInitializers.length > 0) {
    if (constructorMethod === null) {
      const constructorBody = [];
      if (superClassName !== null) {
        constructorBody.push(withSyntheticLocation(classLoc.line, classLoc.column, {
          kind: 'SuperCallStatement',
          arguments: []
        }));
      }
      constructorBody.push(...instanceFieldInitializers.map((statementNode) => cloneNormalized(statementNode)));
      constructorMethod = {
        name: 'constructor',
        parameters: [],
        body: constructorBody,
        async: false
      };
    } else {
      const constructorBody = constructorMethod.body;
      if (superClassName !== null && constructorBody.length > 0 && constructorBody[0].kind === 'SuperCallStatement') {
        constructorMethod = {
          ...constructorMethod,
          body: [
            constructorBody[0],
            ...instanceFieldInitializers.map((statementNode) => cloneNormalized(statementNode)),
            ...constructorBody.slice(1)
          ]
        };
      } else {
        constructorMethod = {
          ...constructorMethod,
          body: [
            ...instanceFieldInitializers.map((statementNode) => cloneNormalized(statementNode)),
            ...constructorBody
          ]
        };
      }
    }
  }

  const classDeclarationStatement = withLocation(sourceFile, statement, {
    kind: 'ClassDeclarationStatement',
    declaration: {
      name: classNameText,
      superClassName,
      fieldNames,
      constructorMethod,
      methods
    }
  });
  const classDecoratorStatements = createClassDecoratorApplicationStatements(
    ts,
    sourceFile,
    classDecorators,
    knownDecoratorBindings,
    classReference,
    classLoc.line,
    classLoc.column
  );
  return [classDeclarationStatement, ...postClassStatements, ...classDecoratorStatements];
}

function normalizeExpression(ts, sourceFile, expression) {
  if (ts.isIdentifier(expression)) {
    if (expression.text === 'undefined') {
      return withLocation(sourceFile, expression, { kind: 'UndefinedLiteral' });
    }
    return withLocation(sourceFile, expression, { kind: 'VariableExpression', name: expression.text });
  }
  if (ts.isNumericLiteral(expression)) {
    return withLocation(sourceFile, expression, { kind: 'NumberLiteral', text: expression.getText(sourceFile) });
  }
  if (ts.isBigIntLiteral && ts.isBigIntLiteral(expression)) {
    return withLocation(sourceFile, expression, { kind: 'NumberLiteral', text: expression.getText(sourceFile) });
  }
  if (ts.isStringLiteral(expression) || ts.isNoSubstitutionTemplateLiteral(expression)) {
    return withLocation(sourceFile, expression, { kind: 'StringLiteral', text: expression.text });
  }
  if (ts.isTemplateExpression(expression)) {
    let current = withLocation(sourceFile, expression.head, {
      kind: 'StringLiteral',
      text: expression.head.text
    });
    for (const span of expression.templateSpans) {
      current = withLocation(sourceFile, span, {
        kind: 'BinaryExpression',
        left: current,
        operator: '+',
        right: normalizeExpression(ts, sourceFile, span.expression)
      });
      current = withLocation(sourceFile, span.literal, {
        kind: 'BinaryExpression',
        left: current,
        operator: '+',
        right: withLocation(sourceFile, span.literal, {
          kind: 'StringLiteral',
          text: span.literal.text
        })
      });
    }
    return current;
  }
  if (ts.isTaggedTemplateExpression(expression)) {
    const tag = normalizeExpression(ts, sourceFile, expression.tag);
    const callLoc = nodeLocation(sourceFile, expression);
    const cookedParts = [];
    const values = [];
    if (ts.isNoSubstitutionTemplateLiteral(expression.template)) {
      cookedParts.push(withLocation(sourceFile, expression.template, {
        kind: 'StringLiteral',
        text: expression.template.text
      }));
    } else if (ts.isTemplateExpression(expression.template)) {
      cookedParts.push(withLocation(sourceFile, expression.template.head, {
        kind: 'StringLiteral',
        text: expression.template.head.text
      }));
      for (const span of expression.template.templateSpans) {
        values.push(normalizeExpression(ts, sourceFile, span.expression));
        cookedParts.push(withLocation(sourceFile, span.literal, {
          kind: 'StringLiteral',
          text: span.literal.text
        }));
      }
    } else {
      unsupported('Unsupported tagged template form in normalizedProgram.');
    }
    return withLocation(sourceFile, expression, {
      kind: 'CallExpression',
      callee: tag,
      arguments: [
        withSyntheticLocation(callLoc.line, callLoc.column, {
          kind: 'ArrayLiteralExpression',
          elements: cookedParts
        }),
        ...values
      ]
    });
  }
  if (expression.kind === ts.SyntaxKind.TrueKeyword) {
    return withLocation(sourceFile, expression, { kind: 'BooleanLiteral', value: true });
  }
  if (expression.kind === ts.SyntaxKind.FalseKeyword) {
    return withLocation(sourceFile, expression, { kind: 'BooleanLiteral', value: false });
  }
  if (expression.kind === ts.SyntaxKind.NullKeyword) {
    return withLocation(sourceFile, expression, { kind: 'NullLiteral' });
  }
  if (expression.kind === ts.SyntaxKind.ThisKeyword) {
    return withLocation(sourceFile, expression, { kind: 'ThisExpression' });
  }
  if (ts.isTypeOfExpression && ts.isTypeOfExpression(expression)) {
    return withLocation(sourceFile, expression, {
      kind: 'UnaryExpression',
      operator: 'typeof',
      expression: normalizeExpression(ts, sourceFile, expression.expression)
    });
  }
  if (ts.isAsExpression(expression)) {
    return normalizeExpression(ts, sourceFile, expression.expression);
  }
  if (ts.isNonNullExpression && ts.isNonNullExpression(expression)) {
    return normalizeExpression(ts, sourceFile, expression.expression);
  }
  if (ts.isExpressionWithTypeArguments && ts.isExpressionWithTypeArguments(expression)) {
    return normalizeExpression(ts, sourceFile, expression.expression);
  }
  if (ts.isSatisfiesExpression && ts.isSatisfiesExpression(expression)) {
    return normalizeExpression(ts, sourceFile, expression.expression);
  }
  if (ts.isTypeAssertionExpression && ts.isTypeAssertionExpression(expression)) {
    return normalizeExpression(ts, sourceFile, expression.expression);
  }
  if (ts.isParenthesizedExpression(expression)) {
    return normalizeExpression(ts, sourceFile, expression.expression);
  }
  if (ts.isVoidExpression && ts.isVoidExpression(expression)) {
    const expressionLoc = nodeLocation(sourceFile, expression);
    return undefinedLiteral(expressionLoc.line, expressionLoc.column);
  }
  if (ts.isDeleteExpression && ts.isDeleteExpression(expression)) {
    return withLocation(sourceFile, expression, {
      kind: 'UnaryExpression',
      operator: 'delete',
      expression: normalizeExpression(ts, sourceFile, expression.expression)
    });
  }
  if (ts.isYieldExpression && ts.isYieldExpression(expression)) {
    const expressionLoc = nodeLocation(sourceFile, expression);
    return withLocation(sourceFile, expression, {
      kind: 'YieldExpression',
      expression: expression.expression
        ? normalizeExpression(ts, sourceFile, expression.expression)
        : undefinedLiteral(expressionLoc.line, expressionLoc.column),
      delegate: !!expression.asteriskToken
    });
  }
  if (ts.isMetaProperty && ts.isMetaProperty(expression)) {
    const expressionLoc = nodeLocation(sourceFile, expression);
    return undefinedLiteral(expressionLoc.line, expressionLoc.column);
  }
  if (ts.isRegularExpressionLiteral && ts.isRegularExpressionLiteral(expression)) {
    return withLocation(sourceFile, expression, {
      kind: 'StringLiteral',
      text: expression.getText(sourceFile)
    });
  }
  if (ts.isPrefixUnaryExpression(expression)) {
    if (expression.operator === ts.SyntaxKind.PlusPlusToken || expression.operator === ts.SyntaxKind.MinusMinusToken) {
      const target = normalizeExpression(ts, sourceFile, expression.operand);
      const expressionLoc = nodeLocation(sourceFile, expression);
      return withLocation(sourceFile, expression, {
        kind: 'AssignmentExpression',
        target,
        operator: '=',
        expression: withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
          kind: 'BinaryExpression',
          left: cloneNormalized(target),
          operator: expression.operator === ts.SyntaxKind.PlusPlusToken ? '+' : '-',
          right: withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
            kind: 'NumberLiteral',
            text: '1'
          })
        })
      });
    }
    const operator = expression.operator === ts.SyntaxKind.PlusToken
      ? '+'
      : expression.operator === ts.SyntaxKind.MinusToken
        ? '-'
        : expression.operator === ts.SyntaxKind.ExclamationToken
          ? '!'
          : expression.operator === ts.SyntaxKind.TildeToken
            ? '~'
            : null;
    if (!operator) {
      unsupported('Unsupported prefix unary operator in normalizedProgram.');
    }
    return withLocation(sourceFile, expression, {
      kind: 'UnaryExpression',
      operator,
      expression: normalizeExpression(ts, sourceFile, expression.operand)
    });
  }
  if (ts.isAwaitExpression(expression)) {
    return withLocation(sourceFile, expression, {
      kind: 'AwaitExpression',
      expression: normalizeExpression(ts, sourceFile, expression.expression)
    });
  }
  if (ts.isPropertyAccessExpression(expression)) {
    const kind = expression.questionDotToken
      ? 'OptionalMemberAccessExpression'
      : 'MemberAccessExpression';
    const memberName = ts.isPrivateIdentifier(expression.name)
      ? manglePrivateName(expression.name.text)
      : expression.name.text;
    return withLocation(sourceFile, expression, {
      kind,
      receiver: normalizeExpression(ts, sourceFile, expression.expression),
      member: memberName
    });
  }
  if (ts.isElementAccessExpression(expression)) {
    const argumentExpression = expression.argumentExpression
      ? unwrapParenthesizedExpression(ts, expression.argumentExpression)
      : null;
    const memberAccessKind = expression.questionDotToken
      ? 'OptionalMemberAccessExpression'
      : 'MemberAccessExpression';
    const indexReadHelperName = expression.questionDotToken
      ? '__tsj_optional_index_read'
      : '__tsj_index_read';
    if (argumentExpression && ts.isStringLiteral(argumentExpression)) {
      return withLocation(sourceFile, expression, {
        kind: memberAccessKind,
        receiver: normalizeExpression(ts, sourceFile, expression.expression),
        member: argumentExpression.text
      });
    }
    if (argumentExpression && ts.isNoSubstitutionTemplateLiteral(argumentExpression)) {
      return withLocation(sourceFile, expression, {
        kind: memberAccessKind,
        receiver: normalizeExpression(ts, sourceFile, expression.expression),
        member: argumentExpression.text
      });
    }
    if (argumentExpression && ts.isNumericLiteral(argumentExpression)) {
      return withLocation(sourceFile, expression, {
        kind: memberAccessKind,
        receiver: normalizeExpression(ts, sourceFile, expression.expression),
        member: argumentExpression.text
      });
    }
    return withLocation(sourceFile, expression, {
      kind: 'CallExpression',
      callee: withLocation(sourceFile, expression.expression, {
        kind: 'VariableExpression',
        name: indexReadHelperName
      }),
      arguments: [
        normalizeExpression(ts, sourceFile, expression.expression),
        argumentExpression
          ? normalizeExpression(ts, sourceFile, argumentExpression)
          : undefinedLiteral(nodeLocation(sourceFile, expression).line, nodeLocation(sourceFile, expression).column)
      ]
    });
  }
  if (ts.isCallExpression(expression)) {
    if (
      ts.isPropertyAccessExpression(expression.expression)
      && expression.expression.expression.kind === ts.SyntaxKind.SuperKeyword
    ) {
      if (expression.questionDotToken || expression.expression.questionDotToken) {
        unsupported('Optional super member calls are unsupported in normalizedProgram.');
      }
      return withLocation(sourceFile, expression, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(nodeLocation(sourceFile, expression).line, nodeLocation(sourceFile, expression).column, {
          kind: 'VariableExpression',
          name: '__tsj_super_invoke'
        }),
        arguments: [
          withSyntheticLocation(nodeLocation(sourceFile, expression.expression.name).line, nodeLocation(sourceFile, expression.expression.name).column, {
            kind: 'StringLiteral',
            text: ts.isPrivateIdentifier(expression.expression.name)
              ? manglePrivateName(expression.expression.name.text)
              : expression.expression.name.text
          }),
          ...expression.arguments.map((argument) => normalizeExpression(ts, sourceFile, argument))
        ]
      });
    }
    if (expression.expression.kind === ts.SyntaxKind.ImportKeyword) {
      if (expression.arguments.length !== 1) {
        unsupported('Dynamic import expressions in normalizedProgram require exactly one argument.');
      }
      const importArgument = unwrapParenthesizedExpression(ts, expression.arguments[0]);
      if (!ts.isStringLiteral(importArgument) && !ts.isNoSubstitutionTemplateLiteral(importArgument)) {
        unsupported('Dynamic import expressions in normalizedProgram require a string literal specifier.');
      }
      const specifier = importArgument.text;
      if (specifier.startsWith('./') || specifier.startsWith('../') || specifier.startsWith('/')) {
        unsupported('Dynamic relative import expressions are unsupported in normalizedProgram.');
      }
      const importLoc = nodeLocation(sourceFile, expression);
      return withLocation(sourceFile, expression, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(importLoc.line, importLoc.column, {
          kind: 'MemberAccessExpression',
          receiver: withSyntheticLocation(importLoc.line, importLoc.column, {
            kind: 'VariableExpression',
            name: 'Promise'
          }),
          member: 'resolve'
        }),
        arguments: [undefinedLiteral(importLoc.line, importLoc.column)]
      });
    }
    if (
      ts.isCallExpression(expression.expression)
      && ts.isIdentifier(expression.expression.expression)
      && expression.expression.expression.text === 'await'
      && expression.expression.arguments.length === 1
    ) {
      return withLocation(sourceFile, expression, {
        kind: 'AwaitExpression',
        expression: withLocation(sourceFile, expression, {
          kind: 'CallExpression',
          callee: normalizeExpression(ts, sourceFile, expression.expression.arguments[0]),
          arguments: expression.arguments.map((argument) => normalizeExpression(ts, sourceFile, argument))
        })
      });
    }
    const callLoc = nodeLocation(sourceFile, expression);
    const spreadSegments = spreadSegmentsFromCallArguments(
      ts,
      sourceFile,
      expression.arguments,
      callLoc.line,
      callLoc.column
    );
    if (spreadSegments !== null) {
      if (expression.questionDotToken) {
        unsupported('Optional call with spread arguments is unsupported in normalizedProgram.');
      }
      return withLocation(sourceFile, expression, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(callLoc.line, callLoc.column, {
          kind: 'VariableExpression',
          name: '__tsj_call_spread'
        }),
        arguments: [
          normalizeExpression(ts, sourceFile, expression.expression),
          ...spreadSegments
        ]
      });
    }
    const kind = expression.questionDotToken
      ? 'OptionalCallExpression'
      : 'CallExpression';
    return withLocation(sourceFile, expression, {
      kind,
      callee: normalizeExpression(ts, sourceFile, expression.expression),
      arguments: expression.arguments.map((argument) => normalizeExpression(ts, sourceFile, argument))
    });
  }
  if (ts.isNewExpression(expression)) {
    return withLocation(sourceFile, expression, {
      kind: 'NewExpression',
      constructor: normalizeExpression(ts, sourceFile, expression.expression),
      arguments: (expression.arguments || []).map((argument) => normalizeExpression(ts, sourceFile, argument))
    });
  }
  if (ts.isArrayLiteralExpression(expression)) {
    const arrayLoc = nodeLocation(sourceFile, expression);
    const spreadSegments = spreadSegmentsFromArrayElements(
      ts,
      sourceFile,
      expression.elements,
      arrayLoc.line,
      arrayLoc.column
    );
    if (spreadSegments !== null) {
      return withLocation(sourceFile, expression, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(arrayLoc.line, arrayLoc.column, {
          kind: 'VariableExpression',
          name: '__tsj_array_spread'
        }),
        arguments: spreadSegments
      });
    }
    return withLocation(sourceFile, expression, {
      kind: 'ArrayLiteralExpression',
      elements: expression.elements.map((element) => normalizeExpression(ts, sourceFile, element))
    });
  }
  if (ts.isObjectLiteralExpression(expression)) {
    const objectLoc = nodeLocation(sourceFile, expression);
    const hasObjectSpread = expression.properties.some((property) => ts.isSpreadAssignment(property));
    if (hasObjectSpread) {
      const spreadSegments = spreadSegmentsFromObjectProperties(
        ts,
        sourceFile,
        expression.properties,
        objectLoc.line,
        objectLoc.column
      );
      if (spreadSegments === null) {
        unsupported('Object literal spread fallback requires static object property names in normalizedProgram.');
      }
      return withLocation(sourceFile, expression, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(objectLoc.line, objectLoc.column, {
          kind: 'VariableExpression',
          name: '__tsj_object_spread'
        }),
        arguments: spreadSegments
      });
    }

    const entries = [];
    const orderedOperations = [];
    let hasDynamicObjectSemantics = false;
    for (const property of expression.properties) {
      const propertyLoc = nodeLocation(sourceFile, property);
      if (ts.isPropertyAssignment(property)) {
        const valueExpression = normalizeExpression(ts, sourceFile, property.initializer);
        const keyExpression = objectLiteralKeyExpression(ts, sourceFile, property.name);
        if (ts.isComputedPropertyName(property.name)) {
          hasDynamicObjectSemantics = true;
        } else {
          entries.push({
            key: propertyNameText(ts, sourceFile, property.name),
            value: valueExpression
          });
        }
        orderedOperations.push({
          kind: 'write',
          keyExpression,
          valueExpression,
          line: propertyLoc.line,
          column: propertyLoc.column
        });
        continue;
      }
      if (ts.isShorthandPropertyAssignment(property)) {
        const valueExpression = withLocation(sourceFile, property.name, { kind: 'VariableExpression', name: property.name.text });
        const keyExpression = stringLiteralExpression(
          property.name.text,
          nodeLocation(sourceFile, property.name).line,
          nodeLocation(sourceFile, property.name).column
        );
        entries.push({
          key: property.name.text,
          value: valueExpression
        });
        orderedOperations.push({
          kind: 'write',
          keyExpression,
          valueExpression,
          line: propertyLoc.line,
          column: propertyLoc.column
        });
        continue;
      }
      if (ts.isMethodDeclaration(property)) {
        const methodValue = normalizeObjectMethodValue(ts, sourceFile, property);
        const keyExpression = objectLiteralKeyExpression(ts, sourceFile, property.name);
        if (ts.isComputedPropertyName(property.name)) {
          hasDynamicObjectSemantics = true;
        } else {
          entries.push({
            key: propertyNameText(ts, sourceFile, property.name),
            value: methodValue
          });
        }
        orderedOperations.push({
          kind: 'write',
          keyExpression,
          valueExpression: methodValue,
          line: propertyLoc.line,
          column: propertyLoc.column
        });
        continue;
      }
      if (ts.isGetAccessorDeclaration(property)) {
        const keyExpression = objectLiteralKeyExpression(ts, sourceFile, property.name);
        const getterExpression = normalizeObjectAccessorValue(ts, sourceFile, property);
        hasDynamicObjectSemantics = true;
        orderedOperations.push({
          kind: 'accessor',
          keyExpression,
          getterExpression,
          setterExpression: undefinedLiteral(propertyLoc.line, propertyLoc.column),
          line: propertyLoc.line,
          column: propertyLoc.column
        });
        continue;
      }
      if (ts.isSetAccessorDeclaration(property)) {
        const keyExpression = objectLiteralKeyExpression(ts, sourceFile, property.name);
        const setterExpression = normalizeObjectAccessorValue(ts, sourceFile, property);
        hasDynamicObjectSemantics = true;
        orderedOperations.push({
          kind: 'accessor',
          keyExpression,
          getterExpression: undefinedLiteral(propertyLoc.line, propertyLoc.column),
          setterExpression,
          line: propertyLoc.line,
          column: propertyLoc.column
        });
        continue;
      }
      unsupported('Unsupported object literal member in normalizedProgram.');
    }

    if (hasDynamicObjectSemantics) {
      const objectName = nextSyntheticName('objLit');
      const objectExpression = variableExpression(objectName, objectLoc.line, objectLoc.column);
      const body = [
        withSyntheticLocation(objectLoc.line, objectLoc.column, {
          kind: 'VariableDeclaration',
          name: objectName,
          expression: withSyntheticLocation(objectLoc.line, objectLoc.column, {
            kind: 'ObjectLiteralExpression',
            entries: []
          })
        }),
        ...orderedOperations.map((operation) => withSyntheticLocation(operation.line, operation.column, {
          kind: 'ExpressionStatement',
          expression: operation.kind === 'accessor'
            ? createObjectAccessorDefineExpression(
              objectExpression,
              operation.keyExpression,
              operation.getterExpression,
              operation.setterExpression,
              operation.line,
              operation.column
            )
            : createObjectDynamicWriteExpression(
              objectExpression,
              operation.keyExpression,
              operation.valueExpression,
              operation.line,
              operation.column
            )
        })),
        withSyntheticLocation(objectLoc.line, objectLoc.column, {
          kind: 'ReturnStatement',
          expression: cloneNormalized(objectExpression)
        })
      ];
      return withLocation(sourceFile, expression, {
        kind: 'CallExpression',
        callee: withSyntheticLocation(objectLoc.line, objectLoc.column, {
          kind: 'FunctionExpression',
          parameters: [],
          body,
          async: false,
          thisMode: 'LEXICAL'
        }),
        arguments: []
      });
    }

    return withLocation(sourceFile, expression, {
      kind: 'ObjectLiteralExpression',
      entries
    });
  }
  if (ts.isFunctionExpression(expression) || ts.isArrowFunction(expression)) {
    const normalizedParameters = normalizeParameters(ts, sourceFile, expression.parameters);
    const bodyStatements = [];
    bodyStatements.push(...normalizedParameters.prologue);
    if (ts.isBlock(expression.body)) {
      for (const statement of expression.body.statements) {
        bodyStatements.push(...normalizeStatement(ts, sourceFile, statement));
      }
    } else {
      bodyStatements.push(withLocation(sourceFile, expression.body, {
        kind: 'ReturnStatement',
        expression: normalizeExpression(ts, sourceFile, expression.body)
      }));
    }
    return withLocation(sourceFile, expression, {
      kind: 'FunctionExpression',
      parameters: normalizedParameters.names,
      body: bodyStatements,
      async: hasModifier(ts, expression, ts.SyntaxKind.AsyncKeyword),
      generator: ts.isFunctionExpression(expression) && !!expression.asteriskToken,
      thisMode: ts.isArrowFunction(expression) ? 'LEXICAL' : 'DYNAMIC'
    });
  }
  if (ts.isClassExpression(expression)) {
    const expressionLoc = nodeLocation(sourceFile, expression);
    const classStatements = normalizeClassDeclaration(ts, sourceFile, expression);
    const classDeclarationStatement = classStatements.find((statementNode) => statementNode.kind === 'ClassDeclarationStatement');
    if (!classDeclarationStatement) {
      unsupported('Class expression normalization requires a class declaration statement.');
    }
    const className = classDeclarationStatement.declaration.name;
    const classReference = variableExpression(className, expressionLoc.line, expressionLoc.column);
    return withLocation(sourceFile, expression, {
      kind: 'CallExpression',
      callee: withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
        kind: 'FunctionExpression',
        parameters: [],
        body: [
          ...classStatements.map((statementNode) => cloneNormalized(statementNode)),
          withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
            kind: 'ReturnStatement',
            expression: cloneNormalized(classReference)
          })
        ],
        async: false,
        thisMode: 'LEXICAL'
      }),
      arguments: []
    });
  }
  if (ts.isConditionalExpression(expression)) {
    return withLocation(sourceFile, expression, {
      kind: 'ConditionalExpression',
      condition: normalizeExpression(ts, sourceFile, expression.condition),
      whenTrue: normalizeExpression(ts, sourceFile, expression.whenTrue),
      whenFalse: normalizeExpression(ts, sourceFile, expression.whenFalse)
    });
  }
  if (ts.isBinaryExpression(expression)) {
    const assignmentOperatorMap = new Map([
      [ts.SyntaxKind.EqualsToken, '='],
      [ts.SyntaxKind.PlusEqualsToken, '+='],
      [ts.SyntaxKind.MinusEqualsToken, '-='],
      [ts.SyntaxKind.AsteriskEqualsToken, '*='],
      [ts.SyntaxKind.SlashEqualsToken, '/='],
      [ts.SyntaxKind.PercentEqualsToken, '%='],
      [ts.SyntaxKind.AmpersandEqualsToken, '&='],
      [ts.SyntaxKind.BarEqualsToken, '|='],
      [ts.SyntaxKind.CaretEqualsToken, '^='],
      [ts.SyntaxKind.LessThanLessThanEqualsToken, '<<='],
      [ts.SyntaxKind.GreaterThanGreaterThanEqualsToken, '>>='],
      [ts.SyntaxKind.GreaterThanGreaterThanGreaterThanEqualsToken, '>>>='],
      [ts.SyntaxKind.AsteriskAsteriskEqualsToken, '**='],
      [ts.SyntaxKind.AmpersandAmpersandEqualsToken, '&&='],
      [ts.SyntaxKind.BarBarEqualsToken, '||='],
      [ts.SyntaxKind.QuestionQuestionEqualsToken, '??=']
    ]);
    const assignmentOperator = assignmentOperatorMap.get(expression.operatorToken.kind);
    if (assignmentOperator) {
      return withLocation(sourceFile, expression, {
        kind: 'AssignmentExpression',
        target: normalizeExpression(ts, sourceFile, expression.left),
        operator: assignmentOperator,
        expression: normalizeExpression(ts, sourceFile, expression.right)
      });
    }

    const operatorMap = new Map([
      [ts.SyntaxKind.PlusToken, '+'],
      [ts.SyntaxKind.MinusToken, '-'],
      [ts.SyntaxKind.AsteriskToken, '*'],
      [ts.SyntaxKind.SlashToken, '/'],
      [ts.SyntaxKind.PercentToken, '%'],
      [ts.SyntaxKind.LessThanToken, '<'],
      [ts.SyntaxKind.LessThanEqualsToken, '<='],
      [ts.SyntaxKind.GreaterThanToken, '>'],
      [ts.SyntaxKind.GreaterThanEqualsToken, '>='],
      [ts.SyntaxKind.EqualsEqualsToken, '=='],
      [ts.SyntaxKind.EqualsEqualsEqualsToken, '==='],
      [ts.SyntaxKind.ExclamationEqualsToken, '!='],
      [ts.SyntaxKind.ExclamationEqualsEqualsToken, '!=='],
      [ts.SyntaxKind.AmpersandAmpersandToken, '&&'],
      [ts.SyntaxKind.BarBarToken, '||'],
      [ts.SyntaxKind.QuestionQuestionToken, '??'],
      [ts.SyntaxKind.AsteriskAsteriskToken, '**'],
      [ts.SyntaxKind.BarToken, '|'],
      [ts.SyntaxKind.AmpersandToken, '&'],
      [ts.SyntaxKind.CaretToken, '^'],
      [ts.SyntaxKind.LessThanLessThanToken, '<<'],
      [ts.SyntaxKind.GreaterThanGreaterThanToken, '>>'],
      [ts.SyntaxKind.GreaterThanGreaterThanGreaterThanToken, '>>>'],
      [ts.SyntaxKind.CommaToken, ','],
      [ts.SyntaxKind.InKeyword, 'in'],
      [ts.SyntaxKind.InstanceOfKeyword, 'instanceof']
    ]);
    const operator = operatorMap.get(expression.operatorToken.kind);
    if (!operator) {
      unsupported('Unsupported binary operator in normalizedProgram.');
    }
    return withLocation(sourceFile, expression, {
      kind: 'BinaryExpression',
      left: normalizeExpression(ts, sourceFile, expression.left),
      operator,
      right: normalizeExpression(ts, sourceFile, expression.right)
    });
  }
  if (ts.isPostfixUnaryExpression && ts.isPostfixUnaryExpression(expression)) {
    const expressionLoc = nodeLocation(sourceFile, expression);
    if (expression.operator !== ts.SyntaxKind.PlusPlusToken && expression.operator !== ts.SyntaxKind.MinusMinusToken) {
      unsupported('Unsupported postfix unary operator in normalizedProgram.');
    }
    const target = normalizeExpression(ts, sourceFile, expression.operand);
    const tempName = nextSyntheticName('postfix');
    const incrementedExpression = withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
      kind: 'BinaryExpression',
      left: cloneNormalized(target),
      operator: expression.operator === ts.SyntaxKind.PlusPlusToken ? '+' : '-',
      right: withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
        kind: 'NumberLiteral',
        text: '1'
      })
    });
    return withLocation(sourceFile, expression, {
      kind: 'CallExpression',
      callee: withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
        kind: 'FunctionExpression',
        parameters: [],
        body: [
          withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
            kind: 'VariableDeclaration',
            name: tempName,
            expression: cloneNormalized(target)
          }),
          withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
            kind: 'AssignmentStatement',
            target: cloneNormalized(target),
            expression: incrementedExpression
          }),
          withSyntheticLocation(expressionLoc.line, expressionLoc.column, {
            kind: 'ReturnStatement',
            expression: variableExpression(tempName, expressionLoc.line, expressionLoc.column)
          })
        ],
        async: false,
        generator: false,
        thisMode: 'LEXICAL'
      }),
      arguments: []
    });
  }
  unsupported(`Unsupported expression kind in normalizedProgram: ${ts.SyntaxKind[expression.kind]}`);
}

function mergeNamespaceCapturedNames(existing, additional) {
  const merged = new Set(existing);
  for (const name of additional) {
    merged.add(name);
  }
  return [...merged];
}

function namespaceExportNamesFromModuleBody(ts, body) {
  if (!body || !ts.isModuleBlock(body)) {
    return [];
  }
  const names = [];
  for (const statement of body.statements) {
    if (!hasModifier(ts, statement, ts.SyntaxKind.ExportKeyword)) {
      continue;
    }
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        if (ts.isIdentifier(declaration.name)) {
          names.push(declaration.name.text);
        }
      }
      continue;
    }
    if (ts.isFunctionDeclaration(statement) && statement.name && ts.isIdentifier(statement.name)) {
      names.push(statement.name.text);
      continue;
    }
    if (ts.isModuleDeclaration(statement) && statement.name && ts.isIdentifier(statement.name)) {
      names.push(statement.name.text);
    }
  }
  return names;
}

function rewriteNamespaceCapturedReferences(value, rootNamespaceName, capturedNames) {
  if (!rootNamespaceName || !capturedNames || capturedNames.length === 0) {
    return value;
  }
  const capturedSet = new Set(capturedNames);
  return rewriteValue(value, (node) => {
    if (node.kind !== 'VariableExpression') {
      return node;
    }
    if (!capturedSet.has(node.name)) {
      return node;
    }
    const line = Number.isInteger(node.line) ? node.line : 1;
    const column = Number.isInteger(node.column) ? node.column : 1;
    return withSyntheticLocation(line, column, {
      kind: 'MemberAccessExpression',
      receiver: variableExpression(rootNamespaceName, line, column),
      member: node.name
    });
  });
}

function namespaceExportEntriesFromModuleBody(ts, sourceFile, body, rootNamespaceName = null, capturedNames = []) {
  if (!body) {
    return [];
  }
  if (ts.isModuleDeclaration(body)) {
    if (!body.name || !ts.isIdentifier(body.name)) {
      return [];
    }
    const nestedLoc = nodeLocation(sourceFile, body);
    const nextCapturedNames = mergeNamespaceCapturedNames(
      capturedNames,
      namespaceExportNamesFromModuleBody(ts, body.body)
    );
    return [{
      key: body.name.text,
      value: namespaceObjectLiteralFromModuleBody(
        ts,
        sourceFile,
        body.body,
        nestedLoc.line,
        nestedLoc.column,
        rootNamespaceName,
        nextCapturedNames
      )
    }];
  }
  if (!ts.isModuleBlock(body)) {
    return [];
  }
  const exportNamesInScope = namespaceExportNamesFromModuleBody(ts, body);
  const nextCapturedNames = mergeNamespaceCapturedNames(capturedNames, exportNamesInScope);
  const entries = [];
  for (const statement of body.statements) {
    if (!hasModifier(ts, statement, ts.SyntaxKind.ExportKeyword)) {
      continue;
    }
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        if (!ts.isIdentifier(declaration.name)) {
          continue;
        }
        const declarationLoc = nodeLocation(sourceFile, declaration);
        entries.push({
          key: declaration.name.text,
          value: declaration.initializer
            ? rewriteNamespaceCapturedReferences(
              normalizeExpression(ts, sourceFile, declaration.initializer),
              rootNamespaceName,
              capturedNames
            )
            : undefinedLiteral(declarationLoc.line, declarationLoc.column)
        });
      }
      continue;
    }
    if (ts.isFunctionDeclaration(statement)) {
      if (!statement.name || !ts.isIdentifier(statement.name) || !statement.body) {
        continue;
      }
      if (statement.asteriskToken) {
        unsupported('Generator namespace export functions are unsupported in normalizedProgram.');
      }
      const normalizedParameters = normalizeParameters(ts, sourceFile, statement.parameters);
      const functionValue = withLocation(sourceFile, statement, {
        kind: 'FunctionExpression',
        parameters: normalizedParameters.names,
        body: [
          ...normalizedParameters.prologue,
          ...statement.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
        ],
        async: hasModifier(ts, statement, ts.SyntaxKind.AsyncKeyword),
        thisMode: 'DYNAMIC'
      });
      entries.push({
        key: statement.name.text,
        value: rewriteNamespaceCapturedReferences(functionValue, rootNamespaceName, capturedNames)
      });
      continue;
    }
    if (ts.isModuleDeclaration(statement)) {
      if (!statement.name || !ts.isIdentifier(statement.name)) {
        continue;
      }
      const statementLoc = nodeLocation(sourceFile, statement);
      entries.push({
        key: statement.name.text,
        value: namespaceObjectLiteralFromModuleBody(
          ts,
          sourceFile,
          statement.body,
          statementLoc.line,
          statementLoc.column,
          rootNamespaceName,
          nextCapturedNames
        )
      });
    }
  }
  return entries;
}

function namespaceObjectLiteralFromModuleBody(
  ts,
  sourceFile,
  body,
  line,
  column,
  rootNamespaceName = null,
  capturedNames = []
) {
  return withSyntheticLocation(line, column, {
    kind: 'ObjectLiteralExpression',
    entries: namespaceExportEntriesFromModuleBody(ts, sourceFile, body, rootNamespaceName, capturedNames)
  });
}

function normalizeModuleDeclarationStatement(ts, sourceFile, statement) {
  if (hasModifier(ts, statement, ts.SyntaxKind.DeclareKeyword)) {
    return [];
  }
  if (!statement.name || !ts.isIdentifier(statement.name)) {
    return [];
  }
  const statementLoc = nodeLocation(sourceFile, statement);
  const namespaceTempName = nextSyntheticName(`${statement.name.text}_ns`);
  const namespaceTempExpression = variableExpression(namespaceTempName, statementLoc.line, statementLoc.column);
  const namespaceObjectExpression = namespaceObjectLiteralFromModuleBody(
    ts,
    sourceFile,
    statement.body,
    statementLoc.line,
    statementLoc.column,
    namespaceTempName,
    []
  );
  const namespaceBuilder = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'CallExpression',
    callee: withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'FunctionExpression',
      parameters: [],
      body: [
        withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'VariableDeclaration',
          name: namespaceTempName,
          expression: undefinedLiteral(statementLoc.line, statementLoc.column)
        }),
        withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'AssignmentStatement',
          target: cloneNormalized(namespaceTempExpression),
          expression: namespaceObjectExpression
        }),
        withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'ReturnStatement',
          expression: cloneNormalized(namespaceTempExpression)
        })
      ],
      async: false,
      generator: false,
      thisMode: 'LEXICAL'
    }),
    arguments: []
  });
  return [withLocation(sourceFile, statement, {
    kind: 'VariableDeclaration',
    name: statement.name.text,
    expression: namespaceBuilder
  })];
}

function normalizeStatement(ts, sourceFile, statement) {
  if (ts.isLabeledStatement(statement)) {
    if (!ts.isIdentifier(statement.label)) {
      unsupported('Labeled statements require identifier labels in normalizedProgram.');
    }
    const label = statement.label.text;
    let normalizedBody;
    if (ts.isForStatement(statement.statement)) {
      return wrapLabeledLoopLowering(
        sourceFile,
        statement,
        label,
        normalizeForStatement(ts, sourceFile, statement.statement, label)
      );
    }
    if (ts.isForOfStatement(statement.statement) || ts.isForInStatement(statement.statement)) {
      return wrapLabeledLoopLowering(
        sourceFile,
        statement,
        label,
        normalizeForOfOrInStatement(ts, sourceFile, statement.statement, label)
      );
    }
    if (ts.isDoStatement(statement.statement)) {
      return wrapLabeledLoopLowering(
        sourceFile,
        statement,
        label,
        normalizeDoStatement(ts, sourceFile, statement.statement, label)
      );
    }
    normalizedBody = normalizeStatement(ts, sourceFile, statement.statement);
    if (normalizedBody.length !== 1) {
      unsupported('Labeled statements in normalizedProgram require a single lowered statement.');
    }
    return [withLocation(sourceFile, statement, {
      kind: 'LabeledStatement',
      label,
      statement: normalizedBody[0]
    })];
  }
  if (ts.isWithStatement(statement)) {
    return [];
  }
  if (ts.isDebuggerStatement(statement)) {
    return [];
  }
  if (ts.isEmptyStatement(statement)) {
    return [];
  }
  if (ts.isImportEqualsDeclaration && ts.isImportEqualsDeclaration(statement)) {
    if (!ts.isIdentifier(statement.name)) {
      unsupported('Import equals declarations require identifier bindings in normalizedProgram.');
    }
    const statementLoc = nodeLocation(sourceFile, statement);
    return [withSyntheticLocation(statementLoc.line, statementLoc.column, {
      kind: 'VariableDeclaration',
      name: statement.name.text,
      expression: undefinedLiteral(statementLoc.line, statementLoc.column)
    })];
  }
  if (ts.isImportDeclaration(statement)) {
    if (!statement.importClause || statement.importClause.isTypeOnly) {
      return [];
    }
    const importLoc = nodeLocation(sourceFile, statement);
    const statements = [];
    if (statement.importClause.name && ts.isIdentifier(statement.importClause.name)) {
      statements.push(withSyntheticLocation(importLoc.line, importLoc.column, {
        kind: 'VariableDeclaration',
        name: statement.importClause.name.text,
        expression: undefinedLiteral(importLoc.line, importLoc.column)
      }));
    }
    if (statement.importClause.namedBindings) {
      if (ts.isNamespaceImport(statement.importClause.namedBindings)) {
        statements.push(withSyntheticLocation(importLoc.line, importLoc.column, {
          kind: 'VariableDeclaration',
          name: statement.importClause.namedBindings.name.text,
          expression: objectLiteralFromEntries([], importLoc.line, importLoc.column)
        }));
      } else if (ts.isNamedImports(statement.importClause.namedBindings)) {
        for (const element of statement.importClause.namedBindings.elements) {
          if (element.isTypeOnly) {
            continue;
          }
          statements.push(withSyntheticLocation(importLoc.line, importLoc.column, {
            kind: 'VariableDeclaration',
            name: element.name.text,
            expression: undefinedLiteral(importLoc.line, importLoc.column)
          }));
        }
      }
    }
    return statements;
  }
  if (ts.isTypeAliasDeclaration(statement) || ts.isInterfaceDeclaration(statement)) {
    return [];
  }
  if (ts.isEnumDeclaration(statement)) {
    if (!statement.name || !ts.isIdentifier(statement.name)) {
      unsupported('Enum declarations in normalizedProgram require identifier names.');
    }
    const statementLoc = nodeLocation(sourceFile, statement);
    const entries = [];
    let nextNumericValue = 0;
    let hasNumericValue = true;
    for (const member of statement.members) {
      let memberName;
      if (ts.isIdentifier(member.name) || ts.isStringLiteral(member.name) || ts.isNumericLiteral(member.name)) {
        memberName = member.name.text;
      } else {
        unsupported('Enum member names in normalizedProgram must be identifier/string/numeric literals.');
      }
      let valueExpression;
      if (member.initializer) {
        valueExpression = normalizeExpression(ts, sourceFile, member.initializer);
      } else if (hasNumericValue) {
        valueExpression = withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'NumberLiteral',
          text: String(nextNumericValue)
        });
      } else {
        valueExpression = withSyntheticLocation(statementLoc.line, statementLoc.column, {
          kind: 'UndefinedLiteral'
        });
      }
      entries.push({
        key: memberName,
        value: valueExpression
      });
      if (valueExpression.kind === 'NumberLiteral') {
        const parsed = Number.parseInt(valueExpression.text, 10);
        if (Number.isFinite(parsed)) {
          entries.push({
            key: String(parsed),
            value: withSyntheticLocation(statementLoc.line, statementLoc.column, {
              kind: 'StringLiteral',
              text: memberName
            })
          });
          nextNumericValue = parsed + 1;
          hasNumericValue = true;
        } else {
          hasNumericValue = false;
        }
      } else {
        hasNumericValue = false;
      }
    }
    return [withLocation(sourceFile, statement, {
      kind: 'VariableDeclaration',
      name: statement.name.text,
      expression: withSyntheticLocation(statementLoc.line, statementLoc.column, {
        kind: 'ObjectLiteralExpression',
        entries
      })
    })];
  }
  if (ts.isExportDeclaration(statement)
    || ts.isExportAssignment(statement)
    || (ts.isNamespaceExportDeclaration && ts.isNamespaceExportDeclaration(statement))) {
    return [];
  }
  if (ts.isModuleDeclaration(statement)) {
    return normalizeModuleDeclarationStatement(ts, sourceFile, statement);
  }
  if (ts.isEnumDeclaration(statement)) {
    return [];
  }
  if (ts.isVariableStatement(statement)) {
    const statements = [];
    for (const declaration of statement.declarationList.declarations) {
      if (ts.isObjectBindingPattern(declaration.name) || ts.isArrayBindingPattern(declaration.name)) {
        statements.push(...normalizeDestructuringVariableDeclaration(ts, sourceFile, declaration));
        continue;
      }
      if (!ts.isIdentifier(declaration.name)) {
        unsupported('Variable declarations in normalizedProgram require identifier + initializer or supported destructuring.');
      }
      const declarationLoc = nodeLocation(sourceFile, declaration);
      statements.push(withLocation(sourceFile, declaration, {
        kind: 'VariableDeclaration',
        name: declaration.name.text,
        expression: declaration.initializer
          ? normalizeExpression(ts, sourceFile, declaration.initializer)
          : undefinedLiteral(declarationLoc.line, declarationLoc.column)
      }));
    }
    return statements;
  }
  if (ts.isFunctionDeclaration(statement)) {
    if (!statement.name) {
      unsupported('Function declarations in normalizedProgram require name + body.');
    }
    if (!statement.body) {
      return [];
    }
    const normalizedParameters = normalizeParameters(ts, sourceFile, statement.parameters);
    return [withLocation(sourceFile, statement, {
      kind: 'FunctionDeclarationStatement',
      declaration: {
        name: statement.name.text,
        parameters: normalizedParameters.names,
        body: [
          ...normalizedParameters.prologue,
          ...statement.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
        ],
        async: hasModifier(ts, statement, ts.SyntaxKind.AsyncKeyword),
        generator: !!statement.asteriskToken
      }
    })];
  }
  if (ts.isClassDeclaration(statement)) {
    return normalizeClassDeclaration(ts, sourceFile, statement);
  }
  if (ts.isIfStatement(statement)) {
    const thenBlock = ts.isBlock(statement.thenStatement)
      ? statement.thenStatement.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
      : normalizeStatement(ts, sourceFile, statement.thenStatement);
    const elseBlock = statement.elseStatement
      ? (ts.isBlock(statement.elseStatement)
        ? statement.elseStatement.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
        : normalizeStatement(ts, sourceFile, statement.elseStatement))
      : [];
    return [withLocation(sourceFile, statement, {
      kind: 'IfStatement',
      condition: normalizeExpression(ts, sourceFile, statement.expression),
      thenBlock,
      elseBlock
    })];
  }
  if (ts.isWhileStatement(statement)) {
    return [withLocation(sourceFile, statement, {
      kind: 'WhileStatement',
      condition: normalizeExpression(ts, sourceFile, statement.expression),
      body: normalizeBlockOrSingleStatement(ts, sourceFile, statement.statement)
    })];
  }
  if (ts.isDoStatement(statement)) {
    return [normalizeDoStatement(ts, sourceFile, statement, null)];
  }
  if (ts.isForStatement(statement)) {
    return [normalizeForStatement(ts, sourceFile, statement, null)];
  }
  if (ts.isForOfStatement(statement) || ts.isForInStatement(statement)) {
    return [normalizeForOfOrInStatement(ts, sourceFile, statement, null)];
  }
  if (ts.isSwitchStatement(statement)) {
    return [normalizeSwitchStatement(ts, sourceFile, statement)];
  }
  if (ts.isTryStatement(statement)) {
    if (!ts.isBlock(statement.tryBlock)) {
      unsupported('Try block must be a block in normalizedProgram.');
    }
    const statementLoc = nodeLocation(sourceFile, statement);
    const catchBinding = statement.catchClause && statement.catchClause.variableDeclaration
      && ts.isIdentifier(statement.catchClause.variableDeclaration.name)
      ? statement.catchClause.variableDeclaration.name.text
      : null;
    const catchBlock = statement.catchClause
      ? statement.catchClause.block.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
      : [];
    if (statement.catchClause && catchBlock.length === 0) {
      catchBlock.push(emptyTryClauseNoopStatement(statementLoc.line, statementLoc.column));
    }
    const finallyBlock = statement.finallyBlock
      ? statement.finallyBlock.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
      : [];
    if (statement.finallyBlock && finallyBlock.length === 0) {
      finallyBlock.push(emptyTryClauseNoopStatement(statementLoc.line, statementLoc.column));
    }
    return [withLocation(sourceFile, statement, {
      kind: 'TryStatement',
      tryBlock: statement.tryBlock.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner)),
      catchBinding,
      catchBlock,
      finallyBlock
    })];
  }
  if (ts.isBreakStatement(statement)) {
    return [withLocation(sourceFile, statement, {
      kind: 'BreakStatement',
      label: statement.label && ts.isIdentifier(statement.label) ? statement.label.text : null
    })];
  }
  if (ts.isContinueStatement(statement)) {
    return [withLocation(sourceFile, statement, {
      kind: 'ContinueStatement',
      label: statement.label && ts.isIdentifier(statement.label) ? statement.label.text : null
    })];
  }
  if (ts.isReturnStatement(statement)) {
    const statementLoc = nodeLocation(sourceFile, statement);
    return [withLocation(sourceFile, statement, {
      kind: 'ReturnStatement',
      expression: statement.expression
        ? normalizeExpression(ts, sourceFile, statement.expression)
        : undefinedLiteral(statementLoc.line, statementLoc.column)
    })];
  }
  if (ts.isThrowStatement(statement)) {
    return [withLocation(sourceFile, statement, {
      kind: 'ThrowStatement',
      expression: normalizeExpression(ts, sourceFile, statement.expression)
    })];
  }
  if (ts.isExpressionStatement(statement)) {
    const expression = statement.expression;
    const unwrappedExpression = unwrapParenthesizedExpression(ts, expression);
    if (ts.isCallExpression(expression) && expression.expression.kind === ts.SyntaxKind.SuperKeyword) {
      return [withLocation(sourceFile, statement, {
        kind: 'SuperCallStatement',
        arguments: expression.arguments.map((argument) => normalizeExpression(ts, sourceFile, argument))
      })];
    }
    if (ts.isBinaryExpression(unwrappedExpression) && unwrappedExpression.operatorToken.kind === ts.SyntaxKind.EqualsToken) {
      if (
        ts.isArrayLiteralExpression(unwrappedExpression.left)
        || ts.isObjectLiteralExpression(unwrappedExpression.left)
        || ts.isParenthesizedExpression(unwrappedExpression.left)
      ) {
        return normalizeDestructuringAssignmentStatement(ts, sourceFile, statement, unwrappedExpression);
      }
      return [withLocation(sourceFile, statement, {
        kind: 'AssignmentStatement',
        target: normalizeExpression(ts, sourceFile, unwrappedExpression.left),
        expression: normalizeExpression(ts, sourceFile, unwrappedExpression.right)
      })];
    }
    if (ts.isCallExpression(expression)
      && ts.isPropertyAccessExpression(expression.expression)
      && ts.isIdentifier(expression.expression.expression)
      && expression.expression.expression.text === 'console'
      && expression.expression.name.text === 'log'
      && expression.arguments.length === 1) {
      return [withLocation(sourceFile, statement, {
        kind: 'ConsoleLogStatement',
        expression: normalizeExpression(ts, sourceFile, expression.arguments[0])
      })];
    }
    return [withLocation(sourceFile, statement, {
      kind: 'ExpressionStatement',
      expression: normalizeExpression(ts, sourceFile, expression)
    })];
  }
  unsupported(`Unsupported statement kind in normalizedProgram: ${ts.SyntaxKind[statement.kind]}`);
}

function normalizeProgram(ts, sourceFile) {
  try {
    return {
      kind: 'Program',
      statements: sourceFile.statements.flatMap((statement) => normalizeStatement(ts, sourceFile, statement))
    };
  } catch (error) {
    if (error instanceof NormalizationError) {
      return null;
    }
    throw error;
  }
}

function collectAstNodes(ts, sourceFile) {
  const nodes = [];

  function visit(node) {
    const start = node.getStart(sourceFile, false);
    const end = node.getEnd();
    const startPosition = sourceFile.getLineAndCharacterOfPosition(start);
    const endPosition = sourceFile.getLineAndCharacterOfPosition(end);
    nodes.push({
      kind: ts.SyntaxKind[node.kind] || `UnknownKind${node.kind}`,
      line: startPosition.line + 1,
      column: startPosition.character + 1,
      endLine: endPosition.line + 1,
      endColumn: endPosition.character + 1
    });
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return nodes;
}

function tokenize(ts, sourcePath, sourceText) {
  const sourceFile = ts.createSourceFile(sourcePath, sourceText, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const diagnostics = sourceFile.parseDiagnostics.map((diagnostic) => diagnosticToJson(ts, sourceFile, diagnostic));
  const astNodes = collectAstNodes(ts, sourceFile);
  const normalizedProgram = normalizeProgram(ts, sourceFile);
  if (diagnostics.length > 0) {
    return {
      schemaVersion: SCHEMA_VERSION,
      diagnostics,
      tokens: [],
      astNodes,
      normalizedProgram
    };
  }

  const scanner = ts.createScanner(ts.ScriptTarget.Latest, true, ts.LanguageVariant.Standard, sourceText);
  const tokens = [];
  while (true) {
    const tokenKind = scanner.scan();
    if (tokenKind === ts.SyntaxKind.EndOfFileToken) {
      break;
    }
    const tokenPos = scanner.getTokenPos();
    const tokenText = scanner.getTokenText();
    const position = sourceFile.getLineAndCharacterOfPosition(tokenPos);
    tokens.push({
      type: tokenTypeName(ts, tokenKind, tokenText),
      text: tokenTextValue(ts, scanner, tokenKind, tokenText),
      line: position.line + 1,
      column: position.character + 1
    });
  }
  const eofPosition = sourceFile.getLineAndCharacterOfPosition(sourceText.length);
  tokens.push({
    type: 'EOF',
    text: '',
    line: eofPosition.line + 1,
    column: eofPosition.character + 1
  });

  return {
    schemaVersion: SCHEMA_VERSION,
    diagnostics: [],
    tokens,
    astNodes,
    normalizedProgram
  };
}

function main() {
  const ts = loadTypeScript();
  const sourceArg = process.argv[2];
  if (!sourceArg) {
    throw new Error('Usage: node emit-backend-tokens.cjs <source-file>');
  }
  const sourcePath = path.resolve(sourceArg);
  if (!fs.existsSync(sourcePath)) {
    throw new Error(`Source file not found: ${sourcePath}`);
  }
  const sourceText = fs.readFileSync(sourcePath, 'utf8');
  const payload = tokenize(ts, sourcePath, sourceText);
  process.stdout.write(JSON.stringify(payload));
}

try {
  main();
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(message + '\n');
  process.exit(1);
}
