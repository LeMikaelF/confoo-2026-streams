package com.mikaelfrancoeur.springbootstreamsdemo;

public record RefundRequest(String id, String orderId, double amount, String reason) {}
