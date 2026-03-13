import { JsonProperty } from "java:com.fasterxml.jackson.annotation.JsonProperty";

class JacksonMatrixPerson {
  @JsonProperty("person_id")
  id: string;

  @JsonProperty("display_name")
  name: string;

  constructor() {
    this.id = "";
    this.name = "";
  }
}
