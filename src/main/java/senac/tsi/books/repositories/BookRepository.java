package senac.tsi.books.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import senac.tsi.books.entities.Book;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> { }
