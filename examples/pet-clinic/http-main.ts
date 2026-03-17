import { run } from "java:org.springframework.boot.SpringApplication";
import { SpringBootApplication } from "java:org.springframework.boot.autoconfigure.SpringBootApplication";
import { OpenAPIDefinition } from "java:io.swagger.v3.oas.annotations.OpenAPIDefinition";

import "./src/domain/owner";
import "./src/domain/pet";
import "./src/repository/clinic-repository";
import "./src/service/clinic-service";
import "./src/web/new-pet-request";
import "./src/web/pet-clinic-controller";

@OpenAPIDefinition({
  info: {
    title: "TSJ Pet Clinic API",
    version: "1.0.0",
    description: "TS-authored Spring Boot pet clinic running through TSJ's generic any-jar path."
  }
})
@SpringBootApplication({ scanBasePackages: ["dev.tsj.generated"] })
export class PetClinicApplication {
  static main(args: string[]) {
    run(PetClinicApplication, args);
  }
}
