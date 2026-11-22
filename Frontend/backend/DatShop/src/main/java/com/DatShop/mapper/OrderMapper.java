package com.DatShop.mapper;

import com.DatShop.dto.order.OrderDetailResponse;
import com.DatShop.dto.order.OrderItemResponse;
import com.DatShop.dto.order.OrderShippingSnapshot;
import com.DatShop.dto.order.OrderSummaryResponse;
import com.DatShop.dto.order.OrderVoucherSnapshot;
import com.DatShop.model.Order;
import com.DatShop.model.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderSummaryResponse toSummary(Order order, int itemCount) {
        return new OrderSummaryResponse(
                order.getOrderNumber(),
                order.getStatus(),
                money(order.getSubtotal()),
                money(order.getDiscountAmount()),
                money(order.getShippingFee()),
                money(order.getTotalAmount()),
                itemCount,
                order.getCreatedAt());
    }

    public static OrderDetailResponse toDetail(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                money(order.getSubtotal()),
                money(order.getDiscountAmount()),
                money(order.getShippingFee()),
                money(order.getTotalAmount()),
                voucher(order),
                shipping(order),
                order.getCustomerNote(),
                items.stream().map(OrderMapper::toItem).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static OrderItemResponse toItem(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getVariantId(),
                item.getProductName(),
                item.getProductSlug(),
                item.getSku(),
                item.getSizeName(),
                item.getColorName(),
                item.getProductImageUrl(),
                money(item.getUnitPrice()),
                item.getQuantity(),
                money(item.getLineTotal()));
    }

    private static OrderVoucherSnapshot voucher(Order order) {
        if (order.getVoucherCode() == null) {
            return null;
        }
        return new OrderVoucherSnapshot(
                order.getVoucherCode(),
                order.getVoucherType(),
                money(order.getVoucherDiscountValue()),
                money(order.getDiscountAmount()));
    }

    private static OrderShippingSnapshot shipping(Order order) {
        return new OrderShippingSnapshot(
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getShippingProvince(),
                order.getShippingDistrict(),
                order.getShippingWard(),
                order.getShippingAddressLine());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
