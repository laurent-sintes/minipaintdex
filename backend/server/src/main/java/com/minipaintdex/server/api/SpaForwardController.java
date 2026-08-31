package com.minipaintdex.server.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SpaForwardController {
    @GetMapping({
            "/about",
            "/shopping",
            "/market/**",
            "/workshop/**"
    })
    String forwardApplicationRoute() {
        return "forward:/index.html";
    }
}
