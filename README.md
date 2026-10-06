# 2026-autumn-home-work 

[![Checks](https://github.com/vk-edu-distrib-compute/2026-autumn-home-work/actions/workflows/gradle-checks.yaml/badge.svg)](https://github.com/vk-edu-distrib-compute/2026-autumn-home-work/actions/workflows/gradle-checks.yaml) [![Codacy Badge](https://app.codacy.com/project/badge/Grade/413dd10e84d04e18b5d86094472011d9)](https://app.codacy.com/gh/vk-edu-distrib-compute/2026-autumn-home-work/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

# Правила репозитория
## Ограничения и запреты
Нарушение приведённых ниже правил может повлечь снижение оценки вплоть до 0 баллов.
* Не добавляйте в репозиторий файлы ресурсов и иные файлы, не являющиеся исходными файлами на языке Java. Необходимые дополнительные файлы (например, файлы данных) храните вне репозитория.
* Не изменяйте код других участников.
* Не изменяйте сборочные скрипты и настройки инструментов проверки.
* Не добавляйте зависимости. Все задания можно выполнить с текущим списком зависимостей; в дальнейшем он может быть расширен для новых заданий.
* Не копируйте чужой код, в том числе фрагментарно.
* Не используйте большие языковые модели (БЯМ, LLM, ChatGPT, Sonnet, DeepSeek, GLM и т.д.) для написания кода.

# Правила оценки заданий
* Чтобы задание было зачтено, необходимо (но недостаточно) успешно пройти все соответствующие тесты и проверки стиля кода.
* Задание, в котором тесты или иные проверки не пройдены, не оценивается. Студент самостоятельно обеспечивает успешное прохождение проверок и отслеживает состояние pull request; дополнительное сопровождение этого процесса не предусмотрено.
* Некоторые задания не содержат тестов и оцениваются исключительно по коду.
* Балл за зачтённое задание может быть ниже максимального, если преподаватель сочтёт это обоснованным.
* Основаниями для снижения баллов могут служить, например: большое количество ошибок в решении (много замечаний на ревью), излишняя сложность решения, а также решение, проходящее тесты, но некорректно работающее на других допустимых входных данных.

Если нашли ошибку в общих файлах репозитория или хотите что-то улучшить - присылайте отдельный PR, обсудим.

# Домашние задание курса "Распределенные вычисления" осень 2026 года.

Будем строить бэкэнд сервиса сокращения ссылок (аналог https://vk.ru/cc, https://clck.ru/, https://bitly.com/). 

### Fork
[Форкните проект](https://help.github.com/articles/fork-a-repo/), склонируйте и добавьте `upstream`:
```
$ git clone git@github.com:<username>/2026-autumn-home-work.git
Cloning into '2026-autumn-home-work'...

...

$ git remote add upstream git@github.com:vk-edu-distrib-compute/2026-autumn-home-work.git
$ git fetch upstream
From github.com:vk-edu-distrib-compute/2026-autumn-home-work
 * [new branch]      master     -> upstrerulesam/master
```
### Run
Запустить можно так (если вы вдруг используете винду то SERVICE_FACTORY переменную нужно задавать по-другому и запускать `gradle.bat`):
```bash
$ SERVICE_FACTORY=<fully.qualified.service.factory.class.name> SERVICE_PORT=8080 ./gradlew run
````

### Test
Так можно запустить тесты:
```bash
$ ./gradlew check
```

### Code style checks
```bash
$ ./gradlew codeStyleChecks
```

### Develop
Откройте в IDE -- [OpenIDE](https://openide.ru/download/) нам будет достаточно.

**ВНИМАНИЕ!** При запуске тестов или сервера в IDE необходимо передавать Java опцию `-Xmx128m`. 

## Домашнее задание № 1 по теме "Протоколы и модели сетевого взаимодействия" | URL-shortener service

Сделать сервис, который из длинной ссылки, делает короткую. Короткая ссылка должна быть вида `http://localhost:<urlShortener SERVICE_PORT>/<ID>`, где `<ID>` - это `random alpha-numeric string of 10 characters`

Для этого в своём Java package `company.vk.edu.distrib.compute.<username>.urlshortener` реализуйте интерфейс [`UrlShortenerService`](src/main/java/company/vk/edu/distrib/compute/urlshortener/UrlShortenerService.java) и поддержите следующий HTTP API протокол:

* `GET /v0/status` -- `200` в нормальной ситуации, `503` в случае проблем.
* `GET /v0/links/<ID>` -- получить длинную ссылку по `<ID>` короткой ссылки. Возвращает `200 OK`, `Content-Type: text/html; charset=utf-8` и ссылку или `404 Not Found`. 
* `POST /v0/links` -- создать короткую ссылку для заданной в теле, `Content-Type: text/html; charset=utf-8`. Возвращает `201 Created`, `Content-Type: text/html; charset=utf-8` и короткую ссылку в теле. 
* `PUT /v0/links/<ID>` -- изменить существующую короткую ссылку по `<ID>` на заданную в теле, `Content-Type: text/html; charset=utf-8`. Возвращает `200 OK`, или `404 Not found` если такого `<ID>` нет.
* `DELETE /v0/links/<ID>` -- удалить ссылку по `<ID>`. Возвращает `202 Accepted`.
* `GET /<ID>` -- отдаётся редирект `301 Moved Permanently` и заголовок `Location: <длинная ссылка соотвествующая ID>`. `404 Not Found` если такого `<ID>` нет.
* Во всех случаях, когда передаётся либо не валидный `<ID>` либо невалидная ссылка в теле запроса (POST/PUT методы) - надо вернуть `422 Unprocessable Content`

1. Сделать наследника [`AbstractHttpServiceFactory`](src/main/java/company/vk/edu/distrib/compute/AbstractHttpServiceFactory.java) в пакете со своим именем/ником, у класса должен быть публичный конструктор **без параметров**
2. Ваша реализация интерфейса `UrlShortenerService`, возвращаемая из вашей же `AbstractHttpServiceFactory`, должна запускать [HttpServer из JDK](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html).
3. Ваш `UrlShortenerService` должен работать с вашей же реализацией интерфейса [`Dao`](src/main/java/company/vk/edu/distrib/compute/Dao.java) и делегировать работу по хранению данных.
4. В минимальной реализации `Dao` достаточно хранить данные в памяти. `T` в `Dao` будет `String`.
5. Пометить своего наследника `AbstractHttpServiceFactory` аннотацией [`UrlShortenerTest`](src/main/java/company/vk/edu/distrib/compute/urlshortener/UrlShortenerTest.java) -- тесты подберут его автоматически, изменять код тестов не требуется.

Продолжайте запускать тесты и исправлять ошибки, не забывая [подтягивать новые тесты и фиксы из `upstream`](https://help.github.com/articles/syncing-a-fork/). 
Если заметите ошибку в `upstream`, заводите баг и присылайте pull request ;)

### Аутентификация

1. Добавить проверку заголовка [basic-аутентификации](https://datatracker.ietf.org/doc/html/rfc7617) согласно со спекой.
2. Пользователей и пароли хранить в отдельном `Dao<String>`
3. Добавить в HTTP API протокол сервиса: `POST /internal/users` -- добавить пользователя, `Content-Type: text/html; charset=utf-8`, тело состоит из одной строки содержащей имя пользователя и пароль разделённые двоеточием (например `admin:super_pass`). Возвращает `200 OK`, если пользователь уже есть заменить пароль на заданный. Метод нужен, чтобы можно было наполнить базу пользователей для простоты тестирования. В реальных сервисах такое делается по-другому.
4. Аутентификацией должны быть закрыты все запросы, кроме `status`, `GET /<ID>` и `/intenal/users`
5. Пометить своего наследника `AbstractHttpServiceFactory` аннотацией [`UrlShortenerAuthTest`](src/main/java/company/vk/edu/distrib/compute/urlshortener/UrlShortenerAuthTest.java).

### Persistent Dao

1. Сделать Dao которые хранят данные на диске, чтобы переживали рестарты

### Критерии оценки

* Основное задание - 10 баллов
* Authentication - 5 балла
* Persistent Dao - 1 балл

### Отчёт
Когда всё будет готово, присылайте pull request со своей реализацией на проверку. Не забывайте **отвечать на комментарии в PR** и **исправлять замечания**!

## Домашнее задание № 2 по теме "Протоколы и модели сетевого взаимодействия" | KV service

Нужно вынести логику хранения данных в отдельный сервис - KV service.

В своём Java package `company.vk.edu.distrib.compute.<username>.kv` реализуйте интерфейс [`KVService`](src/main/java/company/vk/edu/distrib/compute/kv/KVService.java) и поддержите следующий HTTP API протокол:

* `GET /v0/status` -- 200 в нормальной ситуации, 503 в случае проблем.
* `GET /v0/entity?id=<ID>` -- получить данные по ключу <ID>. Возвращает 200 OK и данные или 404 Not Found.
* `PUT /v0/entity?id=<ID>` -- создать/перезаписать (upsert) данные по ключу <ID>. Возвращает 201 Created.
* `DELETE /v0/entity?id=<ID>` -- удалить данные по ключу <ID>. Возвращает 202 Accepted.

1. Сделать наследника [`AbstractHttpServiceFactory`](src/main/java/company/vk/edu/distrib/compute/AbstractHttpServiceFactory.java) в пакете со своим именем/ником.
2. Ваша реализация интерфейса [`KVService`](src/main/java/company/vk/edu/distrib/compute/kv/KVService.java), возвращаемая из вашей `AbstractHttpServiceFactory`, должна запускать [HttpServer из JDK](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html).
3. Ваш `KVService` должен работать с вашей же реализацией интерфейса [`Dao`](src/main/java/company/vk/edu/distrib/compute/Dao.java) и делегировать непосредственную работу с данными хранилища.
4. `Dao` должно хранить данные на диске. Для этого этапа `T` в `Dao` будет `byte[]`.
5. Далее вам нужно своём Java package `company.vk.edu.distrib.compute.<username>.kv`реализовать интерфейс [`RemoteDaoFactory`](src/main/java/company/vk/edu/distrib/compute/kv/RemoteDaoFactory.java), который должен адаптировать интерфейс `Dao` к вашему `KVService` т.е. делать HTTP запросы согласно контракту. Пока считаем что инстанс `KVService` будет только один.
6. Дополните ваш `UrlShortenerService` и поддержите новый метод `UrlShortenerService.setLinksDao` согласно описанию
7. Пометить вашего наследника `AbstractHttpServiceFactory` аннотацией [`KVServiceTest`](src/main/java/company/vk/edu/distrib/compute/kv/KVServiceTest.java), а `RemoteDaoFactory` аннотацией [`RemoteDaoFactoryTest`](src/main/java/company/vk/edu/distrib/compute/kv/RemoteDaoFactoryTest.java)
8. Убедитесь, что все тесты заканчиваются положительно

### Нагрузочное тестирование

Следующий протокол нагрузочного тестирования `KVService` нужно сделать в двух режимах:
1. В однопоточным JDK HttpServer т.е. таком, когда вы не вызываете `HttpServer.setExecutor` (или вызываете и передаёте однопоточный executor)
2. В многопоточном соответственно когда вы вызываете `HttpServer.setExecutor` с каким-то (опять же тут пространство для экспериментов) многопоточным executor 

#### Протокол НТ

Проведите нагрузочное тестирование вашего `KVService` с помощью [wrk](https://github.com/giltene/wrk2) в **одно соединение**. 
* `PUT` запросами на **стабильной** нагрузке (`wrk` должен обеспечивать заданный с помощью `-R` rate запросов) наполните базу
* `GET` запросами на **стабильной** нагрузке по **наполненной** БД

1. wrk2 можно собрать из исходников, взять в вашем дистре линукса или взять готовый докер, например [тут](https://hub.docker.com/r/haydenjeune/wrk2)
2. Для докера инструкция запуска wrk есть прямо по ссылке, помните, что в случае докера надо указать IP вашего сетевого интерфейса созданного докером (обычно это `docker0`), а не просто localhost
3. Запускайте со след. параметрами `-t1 -c1 -R200 -d30s --latency -s /data/request.lua`
4. Сохраните выведенную статистику (начинается после строчки Detailed Percentile spectrum:) в файл и отобразите [здесь](https://hdrhistogram.github.io/HdrHistogram/plotFiles.html)

После проведения HT в обоих режимах скрины графиков (PUT, GET, кнопка Export Image на сайте) надо приложить к вашему PR'у

### Критерии оценки

* Основное задание - 10 баллов
* Нагрузочное тестирование - 6 баллов

## Домашнее задание № 3 по теме "Шардирование"

Реализуем горизонтальное масштабирование через поддержку кластерных конфигураций, состоящих из нескольких узлов, взаимодействующих друг с другом через реализованный HTTP API.

* Кластер распределяет ключи между узлами детерминированным образом.
* В кластере хранится только одна копия данных. Таким образом, общая ёмкость кластера равна суммарной ёмкости входящих в него узлов.
* В качестве хранилища использовать реализацию remote dao из предыдущего задания
* Реализация remote dao, отправляет запрос на узел, отвечающий за обслуживание соответствующего ключа.
* Реализуйте один из алгоритмов распределения данных между узлами, например, consistent hashing, rendezvous hashing.

### Отчёт
Когда всё будет готово, присылайте pull request со своей реализацией на проверку. Не забывайте **отвечать на комментарии в PR** и **исправлять замечания**!

#### Нагрузочное тестирование

* Провести нагрузочное тестирование с помощью wrk2 на распределенный кластер с большим количеством соединений >= 64
* Запускайте со след. параметрами `-t2 -c100 -R200 -d30s --latency -s /data/request.lua`
* Сравнить с предыдущей (монолитной/не распределенной) версией.
* Результаты и анализ сравнения приложить к PR.

### Критерии оценки

* Основное задание - 10 баллов
* Нагрузочное тестирование - 6 баллов
