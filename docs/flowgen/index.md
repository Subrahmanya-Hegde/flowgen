# Spring Boot Flow Diagrams

_Auto-generated: 2026-06-22 13:44_

| # | Type | Class | Method | Endpoint |
|---|------|-------|--------|----------|
| 1 | REST GET | ProductController | [listProducts()](ProductController/listProducts.md) | `/api/products` |
| 2 | REST GET | ProductController | [getProduct()](ProductController/getProduct.md) | `/api/products/id` |
| 3 | REST PUT | ProductController | [updatePrice()](ProductController/updatePrice.md) | `/api/products/id/price` |
| 4 | REST POST | OrderController | [placeOrder()](OrderController/placeOrder.md) | `/api/orders` |
| 5 | REST GET | OrderController | [getOrder()](OrderController/getOrder.md) | `/api/orders/id` |
| 6 | REST DELETE | OrderController | [cancelOrder()](OrderController/cancelOrder.md) | `/api/orders/id` |
| 7 | Kafka Listener | PaymentEventListener | [onPaymentEvent()](PaymentEventListener/onPaymentEvent.md) | `payment.events` |
| 8 | Scheduler | InventoryScheduler | [syncInventory()](InventoryScheduler/syncInventory.md) | `cron: 0 0 3 * * ?` |
