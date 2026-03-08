class SerializableOwner {
  id: string;
  name: string;

  constructor() {
    this.id = "101";
    this.name = "Lin";
  }

  asDto() {
    return this;
  }
}
