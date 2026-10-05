package com.alfas.amortizacioncredito.util

import java.util.regex.Pattern

data class CountryPhoneCode(
    val isoCode: String,
    val name: String,
    val flagEmoji: String,
    val dialCode: String,
    val maxDigits: Int = 10,
    val samplePlaceholder: String = "99 123 4567"
) {
    val displayLabel: String
        get() = "$flagEmoji $name ($dialCode)"

    val shortLabel: String
        get() = "$flagEmoji $dialCode"

    companion object {
        val ECUADOR = CountryPhoneCode("EC", "Ecuador", "🇪🇨", "+593", maxDigits = 9, samplePlaceholder = "99 123 4567")
        val COLOMBIA = CountryPhoneCode("CO", "Colombia", "🇨🇴", "+57", maxDigits = 10, samplePlaceholder = "300 123 4567")
        val PERU = CountryPhoneCode("PE", "Perú", "🇵🇪", "+51", maxDigits = 9, samplePlaceholder = "912 345 678")
        val USA = CountryPhoneCode("US", "Estados Unidos / Canadá", "🇺🇸", "+1", maxDigits = 10, samplePlaceholder = "202 555 0123")
        val SPAIN = CountryPhoneCode("ES", "España", "🇪🇸", "+34", maxDigits = 9, samplePlaceholder = "612 345 678")
        val MEXICO = CountryPhoneCode("MX", "México", "🇲🇽", "+52", maxDigits = 10, samplePlaceholder = "55 1234 5678")
        val CHILE = CountryPhoneCode("CL", "Chile", "🇨🇱", "+56", maxDigits = 9, samplePlaceholder = "9 1234 5678")
        val ARGENTINA = CountryPhoneCode("AR", "Argentina", "🇦🇷", "+54", maxDigits = 10, samplePlaceholder = "11 1234 5678")
        val BRAZIL = CountryPhoneCode("BR", "Brasil", "🇧🇷", "+55", maxDigits = 11, samplePlaceholder = "11 91234 5678")
        val PANAMA = CountryPhoneCode("PA", "Panamá", "🇵🇦", "+507", maxDigits = 8, samplePlaceholder = "6123 4567")
        val VENEZUELA = CountryPhoneCode("VE", "Venezuela", "🇻🇪", "+58", maxDigits = 10, samplePlaceholder = "412 123 4567")
        val BOLIVIA = CountryPhoneCode("BO", "Bolivia", "🇧🇴", "+591", maxDigits = 8, samplePlaceholder = "7123 4567")
        val ITALY = CountryPhoneCode("IT", "Italia", "🇮🇹", "+39", maxDigits = 10, samplePlaceholder = "312 345 6789")

        val ALL_COUNTRIES: List<CountryPhoneCode> = listOf(
            ECUADOR,
            COLOMBIA,
            PERU,
            USA,
            SPAIN,
            MEXICO,
            CHILE,
            ARGENTINA,
            BRAZIL,
            PANAMA,
            VENEZUELA,
            BOLIVIA,
            ITALY
        )

        fun findByDialCode(dialCode: String): CountryPhoneCode {
            val clean = if (dialCode.startsWith("+")) dialCode else "+$dialCode"
            return ALL_COUNTRIES.find { it.dialCode == clean } ?: ECUADOR
        }
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object ValidationUtils {

    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    )

    /**
     * Sanitiza cadenas de texto para eliminar caracteres de control invisibles,
     * tags de scripts y secuencias comunes de inyección.
     */
    fun sanitizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            // Remueve caracteres de control excepto saltos de línea y tabulaciones básicas
            .replace("[\\p{Cntrl}&&[^\r\n\t]]".toRegex(), "")
            // Remueve posibles etiquetas HTML/Script
            .replace("<[^>]*>".toRegex(), "")
            .trim()
    }

    /**
     * Valida si un correo electrónico cumple con un formato estándar válido.
     */
    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return true // Si es opcional
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    /**
     * Valida de manera estricta un correo que es obligatorio.
     */
    fun isStrictValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    /**
     * Filtra una cadena para que solo contenga dígitos numéricos y a lo sumo un punto decimal.
     * Rechaza letras, símbolos, signos negativos o múltiples puntos.
     */
    fun filterDecimalInput(input: String, maxDecimals: Int = 2): String {
        // Reemplazar comas por puntos
        val normalized = input.replace(',', '.')
        val clean = StringBuilder()
        var hasDot = false
        var decimalsCount = 0

        for (ch in normalized) {
            if (ch.isDigit()) {
                if (hasDot) {
                    if (decimalsCount < maxDecimals) {
                        clean.append(ch)
                        decimalsCount++
                    }
                } else {
                    clean.append(ch)
                }
            } else if (ch == '.' && !hasDot) {
                hasDot = true
                clean.append(ch)
            }
        }
        return clean.toString()
    }

    /**
     * Filtra una cadena para que contenga únicamente dígitos numéricos enteros.
     */
    fun filterDigitsOnly(input: String, maxLength: Int = 15): String {
        val digits = input.filter { it.isDigit() }
        return if (digits.length > maxLength) digits.take(maxLength) else digits
    }

    /**
     * Valida la cédula de identidad de Ecuador usando el algoritmo de Módulo 10.
     * Retorna true si es una cédula ecuatoriana matemáticamente válida.
     */
    fun isValidEcuadorianCedula(cedula: String): Boolean {
        val clean = cedula.trim().filter { it.isDigit() }
        if (clean.length != 10) return false

        // Los dos primeros dígitos corresponden a la provincia (01 - 24 o 30 para ecuatorianos en el exterior)
        val province = clean.substring(0, 2).toIntOrNull() ?: return false
        if (!((province in 1..24) || province == 30)) return false

        // El tercer dígito para personas naturales debe ser menor a 6
        val thirdDigit = clean[2].digitToIntOrNull() ?: return false
        if (thirdDigit >= 6) return false

        // Algoritmo Módulo 10
        val coefficients = intArrayOf(2, 1, 2, 1, 2, 1, 2, 1, 2)
        var sum = 0
        for (i in 0 until 9) {
            val digit = clean[i].digitToInt()
            val prod = digit * coefficients[i]
            sum += if (prod >= 10) prod - 9 else prod
        }

        val verifierDigit = clean[9].digitToInt()
        val calculatedVerifier = (10 - (sum % 10)) % 10

        return verifierDigit == calculatedVerifier
    }

    /**
     * Valida un RUC de Ecuador (13 dígitos).
     * Soporta:
     * - Personas Naturales (tercer dígito < 6, algoritmo Módulo 10 en primeros 10 dígitos)
     * - Sociedades Privadas / Extranjeros (tercer dígito = 9, algoritmo Módulo 11)
     * - Entidades Públicas (tercer dígito = 6, algoritmo Módulo 11)
     */
    fun isValidEcuadorianRuc(ruc: String): Boolean {
        val clean = ruc.trim().filter { it.isDigit() }
        if (clean.length != 13) return false
        if (clean == "9999999999999") return true // Consumidor Final

        val province = clean.substring(0, 2).toIntOrNull() ?: return false
        if (!((province in 1..24) || province == 30)) return false

        val thirdDigit = clean[2].digitToIntOrNull() ?: return false

        // 1. Persona Natural: tercer dígito < 6. Los 10 primeros dígitos son cédula válida.
        if (thirdDigit < 6) {
            if (!clean.endsWith("001") && !clean.endsWith("002") && !clean.endsWith("003") && !clean.endsWith("004")) return false
            val cedulaPart = clean.substring(0, 10)
            return isValidEcuadorianCedula(cedulaPart)
        }
        // 2. Sociedades Privadas / Extranjeras sin cédula: tercer dígito = 9. Módulo 11
        else if (thirdDigit == 9) {
            if (!clean.substring(10).startsWith("00")) return false // Establecimiento termina en 001, etc.
            val coefficients = intArrayOf(4, 3, 2, 7, 6, 5, 4, 3, 2)
            var sum = 0
            for (i in 0 until 9) {
                sum += clean[i].digitToInt() * coefficients[i]
            }
            val verifier = clean[9].digitToInt()
            val rem = sum % 11
            val calculated = if (rem == 0) 0 else 11 - rem
            return verifier == calculated
        }
        // 3. Entidades Públicas: tercer dígito = 6. Módulo 11
        else if (thirdDigit == 6) {
            if (!clean.substring(9).startsWith("000")) return false // Establecimiento termina en 0001, etc.
            val coefficients = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)
            var sum = 0
            for (i in 0 until 8) {
                sum += clean[i].digitToInt() * coefficients[i]
            }
            val verifier = clean[8].digitToInt()
            val rem = sum % 11
            val calculated = if (rem == 0) 0 else 11 - rem
            return verifier == calculated
        }

        return false
    }

    /**
     * Valida identificación según el país seleccionado.
     * Si es Ecuador: Aplica validación estricta de Cédula (10 dígitos) o RUC (13 dígitos).
     * Si es otro país: Aplica validación general segura contra inyecciones y longitud adecuada.
     */
    fun validateIdentification(idNumber: String?, country: CountryPhoneCode): ValidationResult {
        if (idNumber.isNullOrBlank()) {
            return ValidationResult(isValid = true) // Opcional
        }
        val clean = sanitizeText(idNumber).trim()

        if (country == CountryPhoneCode.ECUADOR) {
            // Consumidor Final
            if (clean == "9999999999999" || clean == "9999999999") {
                return ValidationResult(isValid = true)
            }

            // Si contiene caracteres que no son dígitos
            if (clean.any { !it.isDigit() }) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "La cédula o RUC en Ecuador solo debe contener números"
                )
            }

            return when {
                clean.length < 10 -> ValidationResult(
                    isValid = false,
                    errorMessage = "Cédula incompleta (debe tener 10 dígitos)"
                )
                clean.length == 10 -> {
                    if (isValidEcuadorianCedula(clean)) {
                        ValidationResult(isValid = true)
                    } else {
                        ValidationResult(
                            isValid = false,
                            errorMessage = "Cédula ecuatoriana no válida (dígito verificador incorrecto)"
                        )
                    }
                }
                clean.length in 11..12 -> ValidationResult(
                    isValid = false,
                    errorMessage = "RUC incompleto (debe tener 13 dígitos)"
                )
                clean.length == 13 -> {
                    if (isValidEcuadorianRuc(clean)) {
                        ValidationResult(isValid = true)
                    } else {
                        ValidationResult(
                            isValid = false,
                            errorMessage = "RUC ecuatoriano no válido"
                        )
                    }
                }
                else -> ValidationResult(
                    isValid = false,
                    errorMessage = "El RUC de Ecuador no puede tener más de 13 dígitos"
                )
            }
        }

        // Otros países: Se admite formato alfanumérico limpio (entre 4 y 20 caracteres)
        val isAlphanumeric = clean.matches("^[a-zA-Z0-9-_.]{4,20}$".toRegex())
        return if (isAlphanumeric) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(isValid = false, errorMessage = "Formato de documento inválido (use caracteres alfanuméricos)")
        }
    }

    /**
     * Extrae el código de país y el número local de una cadena telefónica guardada.
     * Si no tiene código de país, asume Ecuador (+593).
     */
    fun parsePhoneAndCountry(storedPhone: String?): Pair<CountryPhoneCode, String> {
        if (storedPhone.isNullOrBlank()) return Pair(CountryPhoneCode.ECUADOR, "")
        val trimmed = storedPhone.trim()

        for (country in CountryPhoneCode.ALL_COUNTRIES) {
            if (trimmed.startsWith(country.dialCode)) {
                val localPart = trimmed.removePrefix(country.dialCode).trim().filter { it.isDigit() }
                return Pair(country, localPart)
            }
        }

        // Si empieza con 09... o similar sin código de país, asumir Ecuador
        val rawDigits = trimmed.filter { it.isDigit() }
        val localClean = if (rawDigits.startsWith("593")) rawDigits.removePrefix("593") else rawDigits
        return Pair(CountryPhoneCode.ECUADOR, localClean)
    }

    /**
     * Ensambla el número telefónico completo con prefijo internacional.
     */
    fun formatFullPhone(country: CountryPhoneCode, localNumber: String): String {
        val cleanDigits = localNumber.trim().filter { it.isDigit() }
        if (cleanDigits.isEmpty()) return ""
        // Si el usuario en Ecuador ingresó 09... quitar el cero líder para el estándar internacional
        val standardLocal = if (country == CountryPhoneCode.ECUADOR && cleanDigits.startsWith("0")) {
            cleanDigits.substring(1)
        } else {
            cleanDigits
        }
        return "${country.dialCode} $standardLocal"
    }
}
