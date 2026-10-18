import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

/// خطأ إرسال برسالة عربية مفهومة. [fatal] يعني إيقاف الحملة كلها (مثل غياب الصلاحية).
class SmsSendException implements Exception {
  const SmsSendException(this.message, {this.fatal = false});

  final String message;
  final bool fatal;

  @override
  String toString() => message;
}

/// الإرسال عبر كود Kotlin داخل التطبيق (MainActivity) باستخدام SmsManager،
/// وطلب الصلاحية عبر permission_handler.
class SmsSender {
  static const MethodChannel _channel = MethodChannel('sms_campaign/sms');

  /// يطلب صلاحية SEND_SMS، ويعيد true إن كانت ممنوحة.
  Future<bool> requestPermission() async {
    if (await Permission.sms.isGranted) return true;
    final status = await Permission.sms.request();
    return status.isGranted;
  }

  /// يرسل رسالة ويكتمل عند تأكيد الراديو للإرسال، أو يرمي [SmsSendException].
  Future<void> send(String phone, String message) async {
    try {
      await _channel.invokeMethod<bool>('sendSms', {
        'phone': phone,
        'message': message,
      });
    } on PlatformException catch (e) {
      throw _map(e);
    } on MissingPluginException {
      throw const SmsSendException(
        'خدمة الرسائل غير متاحة في هذه النسخة',
        fatal: true,
      );
    }
  }

  SmsSendException _map(PlatformException e) {
    switch (e.code) {
      case 'PERMISSION':
        return const SmsSendException(
          'صلاحية إرسال الرسائل غير ممنوحة',
          fatal: true,
        );
      case 'NO_TELEPHONY':
        return const SmsSendException(
          'هذا الجهاز لا يدعم إرسال الرسائل',
          fatal: true,
        );
      case 'NO_SMS_MANAGER':
        return const SmsSendException(
          'تعذّر تهيئة خدمة الرسائل على هذا الجهاز',
          fatal: true,
        );
      case 'INVALID_ARGS':
        return const SmsSendException('رقم أو نص غير صالح');
      case 'NO_SERVICE':
        return const SmsSendException('لا توجد تغطية شبكة');
      case 'RADIO_OFF':
        return const SmsSendException('الراديو مغلق (وضع الطيران؟)');
      case 'LIMIT':
        return const SmsSendException('تجاوز حد الإرسال المسموح في أندرويد');
      case 'GENERIC':
        return const SmsSendException('فشل الإرسال من الراديو (تحقق من الرصيد والشريحة)');
      case 'TIMEOUT':
        return const SmsSendException('لم يُؤكَّد الإرسال (قد تكون أُرسلت)');
      default:
        return SmsSendException('تعذّر الإرسال (${e.code})');
    }
  }
}
