# JVM Annotation Mapping (TSJ-32)

## Scope
TSJ-32 currently covers runtime-visible annotation and reflection metadata behavior for:
1. Interop-generated bridge classes/methods.
2. TS-authored Spring web adapter classes generated from decorators.

Supported:
1. Runtime-visible class and method annotations on generated bridge and adapter classes.
2. Method parameter name metadata (`-parameters`) on generated classes.
3. TS decorator extraction/mapping model (`TsDecoratorModelExtractor`, `TsDecoratorAnnotationMapping`).
4. TSJ-32b annotation attribute/value parsing subset.
5. TSJ-32c parameter decorator extraction/mapping subset.
6. TSJ-35 transactional decorator mapping subset (`@Transactional`).

Not yet supported:
1. General-purpose TS decorator lowering to arbitrary JVM annotations.
2. Generic parameter-annotation lowering outside documented Spring-focused subset.
3. Full annotation-expression parity beyond documented TSJ-32b syntax.

## TSJ-32a Decorator Mapping Contract
Class decorators:
1. `@Component` -> `org.springframework.stereotype.Component`
2. `@Service` -> `org.springframework.stereotype.Service`
3. `@Repository` -> `org.springframework.stereotype.Repository`
4. `@Controller` -> `org.springframework.stereotype.Controller`
5. `@RestController` -> `org.springframework.web.bind.annotation.RestController`
6. `@RequestMapping` -> `org.springframework.web.bind.annotation.RequestMapping`
7. `@Configuration` -> `org.springframework.context.annotation.Configuration`
8. `@Transactional` -> `org.springframework.transaction.annotation.Transactional`

Method decorators:
1. `@Bean` -> `org.springframework.context.annotation.Bean`
2. `@Autowired` -> `org.springframework.beans.factory.annotation.Autowired`
3. `@RequestMapping` -> `org.springframework.web.bind.annotation.RequestMapping`
4. `@GetMapping` -> `org.springframework.web.bind.annotation.GetMapping`
5. `@PostMapping` -> `org.springframework.web.bind.annotation.PostMapping`
6. `@PutMapping` -> `org.springframework.web.bind.annotation.PutMapping`
7. `@DeleteMapping` -> `org.springframework.web.bind.annotation.DeleteMapping`
8. `@PatchMapping` -> `org.springframework.web.bind.annotation.PatchMapping`
9. `@ExceptionHandler` -> `org.springframework.web.bind.annotation.ExceptionHandler`
10. `@ResponseStatus` -> `org.springframework.web.bind.annotation.ResponseStatus`
11. `@Transactional` -> `org.springframework.transaction.annotation.Transactional`

Parameter decorators (TSJ-32c subset):
1. `@RequestParam` -> `org.springframework.web.bind.annotation.RequestParam`
2. `@PathVariable` -> `org.springframework.web.bind.annotation.PathVariable`
3. `@RequestHeader` -> `org.springframework.web.bind.annotation.RequestHeader`
4. `@RequestBody` -> `org.springframework.web.bind.annotation.RequestBody`

## TSJ-32b Attribute/Value Subset
Supported decorator attribute forms:
1. Single positional value, treated as implicit `value`.
2. Object-literal attributes (`{ value: "...", ... }`).
3. Value kinds: string, number, boolean, arrays.
4. Helpers: `enum("<fqcn>.<CONST>")`, `classOf("<fqcn>")`.

Supported adapter use cases:
1. `@RequestMapping` and route mapping path extraction from `value` or `path`.
2. `@ExceptionHandler` with string/class literal and array forms.
3. `@ResponseStatus` with numeric code or enum constant (`HttpStatus` subset).

Validation diagnostics:
1. `code=TSJ-DECORATOR-ATTRIBUTE`
2. `featureId=TSJ32B-ANNOTATION-ATTRIBUTES`

## TSJ-32c Parameter Subset
Supported parameter decorator argument forms:
1. `@RequestBody` with no arguments.
2. `@RequestParam`, `@PathVariable`, `@RequestHeader` with:
   - positional string (`@RequestParam("q")`)
   - object form (`@RequestParam({ value: "q" })`)
   - object aliases (`name`, `path`) where applicable.
3. Undecorated route parameters default to generated `@RequestParam("argN")`.

Validation diagnostics:
1. `code=TSJ-DECORATOR-PARAM`
2. `featureId=TSJ32C-PARAM-ANNOTATIONS`

## Interop Spec Validation
Use `docs/interop-bridge-spec.md` keys:
1. `classAnnotations=<fqcn>,<fqcn>,...`
2. `bindingAnnotations.<binding>=<fqcn>,<fqcn>,...`

Invalid configuration emits:
1. `code=TSJ-INTEROP-ANNOTATION`
2. `featureId=TSJ32-ANNOTATION-SYNTAX`

## Reflection Coverage
TSJ tests validate:
1. Class annotation visibility via reflection.
2. Method annotation visibility and mapped attribute values via reflection.
3. Method parameter names and parameter-level binding annotations via reflection.
