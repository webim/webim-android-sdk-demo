# Webim SDK demo
[ ![Download](https://api.bintray.com/packages/webim/maven/WebimSdkAndroid/images/download.svg) ](https://bintray.com/webim/maven/WebimSdkAndroid/_latestVersion)
Приложение, демонстрирующее возможности *Webim Android SDK*. Данное приложение опубликовано в [Google Play](https://play.google.com/store/apps/details?id=ru.webim.demo.client).

[Онлайн консультант Webim](https://webim.ru) — это многофункциональный сервис для консультирования посетителей вашего сайта через всплывающее окно чата — его можно бесплатно скачать и установить в виде универсального скрипта или CMS модуля на нужные страницы, а кроме того интегрировать в фирменное мобильное приложение. Библиотека *Webim Android SDK* предоставляет разработчикам мобильных приложений на платформе Google Android средства для интеграции в эти приложения чата между их пользователями и операторами компании-разработчика на основе технологий, применяющихся в онлайн консультанте Вебим.

Данное приложение создано с целью демонстрации, с одной стороны, возможностей нашего SDK, с другой - того, как правильно наш SDK использовать. Во время интеграции чата в ваше собственное приложение, ориентируйтесь на это демо-приложение - оно значительно упростит вам интеграцию.

## Установка SDK
Поддерживается Android версии  4.+ (Android API 15+)
Чтобы начать использовать *Webim Android SDK*, добавьте зависимость в build.gradle вашего приложения
```
compile 'com.webimapp.sdk:webimclientsdkandroid:3.0.1'
```
А также добавьте в AndroidManifest.xml следующие разрешения:
```
<uses-permission android:name="android.permission.INTERNET"/>
```

## Использование
Публичный API находится в пакете com.webimapp.android.sdk и только в нем. Все вложенные пакеты служат исключительно для внутренних нужд SDK
### WebimSession
Для начала работы с чатом необходимо получить объект WebimSession.
```java
WebimSession sessoin = Webim.newSessionBuilder()
    .setContext(context) // Activity ot Application
    .setErrorHandler(errorHandler) // implementation of FatalErrorHandler
    .setAccountName("demo") // Webim account name
    .setLocation("mobile") // Use "mobile" if in doubt
    .build()
```
Для этого необходимо определиться с [размещением](http://webim.ru/pro/help/help-terms/#location) и аккаунтом в сервисе Webim. Метод `Webim.newSessionBuilder()` возвращает builder object, содержащий большое количество методов конфигурации сессии. Подробнее о каждом из них читайте в javadoc.

Метод `build()` должен вызываться из потока (Thread), имеющего ассоциированный объект `android.os.Looper` (обычно это main thread). Далее любая работа с сессией должна выполняться из того же потока, в контексте которого сессия была создана (при вызове любого метода сессии проверяется, из какого потока он был вызван). Все коллбэки будут также выполнены в контексте потока, в котором была создана сессия.

WebimSession содержит методы для управления сетью: `resume()`, `pause()`, `destroy()`. Изначально сессия создается приостановленной, т.е. чтобы начать ее использовать, первым делом необходимо вызвать `resume()`. `pause()` вновь приостанавливает сессию, т.е останавливает использование сетевых ресурсов. Метод `destroy()`, в свою очередь полностью освобождает все ресурсы, занимаемые сессией, и после его вызова восстановление сессии невозможно (при вызове любого метода сессии бросается `IllegalStateException`). Методы `destroy()` и `pause()` разрешается вызывает на разрушенной сессии (при этом никаких действий не выполняется). Это сделано для интеграции с жизненным циклом Activity. Рекомендуется вызывать `pause()` из `Activity.onStop()`, `resume()` - из `Activity.onStart()`, `destroy()` - из `Activity.onDestroy()`.

Метод `getStream()` возвращает объект `MessageStream`, при помощи которого производится вся остальная работа с чатом.

### Просмотр истории сообщений
SDK позволяет просматривать историю сообщений и отслеживать изменение истории (редактирование/удаление сообщений). Для этого используется объект `MessageTracker` возвращаемый методом `MessageStream.newMessageTracker(MessageListener)`.
При каждом вызове `MessageTracker.getNextMessages(int, GetMessagesCallback)` коллбэк получает следующую порцию сообщений выше по истории. При этом `MessageTracker` сохраняет запрошенный интервал сообщений и отслеживает изменения в этом интервале. Эти изменения передаются объекту, реализующему интерфейс `MessageListener`, который необходмо передать методу `MessageStream.newMessageTracker(MessageListener)` при создании объекта `MessageTracker`. Также `MessageListener` будет получать новые сообщения.

### Отправка сообщений
Для отправки сообщений используется метод `MessageStream.sendMessage(String)`. При этом отправляемое сообщение будет сразу же получено в `MessageListener.messageAdded` (при наличии активного `MessageTracker`). После подтверждения приема сообщения сервером будет вызван метод `MessageListener.messageChanged` уже с актуальным сообщением. 

### Работа с push-notifications
Для начала необходимо сконфигурировать сессию для работы с пуш-уведомлениями. Для этого при создании сессии необходимо вызвать метод `SessionBuilder.setPushSystem(PushSystem.GCM)`. В результате этого вебим-сервис будет отправлять пуш-уведомления для вашего устройства. Принятые пуши проходят мимо нашего SDK, и в своем приложении вы должны принимать их самостоятельно. Пример вы можете посмотреть в демо-приложении. После того как пуш принят, вы должны сравнить поле `from` с `Webim.getGcmSenderId()`, и если они совпадают, значит этот пуш отправлен сервисом вебим. Далее, для десериализации данных пуша используется метод `Webim.parseGcmPushNotification(Bundle)`
```java
if(intent.getStringExtra("from").equals(Webim.getGcmSenderId())) {
    WebimPushNotification push = Webim.parseGcmPushNotification(intent.getExtras());
    // process webim push-notification
} else {
    // process your application push-notification
}
```















