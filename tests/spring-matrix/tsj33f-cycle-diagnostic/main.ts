import { Qualifier } from "java:org.springframework.beans.factory.annotation.Qualifier";
import { Service } from "java:org.springframework.stereotype.Service";

@Service
@Qualifier("alpha")
class AlphaService {
  constructor(@Qualifier("beta") dep: any) {
  }
}

@Service
@Qualifier("beta")
class BetaService {
  constructor(@Qualifier("alpha") dep: any) {
  }
}
