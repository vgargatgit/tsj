'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const SCHEMA_VERSION = 'tsj-backend-token-v1';
const KEYWORDS = new Set([
  'function', 'const', 'let', 'var', 'if', 'else', 'while', 'return',
  'true', 'false', 'null', 'for', 'export', 'import', 'from',
  'class', 'extends', 'this', 'super', 'new', 'undefined',
  'async', 'await', 'throw', 'delete', 'break', 'continue', 'do'
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

function rewriteCurrentLoopContinueStatements(statements, replaceContinue) {
  return statements.flatMap((statement) => {
    if (statement.kind === 'ContinueStatement') {
      return replaceContinue(statement);
    }
    if (statement.kind === 'WhileStatement') {
      return [statement];
    }
    if (statement.kind === 'IfStatement') {
      return [{
        ...statement,
        thenBlock: rewriteCurrentLoopContinueStatements(statement.thenBlock, replaceContinue),
        elseBlock: rewriteCurrentLoopContinueStatements(statement.elseBlock, replaceContinue)
      }];
    }
    if (statement.kind === 'TryStatement') {
      return [{
        ...statement,
        tryBlock: rewriteCurrentLoopContinueStatements(statement.tryBlock, replaceContinue),
        catchBlock: rewriteCurrentLoopContinueStatements(statement.catchBlock, replaceContinue),
        finallyBlock: rewriteCurrentLoopContinueStatements(statement.finallyBlock, replaceContinue)
      }];
    }
    return [statement];
  });
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
    if (initializer.declarations.length !== 1) {
      unsupported('For-loop initializer supports exactly one declaration in TSJ-59a subset.');
    }
    const declaration = initializer.declarations[0];
    if (ts.isObjectBindingPattern(declaration.name) || ts.isArrayBindingPattern(declaration.name)) {
      return normalizeDestructuringVariableDeclaration(
        ts,
        sourceFile,
        declaration
      );
    }
    if (!ts.isIdentifier(declaration.name)) {
      unsupported('For-loop declaration initializers require identifier bindings or destructuring in normalizedProgram.');
    }
    const initializerExpression = declaration.initializer
      ? normalizeExpression(ts, sourceFile, declaration.initializer)
      : withLocation(sourceFile, declaration, { kind: 'UndefinedLiteral' });
    return [withLocation(sourceFile, declaration, {
      kind: 'VariableDeclaration',
      name: declaration.name.text,
      expression: initializerExpression
    })];
  }
  return [normalizeExpressionAsStatement(ts, sourceFile, initializer)];
}

function normalizeForStatement(ts, sourceFile, statement) {
  const statementLoc = nodeLocation(sourceFile, statement);
  const initStatements = normalizeForInitializer(ts, sourceFile, statement.initializer);
  const condition = statement.condition
    ? normalizeExpression(ts, sourceFile, statement.condition)
    : withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true });
  const updateStatement = statement.incrementor
    ? normalizeExpressionAsStatement(ts, sourceFile, statement.incrementor)
    : null;
  let body = normalizeBlockOrSingleStatement(ts, sourceFile, statement.statement);

  if (updateStatement !== null) {
    body = rewriteCurrentLoopContinueStatements(body, (continueStatement) => ([
      cloneNormalized(updateStatement),
      continueStatement
    ]));
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

function normalizeForOfOrInStatement(ts, sourceFile, statement) {
  if (ts.isForOfStatement(statement) && statement.awaitModifier) {
    unsupported('for await...of is unsupported in normalizedProgram.');
  }
  const statementLoc = nodeLocation(sourceFile, statement);
  const valuesName = nextSyntheticName(ts.isForOfStatement(statement) ? 'forOfValues' : 'forInKeys');
  const indexName = nextSyntheticName('forIndex');

  const valuesExpression = variableExpression(valuesName, statementLoc.line, statementLoc.column);
  const indexExpression = variableExpression(indexName, statementLoc.line, statementLoc.column);

  const collectionHelperName = ts.isForOfStatement(statement)
    ? '__tsj_for_of_values'
    : '__tsj_for_in_keys';
  const collectionExpression = syntheticCallExpression(
    collectionHelperName,
    [normalizeExpression(ts, sourceFile, statement.expression)],
    statementLoc.line,
    statementLoc.column
  );
  const currentValueExpression = syntheticCallExpression(
    '__tsj_index_read',
    [cloneNormalized(valuesExpression), cloneNormalized(indexExpression)],
    statementLoc.line,
    statementLoc.column
  );

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
  ]));
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

