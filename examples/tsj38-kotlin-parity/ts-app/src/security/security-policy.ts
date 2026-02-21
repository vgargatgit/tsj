export class SecurityPolicy {
  canWrite(role: string): boolean {
    return role === "admin";
  }

  canRead(role: string): boolean {
    return role === "admin" || role === "reader";
  }
}
