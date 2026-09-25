# tablodecori — مدیریت قیمت‌گذاری و سفارش کارگاه

اپلیکیشن Native Android فارسی و RTL برای مدیریت متریال، ست‌های چندتکه، محاسبه قیمت، قیمت‌نامه و سفارش‌های ارسالی. Prototype HTML فقط به‌عنوان مرجع UX و قواعد اولیه استفاده شده و هیچ WebView در پروژه وجود ندارد.

## تکنولوژی و معماری

- Kotlin + Jetpack Compose + Material 3
- Room Database، Offline First و ماندگاری کامل اطلاعات
- MVVM + Repository Pattern + Flow/Coroutines
- Navigation Compose
- موتور `PricingEngine` مستقل از UI و Room؛ مناسب انتقال بعدی به API/Web
- پول در دیتابیس با `Long` (تومان) و محاسبات نسبتی/مساحت با `BigDecimal`
- Soft Delete برای متریال و محصول، Snapshot مالی مستقل برای سفارش‌های تاریخی

## Build

پیش‌نیازها: JDK 17، Android SDK Platform 36. برای ساخت APK:

```bash
./gradlew test lintDebug assembleDebug
```

APK در `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود.
