@Service
@Validated
class ValidationMatrixService {
  validate(@NotBlank({ message: "username.required" }) username: string, @Size({ min: 3, max: 8, message: "alias.length" }) alias: string, @Min({ value: 18, message: "age.min" }) age: number, @Max({ value: 65, message: "age.max" }) score: number, @NotNull({ message: "email.required" }) email: any) {
    return username + "|" + alias + "|" + age + "|" + score + "|" + email;
  }
}
