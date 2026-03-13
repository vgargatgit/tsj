import { TypeMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.TypeMark";
import { FieldMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.FieldMark";
import { MethodMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.MethodMark";
import { ParamMark } from "java:dev.tsj.compiler.backend.jvm.fixtures.annotations.ParamMark";

interface Runner {
  run(): string;
}

abstract class BaseRepo<TBase> {}

@TypeMark
class Repo<TValue extends string> extends BaseRepo<TValue> implements Runner {
  @FieldMark
  private readonly store!: Map<string, TValue>;

  constructor(@ParamMark private readonly source: string) {}

  @MethodMark
  protected load<TQuery extends TValue>(@ParamMark id: TQuery): TValue {
    return id;
  }

  run(): string {
    return this.source;
  }
}
