package com.example.ui

import com.example.BuildConfig
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object EmailRecoveryHelper {

    private const val GMAIL_SMTP_HOST = "smtp.gmail.com"
    private const val GMAIL_SMTP_PORT_465 = 465
    private const val GMAIL_SMTP_PORT_587 = 587
    private val SENDER_EMAIL = BuildConfig.SMTP_EMAIL
    private val SENDER_APP_PASSWORD = BuildConfig.SMTP_PASSWORD // 16-character secure app password without spaces

    /**
     * Generates a secure, random 6-digit numeric verification OTP.
     */
    fun generateVerificationCode(): String {
        val random = java.security.SecureRandom()
        val code = 100000 + random.nextInt(900000)
        return code.toString()
    }

    /**
     * Sends the verification code to the target recovery email using Gmail SMTP.
     * Tries clean SSL on port 465 first, with automated fallback to STARTTLS on port 587.
     */
    suspend fun sendRecoveryEmail(
        recipientEmail: String,
        verificationCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        // Try Port 465 (SMTPS) first
        try {
            return@withContext runSmtpSsl(recipientEmail, verificationCode)
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = e
        }

        // Try Port 587 (STARTTLS) as high-resilience fallback
        try {
            return@withContext runSmtpStartTls(recipientEmail, verificationCode)
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = e
        }

        Result.failure(Exception("فشلت جميع محاولات الإرسال الآمن للبريد. السبب الأخير: ${lastError?.message}", lastError))
    }

    private fun runSmtpSsl(recipientEmail: String, verificationCode: String): Result<Unit> {
        val rawSocket = Socket()
        try {
            rawSocket.connect(java.net.InetSocketAddress(GMAIL_SMTP_HOST, GMAIL_SMTP_PORT_465), 15000) // 15 seconds connect timeout
            rawSocket.soTimeout = 15000 // 15 seconds read timeout

            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(
                rawSocket,
                GMAIL_SMTP_HOST,
                GMAIL_SMTP_PORT_465,
                true
            ) as SSLSocket
            sslSocket.startHandshake()

            val reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), "UTF-8"))
            val writer = PrintWriter(OutputStreamWriter(sslSocket.getOutputStream(), "UTF-8"), true)

            val greeting = readSmtpResponse(reader)
            if (!greeting.startsWith("220")) throw Exception("خادم SMTP غير مستعد: $greeting")

            executeSmtpFlow(reader, writer, recipientEmail, verificationCode)
            
            writer.println("QUIT")
            return Result.success(Unit)
        } finally {
            try { rawSocket.close() } catch (ignored: Exception) {}
        }
    }

    private fun runSmtpStartTls(recipientEmail: String, verificationCode: String): Result<Unit> {
        val rawSocket = Socket()
        try {
            rawSocket.connect(java.net.InetSocketAddress(GMAIL_SMTP_HOST, GMAIL_SMTP_PORT_587), 15000) // 15 seconds connect timeout
            rawSocket.soTimeout = 15000 // 15 seconds read timeout

            var reader = BufferedReader(InputStreamReader(rawSocket.getInputStream(), "UTF-8"))
            var writer = PrintWriter(OutputStreamWriter(rawSocket.getOutputStream(), "UTF-8"), true)

            val greeting = readSmtpResponse(reader)
            if (!greeting.startsWith("220")) throw Exception("خادم SMTP غير مستعد: $greeting")

            writer.println("EHLO localhost")
            readSmtpResponse(reader)

            writer.println("STARTTLS")
            val startTlsResponse = readSmtpResponse(reader)
            if (!startTlsResponse.startsWith("220")) {
                throw Exception("فشل ترقية الاتصال الآمن STARTTLS: $startTlsResponse")
            }

            // Upgrade raw socket to SSL socket
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(
                rawSocket,
                GMAIL_SMTP_HOST,
                GMAIL_SMTP_PORT_587,
                true
            ) as SSLSocket
            sslSocket.startHandshake()

            reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), "UTF-8"))
            writer = PrintWriter(OutputStreamWriter(sslSocket.getOutputStream(), "UTF-8"), true)

            executeSmtpFlow(reader, writer, recipientEmail, verificationCode)

            writer.println("QUIT")
            return Result.success(Unit)
        } finally {
            try { rawSocket.close() } catch (ignored: Exception) {}
        }
    }

    private fun executeSmtpFlow(
        reader: BufferedReader,
        writer: PrintWriter,
        recipientEmail: String,
        verificationCode: String
    ) {
        // EHLO Greeting
        writer.println("EHLO localhost")
        readSmtpResponse(reader)

        // Auth Login Request
        writer.println("AUTH LOGIN")
        readSmtpResponse(reader)

        // Send base64 username
        val b64User = Base64.encodeToString(SENDER_EMAIL.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        writer.println(b64User)
        readSmtpResponse(reader)

        // Send base64 password
        val b64Pass = Base64.encodeToString(SENDER_APP_PASSWORD.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        writer.println(b64Pass)
        val authResponse = readSmtpResponse(reader)
        if (!authResponse.startsWith("235") && !authResponse.contains("Accepted")) {
            throw Exception("فشل التحقق من البريد: $authResponse")
        }

        // MAIL FROM
        writer.println("MAIL FROM:<$SENDER_EMAIL>")
        readSmtpResponse(reader)

        // RCPT TO
        writer.println("RCPT TO:<$recipientEmail>")
        readSmtpResponse(reader)

        // DATA mode
        writer.println("DATA")
        val dataResponse = readSmtpResponse(reader)
        if (!dataResponse.startsWith("354")) {
            throw Exception("خطأ في بدء إرسال البيانات: $dataResponse")
        }

        // Subject, MIME, and localized headers
        writer.println("From: $SENDER_EMAIL")
        writer.println("To: $recipientEmail")
        
        val subjectBase64 = Base64.encodeToString(
            "رمز التحقق لإعادة تعيين كلمة مرور دفتر الحسابات".toByteArray(Charsets.UTF_8), 
            Base64.NO_WRAP
        )
        writer.println("Subject: =?UTF-8?B?$subjectBase64?=")
        writer.println("MIME-Version: 1.0")
        writer.println("Content-Type: text/html; charset=utf-8")
        writer.println()

        // HTML styled message
        val dateStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val deviceManufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        
        val htmlMessage = """
            <div style="direction: rtl; font-family: 'Segoe UI', Arial, sans-serif; text-align: right; line-height: 1.6; color: #333333; max-width: 580px; margin: 0 auto; border: 1px solid #E0E0E0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 10px rgba(0,0,0,0.05);">
                <div style="background-color: #3F51B5; padding: 24px; text-align: center; color: #FFFFFF;">
                    <h2 style="margin: 0; font-size: 24px; font-weight: bold;">تطبيق دفتر الحسابات الذكي</h2>
                    <p style="margin: 4px 0 0 0; font-size: 14px; opacity: 0.85;">نظام استرداد الحسابات الآمن</p>
                </div>
                <div style="padding: 30px; background-color: #FFFFFF;">
                    <p style="font-size: 16px; font-weight: bold; color: #3F51B5; margin-top: 0;">طلب إعادة تعيين كلمة المرور</p>
                    <p style="font-size: 14px; margin-bottom: 24px;">مرحباً، لقد تلقينا طلباً لإعادة تعيين كلمة مرور التطبيق الخاصة بك. يرجى استخدام رمز الأمان المؤقت التالي للتحقق من ملكيتك لتفعيل تعيين رمز المرور الجديد:</p>
                    
                    <div style="background-color: #F8F9FA; border: 2px dashed #3F51B5; padding: 20px; border-radius: 8px; text-align: center; margin: 24px 0;">
                        <span style="font-size: 34px; font-weight: bold; color: #E91E63; letter-spacing: 6px; padding: 4px 12px; background: #FFF5F7; border-radius: 4px; display: inline-block;">$verificationCode</span>
                    </div>
                    
                    <p style="font-size: 13px; color: #E53935; font-weight: bold; margin-bottom: 24px;">تنبيه أمني: ينتهي صلاحية هذا الرمز قريباً. يرجى عدم مشاركة هذا الرمز مع أي شخص لحماية دفاتر حساباتك المالية.</p>
                    
                    <hr style="border: none; border-top: 1px solid #EEEEEE; margin: 24px 0;" />
                    
                    <table style="width: 100%; font-size: 12px; color: #9E9E9E; border-collapse: collapse;">
                        <tr style="border-bottom: 1px solid #F5F5F5;">
                            <td style="padding: 6px 0;"><strong>تاريخ الطلب:</strong> $dateStr</td>
                        </tr>
                        <tr style="border-bottom: 1px solid #F5F5F5;">
                            <td style="padding: 6px 0;"><strong>جهاز وموديل الطلب:</strong> $deviceManufacturer $deviceModel (Android $androidVersion)</td>
                        </tr>
                        <tr>
                            <td style="padding: 6px 0;"><strong>نوع المحاولة:</strong> استعادة رمز المرور الآمن والمزامن</td>
                        </tr>
                    </table>
                </div>
                <div style="background-color: #F5F5F5; padding: 16px; text-align: center; font-size: 12px; color: #757575; border-top: 1px solid #EEEEEE;">
                    جميع الحقوق محفوظة &copy; تطبيق دفتر الحسابات الذكي 2026.
                </div>
            </div>
        """.trimIndent()

        writer.println(htmlMessage)
        writer.println(".") // SMTP message sequence terminator
        
        val sendResponse = readSmtpResponse(reader)
        if (!sendResponse.startsWith("250")) {
            throw Exception("فشل خاوم SMTP في قبول محتوى الرسالة: $sendResponse")
        }
    }

    private fun readSmtpResponse(reader: BufferedReader): String {
        val response = StringBuilder()
        var line = reader.readLine() ?: return ""
        response.append(line).append("\n")
        // Loop while the response indicates multiline (dash (-) as the fourth character)
        while (line!!.length > 3 && line[3] == '-') {
            val nextLine = reader.readLine() ?: break
            line = nextLine
            response.append(nextLine).append("\n")
        }
        return response.toString()
    }
}
