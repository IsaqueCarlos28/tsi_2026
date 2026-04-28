package senac.tsi.books.controllers;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import senac.tsi.books.entities.Book;
import senac.tsi.books.exceptions.BookNotFoundException;
import senac.tsi.books.repositories.BookRepository;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name="books", description = "Books route")
@RestController
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PagedResourcesAssembler<Book> pagedResourcesAssembler;

    private final Bucket bucket;

    @Autowired
    public BookController(BookRepository bookRepository,
                          PagedResourcesAssembler<Book> pagedResourcesAssembler
    ){
        //this.pagedResourcesAssembler = pagedResourcesAssembler;
        //this.bookRepository = bookRepository;
        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Tag(name = "Get")
    @Operation(summary = "Get all books", description = """
            Get all books on the database, 
            even if the route returns one or less 
            itens the API still returns a list
            """)
    @GetMapping("/books")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<PagedModel<EntityModel<Book>>> getBooks(@ParameterObject Pageable pageable){
        if (bucket.tryConsume(1)) {
            var books = bookRepository.findAll(pageable);

            PagedModel<EntityModel<Book>> pagedModelBooks = pagedResourcesAssembler.toModel(books);

            return ResponseEntity.ok(pagedModelBooks);
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    @Tag(name = "Get Book by id",
            description = "Get a single book by id, or returns 404 not found")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Found the book",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Book.class)) }),
            @ApiResponse(responseCode = "400", description = "Invalid id supplied",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content) })
    @GetMapping("/books/{id}")
    public EntityModel<Book> getBookById(
            @PathVariable(name = "id") long id){

        var book = bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));

        return EntityModel.of(book,
                linkTo(methodOn(BookController.class).getBookById(id)).withSelfRel(),
                linkTo(methodOn(BookController.class).getBooks(Pageable.unpaged())).withRel("books"));
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Book created successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Book.class)) }),
            @ApiResponse(responseCode = "400", description = "Invalid input provided") })
    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Book> createBook(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Book to create", required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Book.class),
                    examples = @ExampleObject(value = "{ \"title\": \"New Book\", \"author\": \"Author Name\" }")))
    @RequestBody Book newBook){
        bookRepository.save(newBook);
        return ResponseEntity.created(
                        URI.create("/books/"+ newBook.getId()))
                .body(newBook);

    }

    @PutMapping("/books/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable long id,
                                     @RequestBody Book updatedBook){

        return bookRepository.findById(id).map(
                book -> {
                    book.setTitle(updatedBook.getTitle());
                    book.setAuthor(updatedBook.getAuthor());
                    return ResponseEntity.ok(bookRepository.save(book));
                }
        ).orElseGet(() -> {
            return ResponseEntity.created(URI.create("/books/"+
                    updatedBook.getId()))
                    .body(bookRepository.save(updatedBook));
        });
    }

    @DeleteMapping("/books/{id}")
    public ResponseEntity deleteBook(@PathVariable long id){
        var book = bookRepository.findById(id).orElse(null);
        if(book == null)
            return ResponseEntity.notFound().build();

        bookRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
