package com.alfas.amortizacioncredito.domain.security

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firmador Criptográfico Nativo PAdES (PDF Advanced Electronic Signatures - ISO 32000-1 / RFC 2315 / RFC 5652).
 *
 * Cumple con los estándares de validación de FirmaEC (Gobierno de Ecuador / MINTEL / ARCOTEL) y Adobe Acrobat Reader.
 * - Inserta el diccionario de firma /Type /Sig con subfiltro /SubFilter /adbe.pkcs7.detached.
 * - Calcula el /ByteRange exacto y hashea el PDF con SHA-256.
 * - Construye la estructura binaria DER ASN.1 PKCS#7 / CMS SignedData firmada con la clave privada del certificado .p12.
 * - Embebe la cadena de certificados X.509 de la entidad emisora (Security Data, BCE, ANFAC, Judicatura, etc.).
 */
object PdfPadesSigner {

    private const val PLACEHOLDER_HEX_SIZE = 16384 // 8192 bytes para el contenedor PKCS#7
    private const val OID_SIGNED_DATA = "1.2.840.113549.1.7.2"
    private const val OID_DATA = "1.2.840.113549.1.7.1"
    private const val OID_SHA256 = "2.16.840.1.101.3.4.2.1"
    private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val OID_ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2"
    private const val OID_CONTENT_TYPE = "1.2.840.113549.1.9.3"
    private const val OID_MESSAGE_DIGEST = "1.2.840.113549.1.9.4"
    private const val OID_SIGNING_TIME = "1.2.840.113549.1.9.5"

