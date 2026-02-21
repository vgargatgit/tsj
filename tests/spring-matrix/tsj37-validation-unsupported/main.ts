@Service
class ValidationService {
  @Validated
  validate(@Email address: string) {
    return address;
  }
}
