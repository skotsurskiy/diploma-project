-- Ініціалізація двох незалежних баз даних в одному екземплярі PostgreSQL:
-- orderdb (для order-service) та paymentdb (для payment-service).
-- Кожен сервіс має власну БД та власного користувача — слабка зв'язаність на рівні даних.

CREATE USER "order" WITH PASSWORD 'order';
CREATE DATABASE orderdb OWNER "order";

CREATE USER payment WITH PASSWORD 'payment';
CREATE DATABASE paymentdb OWNER payment;
