# How to Add Idempotency to This Project (`POST /books`)

This guide explains, step-by-step, how to implement **in-memory idempotency** in this project for `POST /books` using the `Idempotency-Key` header.

It is based on the current structure of:
- `src/main/java/senac/tsi/books/controllers/BookController.java`
- `src/test/java/senac/tsi/books/BookControllerIdempotencyTest.java`

---

## 1) What idempotency means here

For `POST /books`, idempotency means:
- same `Idempotency-Key` + same payload => return the **same creation result** (do not create duplicate rows)
- same `Idempotency-Key` + different payload => reject with `409 Conflict`
- missing or blank `Idempotency-Key` => reject with `400 Bad Request`

---

## 2) Define the API contract first

In `BookController#createBook`, require the header:

```java
@RequestHeader("Idempotency-Key") String idempotencyKey;
```

And document it in OpenAPI:

```java
@Parameter(description = "Required key used to make repeated create requests idempotent", required = true)
```

Also document expected responses (`201`, `400`, `409`) with `@ApiResponses`.

---

## 3) Add in-memory storage for processed keys

In `BookController`, add:

```java
private final Map<String, IdempotentCreateResponse> createBookResponses = new ConcurrentHashMap<>();
private final Object createBookIdempotencyLock = new Object();
```

Why:
- `ConcurrentHashMap` stores previously processed keys
- lock protects check-and-save flow so two concurrent requests with same key do not both create data

---

## 4) Add payload fingerprinting

Create a small record for request identity:

```java
private record CreateBookFingerprint(String title, String author) {}
```

For each request:

```java
var requestFingerprint = new CreateBookFingerprint(newBook.getTitle(), newBook.getAuthor());
```

This lets you compare if repeated requests are the same logical operation.

---

## 5) Add stored response model

Keep enough data to replay the original create response:

```java
private record IdempotentCreateResponse(CreateBookFingerprint requestFingerprint, Book book, URI location) {}
```

When a request is successfully created, store:
- the fingerprint
- created `Book`
- `Location` URI (`/books/{id}`)

---

## 6) Implement the controller flow (core logic)

Inside `createBook`:

1. Validate header is not blank
2. Build fingerprint from payload
3. Enter synchronized block
4. Check if key already exists
   - exists + same fingerprint => return original `201 Created` + same `Location` and body
   - exists + different fingerprint => return `409 Conflict`
5. If key does not exist:
   - persist entity
   - store idempotency response in map
   - return `201 Created`

Reference helper used in your code:

```java
private ResponseEntity<Book> persistNewBook(Book newBook) {
    var savedBook = bookRepository.save(newBook);
    return ResponseEntity.created(URI.create("/books/" + savedBook.getId()))
            .body(savedBook);
}
```

---

## 7) Add integration tests

Use `MockMvc` tests in:
- `src/test/java/senac/tsi/books/BookControllerIdempotencyTest.java`

Cover at least these cases:

1. **same key + same payload**
   - first request: `201`
   - second request: `201`
   - same `Location`
   - repository count stays `1`

2. **same key + different payload**
   - second request returns `409`
   - repository count stays `1`

3. **missing key**
   - returns `400`
   - repository count stays `0`

4. **blank key**
   - returns `400`
   - repository count stays `0`

---

## 8) Verify locally

Run tests:

```zsh
cd /Users/leonardo.romao/IdeaProjects/tsi_2026
./mvnw -q test
```

Manual API checks (optional):

```zsh
curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: key-1' \
  -d '{"title":"DDD","author":"Eric Evans"}'

curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: key-1' \
  -d '{"title":"DDD","author":"Eric Evans"}'

curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: key-1' \
  -d '{"title":"Different","author":"Eric Evans"}'
```

---

## 9) Current limitations (important)

This implementation is intentionally simple:
- keys are stored only in memory (lost on restart)
- works only per app instance (not shared across multiple nodes)
- no expiration/TTL for old keys (map can grow over time)

---

## 10) Production-ready next steps

If you want to harden this design later:
- move key storage to Redis or database
- add TTL/expiration for keys
- persist hash/fingerprint + response metadata atomically
- add observability (metrics/logging for replay/conflict rates)

---

## Quick checklist for future endpoints

When adding idempotency to another `POST` route:
- require `Idempotency-Key`
- define payload fingerprint
- enforce same-key/same-payload replay
- reject same-key/different-payload (`409`)
- test replay, conflict, missing key, blank key

