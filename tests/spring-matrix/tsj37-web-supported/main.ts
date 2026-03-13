import { GetMapping } from "java:org.springframework.web.bind.annotation.GetMapping";
import { RequestMapping } from "java:org.springframework.web.bind.annotation.RequestMapping";
import { RequestParam } from "java:org.springframework.web.bind.annotation.RequestParam";
import { RestController } from "java:org.springframework.web.bind.annotation.RestController";

@RestController
@RequestMapping("/api")
class WebMatrixController {
  @GetMapping("/echo")
  echo(@RequestParam("value") value: string) {
    return "echo:" + value;
  }
}
