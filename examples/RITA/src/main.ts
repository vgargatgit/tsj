import {
  bootstrapGreeting,
  createJavaGreetingController,
  isComponentAnnotationPresent,
  countInjectAnnotatedFields,
  runtimeClassName
} from "java:dev.rita.di.ReflectionDiDemo";

function check(name: string, condition: boolean) {
  console.log(name + ":" + condition);
}

function Component(target: any) {
  return target;
}

function Inject(_target: any, _propertyKey: string) {
  // No-op runtime decorator for TS-side simulation.
}

@Component
class TsRepository {
}

@Component
class TsService {
  @Inject
  repo: TsRepository | undefined;
}

const message = bootstrapGreeting("TSJ");
check("java_di_message", message === "hello TSJ");

const javaController = createJavaGreetingController();
check("java_component_annotation_visible", isComponentAnnotationPresent(javaController));
check("java_inject_annotation_visible", countInjectAnnotatedFields(javaController) === 1);

const tsService = new TsService();
check("ts_component_annotation_not_visible_to_java_reflection", !isComponentAnnotationPresent(tsService));
check("ts_inject_annotation_not_visible_to_java_reflection", countInjectAnnotatedFields(tsService) === 0);
const tsRuntimeClassName = runtimeClassName(tsService);
console.log("ts_runtime_class_name:" + tsRuntimeClassName);
check("ts_runtime_class_is_map_backing_object", tsRuntimeClassName === "java.util.LinkedHashMap");
