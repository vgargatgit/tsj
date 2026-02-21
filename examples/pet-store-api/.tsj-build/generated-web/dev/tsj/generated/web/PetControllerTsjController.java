package dev.tsj.generated.web;

@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/api/pets")
public final class PetControllerTsjController {
    public PetControllerTsjController() {
    }

    @org.springframework.web.bind.annotation.GetMapping("/list")
    public Object list() {
        return dev.tsj.generated.MainProgram.__tsjInvokeController("PetController", "list");
    }

    @org.springframework.web.bind.annotation.GetMapping("/get")
    public Object getById(@org.springframework.web.bind.annotation.RequestParam("arg0") Object arg0) {
        return dev.tsj.generated.MainProgram.__tsjInvokeController("PetController", "getById", arg0);
    }

    @org.springframework.web.bind.annotation.PostMapping("/create")
    public Object create(@org.springframework.web.bind.annotation.RequestParam("arg0") Object arg0, @org.springframework.web.bind.annotation.RequestParam("arg1") Object arg1, @org.springframework.web.bind.annotation.RequestParam("arg2") Object arg2, @org.springframework.web.bind.annotation.RequestParam("arg3") Object arg3) {
        return dev.tsj.generated.MainProgram.__tsjInvokeController("PetController", "create", arg0, arg1, arg2, arg3);
    }

    @org.springframework.web.bind.annotation.PutMapping("/update")
    public Object update(@org.springframework.web.bind.annotation.RequestParam("arg0") Object arg0, @org.springframework.web.bind.annotation.RequestParam("arg1") Object arg1, @org.springframework.web.bind.annotation.RequestParam("arg2") Object arg2, @org.springframework.web.bind.annotation.RequestParam("arg3") Object arg3, @org.springframework.web.bind.annotation.RequestParam("arg4") Object arg4) {
        return dev.tsj.generated.MainProgram.__tsjInvokeController("PetController", "update", arg0, arg1, arg2, arg3, arg4);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/delete")
    public Object remove(@org.springframework.web.bind.annotation.RequestParam("arg0") Object arg0) {
        return dev.tsj.generated.MainProgram.__tsjInvokeController("PetController", "remove", arg0);
    }

}
