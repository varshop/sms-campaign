import 'package:flutter_sms/flutter_sms.dart';
import 'package:permission_handler/permission_handler.dart';

/// خطأ إرسال برسالة عربية مفهومة. [fatal] يعني إيقاف الحملة كلها.
class SmsSendException implements Exception {
  const SmsSendException(this.message, {this.fatal = false});

  final String message;
  final bool fatal;

  @override
  String toString() => message;
}

/// طبقة رقيقة فوق حزمة flutter_sms لإرسال الرسائل المباشرة.
class SmsSender {
  SmsSender();

  /// يطلب صلاحية SEND_SMS، ويعيد true إن كانت ممنوحة.
  Future<bool> requestPermission() async {
    var status = await Permission.sms.status;
    if (!status.isGranted) {
      status = await Permission.sms.request();
    }
    return status.isGranted;
  }

  /// يرسل رسالة فورية عبر الشريحة
  Future<void> send(String phone, String message) async {
    bool hasPermission = await requestPermission();
    if (!hasPermission) {
      throw const SmsSendException(
        'صلاحية إرسال الرسائل غير ممنوحة',
        fatal: true,
      );
    }

    try {
      String result = await sendSMS(
        message: message,
        recipients: [phone],
        sendDirect: true, // الإرسال المباشر فوراً من الشريحة
      );

      if (result != 'sent' && !result.contains('sent')) {
        throw SmsSendException('تعذّر تأكيد إرسال الرسالة ($result)');
      }
    } catch (e) {
      if (e is SmsSendException) rethrow;
      throw SmsSendException('فشل إرسال الرسالة إلى $phone: ${e.toString()}');
    }
  }
}
