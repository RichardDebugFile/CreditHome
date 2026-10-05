package com.alfas.amortizacioncredito.domain.service

import android.util.Base64
import com.alfas.amortizacioncredito.data.model.SmtpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class GmailSmtpSender {

    suspend fun sendEmail(
        config: SmtpConfig,
        recipientEmail: String,
        subject: String,
        htmlBody: String,
        plainTextBody: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(Exception("El servidor SMTP de Google no está configurado o faltan credenciales."))
        }

        var socket: Socket? = null
        try {
            val host = config.host.ifBlank { "smtp.gmail.com" }
            val port = if (config.port > 0) config.port else 465

            if (config.useSsl || port == 465) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket() as SSLSocket
                sslSocket.connect(InetSocketAddress(host, port), 12000)
                sslSocket.startHandshake()
                socket = sslSocket
            } else {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 12000)
            }

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            fun readResponse(): String {
                val sb = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append("\n")
                    // En SMTP, líneas intermedias tienen un guión '-' después del código (ej. 250-), y la final tiene un espacio (ej. 250 )
                    if (line.length >= 4 && line[3] == ' ') break
                    if (line.length < 4) break
                }
                return sb.toString().trim()
            }

            fun sendCommand(cmd: String, expectedCode: String): String {
                writer.write("$cmd\r\n")
                writer.flush()
                val response = readResponse()
                if (!response.startsWith(expectedCode)) {
                    throw Exception("Error SMTP en comando '$cmd': $response")
                }
                return response
            }

            // 1. Saludo inicial del servidor
            val initial = readResponse()
            if (!initial.startsWith("220")) {
                throw Exception("Servidor SMTP no listo: $initial")
            }

            // 2. EHLO
            sendCommand("EHLO localhost", "250")

            // 3. AUTH LOGIN
            sendCommand("AUTH LOGIN", "334")

            // Usuario en Base64
            val userB64 = Base64.encodeToString(config.senderEmail.trim().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            sendCommand(userB64, "334")

            // Contraseña de aplicación en Base64
            val cleanPass = config.appPassword.replace(" ", "").trim()
            val passB64 = Base64.encodeToString(cleanPass.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val authResp = sendCommand(passB64, "235")

            // 4. MAIL FROM
            sendCommand("MAIL FROM:<${config.senderEmail.trim()}>", "250")

            // 5. RCPT TO
            sendCommand("RCPT TO:<${recipientEmail.trim()}>", "250")

            // 6. DATA
            sendCommand("DATA", "354")

            // 7. Enviar cabeceras y cuerpo MIME
            val boundary = "====Boundary_${UUID.randomUUID().toString().replace("-", "")}===="
            val dateStr = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())

            val headers = StringBuilder()
            headers.append("From: =?UTF-8?B?${Base64.encodeToString(config.senderName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}?= <${config.senderEmail.trim()}>\r\n")
            headers.append("To: <${recipientEmail.trim()}>\r\n")
            headers.append("Subject: =?UTF-8?B?${Base64.encodeToString(subject.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}?=\r\n")
            headers.append("Date: $dateStr\r\n")
            headers.append("MIME-Version: 1.0\r\n")
            headers.append("Content-Type: multipart/alternative; boundary=\"$boundary\"\r\n")
            headers.append("\r\n")

            writer.write(headers.toString())

            // Parte de texto plano
            val plainText = if (plainTextBody.isNotBlank()) plainTextBody else "Por favor abre este correo en un cliente compatible con HTML."
            writer.write("--$boundary\r\n")
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n")
            writer.write("Content-Transfer-Encoding: base64\r\n\r\n")
            writer.write(Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.DEFAULT) + "\r\n")

            // Parte HTML
            writer.write("--$boundary\r\n")
            writer.write("Content-Type: text/html; charset=UTF-8\r\n")
            writer.write("Content-Transfer-Encoding: base64\r\n\r\n")
            writer.write(Base64.encodeToString(htmlBody.toByteArray(Charsets.UTF_8), Base64.DEFAULT) + "\r\n")

            // Fin de multipart
            writer.write("--$boundary--\r\n")

            // Fin del mensaje DATA
            sendCommand(".", "250")

            // 8. QUIT
            try {
                sendCommand("QUIT", "221")
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            val msg = if (e.message?.contains("535", ignoreCase = true) == true || e.message?.contains("BadCredentials", ignoreCase = true) == true) {
                "Error de autenticación Google (535): Verifica que hayas usado una 'Contraseña de Aplicación' de 16 dígitos y no tu contraseña personal de Gmail."
            } else {
                e.localizedMessage ?: "Error desconocido al enviar correo"
            }
            Result.failure(Exception(msg))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
