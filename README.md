# my-market-app
Спринт 5: витрина интернет-магазина на Spring Boot (Web MVC + Thymeleaf + Spring Data JPA + H2)

## HTML-шаблоны и эндпоинты

HTML-шаблоны взяты из готового макета спринта (постановка спринта 5). Контракт эндпоинтов соответствует постановке:

- `GET /` и `GET /items` — витрина (шаблон `items`);
- `POST /items` — изменение количества в корзине со страницы витрины: редирект на `/items` с сохранением параметров запроса;
- `GET /items/{id}` и `POST /items/{id}` — карточка товара (шаблон `item`);
- `GET /cart/items` и `POST /cart/items` — корзина (шаблон `cart` с атрибутами `items` и `total`);
- `GET /orders` — список заказов (шаблон `orders`);
- `GET /orders/{id}` — страница заказа (шаблон `order`, флаг `newOrder`);
- `POST /buy` — оформление заказа: редирект на `/orders/{id}?newOrder=true`.

`POST /items` и `POST /buy` возвращают редирект, как требует постановка; `POST /items/{id}` и `POST /cart/items` возвращают шаблон напрямую.
