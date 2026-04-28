package senac.tsi.books;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import senac.tsi.books.repositories.BookRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
    }

    @Test
    void shouldReturnTheOriginalResponseWhenIdempotencyKeyIsReusedWithSamePayload() throws Exception {
        String requestBody = """
                {
                  "title": "Domain-Driven Design",
                  "author": "Eric Evans"
                }
                """;

        MvcResult firstResponse = mockMvc.perform(post("/books")
                        .header("Idempotency-Key", "book-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        String firstLocation = firstResponse.getResponse().getHeader("Location");
        assertThat(firstLocation).isNotBlank();

        MvcResult secondResponse = mockMvc.perform(post("/books")
                        .header("Idempotency-Key", "book-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", firstLocation))
                .andReturn();

        assertThat(secondResponse.getResponse().getContentAsString())
                .isEqualTo(firstResponse.getResponse().getContentAsString());
        assertThat(bookRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectReusedIdempotencyKeyWithDifferentPayload() throws Exception {
        mockMvc.perform(post("/books")
                        .header("Idempotency-Key", "book-create-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Clean Code",
                                  "author": "Robert C. Martin"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/books")
                        .header("Idempotency-Key", "book-create-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Clean Architecture",
                                  "author": "Robert C. Martin"
                                }
                                """))
                .andExpect(status().isConflict());

        assertThat(bookRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectRequestsWhenNoIdempotencyKeyIsProvided() throws Exception {
        String requestBody = """
                {
                  "title": "Refactoring",
                  "author": "Martin Fowler"
                }
                """;

        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        assertThat(bookRepository.count()).isZero();
    }

    @Test
    void shouldRejectBlankIdempotencyKey() throws Exception {
        mockMvc.perform(post("/books")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Patterns of Enterprise Application Architecture",
                                  "author": "Martin Fowler"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(bookRepository.count()).isZero();
    }
}


