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
