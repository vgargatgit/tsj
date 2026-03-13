import { NotBlank } from "java:jakarta.validation.constraints.NotBlank";
import { Size } from "java:jakarta.validation.constraints.Size";

class ValidationMatrixPerson {
  @NotBlank({ message: "person.name.required" })
  name: string;

  @Size({ min: 3, max: 8, message: "person.alias.length" })
  alias: string;

  constructor() {
    this.name = "";
    this.alias = "";
  }
}
