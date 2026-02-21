@RestController
@RequestMapping("/api")
class WebMatrixController {
  @GetMapping("/echo")
  echo(@RequestParam("value") value: string) {
    return "echo:" + value;
  }
}
