package sit.int221.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sit.int221.backend.dtos.order.OrderItemRequestDto;
import sit.int221.backend.dtos.order.OrderRequestDto;
import sit.int221.backend.dtos.order.OrderResponseDto;
import sit.int221.backend.entities.Order;
import sit.int221.backend.entities.SaleItem;
import sit.int221.backend.entities.SellerProfile;
import sit.int221.backend.entities.User;
import sit.int221.backend.mappers.OrderMapper;
import sit.int221.backend.repositories.OrderRepository;
import sit.int221.backend.repositories.SaleItemRepository;
import sit.int221.backend.repositories.SellerProfileRepository;
import sit.int221.backend.repositories.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final UserRepository userRepo;
    private final SellerProfileRepository sellerProfileRepo;
    private final SaleItemRepository saleItemRepo;
    private final OrderRepository orderRepo;

    /* ---------------- Create new order ---------------- */
    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto dto) {
        // 1. buyer
        User buyer = userRepo.findById(dto.getBuyerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));

        // 2. seller
        SellerProfile seller = sellerProfileRepo.findById(dto.getSellerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));

        // 3. sale items
        List<SaleItem> saleItems = saleItemRepo.findAllById(
                dto.getOrderItems().stream().map(OrderItemRequestDto::getSaleItemId).toList()
        );

        if (saleItems.size() != dto.getOrderItems().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Some sale items not found");
        }

        // 4. check all items belong to same seller
        boolean anyWrongSeller = saleItems.stream()
                .anyMatch(si -> si.getSeller() == null || !si.getSeller().getId().equals(seller.getId()));

        if (anyWrongSeller) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All sale items must belong to the same seller");
        }

        // 5. map & save
        Order order = OrderMapper.toEntity(dto, buyer, seller, saleItems);
        Order saved = orderRepo.save(order);

        return OrderMapper.toResponse(saved);
    }

    /* ---------------- Get all buyer's orders ---------------- */
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersByBuyer(Integer buyerId) {
        // 1. หา buyer ก่อน
        User buyer = userRepo.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 2. หา orders ของ buyer
        List<Order> orders = orderRepo.findByBuyer(buyer);

        // 3. map -> DTO
        return orders.stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    /* ---------------- Get order by id ---------------- */
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Integer orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")
                );

        return OrderMapper.toResponse(order);
    }
}
