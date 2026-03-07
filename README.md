#  Shop API — Лабораторные 2–5 (REST + DB + Security + JWT)

Проект представляет собой учебный REST-сервис интернет-магазина, созданный в рамках лабораторных работ №2–5.  
Реализована работа с товарами, категориями, клиентами и заказами, а также бизнес-операции, безопасность, роли и механизм JWT-аутентификации с refresh-токенами и управлением сессиями.

---

## Используемые технологии

| Компонент | Технология |
|----------|------------|
| Backend framework | Spring Boot |
| REST | Spring Web |
| ORM | Spring Data JPA |
| База данных | PostgreSQL |
| Безопасность | Spring Security + JWT |
| Миграции схемы | Flyway |
| Валидация данных | Jakarta Validation |
| API тестирование | Postman Collection |

---

##  Основные сущности

| Сущность | Описание |
|----------|----------|
| `User` | Пользователь системы (роль USER или ADMIN) |
| `UserSession` | Таблица refresh-токенов и состояния сессий |
| `Category` | Категории товаров |
| `Product` | Товары |
| `Customer` | Клиенты |
| `Order` | Заказы |
| `OrderItem` | Позиции заказа |

---

## Роли и доступы

| Роль | Возможности |
|------|-------------|
| `ADMIN` | Полный доступ ко всем CRUD-операциям |
| `USER` | Может оформлять заказы и управлять корзиной |

---

##  CRUD-операции (ЛР2–3)

✔ `POST /api/categories` — создать категорию  
✔ `GET /api/categories` — список категорий  
✔ `GET /api/categories/{id}` — получить по id  
✔ `PUT /api/categories/{id}` — обновить  
✔ `DELETE /api/categories/{id}` — удалить

Аналогично реализовано для:

- `/api/products`
- `/api/customers`
- `/api/orders`
- `/api/order-items`

---

##  Бизнес-операции (ЛР3–5)

| Эндпоинт | Назначение |
|---------|------------|
| `POST /api/orders/checkout` | Оформление заказа по товарам |
| `POST /api/orders/{orderId}/items` | Добавление товара в заказ, если статус `CREATED` |
| `DELETE /api/orders/{orderId}/items/{productId}` | Удаление товара |
| `POST /api/orders/{orderId}/cancel` | Отмена заказа |
| `GET /api/orders/customer/{customerId}/summary` | Отчёт по заказам клиента |

---

##  JWT-аутентификация и refresh токены (ЛР5)

Механизм выдает:

- **accessToken** — используется в `Authorization: Bearer <token>`
- **refreshToken** — используется для получения новой пары токенов

Состояние refresh-токенов хранится в таблице `user_sessions`:

| Статус | Описание |
|--------|----------|
| `ACTIVE` | токен действителен |
| `INVALIDATED` | использован после refresh и заблокирован |
| `EXPIRED` | срок действия истёк |

---

##  Тестовый сценарий по условию задания

1. `POST /auth/login` — получить пару токенов
2. `GET /api/categories` — доступ c accessToken
3. `POST /auth/refresh` — получить новую пару токенов
4. Повторный refresh старым refreshToken должен дать **401/403**
5. Проверить таблицу `user_sessions` — статус изменён корректно

---

##  Postman Collection

 `Shop-postman (Labs 2–5).json`  
Коллекция включает CRUD, бизнес-операции, refresh-механику и автозаполнение переменных.

---

##  Запуск проекта

1. Создать базу PostgreSQL:

```sql
CREATE DATABASE shop;
```

2. Задать переменные окружения (пример Windows PowerShell):

```powershell
$env:DB_USER="shop_user"
$env:DB_PASSWORD="<YOUR_PASSWORD>"
$env:APP_JWT_SECRET="<YOUR_PASSWORD>_very_secret"

# для ЛР6 (HTTPS):
$env:SSL_KEYSTORE_PATH="C:\\certs\\shop-keystore.p12"
$env:SSL_KEYSTORE_PASSWORD="<YOUR_PASSWORD>"
```

3. Запуск:

```bash
mvn spring-boot:run
```

После включения TLS приложение поднимается на **https://localhost:8443**.

---
