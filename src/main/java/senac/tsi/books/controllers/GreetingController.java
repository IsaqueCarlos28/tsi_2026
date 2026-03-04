package senac.tsi.books.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class GreetingController {

    private static final String template = "Hello. %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/greetings")
    public Greeting greeting(@RequestParam(defaultValue = "Olá turma de TSI") String text){
        return new Greeting(counter.incrementAndGet(),template.formatted(text));
    }


    class Greeting{
        public long id;
        public String text;

        public Greeting(long id, String text) {
            this.id = id;
            this.text = text;
        }
    }
}
