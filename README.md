# SUT B — Прокат обладнання

Java 21, Maven, PostgreSQL 17, Spring Boot, JdbcTemplate, JUnit 5 та Mockito. Варіант 08 із персональним правилом.

## Поведінка

Обладнання має унікальний непорожній код до 40 символів, добову ціну 100–1 000 000 копійок та невід’ємний залишок. Одна оренда містить один вид обладнання: 1–20 одиниць на 1–30 днів включно. Код оренди унікальний, непорожній, до 40 символів; ідентифікатор обладнання додатний.

Оформлення перевіряє заявку, існування обладнання та достатність залишку. Потім один раз викликає RentalPolicy.isAllowed(equipment, quantity, days). False або виняток припиняє оформлення без запису. Успіх атомарно створює оренду Active і позицію зі знімком добової ціни.

Початкова сума — добова ціна × кількість × дні у цілих копійках. Персональне правило нижче визначає знижку, заставу, доплату або дозвіл. Інших знижок чи доплат немає. Преміальність фіксується при оформленні. Подальші зміни обладнання не змінюють збереженої оренди. Залишок перевіряється, але оформлення й повернення його не змінюють.

Повернення змінює Active на Returned; повторне повернення заборонене. Початкова сума зберігається, lateFee залишається нульовою. Зовнішня перевірка при поверненні не викликається. Календар, платежі та паралельне оформлення не моделюються.

## Персональне правило

Застава — 15% суми оренди для строку до 13 днів, 30% від 14 днів; максимум 2000 грн.

`calculateDeposit(dailyRate, quantity, days, premium)`. Оформлення зберігає заставу в полі deposit окремо від total. Застава не є платою за оренду; початкова сума до внесення — total + deposit. При поверненні deposit зберігається як історичне значення, фактичні платежі та повернення коштів не моделюються. У старих записах після міграції deposit дорівнює 0.

Усі межі включні, якщо явно не зазначено «менше». Відсотки рахуються від суми до знижок. Загальна сума знижки, застави чи доплати округлюється донизу до цілої копійки один раз; потім застосовується мінімум або максимум. Загальні межі кількості 1–20 і тривалості 1–30 діють завжди.

## API

`RentalService(equipment, rentals, policy)`:

- `calculateTotal(dailyRate, quantity, days)` — початкова сума;
- `rent(new RentalRequest(reference, equipmentId, quantity, days, premium))` — оформлення;
- `returnRental(id)` — повернення.

Некоректні входи та відсутні записи — IllegalArgumentException; бізнес-відмови — RentalRejectedException. Помилки БД передаються як SQLException.

EquipmentRepository(connection): create(Equipment), findById, update(Equipment), delete. RentalRepository(connection): create(RentalOrder), findById, findRentals(equipmentId, status), markReturned(id, lateFee), delete.

create повертає модель з ідентифікаторами БД, ігноруючи вхідні id. findById повертає null за відсутності запису. findRentals застосовує обидва фільтри одночасно та впорядковує результати за id. update відсутнього обладнання — IllegalArgumentException; markReturned відсутньої чи повернутої оренди — RentalRejectedException. Видалення відсутнього запису нічого не змінює. Видалення оренди каскадно видаляє позицію; обладнання з позиціями видалити не можна.

У REST-застосунку Spring керує залежностями та з’єднаннями; репозиторії працюють через JdbcTemplate. Конструктори з JDBC Connection залишені для окремих тестів: таке з’єднання закриває тест. Деталі операцій і транзакцій визначає реалізація репозиторію. Для бізнес-сценаріїв використовуйте послідовні операції та ізольовані дані.

## Підготовка та запуск

Створіть навчального користувача (один раз) і окрему БД у локальній PostgreSQL:

```sql
CREATE ROLE rental_student LOGIN;
\password rental_student
CREATE DATABASE rental_variant08_test OWNER rental_student;
```

Із папки sut-b застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U rental_student -d rental_variant08_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
psql -h localhost -U rental_student -d rental_variant08_test -v ON_ERROR_STOP=1 -f migrations/V2__add_deposit.sql
```

Якщо V1 уже застосовано, виконайте лише V2.

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль задається через DB_PASSWORD.

```sh
mvn compile
mvn test
```

Окремо від звичайної перевірки підключення наявний regression suite. Дані для бізнес-сценаріїв готуйте у власних тестах. Автоматичного створення або видалення схем немає. Для незалежності тестів очищайте власні оренди перед обладнанням або використовуйте окрему тестову схему.


## REST API

Запуск: `mvn spring-boot:run` або клас `RentalApplication`. Потрібні Java 21 і підготовлена PostgreSQL із застосованими міграціями цього проєкту.
Адреса: `http://127.0.0.1:8080`; порт змінюється через `API_PORT`.
Параметри БД: `DB_HOST`, `DB_PORT`, `DB_NAME` (типово `rental_variant08_test`), `DB_USER` (типово `rental_student`), `DB_PASSWORD`.

| Метод | Шлях | Призначення |
|---|---|---|
| POST | `/equipment` | Створити обладнання, 201 та заголовок Location |
| GET | `/equipment/{id}` | Отримати обладнання, 200 |
| POST | `/rentals` | Оформити оренду через сервіс, 201 та Location |
| GET | `/rentals/{id}` | Прочитати збережену оренду, 200 |
| POST | `/rentals/{id}/return` | Повернути обладнання через сервіс, 200 |

