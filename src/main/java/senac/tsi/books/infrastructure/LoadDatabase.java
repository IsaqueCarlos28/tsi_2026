package senac.tsi.books.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import senac.tsi.books.entities.Book;
import senac.tsi.books.repositories.BookRepository;

@Configuration
public class LoadDatabase {
    private static final Logger log = LoggerFactory.getLogger(LoadDatabase.class);

    @Bean
    CommandLineRunner initDatabase(BookRepository repository){
        return args -> {
            log.info("Preloading" + repository.save(new
                    Book("O senhor dos anéis", "J.R.R. Tolkien")));
            log.info("Preloading" + repository.save(new
                    Book("Eu Robo", "Isaac Asimov")));
        };
    }
}
