package io.cvdejan.orderservice.book;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

@TestMethodOrder(MethodOrderer.Random.class)
class BookClientTest {
    private MockWebServer mockWebServer;
    private BookClient bookClient;

    @BeforeEach
   void setup() throws IOException {
        this.mockWebServer=new MockWebServer();
        this.mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").uri().toString())
                .build();
        this.bookClient=new BookClient(webClient);
    }

    @Test
    void whenBookExistsThenReturnBook() throws JsonProcessingException {
        var bookIsbn="1234567890";
        var testBook=getTestBook();

        var mockResponse=new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                    {
                        "isbn": %s,
                        "title": "Title",
                        "author": "Author",
                        "price": 9.90,
                        "publisher": "Polarsophia"
                    }
                    """.formatted(bookIsbn));

        mockWebServer.enqueue(mockResponse);
        Mono<Book> book=bookClient.getBookByIsbn(bookIsbn);

        StepVerifier.create(book)
                .expectNextMatches(b->b.isbn().equals(bookIsbn))
                .verifyComplete();
    }

    @AfterEach
    void clean() throws IOException {
        this.mockWebServer.shutdown();
    }

    public Book getTestBook(){
        return Book.builder()
                .isbn("1234567890")
                .title("Author")
                .price(9.90)
                .build();
    }
}