Для JSON передавайте `Content-Type: application/json`.
Обладнання: `{"code":"CAM-01","dailyRate":10000,"stock":5}`.
Оренда: `{"reference":"RENT-001","equipmentId":1,"quantity":1,"days":7,"premium":false}`.
Замість 1 використовуйте `id` створеного обладнання. Суми передаються в копійках.
Повернення виконується без тіла запиту.

Особисте правило цього варіанта діє через наявний сервіс. Його результат можна перевірити повторним GET: `total`, `lateFee`, `status`, `deposit` та `item`.
Невідомий ресурс повертає 404, бізнес-відмова або повторне повернення — 409, некоректний запит — 400.
Помилки мають JSON `{"code":"...","message":"..."}`. Статуси оренди: `Active`, `Returned`.

`RENTAL_POLICY_URL` вмикає наявну зовнішню HTTP-перевірку; без цієї змінної локальна політика дозволяє операцію. Недоступна зовнішня перевірка повертає 502.
Spring керує залежностями та пулом з’єднань. Репозиторії використовують JdbcTemplate, створення оренди й позиції є однією транзакцією.
Конструктори з Connection збережені для попередніх тестів; SQL-помилки JdbcTemplate обгортає в DataAccessException.
API не очищає БД й не застосовує міграції автоматично. Для навчальних сценаріїв виконуйте операції послідовно.


## UI та окремий regression suite

Готовий UI розташований у сусідній папці [sut-c-ui](../sut-c-ui/README.md).
Він доступний після звичайного запуску цього backend на /index.html; окрема збірка frontend не потрібна.
Запускайте backend із папки sut-b. Порт за замовчуванням — 8080.

Regression suite — окремий навчальний набір із навмисними проблемами стабільності.
Для його запуску потрібен Docker; PostgreSQL піднімається через Testcontainers, міграції застосовуються набором.
Команда з цієї папки:

```sh
mvn -Pregression test
```

Звичайні тести та цей окремий набір мають різні умови запуску.
Студентські тести повинні перевіряти вимоги та фактичний стан системи; повторні запуски мають бути незалежними.
Для security testing використовуйте окремі vulnerable-копії, а не цей SUT.

[Огляд варіанта](../README.md)

## Лабораторна робота №8 — стабільність тестів

Smoke-набір містить 4 критичні швидкі перевірки. Повна регресія містить усі 10 тестів, включаючи smoke.

```sh
mvn -Psmoke test
mvn -Pregression test
```

Перевірка окремого тесту:

```sh
mvn -Pregression -Dtest=RentalRegressionIT#returnRental test
```

Повторна перевірка стабільності:

```sh
for run in 1 2 3 4 5; do mvn -q -Pregression test || break; done
```

Перевірка з різним випадковим порядком та зворотним порядком тестових даних:

```sh
mvn -Pregression -Djunit.jupiter.execution.order.random.seed=101 test
mvn -Pregression -Djunit.jupiter.execution.order.random.seed=202 -Dregression.reverseFixture=true test
```

### Виявлені причини нестабільності

| Симптом | Причина | Виправлення |
|---|---|---|
| `returnRental` падав залежно від порядку | Використовував спільний `rentalId`, який створював інший тест | Тест сам створює оренду |
| Помилка `duplicate key` | Два тести мали однаковий унікальний код обладнання | Кожен тест генерує власний UUID |
| `rentalAfterPolicyCheck` інколи бачив 0 записів | Фіксоване `Thread.sleep(50)` не гарантувало завершення операції | `CountDownLatch` сигналізує про стан policy, а `CompletableFuture.join()` очікує завершення операції |
| `rentalSelection` залежав від порядку даних | SQL-запит без `ORDER BY` та фіксоване очікування | Використано контракт `findRentals`, яий сортує за `id`, і фактичний порядок fixture |

Усі тести тепер самі створюють потрібні дані, не залежать від порядку та не використовують retry або фіксовані очікування.

## Лабораторна робота №9 - CI/CD and Continuous Testing

CI pipeline описано у `.github/workflows/ci.yml`. Він автоматично запускається після `push` та для `pull_request`.

Pipeline складається з трьох послідовних jobs:

1. `Build` - компіляція та пакування проєкту без запуску тестів;
2. `Unit tests` - 4 unit-тести `RentalServiceUnitTest`;
3. `Integration tests` - 10 інтеграційних тестів `RentalRegressionIT` з PostgreSQL у Testcontainers.

Jobs зв'язані через `needs`, тому наступний етап починається лише після успішного попереднього. Test failures не ігноруються: команда Maven повертає ненульовий exit code, і job отримує статус `failed`.

Після unit та integration jobs JUnit XML/TXT-звіти з `target/surefire-reports` зберігаються як GitHub Actions artifacts. `if: always()` дозволяє зберегти звіт навіть після падіння тесту, але не змінює failed-статус job.

### Локальна перевірка

```sh
mvn -DskipTests package
mvn -Dtest=RentalServiceUnitTest test
mvn -Pregression test
```

### Демонстрація failed pipeline

1. У гілці `demo-failed-pipeline` тимчасово змінити очікуване значення у `calculatesTotal` з `60_000` на `60_001`.
2. Зробити commit і push. Trigger `push` автоматично запустить pipeline.
3. У GitHub Actions відкрити `Unit tests` та знайти у log: `expected: <60001> but was: <60000>`.
4. Завантажити artifact `unit-test-report`, у якому збережено причину failure.
5. Повернути `60_000`, зробити новий commit і push. Наступний pipeline має завершитися успішно.