    /**
     * Firma criptográficamente un archivo PDF con la clave privada y certificado de un .p12.
     * Retorna el archivo PDF firmado o null si ocurre algún error.
     */
    fun signPdfFile(
        inputFile: File,
        outputFile: File,
        privateKey: PrivateKey,
        certificateChain: Array<X509Certificate>,
        signerName: String = "",
        reason: String = "Comprobante de Pago Oficial - Ley de Comercio Electrónico y Firmas Ecuador",
        location: String = "Ecuador",
        contactInfo: String = ""
    ): Boolean {
        return try {
            val inputBytes = FileInputStream(inputFile).use { it.readBytes() }
            val signedBytes = signPdfBytes(
                pdfBytes = inputBytes,
                privateKey = privateKey,
                certificateChain = certificateChain,
                signerName = signerName,
                reason = reason,
                location = location,
                contactInfo = contactInfo
            ) ?: return false

            FileOutputStream(outputFile).use { out ->
                out.write(signedBytes)
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Firma los bytes en memoria de un PDF y retorna el byte array con la estructura PAdES completa.
     */
    fun signPdfBytes(
        pdfBytes: ByteArray,
        privateKey: PrivateKey,
        certificateChain: Array<X509Certificate>,
        signerName: String = "",
        reason: String = "Comprobante de Pago Oficial - Ley de Comercio Electrónico y Firmas Ecuador",
        location: String = "Ecuador",
        contactInfo: String = ""
    ): ByteArray? {
        return try {
            if (certificateChain.isEmpty()) return null

            val signingCert = certificateChain[0]
            val resolvedSignerName = signerName.ifBlank {
                extractCommonName(signingCert.subjectDN.name) ?: "Firmante Autorizado"
            }

            // 1. Analizar estructura del PDF original
            val pdfStr = String(pdfBytes, Charsets.ISO_8859_1)

            // Buscar el último startxref
            val lastStartXrefIdx = pdfStr.lastIndexOf("startxref")
            if (lastStartXrefIdx == -1) return null

            val prevXrefOffset = pdfStr.substring(lastStartXrefIdx + 9).trim().takeWhile { it.isDigit() }

            // Buscar el número de objeto más alto
            val objRegex = Regex("(\\d+)\\s+\\d+\\s+obj")
            var maxObjNum = 1
            for (match in objRegex.findAll(pdfStr)) {
                val num = match.groupValues[1].toIntOrNull() ?: 1
                if (num > maxObjNum) maxObjNum = num
            }

            // Buscar la referencia del Catalog (/Root X Y R)
            val rootRegex = Regex("/Root\\s+(\\d+)\\s+(\\d+)\\s+R")
            val rootMatch = rootRegex.find(pdfStr) ?: return null
            val origCatalogObjNum = rootMatch.groupValues[1].toInt()
            val origCatalogGenNum = rootMatch.groupValues[2].toInt()

            // Buscar la referencia de /Pages dentro del Catalog original
            val catalogObjRegex = Regex("$origCatalogObjNum\\s+$origCatalogGenNum\\s+obj\\s*<<(.*?)>>", RegexOption.DOT_MATCHES_ALL)
            val catalogContent = catalogObjRegex.find(pdfStr)?.groupValues?.get(1) ?: ""
            val pagesRegex = Regex("/Pages\\s+(\\d+)\\s+(\\d+)\\s+R")
            val pagesMatch = pagesRegex.find(catalogContent)
            val pagesRefStr = if (pagesMatch != null) "/Pages ${pagesMatch.groupValues[1]} ${pagesMatch.groupValues[2]} R" else "/Pages 2 0 R"

            // 2. Asignar nuevos números de objetos para la actualización incremental
            val acroFormObjNum = maxObjNum + 1
            val sigFieldObjNum = maxObjNum + 2
            val sigDictObjNum = maxObjNum + 3
            val newCatalogObjNum = maxObjNum + 4

            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT-5")
            }
            val dateStr = "D:" + dateFormat.format(Date()) + "-05'00'"

            // Placeholder de ceros para el espacio de firma
            val placeholderContents = "0".repeat(PLACEHOLDER_HEX_SIZE)

            // 3. Construir la sección incremental
            val incrementalBuilder = StringBuilder()
            val objOffsets = mutableListOf<Pair<Int, Int>>() // Pair(objNum, offsetInNewSection)

            // Espacio en blanco inicial
            incrementalBuilder.append("\n")

            // Objeto AcroForm
            val acroFormOffset = incrementalBuilder.length
            objOffsets.add(Pair(acroFormObjNum, acroFormOffset))
            incrementalBuilder.append("$acroFormObjNum 0 obj\n")
            incrementalBuilder.append("<<\n")
            incrementalBuilder.append("  /Fields [ $sigFieldObjNum 0 R ]\n")
            incrementalBuilder.append("  /SigFlags 3\n")
            incrementalBuilder.append(">>\n")
            incrementalBuilder.append("endobj\n")

            // Objeto Signature Field Annotation
            val sigFieldOffset = incrementalBuilder.length
            objOffsets.add(Pair(sigFieldObjNum, sigFieldOffset))
            incrementalBuilder.append("$sigFieldObjNum 0 obj\n")
            incrementalBuilder.append("<<\n")
            incrementalBuilder.append("  /Type /Annot\n")
            incrementalBuilder.append("  /Subtype /Widget\n")
            incrementalBuilder.append("  /FT /Sig\n")
            incrementalBuilder.append("  /Rect [ 0 0 0 0 ]\n")
            incrementalBuilder.append("  /V $sigDictObjNum 0 R\n")
            incrementalBuilder.append("  /T (FirmaElectronicaEcuador)\n")
            incrementalBuilder.append("  /F 4\n")
            incrementalBuilder.append(">>\n")
            incrementalBuilder.append("endobj\n")

            // Objeto Signature Dictionary
            val sigDictOffset = incrementalBuilder.length
            objOffsets.add(Pair(sigDictObjNum, sigDictOffset))
            incrementalBuilder.append("$sigDictObjNum 0 obj\n")
            incrementalBuilder.append("<<\n")
            incrementalBuilder.append("  /Type /Sig\n")
            incrementalBuilder.append("  /Filter /Adobe.PPKLite\n")
            incrementalBuilder.append("  /SubFilter /adbe.pkcs7.detached\n")
            incrementalBuilder.append("  /Name (${escapePdfString(resolvedSignerName)})\n")
            incrementalBuilder.append("  /M ($dateStr)\n")
            incrementalBuilder.append("  /Reason (${escapePdfString(reason)})\n")
            incrementalBuilder.append("  /Location (${escapePdfString(location)})\n")
            if (contactInfo.isNotBlank()) {
                incrementalBuilder.append("  /ContactInfo (${escapePdfString(contactInfo)})\n")
            }
            incrementalBuilder.append("  /ByteRange [ 0000000000 0000000000 0000000000 0000000000 ]\n")
            incrementalBuilder.append("  /Contents <$placeholderContents>\n")
            incrementalBuilder.append(">>\n")
            incrementalBuilder.append("endobj\n")

            // Objeto Nuevo Catalog (Root) que referencia el AcroForm
            val newCatalogOffset = incrementalBuilder.length
            objOffsets.add(Pair(newCatalogObjNum, newCatalogOffset))
            incrementalBuilder.append("$newCatalogObjNum 0 obj\n")
            incrementalBuilder.append("<<\n")
            incrementalBuilder.append("  /Type /Catalog\n")
            incrementalBuilder.append("  $pagesRefStr\n")
            incrementalBuilder.append("  /AcroForm $acroFormObjNum 0 R\n")
            incrementalBuilder.append(">>\n")
            incrementalBuilder.append("endobj\n")

            // Tabla xref incremental
            val newXrefOffsetInIncremental = incrementalBuilder.length
            val baseLength = pdfBytes.size

            incrementalBuilder.append("xref\n")
            incrementalBuilder.append("$acroFormObjNum 4\n")
            for (obj in objOffsets) {
                val absoluteOffset = baseLength + obj.second
                incrementalBuilder.append(String.format(Locale.US, "%010d 00000 n \n", absoluteOffset))
            }

            // Trailer incremental
            incrementalBuilder.append("trailer\n")
            incrementalBuilder.append("<<\n")
            incrementalBuilder.append("  /Size ${newCatalogObjNum + 1}\n")
            incrementalBuilder.append("  /Root $newCatalogObjNum 0 R\n")
            incrementalBuilder.append("  /Prev $prevXrefOffset\n")
            incrementalBuilder.append(">>\n")
            incrementalBuilder.append("startxref\n")
            incrementalBuilder.append("${baseLength + newXrefOffsetInIncremental}\n")
            incrementalBuilder.append("%%EOF\n")

            val incrementalBytes = incrementalBuilder.toString().toByteArray(Charsets.ISO_8859_1)

            // Unir PDF base + sección incremental
            val fullSignedPdf = ByteArray(baseLength + incrementalBytes.size)
            System.arraycopy(pdfBytes, 0, fullSignedPdf, 0, baseLength)
            System.arraycopy(incrementalBytes, 0, fullSignedPdf, baseLength, incrementalBytes.size)

            val fullPdfStr = String(fullSignedPdf, Charsets.ISO_8859_1)

            // 4. Localizar posiciones exactas de /Contents <...> y /ByteRange
            val contentsTag = "/Contents <"
            val contentsIdx = fullPdfStr.indexOf(contentsTag)
            if (contentsIdx == -1) return null

            val contentsStart = contentsIdx + contentsTag.length
            val contentsEnd = contentsStart + PLACEHOLDER_HEX_SIZE // posición del '>' de cierre

            val byteRange1Start = 0
            val byteRange1Len = contentsStart - 1 // Incluye el '<'

            val byteRange2Start = contentsEnd + 1 // Comienza inmediatamente después de '>'
            val byteRange2Len = fullSignedPdf.size - byteRange2Start

            // Formatear ByteRange con 10 dígitos fijos
            val formattedByteRange = String.format(
                Locale.US,
                "/ByteRange [ %010d %010d %010d %010d ]",
                byteRange1Start,
                byteRange1Len,
                byteRange2Start,
                byteRange2Len
            )

            val byteRangeTag = "/ByteRange [ 0000000000 0000000000 0000000000 0000000000 ]"
            val byteRangeIdx = fullPdfStr.indexOf(byteRangeTag)
            if (byteRangeIdx == -1) return null

            val byteRangeBytes = formattedByteRange.toByteArray(Charsets.ISO_8859_1)
            System.arraycopy(byteRangeBytes, 0, fullSignedPdf, byteRangeIdx, byteRangeBytes.size)

            // 5. Calcular Hash SHA-256 de las dos partes del PDF (ByteRange)
            val md = MessageDigest.getInstance("SHA-256")
            md.update(fullSignedPdf, byteRange1Start, byteRange1Len)
            md.update(fullSignedPdf, byteRange2Start, byteRange2Len)
            val pdfDigest = md.digest()

            // 6. Construir estructura criptográfica DER ASN.1 PKCS#7 / CMS SignedData
            val pkcs7Der = createPkcs7SignedData(
                pdfDigest = pdfDigest,
                privateKey = privateKey,
                certificateChain = certificateChain
            ) ?: return null

            // 7. Convertir la firma PKCS#7 a Hex y colocarla dentro de /Contents <...>
            val hexSignature = pkcs7Der.joinToString("") { "%02X".format(it) }
            if (hexSignature.length > PLACEHOLDER_HEX_SIZE) {
                System.err.println("Error: El contenedor PKCS#7 excede el espacio reservado")
                return null
            }

            // Rellenar con ceros hasta completar el tamaño reservado
            val paddedHex = hexSignature.padEnd(PLACEHOLDER_HEX_SIZE, '0')
            val paddedHexBytes = paddedHex.toByteArray(Charsets.ISO_8859_1)

            System.arraycopy(paddedHexBytes, 0, fullSignedPdf, contentsStart, paddedHexBytes.size)

            fullSignedPdf
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Construye un contenedor estándar DER ASN.1 PKCS#7 / CMS SignedData (RFC 2315 / RFC 5652).
     */
    private fun createPkcs7SignedData(
        pdfDigest: ByteArray,
        privateKey: PrivateKey,
        certificateChain: Array<X509Certificate>
    ): ByteArray? {
        return try {
            val signingCert = certificateChain[0]

            // 1. Signed Attributes (ContentType, SigningTime, MessageDigest)
            // Atributo 1: ContentType (1.2.840.113549.1.9.3) -> 1.2.840.113549.1.7.1 (data)
            val attrContentType = derSequence(
                encodeOid(OID_CONTENT_TYPE),
                derSet(encodeOid(OID_DATA))
            )

            // Atributo 2: SigningTime (1.2.840.113549.1.9.5) -> UTCTime
            val utcFormat = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val utcTimeStr = utcFormat.format(Date())
            val attrSigningTime = derSequence(
                encodeOid(OID_SIGNING_TIME),
                derSet(derUtcTime(utcTimeStr))
            )

            // Atributo 3: MessageDigest (1.2.840.113549.1.9.4) -> OctetString(pdfDigest)
            val attrMessageDigest = derSequence(
                encodeOid(OID_MESSAGE_DIGEST),
                derSet(derOctetString(pdfDigest))
            )

            // Los atributos deben estar ordenados en DER por su codificación
            val signedAttrsList = listOf(attrContentType, attrSigningTime, attrMessageDigest).sortedWith(
                Comparator { a, b -> compareBytes(a, b) }
            )

            // Bytes para firmar: SET OF Attribute (Tag 0x31)
            val signedAttrsSetBytes = derSet(*signedAttrsList.toTypedArray())

            // 2. Firmar los atributos con la clave privada (RSA o ECDSA)
            val sigAlgo = if (privateKey.algorithm.contains("EC", ignoreCase = true)) {
                "SHA256withECDSA"
            } else {
                "SHA256withRSA"
            }

            val signer = Signature.getInstance(sigAlgo)
            signer.initSign(privateKey)
            signer.update(signedAttrsSetBytes)
            val signatureBytes = signer.sign()

            // 3. Algoritmo de firma OID
            val sigAlgOid = if (privateKey.algorithm.contains("EC", ignoreCase = true)) {
                OID_ECDSA_WITH_SHA256
            } else {
                OID_SHA256_WITH_RSA
            }
            val signatureAlgorithmIdentifier = derSequence(
                encodeOid(sigAlgOid),
                derNull()
            )

            // 4. SignerInfo
            // IssuerAndSerialNumber
            val issuerDer = signingCert.issuerX500Principal.encoded
            val serialNumberDer = derInteger(signingCert.serialNumber.toByteArray())
            val issuerAndSerial = derSequence(issuerDer, serialNumberDer)

            // DigestAlgorithmIdentifier
            val digestAlgorithmIdentifier = derSequence(
                encodeOid(OID_SHA256),
                derNull()
            )

            // Atributos firmados para embeber en SignerInfo: [0] IMPLICIT Attributes (Tag 0xA0)
            val signedAttrsContextTag = derTagged(0xA0, *signedAttrsList.toTypedArray())

            val signerInfo = derSequence(
                derInteger(1), // version
                issuerAndSerial,
                digestAlgorithmIdentifier,
                signedAttrsContextTag,
                signatureAlgorithmIdentifier,
                derOctetString(signatureBytes)
            )

            // 5. Cadena de Certificados [0] IMPLICIT CertificateSet (Tag 0xA0)
            val certsBaos = ByteArrayOutputStream()
            for (cert in certificateChain) {
                certsBaos.write(cert.encoded)
            }
            val certsTagged = derTaggedRaw(0xA0, certsBaos.toByteArray())

            // 6. SignedData
            val encapContentInfo = derSequence(
                encodeOid(OID_DATA)
            )

            val digestAlgorithmsSet = derSet(
                derSequence(
                    encodeOid(OID_SHA256),
                    derNull()
                )
            )

            val signedDataBody = derSequence(
                derInteger(1), // version
                digestAlgorithmsSet,
                encapContentInfo,
                certsTagged,
                derSet(signerInfo)
            )

            // 7. ContentInfo: 1.2.840.113549.1.7.2 (signedData) + [0] EXPLICIT signedDataBody
            val explicitSignedData = derTaggedRaw(0xA0, signedDataBody)

            derSequence(
                encodeOid(OID_SIGNED_DATA),
                explicitSignedData
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==========================================
    // UTILIDADES ASN.1 DER ENCODING
    // ==========================================

    private fun derLength(length: Int): ByteArray {
        return when {
            length < 128 -> byteArrayOf(length.toByte())
            length < 256 -> byteArrayOf(0x81.toByte(), length.toByte())
            length < 65536 -> byteArrayOf(0x82.toByte(), ((length shr 8) and 0xFF).toByte(), (length and 0xFF).toByte())
            else -> byteArrayOf(
                0x83.toByte(),
                ((length shr 16) and 0xFF).toByte(),
                ((length shr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte()
            )
        }
    }

    private fun derSequence(vararg items: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        for (item in items) baos.write(item)
        val body = baos.toByteArray()
        val len = derLength(body.size)
        return byteArrayOf(0x30.toByte()) + len + body
    }

    private fun derSet(vararg items: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        for (item in items) baos.write(item)
        val body = baos.toByteArray()
        val len = derLength(body.size)
        return byteArrayOf(0x31.toByte()) + len + body
    }

    private fun derTagged(tag: Int, vararg items: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        for (item in items) baos.write(item)
        val body = baos.toByteArray()
        val len = derLength(body.size)
        return byteArrayOf(tag.toByte()) + len + body
    }

    private fun derTaggedRaw(tag: Int, rawBody: ByteArray): ByteArray {
        val len = derLength(rawBody.size)
        return byteArrayOf(tag.toByte()) + len + rawBody
    }

    private fun derOctetString(bytes: ByteArray): ByteArray {
        val len = derLength(bytes.size)
        return byteArrayOf(0x04.toByte()) + len + bytes
    }

    private fun derInteger(value: Int): ByteArray {
        return if (value == 0) {
            byteArrayOf(0x02, 0x01, 0x00)
        } else {
            val list = mutableListOf<Byte>()
            var v = value
            while (v > 0) {
                list.add((v and 0xFF).toByte())
                v = v shr 8
            }
            list.reverse()
            if ((list[0].toInt() and 0x80) != 0) {
                list.add(0, 0x00.toByte())
            }
            val body = list.toByteArray()
            byteArrayOf(0x02) + derLength(body.size) + body
        }
    }

    private fun derInteger(bytes: ByteArray): ByteArray {
        val payload = if ((bytes[0].toInt() and 0x80) != 0) {
            byteArrayOf(0x00) + bytes
        } else {
            bytes
        }
        return byteArrayOf(0x02) + derLength(payload.size) + payload
    }

    private fun derNull(): ByteArray {
        return byteArrayOf(0x05, 0x00)
    }

    private fun derUtcTime(timeStr: String): ByteArray {
        val body = timeStr.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x17) + derLength(body.size) + body
    }

    private fun encodeOid(oidStr: String): ByteArray {
        val parts = oidStr.split(".").map { it.toInt() }
        val first = 40 * parts[0] + parts[1]
        val list = mutableListOf<Int>()
        list.add(first)
        for (i in 2 until parts.size) {
            var p = parts[i]
            if (p < 128) {
                list.add(p)
            } else {
                val buf = mutableListOf<Int>()
                buf.add(p and 0x7F)
                p = p shr 7
                while (p > 0) {
                    buf.add((p and 0x7F) or 0x80)
                    p = p shr 7
                }
                buf.reverse()
                list.addAll(buf)
            }
        }
        val body = list.map { it.toByte() }.toByteArray()
        return byteArrayOf(0x06) + derLength(body.size) + body
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val byteA = a[i].toInt() and 0xFF
            val byteB = b[i].toInt() and 0xFF
            if (byteA != byteB) return byteA.compareTo(byteB)
        }
        return a.size.compareTo(b.size)
    }

    private fun extractCommonName(dn: String): String? {
        val parts = dn.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("CN=", ignoreCase = true)) {
                return trimmed.substring(3).trim()
            }
        }
        return null
    }

    private fun escapePdfString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
    }
}
