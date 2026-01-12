package com.mikaelfrancoeur.springbootstreamsdemo.domain;

public record RefundRequest(String id, String orderId, double amount, String reason) {}
