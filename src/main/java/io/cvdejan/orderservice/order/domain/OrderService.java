package io.cvdejan.orderservice.order.domain;

import io.cvdejan.orderservice.book.Book;
import io.cvdejan.orderservice.book.BookClient;
import io.cvdejan.orderservice.event.OrderAcceptedMessage;
import io.cvdejan.orderservice.event.OrderDispatchedMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final BookClient bookClient;
    private final StreamBridge streamBridge;
    private static final Logger log= LoggerFactory.getLogger(OrderService.class);

    public Flux<Order> getAllOrders(String userId){
        return orderRepository.findAllByCreatedBy(userId);
    }

    @Transactional
    public Mono<Order> submitOrder(String isbn, int quantity){
        return bookClient.getBookByIsbn(isbn)
                .map(book -> buildAcceptedOrder(book,quantity))
                .defaultIfEmpty(buildRejectedOrder(isbn,quantity))
                .flatMap(orderRepository::save)
                .doOnNext(this::publishOrderAcceptedEvent);
    }

    private void publishOrderAcceptedEvent(Order order){
        if(!order.status().equals(OrderStatus.ACCEPTED)) return;
        OrderAcceptedMessage orderAcceptedMessage = new OrderAcceptedMessage(order.id());
        log.info("Sending order accepted event with id: {}",order.id());
        boolean result = streamBridge.send("acceptOrder-out-0", orderAcceptedMessage);
        log.info("Result of sending data for order with id {}: {}",order.id(),result);
    }

    public Flux<Order> consumeOrderDispatchedEvent(Flux<OrderDispatchedMessage> flux){
        return flux.flatMap(message->orderRepository.findById(message.orderId()))
                .map(this::buildDispatchedOrder)
                .flatMap(orderRepository::save);
    }

    private Order buildDispatchedOrder(Order existinOrder){
        return new Order(
                existinOrder.id(),
                existinOrder.bookIsbn(),
                existinOrder.bookName(),
                existinOrder.bookPrice(),
                existinOrder.quantity(),
                OrderStatus.DISPATCHED,
                existinOrder.createdDate(),
                existinOrder.lastModifiedDate(),
                existinOrder.createdBy(),
                existinOrder.lastModifiedBy(),
                existinOrder.version()
        );
    }

    public static Order buildRejectedOrder(String bookIsbn, int quantity){
        return Order.of(bookIsbn, null, null, quantity, OrderStatus.REJECTED);
    }

    public static Order buildAcceptedOrder(Book book, int quantity){
        return Order.of(book.isbn(), book.title() + " - " + book.author(),book.price(),quantity,OrderStatus.ACCEPTED);
    }
}
