export class NewPetRequest {
  name: string;
  type: string;
  birthDate: string;

  constructor() {
    this.name = "";
    this.type = "";
    this.birthDate = "";
  }
}
