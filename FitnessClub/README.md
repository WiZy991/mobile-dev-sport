# Fitness Club - Android приложение для фитнес-клуба

Мобильное приложение для клиентов фитнес-клуба, разработанное на Kotlin с использованием Jetpack Compose.

## Функциональность

### Реализовано:
- **Авторизация и регистрация** - вход по email/паролю, регистрация новых пользователей
- **Расписание тренировок** - просмотр расписания по дням, фильтрация по типу тренировок
- **Запись на тренировки** - бронирование мест на групповые и персональные тренировки
- **Лист ожидания** - возможность записаться в очередь при отсутствии мест
- **Мои записи** - просмотр предстоящих и прошедших тренировок, отмена записи
- **Профиль пользователя** - информация о пользователе, бонусные баллы
- **Абонементы** - просмотр активных абонементов, заморозка/разморозка
- **Push-уведомления** - интеграция с Firebase Cloud Messaging

## Технологический стек

- **Kotlin** - основной язык разработки
- **Jetpack Compose** - современный UI toolkit
- **Material 3** - дизайн-система
- **MVVM** - архитектурный паттерн
- **Hilt** - Dependency Injection
- **Retrofit + OkHttp** - работа с REST API
- **Coroutines + Flow** - асинхронное программирование
- **DataStore** - локальное хранение данных
- **Navigation Compose** - навигация между экранами
- **Coil** - загрузка изображений
- **Firebase Messaging** - push-уведомления

## Структура проекта

```
app/src/main/java/com/fitnessclub/app/
├── data/
│   ├── api/           # Retrofit API интерфейсы
│   ├── local/         # Локальное хранение (DataStore)
│   ├── model/         # Data классы
│   ├── repository/    # Репозитории
│   └── service/       # Сервисы (Firebase)
├── di/                # Hilt модули
├── ui/
│   ├── navigation/    # Навигация
│   ├── screens/       # Экраны приложения
│   │   ├── auth/      # Авторизация
│   │   ├── main/      # Главный экран
│   │   ├── schedule/  # Расписание
│   │   ├── mytrainings/ # Мои записи
│   │   ├── profile/   # Профиль
│   │   └── training/  # Детали тренировки
│   └── theme/         # Тема приложения
├── FitnessClubApp.kt  # Application класс
└── MainActivity.kt    # Главная Activity
```

## Настройка проекта

### Требования
- Android Studio Hedgehog или новее
- JDK 17
- Android SDK 34

### Сборка
1. Клонируйте репозиторий
2. Откройте проект в Android Studio
3. Синхронизируйте Gradle
4. Запустите приложение

### Конфигурация API
Измените BASE_URL в `AppModule.kt`:
```kotlin
private const val BASE_URL = "https://your-api-url.com/v1/"
```

### Firebase
1. Создайте проект в Firebase Console
2. Добавьте `google-services.json` в папку `app/`
3. Включите Firebase Cloud Messaging

## API Endpoints

Приложение ожидает следующие API endpoints:

### Auth
- `POST /auth/login` - авторизация
- `POST /auth/register` - регистрация
- `POST /auth/refresh` - обновление токена
- `POST /auth/logout` - выход

### User
- `GET /user/profile` - профиль пользователя
- `PUT /user/profile` - обновление профиля
- `POST /user/push-token` - регистрация push токена

### Trainings
- `GET /trainings` - список тренировок (query: date, type)
- `GET /trainings/{id}` - детали тренировки
- `POST /trainings/{id}/book` - запись на тренировку
- `POST /trainings/{id}/waiting-list` - запись в лист ожидания

### Bookings
- `GET /bookings` - мои записи
- `DELETE /bookings/{id}` - отмена записи

### Subscriptions
- `GET /subscriptions` - мои абонементы
- `GET /subscriptions/plans` - доступные тарифы
- `POST /subscriptions/{id}/freeze` - заморозка (query: days)
- `POST /subscriptions/{id}/unfreeze` - разморозка

## Дальнейшее развитие

### Планируемые функции:
- [ ] Покупка абонементов в приложении
- [ ] Интеграция платежных систем (СБП, банковские карты)
- [ ] QR-код для входа в клуб
- [ ] Рейтинг и отзывы о тренерах
- [ ] Чат с тренером
- [ ] Трекер прогресса
- [ ] Персональные программы тренировок
- [ ] Интеграция с фитнес-трекерами

## Лицензия

MIT License
