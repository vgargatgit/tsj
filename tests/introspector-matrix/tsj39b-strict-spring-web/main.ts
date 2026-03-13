import { RestController } from "java:org.springframework.web.bind.annotation.RestController";
import { RequestMapping } from "java:org.springframework.web.bind.annotation.RequestMapping";
import { GetMapping } from "java:org.springframework.web.bind.annotation.GetMapping";
import { RequestParam } from "java:org.springframework.web.bind.annotation.RequestParam";

@RestController
@RequestMapping("/api")
class StrictWebMatrixController {
  @GetMapping("/echo")
  echo(@RequestParam("value") value: string): string {
    return "echo:" + value;
  }
}
