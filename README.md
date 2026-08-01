# Zinah Android App

تطبيق أندرويد لـ "زينة" للأدعية والأذكار. يعرض التطبيق واجهة بها أذكار ويسمح للمستخدم بتحديد مدة التذكير (كل ساعة، الخ) لظهور إشعارات بها أذكار مختلفة.

## الميزات الجديدة (v1.9) — مواقيت الصلاة مع الأذان

تمت إضافة ميزة كاملة لحساب مواقيت الصلاة الخمس وتشغيل الأذان تلقائيًا عند دخول كل وقت.

### الميزات
- ✅ **تبويب منفصل** «مواقيت الصلاة» في شريط تنقل سفلي (Bottom Navigation)
- ✅ **تحديد الموقع تلقائيًا** عبر GPS (FusedLocationProviderClient) مع خيار الاختيار اليدوي للمدينة
- ✅ **حساب المواقيت** عبر Aladhan API المجاني (يدعم 9 طرق حساب: أم القرى، MWL، المصرية، الخليج، الكويت، قطر، فرنسا، تركيا، ISNA)
- ✅ **تشغيل الأذان** عند دخول الوقت عبر Foreground Service (type=mediaPlayback) لضمان استمراره حتى لو أُغلق التطبيق
- ✅ **شاشة أذان كاملة** (AdhanActivity) تظهر فوق قفل الشاشة مع زر «إيقاف»
- ✅ **تفعيل منفصل لكل صلاة** (الفجر، الظهر، العصر، المغرب، العشاء)
- ✅ **اختيار صوت الأذان** (مكة / المدينة / نغمة قصيرة)
- ✅ **عد تنازلي** للصلاة القادمة يُحدَّث كل 30 ثانية
- ✅ **إعادة جدولة تلقائية** بعد إعادة تشغيل الجهاز (BootReceiver)
- ✅ **عرض التاريخ الهجري** مع اسم اليوم بالعربية
- ✅ **إشعار heads-up** مع زر «إيقاف الأذان»

### الملفات الجديدة
| الملف | الوصف |
|---|---|
| `PrayerTime.kt` | نموذج البيانات: `PrayerType` enum + `PrayerTimings` data class مع parser لاستجابة Aladhan API |
| `AdhanApiService.kt` | عميل Aladhan API (OkHttp) — `fetchTimingsByCoordinates` و `fetchTimingsByCity` |
| `PrayerTimePreferences.kt` | SharedPreferences helper لكل إعدادات المواقيت |
| `LocationHelper.kt` | FusedLocationProviderClient wrapper مع reverse-geocode لاسم المدينة |
| `PrayerTimeScheduler.kt` | جدولة منبهات AlarmManager لكل صلاة (5 PendingIntent منفصلة) |
| `AdhanPlayer.kt` | MediaPlayer singleton بصوت USAGE_ALARM يتجاوز وضع عدم الإزعاج |
| `AdhanForegroundService.kt` | Foreground service (mediaPlayback) يشغّل الأذان في الخلفية |
| `PrayerAlarmReceiver.kt` | BroadcastReceiver يُطلق عند دخول الوقت — يبدأ الخدمة + الشاشة + الإشعار |
| `AdhanActivity.kt` | شاشة ملء الشاشة مع اسم الصلاة وزر الإيقاف |
| `PrayerTimesScreen.kt` | واجهة Compose لتبويب المواقيت |
| `res/raw/adhan_makkah.mp3` | صوت أذان مكة (placeholder — استبدله بالملف الحقيقي) |
| `res/raw/adhan_madinah.mp3` | صوت أذان المدينة (placeholder — استبدله بالملف الحقيقي) |

### الملفات المعدلة
| الملف | التعديل |
|---|---|
| `AndroidManifest.xml` | إضافة `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + تسجيل `AdhanActivity`, `AdhanForegroundService`, `PrayerAlarmReceiver` |
| `app/build.gradle.kts` | إضافة `play-services-location`, `okhttp`, `kotlinx-coroutines-android`, `material-icons-extended` + رفع الإصدار إلى 1.9 |
| `BootReceiver.kt` | إعادة جدولة مواقيت الصلاة بعد إعادة التشغيل (coroutine) |
| `MainActivity.kt` | إضافة Bottom Navigation بتبويبين + استخراج محتوى الأذكار إلى `AdhkarContent()` Composable |
| `res/values/strings.xml` | إضافة ترجمات للتبويبات والصلوات |

### كيفية الاستخدام
1. افتح التطبيق → اضغط على تبويب **«مواقيت الصلاة»** في الأسفل
2. فعّل المفتاح الرئيسي «مواقيت الصلاة»
3. اختر طريقة تحديد الموقع:
   - **GPS تلقائي** (يطلب إذن الموقع)
   - **اختيار يدوي** (أدخل: `Makkah,Saudi Arabia` أو `Cairo,Egypt` ...)
4. اختر طريقة الحساب (افتراضي: أم القرى)
5. اختر صوت الأذان (مكة / المدينة / نغمة قصيرة)
6. فعّل/أوقف الأذان لكل صلاة على حدة
7. عند دخول الوقت: يظهر إشعار + شاشة كاملة + يُشغَّل الأذان تلقائيًا

### استبدال ملفات الأذان
الملفات `res/raw/adhan_makkah.mp3` و `res/raw/adhan_madinah.mp3` هي **نسخ مؤقتة** من `sali_ala_mohammad.mp3`. لاستخدام الأذان الحقيقي:
1. حمّل ملف أذان MP3 من مصدر موثوق
2. استبدل الملفات في `app/src/main/res/raw/` (نفس الاسم)
3. أعد بناء التطبيق

### الأذونات المطلوبة
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — لتحديد الموقع عبر GPS
- `INTERNET` / `ACCESS_NETWORK_STATE` — لاستدعاء Aladhan API
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — لجدولة المنبهات بدقة
- `WAKE_LOCK` — لإيقاظ الجهاز عند دخول الوقت
- `USE_FULL_SCREEN_INTENT` — لعرض شاشة الأذان فوق القفل
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — لتشغيل الأذان في الخلفية
- `RECEIVE_BOOT_COMPLETED` — لإعادة الجدولة بعد إعادة التشغيل
- `POST_NOTIFICATIONS` — لإظهار إشعار دخول الوقت
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — لمنع النظام من إيقاف التطبيق

### API المستخدم
- **Aladhan Prayer Times API**: https://aladhan.com/prayer-times-api
  - `GET /v1/timings/{timestamp}?latitude=..&longitude=..&method=..`
  - `GET /v1/timingsByCity?city=..&country=..&method=..`
  - مجاني، بدون مفتاح API، يدعم 9 طرق حساب مختلفة