function normalizeSwitchClauseBody(ts, sourceFile, clause) {
  const body = clause.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner));
  if (body.length === 0) {
    return [];
  }
  const last = body[body.length - 1];
  if (last.kind === 'BreakStatement') {
    return body.slice(0, -1);
  }
  return body;
}

function normalizeSwitchStatement(ts, sourceFile, statement) {
  const statementLoc = nodeLocation(sourceFile, statement);
  const switchValueName = nextSyntheticName('__tsj_switch_value');
  const switchValueExpression = withSyntheticLocation(statementLoc.line, statementLoc.column, {
    kind: 'VariableExpression',
    name: switchValueName
  });

  const switchValueDeclaration = withLocation(sourceFile, statement, {
    kind: 'VariableDeclaration',
    name: switchValueName,
    expression: normalizeExpression(ts, sourceFile, statement.expression)
  });

  const loopBody = [];
  let defaultBody = [];
  let seenDefault = false;

  for (const clause of statement.caseBlock.clauses) {
    if (ts.isCaseClause(clause)) {
      const clauseLoc = nodeLocation(sourceFile, clause);
      const thenBlock = normalizeSwitchClauseBody(ts, sourceFile, clause);
      const lastThen = thenBlock.length > 0 ? thenBlock[thenBlock.length - 1] : null;
      const clauseTerminates = lastThen && (lastThen.kind === 'ReturnStatement' || lastThen.kind === 'ThrowStatement');
      if (!clauseTerminates) {
        thenBlock.push(withSyntheticLocation(clauseLoc.line, clauseLoc.column, { kind: 'BreakStatement' }));
      }
      loopBody.push(withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
        kind: 'IfStatement',
        condition: withSyntheticLocation(clauseLoc.line, clauseLoc.column, {
          kind: 'BinaryExpression',
          left: cloneNormalized(switchValueExpression),
          operator: '===',
          right: normalizeExpression(ts, sourceFile, clause.expression)
        }),
        thenBlock,
        elseBlock: []
      }));
      continue;
    }
    if (seenDefault) {
      unsupported('Switch statements in TSJ-59a subset support at most one default clause.');
    }
    seenDefault = true;
    defaultBody = normalizeSwitchClauseBody(ts, sourceFile, clause);
  }

  loopBody.push(...defaultBody);
  const lastDefault = defaultBody.length > 0 ? defaultBody[defaultBody.length - 1] : null;
  const defaultTerminates = lastDefault && (lastDefault.kind === 'ReturnStatement' || lastDefault.kind === 'ThrowStatement');
  if (!defaultTerminates) {
    loopBody.push(withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BreakStatement' }));
  }

  const dispatchLoop = withLocation(sourceFile, statement, {
    kind: 'WhileStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true }),
    body: loopBody
  });

  return withLocation(sourceFile, statement, {
    kind: 'IfStatement',
    condition: withSyntheticLocation(statementLoc.line, statementLoc.column, { kind: 'BooleanLiteral', value: true }),
    thenBlock: [switchValueDeclaration, dispatchLoop],
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

function propertyNameText(ts, propertyName) {
  if (ts.isIdentifier(propertyName) || ts.isStringLiteral(propertyName) || ts.isNumericLiteral(propertyName)) {
    return propertyName.text;
  }
  unsupported('Destructuring supports only identifier/string/numeric property names in normalizedProgram.');
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
      if (element.initializer) {
        unsupported('Destructuring default values are unsupported in normalizedProgram.');
      }
      let key;
      if (element.propertyName) {
        key = propertyNameText(ts, element.propertyName);
      } else if (ts.isIdentifier(element.name)) {
        key = element.name.text;
      } else {
        unsupported('Object destructuring requires explicit property names for nested bindings.');
      }
      const valueExpression = memberAccessExpression(sourceExpression, key, line, column);
      statements.push(...expandBindingNameToStatements(
        ts,
        sourceFile,
        element.name,
        valueExpression,
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
        unsupported('Array rest destructuring is unsupported in normalizedProgram.');
      }
      if (element.initializer) {
        unsupported('Destructuring default values are unsupported in normalizedProgram.');
      }
      const valueExpression = memberAccessExpression(sourceExpression, String(index), line, column);
      statements.push(...expandBindingNameToStatements(
        ts,
        sourceFile,
        element.name,
        valueExpression,
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
        const key = propertyNameText(ts, property.name);
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
      if (!ts.isIdentifier(property.name) && !ts.isStringLiteral(property.name) && !ts.isNumericLiteral(property.name)) {
        unsupported('Only identifier/string/numeric object keys are supported in normalizedProgram.');
      }
      chunk.push({
        key: propertyNameText(ts, property.name),
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
      // Keep normalization permissive for advanced object method forms in torture fixtures.
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
  if (!statement.name || !ts.isIdentifier(statement.name)) {
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
  const classReference = variableExpression(statement.name.text, classLoc.line, classLoc.column);
  const prototypeReference = memberAccessExpression(classReference, 'prototype', classLoc.line, classLoc.column);

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
      const initializerExpression = member.initializer
        ? normalizeExpression(ts, sourceFile, member.initializer)
        : withSyntheticLocation(memberLoc.line, memberLoc.column, { kind: 'UndefinedLiteral' });
      const assignmentStatement = createClassPropertyWriteStatement(
        hasModifier(ts, member, ts.SyntaxKind.StaticKeyword)
          ? classReference
          : withSyntheticLocation(memberLoc.line, memberLoc.column, { kind: 'ThisExpression' }),
        keySpec,
        initializerExpression,
        memberLoc.line,
        memberLoc.column
      );
      if (hasModifier(ts, member, ts.SyntaxKind.StaticKeyword)) {
        postClassStatements.push(assignmentStatement);
      } else {
        instanceFieldInitializers.push(assignmentStatement);
        if (keySpec.keyExpression === null) {
          fieldNames.push(keySpec.literalKey);
        }
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
      constructorMethod = {
        name: 'constructor',
        parameters: normalizedParameters.names,
        body: [
          ...normalizedParameters.prologue,
          ...member.body.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
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
      if (hasModifier(ts, member, ts.SyntaxKind.StaticKeyword)) {
        postClassStatements.push(createClassPropertyWriteStatement(
          classReference,
          keySpec,
          classMethodFunctionExpression(methodDefinition, methodLoc.line, methodLoc.column),
          methodLoc.line,
          methodLoc.column
        ));
        continue;
      }
      if (keySpec.keyExpression === null) {
        methods.push({
          name: keySpec.literalKey,
          parameters: methodDefinition.parameters,
          body: methodDefinition.body,
          async: methodDefinition.async
        });
        continue;
      }
      postClassStatements.push(createClassPropertyWriteStatement(
        prototypeReference,
        keySpec,
        classMethodFunctionExpression(methodDefinition, methodLoc.line, methodLoc.column),
        methodLoc.line,
        methodLoc.column
      ));
      continue;
    }
    if (ts.isGetAccessorDeclaration(member) || ts.isSetAccessorDeclaration(member)) {
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
      name: statement.name.text,
      superClassName,
      fieldNames,
      constructorMethod,
      methods
    }
  });
  return [classDeclarationStatement, ...postClassStatements];
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
    return withLocation(sourceFile, expression, { kind: 'StringLiteral', text: 'undefined' });
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
  if (ts.isYieldExpression && ts.isYieldExpression(expression)) {
    if (!expression.expression) {
      const expressionLoc = nodeLocation(sourceFile, expression);
      return undefinedLiteral(expressionLoc.line, expressionLoc.column);
    }
    return normalizeExpression(ts, sourceFile, expression.expression);
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
    const operator = expression.operator === ts.SyntaxKind.MinusToken
      ? '-'
      : expression.operator === ts.SyntaxKind.ExclamationToken
        ? '!'
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
    if (argumentExpression && ts.isStringLiteral(argumentExpression)) {
      return withLocation(sourceFile, expression, {
        kind: 'MemberAccessExpression',
        receiver: normalizeExpression(ts, sourceFile, expression.expression),
        member: argumentExpression.text
      });
    }
    if (argumentExpression && ts.isNoSubstitutionTemplateLiteral(argumentExpression)) {
      return withLocation(sourceFile, expression, {
        kind: 'MemberAccessExpression',
        receiver: normalizeExpression(ts, sourceFile, expression.expression),
        member: argumentExpression.text
      });
    }
    if (argumentExpression && ts.isNumericLiteral(argumentExpression)) {
      return withLocation(sourceFile, expression, {
        kind: 'MemberAccessExpression',
        receiver: normalizeExpression(ts, sourceFile, expression.expression),
        member: argumentExpression.text
      });
    }
    return withLocation(sourceFile, expression, {
      kind: 'CallExpression',
      callee: withLocation(sourceFile, expression.expression, {
        kind: 'VariableExpression',
        name: '__tsj_index_read'
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
    if (expression.expression.kind === ts.SyntaxKind.ImportKeyword) {
      const expressionLoc = nodeLocation(sourceFile, expression);
      return undefinedLiteral(expressionLoc.line, expressionLoc.column);
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
    const spreadSegments = spreadSegmentsFromObjectProperties(
      ts,
      sourceFile,
      expression.properties,
      objectLoc.line,
      objectLoc.column
    );
    if (spreadSegments !== null) {
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
    for (const property of expression.properties) {
      if (ts.isPropertyAssignment(property)) {
        if (!ts.isIdentifier(property.name) && !ts.isStringLiteral(property.name) && !ts.isNumericLiteral(property.name)) {
          unsupported('Only identifier/string/numeric object keys are supported in normalizedProgram.');
        }
        entries.push({
          key: propertyNameText(ts, property.name),
          value: normalizeExpression(ts, sourceFile, property.initializer)
        });
        continue;
      }
      if (ts.isShorthandPropertyAssignment(property)) {
        entries.push({
          key: property.name.text,
          value: withLocation(sourceFile, property.name, { kind: 'VariableExpression', name: property.name.text })
        });
        continue;
      }
      if (ts.isMethodDeclaration(property)) {
        continue;
      }
      unsupported('Unsupported object literal member in normalizedProgram.');
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
      thisMode: ts.isArrowFunction(expression) ? 'LEXICAL' : 'DYNAMIC'
    });
  }
  if (ts.isClassExpression(expression)) {
    const expressionLoc = nodeLocation(sourceFile, expression);
    return undefinedLiteral(expressionLoc.line, expressionLoc.column);
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
      [ts.SyntaxKind.AsteriskAsteriskToken, '*'],
      [ts.SyntaxKind.BarToken, '+'],
      [ts.SyntaxKind.AmpersandToken, '+'],
      [ts.SyntaxKind.CaretToken, '+'],
      [ts.SyntaxKind.LessThanLessThanToken, '+'],
      [ts.SyntaxKind.GreaterThanGreaterThanToken, '+'],
      [ts.SyntaxKind.GreaterThanGreaterThanGreaterThanToken, '+'],
      [ts.SyntaxKind.InKeyword, '==='],
      [ts.SyntaxKind.InstanceOfKeyword, '===']
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
    const target = normalizeExpression(ts, sourceFile, expression.operand);
    const expressionLoc = nodeLocation(sourceFile, expression);
    if (expression.operator !== ts.SyntaxKind.PlusPlusToken && expression.operator !== ts.SyntaxKind.MinusMinusToken) {
      unsupported('Unsupported postfix unary operator in normalizedProgram.');
    }
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
  unsupported(`Unsupported expression kind in normalizedProgram: ${ts.SyntaxKind[expression.kind]}`);
}

function namespaceExportEntriesFromModuleBody(ts, sourceFile, body) {
  if (!body) {
    return [];
  }
  if (ts.isModuleDeclaration(body)) {
    if (!body.name || !ts.isIdentifier(body.name)) {
      return [];
    }
    const nestedLoc = nodeLocation(sourceFile, body);
    return [{
      key: body.name.text,
      value: namespaceObjectLiteralFromModuleBody(ts, sourceFile, body.body, nestedLoc.line, nestedLoc.column)
    }];
  }
  if (!ts.isModuleBlock(body)) {
    return [];
  }
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
            ? normalizeExpression(ts, sourceFile, declaration.initializer)
            : undefinedLiteral(declarationLoc.line, declarationLoc.column)
        });
      }
      continue;
    }
    if (ts.isModuleDeclaration(statement)) {
      if (!statement.name || !ts.isIdentifier(statement.name)) {
        continue;
      }
      const statementLoc = nodeLocation(sourceFile, statement);
      entries.push({
        key: statement.name.text,
        value: namespaceObjectLiteralFromModuleBody(ts, sourceFile, statement.body, statementLoc.line, statementLoc.column)
      });
    }
  }
  return entries;
}

function namespaceObjectLiteralFromModuleBody(ts, sourceFile, body, line, column) {
  return withSyntheticLocation(line, column, {
    kind: 'ObjectLiteralExpression',
    entries: namespaceExportEntriesFromModuleBody(ts, sourceFile, body)
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
  return [withLocation(sourceFile, statement, {
    kind: 'VariableDeclaration',
    name: statement.name.text,
    expression: namespaceObjectLiteralFromModuleBody(
      ts,
      sourceFile,
      statement.body,
      statementLoc.line,
      statementLoc.column
    )
  })];
}

function normalizeStatement(ts, sourceFile, statement) {
  if (ts.isLabeledStatement(statement)) {
    return [];
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
    if (statement.declarationList.declarations.length !== 1) {
      unsupported('Only single variable declarations are supported in normalizedProgram.');
    }
    const declaration = statement.declarationList.declarations[0];
    if (ts.isObjectBindingPattern(declaration.name) || ts.isArrayBindingPattern(declaration.name)) {
      return normalizeDestructuringVariableDeclaration(ts, sourceFile, declaration);
    }
    if (!ts.isIdentifier(declaration.name)) {
      unsupported('Variable declarations in normalizedProgram require identifier + initializer or supported destructuring.');
    }
    const declarationLoc = nodeLocation(sourceFile, declaration);
    return [withLocation(sourceFile, statement, {
      kind: 'VariableDeclaration',
      name: declaration.name.text,
      expression: declaration.initializer
        ? normalizeExpression(ts, sourceFile, declaration.initializer)
        : undefinedLiteral(declarationLoc.line, declarationLoc.column)
    })];
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
        async: hasModifier(ts, statement, ts.SyntaxKind.AsyncKeyword)
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
    const statementLoc = nodeLocation(sourceFile, statement);
    const condition = normalizeExpression(ts, sourceFile, statement.expression);
    let body = normalizeBlockOrSingleStatement(ts, sourceFile, statement.statement);
    body = rewriteCurrentLoopContinueStatements(body, (continueStatement) => ([
      createLoopExitGuard(continueStatement.line, continueStatement.column, condition),
      continueStatement
    ]));
    body.push(createLoopExitGuard(statementLoc.line, statementLoc.column, condition));
    return [withLocation(sourceFile, statement, {
      kind: 'WhileStatement',
      condition: withSyntheticLocation(statementLoc.line, statementLoc.column, {
        kind: 'BooleanLiteral',
        value: true
      }),
      body
    })];
  }
  if (ts.isForStatement(statement)) {
    return [normalizeForStatement(ts, sourceFile, statement)];
  }
  if (ts.isForOfStatement(statement) || ts.isForInStatement(statement)) {
    return [normalizeForOfOrInStatement(ts, sourceFile, statement)];
  }
  if (ts.isSwitchStatement(statement)) {
    return [normalizeSwitchStatement(ts, sourceFile, statement)];
  }
  if (ts.isTryStatement(statement)) {
    if (!ts.isBlock(statement.tryBlock)) {
      unsupported('Try block must be a block in normalizedProgram.');
    }
    const catchBinding = statement.catchClause && statement.catchClause.variableDeclaration
      && ts.isIdentifier(statement.catchClause.variableDeclaration.name)
      ? statement.catchClause.variableDeclaration.name.text
      : null;
    const catchBlock = statement.catchClause
      ? statement.catchClause.block.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
      : [];
    const finallyBlock = statement.finallyBlock
      ? statement.finallyBlock.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner))
      : [];
    return [withLocation(sourceFile, statement, {
      kind: 'TryStatement',
      tryBlock: statement.tryBlock.statements.flatMap((inner) => normalizeStatement(ts, sourceFile, inner)),
      catchBinding,
      catchBlock,
      finallyBlock
    })];
  }
  if (ts.isBreakStatement(statement)) {
    if (statement.label) {
      unsupported('Labeled break is unsupported in normalizedProgram.');
    }
    return [withLocation(sourceFile, statement, { kind: 'BreakStatement' })];
  }
  if (ts.isContinueStatement(statement)) {
    if (statement.label) {
      unsupported('Labeled continue is unsupported in normalizedProgram.');
    }
    return [withLocation(sourceFile, statement, { kind: 'ContinueStatement' })];
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
