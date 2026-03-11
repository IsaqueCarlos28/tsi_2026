package senac.tsi.books.utils;

public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException (long bookId){
        super("could not find" + bookId);
    }
}
