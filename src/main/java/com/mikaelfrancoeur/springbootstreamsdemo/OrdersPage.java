package com.mikaelfrancoeur.springbootstreamsdemo;

import java.util.List;

record OrdersPage(List<Order> orders, String nextCursor) {
    boolean hasMore() {
        return nextCursor != null && !nextCursor.isBlank();
    }
}
