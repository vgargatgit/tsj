export interface Pet {
  id: string;
  name: string;
  species: string;
  age: number;
  vaccinated: boolean;
}

export interface CreatePetRequest {
  name: string;
  species: string;
  age: number;
  vaccinated: boolean;
}

export interface UpdatePetRequest {
  name: string;
  species: string;
  age: number;
  vaccinated: boolean;
}

